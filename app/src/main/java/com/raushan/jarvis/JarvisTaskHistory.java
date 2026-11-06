package com.raushan.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

final class JarvisTaskHistory {
    private static final String PREFS = "jarvis_settings";
    private static final String KEY = "task_history";
    private static final int MAX_ENTRIES = 100;
    private JarvisTaskHistory() { }

    static synchronized void record(Context context, String action, String status, String details) {
        try {
            JSONArray history = load(context);
            JSONObject item = new JSONObject();
            item.put("time", System.currentTimeMillis());
            item.put("action", clip(action, 220));
            item.put("status", clip(status, 32));
            item.put("details", clip(details, 360));
            history.put(item);
            while (history.length() > MAX_ENTRIES) history.remove(0);
            prefs(context).edit().putString(KEY, history.toString()).apply();
        } catch (Exception ignored) { }
    }

    static synchronized String recentSummary(Context context) {
        JSONArray history = load(context);
        if (history.length() == 0) return "Task history is empty. JARVIS will record handled device commands and supported actions here.";
        StringBuilder output = new StringBuilder("RECENT J.A.R.V.I.S. TASK HISTORY\n");
        SimpleDateFormat format = new SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault());
        int start = Math.max(0, history.length() - 15);
        for (int i = history.length() - 1; i >= start; i--) {
            JSONObject item = history.optJSONObject(i);
            if (item == null) continue;
            output.append(format.format(new Date(item.optLong("time", 0L))))
                    .append(" • ").append(item.optString("status", "RECORDED"))
                    .append("\n").append(item.optString("action", "Unknown action"));
            String details = item.optString("details", "");
            if (!details.isEmpty()) output.append("\n").append(details);
            output.append("\n\n");
        }
        return output.toString().trim();
    }

    static synchronized void clear(Context context) {
        prefs(context).edit().remove(KEY).apply();
    }

    private static JSONArray load(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clip(String value, int limit) {
        if (value == null) return "";
        String clean = value.trim();
        return clean.length() > limit ? clean.substring(0, limit) : clean;
    }
}
