package com.raushan.jarvis;

import android.content.ContentResolver;
import android.net.Uri;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Locale;

/** Reads only bounded, text-based user-selected documents. */
final class JarvisDocumentReader {
    private static final int MAX_BYTES = 256 * 1024;
    private static final int MAX_CHARS = 12000;

    static final class Document {
        final String name;
        final String mimeType;
        final String text;

        Document(String name, String mimeType, String text) {
            this.name = name;
            this.mimeType = mimeType;
            this.text = text;
        }
    }

    private JarvisDocumentReader() { }

    static Document read(ContentResolver resolver, Uri uri, String name) throws Exception {
        if (uri == null) throw new Exception("No document was selected.");
        String mime = resolver.getType(uri);
        if (mime == null) mime = "application/octet-stream";
        if (!canRead(name, mime)) {
            throw new Exception("JARVIS can currently analyse plain text, Markdown, CSV, JSON, XML, and log files. PDF and image analysis are separate modules still being added.");
        }

        byte[] bytes;
        try (InputStream input = resolver.openInputStream(uri)) {
            if (input == null) throw new Exception("Android could not open this document.");
            bytes = readBounded(input);
        }
        String value = new String(bytes, Charset.forName("UTF-8"));
        if (value.indexOf('\u0000') >= 0) {
            throw new Exception("This document appears to be binary, not a supported text file.");
        }
        value = value.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (value.isEmpty()) throw new Exception("This document is empty.");
        if (value.length() > MAX_CHARS) value = value.substring(0, MAX_CHARS)
                + "\n\n[Content truncated at the 16,000 character safety limit.]";
        return new Document(name == null ? "Selected document" : name, mime, value);
    }

    static boolean canRead(String name, String mime) {
        String type = mime.toLowerCase(Locale.ROOT);
        if (type.startsWith("text/") || type.equals("application/json")
                || type.equals("application/xml") || type.endsWith("+json")
                || type.endsWith("+xml")) return true;
        if (name == null) return false;
        String lower = name.toLowerCase(Locale.ROOT);
        return lower.endsWith(".txt") || lower.endsWith(".md") || lower.endsWith(".markdown")
                || lower.endsWith(".csv") || lower.endsWith(".json") || lower.endsWith(".xml")
                || lower.endsWith(".log") || lower.endsWith(".yaml") || lower.endsWith(".yml")
                || lower.endsWith(".ini") || lower.endsWith(".properties");
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BYTES) {
                throw new Exception("This document is larger than the 256 KB analysis limit.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
