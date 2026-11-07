package com.raushan.jarvis;

import android.content.ContentResolver;
import android.content.Context;
import android.net.Uri;
import android.os.Environment;
import android.os.StatFs;
import android.provider.DocumentsContract;
import android.database.Cursor;
import android.text.format.Formatter;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Scoped, user-granted storage operations for JARVIS.
 *
 * This class deliberately works only inside a tree URI explicitly selected
 * by the device owner through ACTION_OPEN_DOCUMENT_TREE. It never attempts
 * to enumerate arbitrary shared storage or another application's private data.
 */
public final class JarvisDataCore {
    private static final int MAX_NODES_TO_SCAN = 1800;
    private static final int MAX_DEPTH = 10;
    private static final int MAX_TEXT_BYTES = 160 * 1024;

    private JarvisDataCore() { }

    public static final class Entry {
        public final String id;
        public final String parentId;
        public final String name;
        public final String mimeType;
        public final boolean directory;

        Entry(String id, String parentId, String name, String mimeType) {
            this.id = id;
            this.parentId = parentId;
            this.name = name == null ? "Unnamed" : name;
            this.mimeType = mimeType == null ? "" : mimeType;
            this.directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(this.mimeType);
        }

        public Uri asUri(Uri treeUri) {
            return DocumentsContract.buildDocumentUriUsingTree(treeUri, id);
        }
    }

    private static final class Node {
        final String id;
        final String parentId;
        final int depth;

        Node(String id, String parentId, int depth) {
            this.id = id;
            this.parentId = parentId;
            this.depth = depth;
        }
    }

    public static Uri savedTree(Context context) {
        String value = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
                .getString("document_tree_uri", "");
        if (value == null || value.trim().isEmpty()) return null;
        try { return Uri.parse(value); }
        catch (Exception ignored) { return null; }
    }

    public static boolean hasWriteGrant(Context context, Uri treeUri) {
        if (treeUri == null) return false;
        try {
            for (android.content.UriPermission permission :
                    context.getContentResolver().getPersistedUriPermissions()) {
                if (treeUri.equals(permission.getUri()) && permission.isWritePermission()) return true;
            }
        } catch (Exception ignored) { }
        return false;
    }

    /**
     * Searches display names below the selected tree. The scan is bounded so
     * a large provider cannot freeze the UI or consume unbounded resources.
     */
    public static ArrayList<Entry> search(Context context, Uri treeUri, String query,
                                          boolean includeDirectories, int maxResults) throws Exception {
        if (treeUri == null) throw new SecurityException("No user-selected folder is available.");
        String term = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (term.isEmpty()) throw new IllegalArgumentException("Enter a file or folder name to search for.");

        ContentResolver resolver = context.getContentResolver();
        ArrayList<Entry> matches = new ArrayList<>();
        ArrayDeque<Node> queue = new ArrayDeque<>();
        Set<String> visited = new HashSet<>();
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        queue.add(new Node(rootId, null, 0));
        int scanned = 0;

        while (!queue.isEmpty() && scanned < MAX_NODES_TO_SCAN && matches.size() < maxResults) {
            Node parent = queue.removeFirst();
            if (!visited.add(parent.id)) continue;
            scanned++;

            Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parent.id);
            Cursor cursor = null;
            try {
                cursor = resolver.query(childrenUri, new String[] {
                        DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                        DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                        DocumentsContract.Document.COLUMN_MIME_TYPE
                }, null, null, DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC");

                if (cursor == null) continue;
                while (cursor.moveToNext() && matches.size() < maxResults) {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    String mime = cursor.getString(2);
                    if (id == null) continue;

                    Entry entry = new Entry(id, parent.id, name, mime);
                    if (entry.name.toLowerCase(Locale.ROOT).contains(term)
                            && (includeDirectories || !entry.directory)) {
                        matches.add(entry);
                    }
                    if (entry.directory && parent.depth < MAX_DEPTH && !visited.contains(id)) {
                        queue.addLast(new Node(id, parent.id, parent.depth + 1));
                    }
                }
            } finally {
                if (cursor != null) cursor.close();
            }
        }
        return matches;
    }

    public static String readText(Context context, Uri fileUri) throws Exception {
        if (fileUri == null) throw new IllegalArgumentException("No file was selected.");
        String name = queryDisplayName(context, fileUri);
        String mime = context.getContentResolver().getType(fileUri);
        if (!isReadableText(name, mime)) {
            throw new UnsupportedOperationException("This file format cannot be extracted as text by the current JARVIS build.");
        }

        InputStream input = null;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        boolean truncated = false;
        try {
            input = context.getContentResolver().openInputStream(fileUri);
            if (input == null) throw new IllegalStateException("The selected provider did not open the file.");
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                int remaining = MAX_TEXT_BYTES - output.size();
                if (remaining <= 0) { truncated = true; break; }
                int write = Math.min(count, remaining);
                output.write(buffer, 0, write);
                if (write < count) { truncated = true; break; }
            }
        } finally {
            if (input != null) try { input.close(); } catch (Exception ignored) { }
            try { output.close(); } catch (Exception ignored) { }
        }
        String text = new String(output.toByteArray(), StandardCharsets.UTF_8);
        if (truncated) text += "\n\n[Preview shortened at 160 KB.]";
        return text;
    }

    public static Uri createTextFile(Context context, Uri treeUri, String fileName,
                                     String contents) throws Exception {
        requireWriteGrant(context, treeUri);
        String safeName = validateName(fileName);
        String rootId = DocumentsContract.getTreeDocumentId(treeUri);
        Uri rootDocument = DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId);
        Uri created = DocumentsContract.createDocument(context.getContentResolver(),
                rootDocument, "text/plain", safeName);
        if (created == null) throw new IllegalStateException("The selected folder provider could not create a document.");

        OutputStream output = null;
        try {
            output = context.getContentResolver().openOutputStream(created, "wt");
            if (output == null) throw new IllegalStateException("The new file could not be opened for writing.");
            output.write((contents == null ? "" : contents).getBytes(StandardCharsets.UTF_8));
            output.flush();
        } catch (Exception error) {
            try { DocumentsContract.deleteDocument(context.getContentResolver(), created); }
            catch (Exception ignored) { }
            throw error;
        } finally {
            if (output != null) try { output.close(); } catch (Exception ignored) { }
        }
        return created;
    }

    public static Uri rename(Context context, Uri treeUri, Entry entry, String newName) throws Exception {
        requireWriteGrant(context, treeUri);
        if (entry == null || entry.directory) throw new IllegalArgumentException("Choose a file to rename.");
        return DocumentsContract.renameDocument(context.getContentResolver(), entry.asUri(treeUri),
                validateName(newName));
    }

    public static Uri copyToFolder(Context context, Uri treeUri, Entry entry,
                                   String destinationFolderId) throws Exception {
        requireWriteGrant(context, treeUri);
        if (entry == null || entry.directory) throw new IllegalArgumentException("Choose a file to copy.");
        Uri source = entry.asUri(treeUri);
        Uri destination = DocumentsContract.buildDocumentUriUsingTree(treeUri, destinationFolderId);
        return DocumentsContract.copyDocument(context.getContentResolver(), source, destination);
    }

    public static Uri moveToFolder(Context context, Uri treeUri, Entry entry,
                                   String destinationFolderId) throws Exception {
        requireWriteGrant(context, treeUri);
        if (entry == null || entry.directory) throw new IllegalArgumentException("Choose a file to move.");
        if (entry.parentId == null) throw new IllegalArgumentException("The source folder is unavailable.");
        Uri source = entry.asUri(treeUri);
        Uri sourceParent = DocumentsContract.buildDocumentUriUsingTree(treeUri, entry.parentId);
        Uri destination = DocumentsContract.buildDocumentUriUsingTree(treeUri, destinationFolderId);
        return DocumentsContract.moveDocument(context.getContentResolver(), source, sourceParent, destination);
    }

    public static String storageSummary(Context context) {
        try {
            String path = Environment.getExternalStorageDirectory().getAbsolutePath();
            StatFs stats = new StatFs(path);
            long total = stats.getTotalBytes();
            long free = stats.getAvailableBytes();
            long used = Math.max(0L, total - free);
            return "Shared device storage: " + Formatter.formatFileSize(context, used) + " used, "
                    + Formatter.formatFileSize(context, free) + " available, "
                    + Formatter.formatFileSize(context, total) + " total.";
        } catch (Exception error) {
            return "Android could not report shared-storage capacity on this device.";
        }
    }

    private static void requireWriteGrant(Context context, Uri treeUri) {
        if (!hasWriteGrant(context, treeUri)) {
            throw new SecurityException("JARVIS has read access but not write access to this selected folder. Re-select the folder and grant modification access.");
        }
    }

    private static String validateName(String value) {
        String name = value == null ? "" : value.trim();
        if (name.isEmpty() || name.length() > 120 || name.contains("/") || name.contains("\\")
                || name.equals(".") || name.equals("..")) {
            throw new IllegalArgumentException("Use a file name up to 120 characters, without folder separators.");
        }
        return name;
    }

    private static String queryDisplayName(Context context, Uri uri) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(uri,
                    new String[]{android.provider.OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null) return name;
            }
        } catch (Exception ignored) {
        } finally {
            if (cursor != null) cursor.close();
        }
        String last = uri.getLastPathSegment();
        return last == null ? "document" : last;
    }

    private static boolean isReadableText(String name, String mime) {
        if (mime != null && mime.toLowerCase(Locale.ROOT).startsWith("text/")) return true;
        String lower = name == null ? "" : name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".csv")
                || lower.endsWith(".json") || lower.endsWith(".xml") || lower.endsWith(".html")
                || lower.endsWith(".htm") || lower.endsWith(".log") || lower.endsWith(".yaml")
                || lower.endsWith(".yml");
    }
}
