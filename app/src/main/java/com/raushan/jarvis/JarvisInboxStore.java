package com.raushan.jarvis;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import org.json.JSONArray;
import org.json.JSONObject;

/** On-device store for JARVIS reminders and system alerts. */
public final class JarvisInboxStore {
    private static final String PREFS = "jarvis_settings";
    private static final String KEY = "jarvis_inbox";
    private static final int MAX_ITEMS = 100;

    private JarvisInboxStore() { }

    private static JSONArray read(Context context) {
        String raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]");
        try { return new JSONArray(raw); }
        catch (Exception ignored) { return new JSONArray(); }
    }

    private static void write(Context context, JSONArray items) {
        while (items.length() > MAX_ITEMS) {
            JSONObject oldest = items.optJSONObject(0);
            if (oldest != null) {
                String status = oldest.optString("status", "");
                if ("SCHEDULED".equals(status) || "SNOOZED".equals(status)) {
                    cancelReminder(context, oldest.optLong("id", 0L));
                }
            }
            items.remove(0);
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putString(KEY, items.toString()).apply();
    }

    public static JSONArray getAll(Context context) {
        JSONArray source = read(context);
        JSONArray copy = new JSONArray();
        for (int i = 0; i < source.length(); i++) {
            JSONObject item = source.optJSONObject(i);
            if (item != null) copy.put(item);
        }
        return copy;
    }

    public static long add(Context context, String type, String title, String message,
                           long dueAt, String status, boolean unread) {
        JSONArray items = read(context);
        // IDs remain unique even when multiple alerts are created in the same millisecond.
        long id = Math.max(1L, System.currentTimeMillis());
        for (int i = 0; i < items.length(); i++) {
            JSONObject existing = items.optJSONObject(i);
            if (existing != null) id = Math.max(id, existing.optLong("id", 0L) + 1L);
        }
        try {
            JSONObject item = new JSONObject();
            item.put("id", id);
            item.put("type", type == null ? "ALERT" : type);
            item.put("title", title == null ? "JARVIS notification" : title);
            item.put("message", message == null ? "" : message);
            item.put("createdAt", System.currentTimeMillis());
            item.put("dueAt", dueAt);
            item.put("status", status == null ? "NEW" : status);
            item.put("read", !unread);
            items.put(item);
            write(context, items);
        } catch (Exception ignored) { }
        return id;
    }

    public static void markRead(Context context, long id) {
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && item.optLong("id", -1L) == id) {
                try { item.put("read", true); } catch (Exception ignored) { }
                write(context, items);
                return;
            }
        }
    }

    public static void markAllRead(Context context) {
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null) {
                try { item.put("read", true); } catch (Exception ignored) { }
            }
        }
        write(context, items);
    }

    public static void markDelivered(Context context, long id) {
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && item.optLong("id", -1L) == id) {
                try {
                    item.put("status", "DELIVERED");
                    item.put("createdAt", System.currentTimeMillis());
                    item.put("read", false);
                } catch (Exception ignored) { }
                write(context, items);
                return;
            }
        }
    }

    public static void delete(Context context, long id) {
        JSONArray items = read(context);
        JSONArray kept = new JSONArray();
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            if (item.optLong("id", -1L) == id) {
                String status = item.optString("status", "");
                if ("SCHEDULED".equals(status) || "SNOOZED".equals(status)) cancelReminder(context, id);
            } else {
                kept.put(item);
            }
        }
        write(context, kept);
    }

    public static boolean snooze(Context context, long id, long delayMillis) {
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null || item.optLong("id", -1L) != id) continue;
            long dueAt = System.currentTimeMillis() + Math.max(60_000L, delayMillis);
            String title = item.optString("title", "JARVIS reminder");
            String message = item.optString("message", "You have a JARVIS reminder.");
            if (!scheduleReminder(context, id, title, message, dueAt)) return false;
            try {
                item.put("dueAt", dueAt);
                item.put("status", "SNOOZED");
                item.put("read", true);
            } catch (Exception ignored) { return false; }
            write(context, items);
            return true;
        }
        return false;
    }

    public static int unreadCount(Context context) {
        JSONArray items = read(context);
        int count = 0;
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item != null && !item.optBoolean("read", true)) count++;
        }
        return count;
    }

    public static boolean scheduleReminder(Context context, long id, String title,
                                           String message, long dueAt) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return false;
        Intent alert = new Intent(context, JarvisNotificationReceiver.class)
                .setAction(JarvisNotificationReceiver.ACTION_REMINDER);
        alert.putExtra(JarvisNotificationReceiver.EXTRA_ID, id);
        alert.putExtra(JarvisNotificationReceiver.EXTRA_TITLE, title);
        alert.putExtra(JarvisNotificationReceiver.EXTRA_TEXT, message);
        PendingIntent pending = reminderPendingIntent(context, id, alert);
        try {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    Math.max(System.currentTimeMillis() + 1000L, dueAt), pending);
            return true;
        } catch (SecurityException | IllegalStateException error) {
            return false;
        }
    }

    private static PendingIntent reminderPendingIntent(Context context, long id, Intent intent) {
        int requestCode = (int) (id & 0x7fffffffL);
        if (requestCode == 0) requestCode = 1;
        // PendingIntent identity includes the data URI, preventing long-ID wraparound collisions.
        intent.setData(Uri.parse("jarvis://reminder/" + id));
        return PendingIntent.getBroadcast(context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelReminder(Context context, long id) {
        Intent alert = new Intent(context, JarvisNotificationReceiver.class)
                .setAction(JarvisNotificationReceiver.ACTION_REMINDER);
        PendingIntent pending = reminderPendingIntent(context, id, alert);
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms != null) alarms.cancel(pending);
        pending.cancel();
    }

    public static void restoreScheduledReminders(Context context) {
        JSONArray items = read(context);
        for (int i = 0; i < items.length(); i++) {
            JSONObject item = items.optJSONObject(i);
            if (item == null) continue;
            String type = item.optString("type", "");
            String status = item.optString("status", "");
            // Normal reminders are restored while SCHEDULED; any item can be snoozed.
            boolean pendingReminder = "REMINDER".equals(type) && "SCHEDULED".equals(status);
            boolean snoozedItem = "SNOOZED".equals(status);
            if (!pendingReminder && !snoozedItem) continue;
            long id = item.optLong("id", 0L);
            long dueAt = item.optLong("dueAt", 0L);
            if (id > 0 && dueAt > 0) {
                scheduleReminder(context, id, item.optString("title", "JARVIS reminder"),
                        item.optString("message", "You have a reminder."), dueAt);
            }
        }
    }
}
