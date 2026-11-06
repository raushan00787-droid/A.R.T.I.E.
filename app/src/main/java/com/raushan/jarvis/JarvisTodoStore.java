package com.raushan.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class JarvisTodoStore {
    private static final String PREFS = "jarvis_settings";
    private static final String KEY = "todo_tasks";
    private static final String NEXT_ID = "todo_next_id";
    private static final int MAX_TASKS = 100;

    private JarvisTodoStore() { }

    static synchronized int add(Context context, String title) throws Exception {
        String clean = title == null ? "" : title.trim();
        if (clean.isEmpty() || clean.length() > 240) {
            throw new Exception("A task must contain between 1 and 240 characters.");
        }
        JSONArray tasks = load(context);
        if (tasks.length() >= MAX_TASKS) throw new Exception("The task list is full (100 tasks).");
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int id = prefs.getInt(NEXT_ID, 20000) + 1;
        if (id <= 0) id = 20001;
        prefs.edit().putInt(NEXT_ID, id).apply();
        JSONObject task = new JSONObject();
        task.put("id", id);
        task.put("title", clean);
        task.put("created_at", System.currentTimeMillis());
        task.put("completed", false);
        task.put("completed_at", 0L);
        tasks.put(task);
        save(context, tasks);
        return id;
    }

    static synchronized int findPending(Context context, String query) {
        String key = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        if (key.isEmpty()) return -1;
        JSONArray tasks = load(context);
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task != null && !task.optBoolean("completed", false)
                    && task.optString("title", "").toLowerCase(Locale.ROOT).contains(key)) {
                return task.optInt("id", -1);
            }
        }
        return -1;
    }

    static synchronized boolean complete(Context context, int id) {
        JSONArray tasks = load(context);
        boolean found = false;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task != null && task.optInt("id", -1) == id) {
                if (task.optBoolean("completed", false)) return false;
                task.put("completed", true);
                task.put("completed_at", System.currentTimeMillis());
                found = true;
                break;
            }
        }
        if (found) save(context, tasks);
        return found;
    }

    static synchronized boolean delete(Context context, int id) {
        JSONArray tasks = load(context);
        JSONArray updated = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) continue;
            if (task.optInt("id", -1) == id) removed = true;
            else updated.put(task);
        }
        if (removed) save(context, updated);
        return removed;
    }

    static synchronized String summary(Context context, boolean includeCompleted) {
        JSONArray tasks = load(context);
        int pending = 0;
        int done = 0;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) continue;
            if (task.optBoolean("completed", false)) done++;
            else pending++;
        }
        if (tasks.length() == 0) return "Your task list is empty. Say 'add task' followed by what you need to do.";
        StringBuilder output = new StringBuilder("J.A.R.V.I.S. TASK LIST\n");
        output.append("Pending: ").append(pending).append(" • Completed: ").append(done).append("\n\n");
        SimpleDateFormat format = new SimpleDateFormat("dd MMM", Locale.getDefault());
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null) continue;
            boolean completed = task.optBoolean("completed", false);
            if (completed && !includeCompleted) continue;
            output.append(completed ? "✓ " : "○ ")
                    .append(task.optString("title", "Untitled task"))
                    .append("  [").append(format.format(new Date(task.optLong("created_at", 0L)))).append("]\n");
        }
        return output.toString().trim();
    }

    static synchronized void clear(Context context) {
        save(context, new JSONArray());
    }

    private static JSONArray load(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void save(Context context, JSONArray tasks) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, tasks.toString()).apply();
    }
}
