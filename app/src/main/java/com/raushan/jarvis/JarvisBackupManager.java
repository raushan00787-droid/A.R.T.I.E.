package com.raushan.jarvis;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;

final class JarvisBackupManager {
    private static final String FORMAT = "jarvis-native-backup";
    private static final int VERSION = 1;
    private static final int MAX_BACKUP_BYTES = 512 * 1024;

    private JarvisBackupManager() { }

    static void writeBackup(ContentResolver resolver, Uri destination,
                            SharedPreferences preferences) throws Exception {
        JSONObject root = new JSONObject();
        root.put("format", FORMAT);
        root.put("schema_version", VERSION);
        root.put("exported_at", System.currentTimeMillis());

        JSONObject settings = new JSONObject();
        settings.put("saved_memories", preferences.getString("saved_memories", "[]"));
        String conversationHistory = preferences.getString("conversation_history", "[]");
        if (conversationHistory == null || conversationHistory.trim().isEmpty()) conversationHistory = "[]";
        settings.put("conversation_history", conversationHistory);
        settings.put("task_history", preferences.getString("task_history", "[]"));
        settings.put("speech_language_mode", preferences.getInt("speech_language_mode", 0));
        settings.put("speech_rate", preferences.getFloat("speech_rate", 1.0f));
        settings.put("spoken_replies", preferences.getBoolean("spoken_replies", true));
        root.put("settings", settings);

        byte[] bytes = root.toString(2).getBytes(Charset.forName("UTF-8"));
        if (bytes.length > MAX_BACKUP_BYTES) throw new Exception("The backup exceeds the 512 KB size limit.");
        try (OutputStream output = resolver.openOutputStream(destination, "w")) {
            if (output == null) throw new Exception("Android could not open the selected backup destination.");
            output.write(bytes);
            output.flush();
        }
    }

    static JSONObject readBackup(ContentResolver resolver, Uri source) throws Exception {
        byte[] bytes;
        try (InputStream input = resolver.openInputStream(source)) {
            if (input == null) throw new Exception("Android could not read the selected backup file.");
            bytes = readBounded(input);
        }
        JSONObject root = new JSONObject(new String(bytes, Charset.forName("UTF-8")));
        validate(root);
        return root;
    }

    static void restoreBackup(JSONObject root, SharedPreferences preferences) throws Exception {
        validate(root);
        JSONObject settings = root.getJSONObject("settings");
        String memories = settings.optString("saved_memories", "[]");
        String conversation = settings.optString("conversation_history", "");
        String history = settings.optString("task_history", "[]");

        int languageMode = settings.optInt("speech_language_mode", 0);
        if (languageMode < 0 || languageMode > 2) languageMode = 0;
        float speechRate = (float) settings.optDouble("speech_rate", 1.0);
        if (speechRate < 0.7f || speechRate > 1.6f) speechRate = 1.0f;

        preferences.edit()
                .putString("saved_memories", memories)
                .putString("conversation_history", conversation)
                .putString("task_history", history)
                .putInt("speech_language_mode", languageMode)
                .putFloat("speech_rate", speechRate)
                .putBoolean("spoken_replies", settings.optBoolean("spoken_replies", true))
                // Never resume microphone or overlay behaviour just because a backup was restored.
                .putBoolean("live_mode_enabled", false)
                .putBoolean("live_mode_paused", false)
                .putBoolean("floating_orb_enabled", false)
                .apply();
    }

    static String preview(JSONObject root) throws Exception {
        validate(root);
        JSONObject settings = root.getJSONObject("settings");
        int memories = new JSONArray(settings.optString("saved_memories", "[]")).length();
        int chat = new JSONArray(settings.optString("conversation_history", "[]")).length();
        int tasks = new JSONArray(settings.optString("task_history", "[]")).length();
        return "Backup format: JARVIS Native v" + VERSION
                + "\nSaved notes: " + memories
                + "\nConversation entries: " + chat
                + "\nTask history entries: " + tasks
                + "\nSpeech language and speed preferences are included."
                + "\n\nRestore replaces these local records. Android permissions, folder grants, account access, microphone mode, and Floating Orb state are not restored.";
    }

    private static void validate(JSONObject root) throws Exception {
        if (root == null || !FORMAT.equals(root.optString("format", ""))) {
            throw new Exception("This is not a JARVIS Native backup file.");
        }
        if (root.optInt("schema_version", -1) != VERSION) {
            throw new Exception("This backup uses an unsupported JARVIS backup format version.");
        }
        JSONObject settings = root.optJSONObject("settings");
        if (settings == null) throw new Exception("The backup does not contain settings data.");

        String conversationRaw = settings.optString("conversation_history", "[]");
        if (conversationRaw == null || conversationRaw.trim().isEmpty()) conversationRaw = "[]";
        JSONArray memories = new JSONArray(settings.optString("saved_memories", "[]"));
        JSONArray conversation = new JSONArray(conversationRaw);
        JSONArray tasks = new JSONArray(settings.optString("task_history", "[]"));
        if (memories.length() > 20) throw new Exception("The backup contains too many saved notes.");
        if (conversation.length() > 21) throw new Exception("The backup contains too many conversation entries.");
        if (tasks.length() > 100) throw new Exception("The backup contains too many task-history entries.");

        for (int i = 0; i < memories.length(); i++) {
            if (memories.optString(i, "").length() > 1000) throw new Exception("A saved note is too large.");
        }
        for (int i = 0; i < conversation.length(); i++) {
            JSONObject entry = conversation.optJSONObject(i);
            if (entry == null) throw new Exception("The conversation history contains an invalid entry.");
            String role = entry.optString("role", "");
            if (!"system".equals(role) && !"user".equals(role) && !"assistant".equals(role)) {
                throw new Exception("The conversation history contains an unsupported role.");
            }
            if (entry.optString("content", "").length() > 16000) {
                throw new Exception("A conversation entry is too large.");
            }
        }
    }

    private static byte[] readBounded(InputStream input) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int count;
        while ((count = input.read(buffer)) != -1) {
            if (output.size() + count > MAX_BACKUP_BYTES) {
                throw new Exception("The selected backup exceeds the 512 KB safety limit.");
            }
            output.write(buffer, 0, count);
        }
        return output.toByteArray();
    }
}
