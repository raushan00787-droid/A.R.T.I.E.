package com.raushan.jarvis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import org.json.JSONArray;
import org.json.JSONObject;

final class JarvisReminderScheduler {
    private static final String PREFS = "jarvis_settings";
    private static final String KEY = "scheduled_reminders";
    private static final String NEXT_ID = "reminder_next_id";
    private static final int MAX_REMINDERS = 100;

    private JarvisReminderScheduler() { }

    static synchronized int schedule(Context context, String task, long delayMillis, long intervalMillis) throws Exception {
        String label = task == null ? "" : task.trim();
        if (label.isEmpty() || label.length() > 300) throw new Exception("Reminder text must be between 1 and 300 characters.");
        if (delayMillis < 1000L || delayMillis > 365L * 24L * 60L * 60L * 1000L) {
            throw new Exception("Reminder time is outside the supported range.");
        }
        if (intervalMillis != 0L && (intervalMillis < 60L * 1000L
                || intervalMillis > 30L * 24L * 60L * 60L * 1000L)) {
            throw new Exception("Recurring reminders must repeat between 1 minute and 30 days.");
        }
        JSONArray items = load(context);
        if (items.length() >= MAX_REMINDERS) throw new Exception("The local reminder list is full (100 reminders).");
        int id = nextId(context);
        long fireAt = System.currentTimeMillis() + delayMillis;
        JSONObject item = new JSONObject();
        item.put("id", id);
        item.put("text", label);
        item.put("fire_at", fireAt);
        item.put("interval_ms", intervalMillis);
        items.put(item);
        save(context, items);
        scheduleAlarm(context, id, fireAt);
        return id;
    }

    static synchronized void onDelivered(Context context, int id) {
        if (id <= 0) return;
        JSONArray items = load(context);
        JSONArray updated = new JSONArray();
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (item.optInt("id", -1) != id) {
                updated.put(item);
                continue;
            }
            changed = true;
            long interval = item.optLong("interval_ms", 0L);
            if (interval > 0L) {
                try {
                    item.put("fire_at", now + interval);
                    updated.put(item);
                    scheduleAlarm(context, id, now + interval);
                } catch (Exception ignored) { }
            }
        }
        if (changed) save(context, updated);
    }

    static synchronized void rescheduleAll(Context context) {
        JSONArray items = load(context);
        JSONArray updated = new JSONArray();
        long now = System.currentTimeMillis();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            int id = item.optInt("id", -1);
            long fireAt = item.optLong("fire_at", 0L);
            long interval = item.optLong("interval_ms", 0L);
            if (id <= 0) continue;
            if (fireAt <= now) fireAt = now + (interval > 0L ? interval : 5000L);
            try {
                item.put("fire_at", fireAt);
                updated.put(item);
                scheduleAlarm(context, id, fireAt);
            } catch (Exception ignored) { }
        }
        save(context, updated);
    }

    static synchronized JSONArray getAll(Context context) {
        return load(context);
    }

    static synchronized int findByText(Context context, String query) {
        String key = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);
        if (key.isEmpty()) return -1;
        JSONArray items = load(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String text = item.optString("text", "").toLowerCase(java.util.Locale.ROOT);
            if (text.contains(key)) return item.optInt("id", -1);
        }
        return -1;
    }

    static synchronized boolean cancel(Context context, int id) {
        if (id <= 0) return false;
        cancelAlarm(context, id);
        JSONArray items = load(context);
        JSONArray updated = new JSONArray();
        boolean removed = false;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (item.optInt("id", -1) == id) removed = true;
            else updated.put(item);
        }
        if (removed) save(context, updated);
        return removed;
    }

    static synchronized String summary(Context context) {
        JSONArray items = load(context);
        if (items.length() == 0) return "There are no saved reminders.";
        StringBuilder output = new StringBuilder("J.A.R.V.I.S. REMINDERS\n");
        java.text.SimpleDateFormat format = new java.text.SimpleDateFormat("dd MMM, HH:mm", java.util.Locale.getDefault());
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            output.append(i + 1).append(". ")
                    .append(item.optString("text", "Reminder"))
                    .append("\n   ").append(format.format(new java.util.Date(item.optLong("fire_at", 0L))));
            long interval = item.optLong("interval_ms", 0L);
            if (interval > 0L) output.append(" • repeats every ").append(formatInterval(interval));
            output.append("\n");
        }
        return output.toString().trim();
    }

    static synchronized void replaceAll(Context context, JSONArray restored) {
        JSONArray current = load(context);
        for (int i = 0; i < current.length(); i++) {
            JSONObject item = current.optJSONObject(i);
            if (item != null) cancelAlarm(context, item.optInt("id", -1));
        }
        JSONArray safe = restored == null ? new JSONArray() : restored;
        save(context, safe);
        rescheduleAll(context);
    }

    private static int nextId(Context context) {
        int next = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getInt(NEXT_ID, 10000) + 1;
        if (next <= 0) next = 10001;
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt(NEXT_ID, next).apply();
        return next;
    }

    private static void scheduleAlarm(Context context, int id, long fireAt) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (manager == null) return;
        Intent intent = new Intent(context, JarvisNotificationReceiver.class)
                .setAction(JarvisNotificationReceiver.ACTION_REMINDER)
                .putExtra(JarvisNotificationReceiver.EXTRA_ID, id);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireAt, pending);
    }

    private static void cancelAlarm(Context context, int id) {
        AlarmManager manager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(context, JarvisNotificationReceiver.class)
                .setAction(JarvisNotificationReceiver.ACTION_REMINDER)
                .putExtra(JarvisNotificationReceiver.EXTRA_ID, id);
        PendingIntent pending = PendingIntent.getBroadcast(context, id, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (manager != null) manager.cancel(pending);
        pending.cancel();
    }

    private static JSONArray load(Context context) {
        try {
            return new JSONArray(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void save(Context context, JSONArray items) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, items.toString()).apply();
    }

    private static String formatInterval(long interval) {
        long minutes = interval / 60000L;
        if (minutes % 10080L == 0) return (minutes / 10080L) + " week(s)";
        if (minutes % 1440L == 0) return (minutes / 1440L) + " day(s)";
        if (minutes % 60L == 0) return (minutes / 60L) + " hour(s)";
        return minutes + " minute(s)";
    }
}
