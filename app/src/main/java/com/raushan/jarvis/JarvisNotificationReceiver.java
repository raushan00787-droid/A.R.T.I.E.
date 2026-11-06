package com.raushan.jarvis;

import android.app.Notification;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import org.json.JSONObject;
import java.util.Map;

public class JarvisNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_REMINDER = "com.raushan.jarvis.notification.REMINDER";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_ID = "reminder_id";
    private static final String CHANNEL_ID = "jarvis_personal_alerts";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            restoreReminders(context);
            return;
        }
        String title;
        String text;
        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            title = "Battery running low";
            text = "Sir, your device battery is low. Consider connecting the charger.";
        } else if (ACTION_REMINDER.equals(action)) {
            title = intent.getStringExtra(EXTRA_TITLE);
            text = intent.getStringExtra(EXTRA_TEXT);
            int reminderId = intent.getIntExtra(EXTRA_ID, -1);
            if (reminderId >= 0) {
                context.getSharedPreferences("jarvis_reminders", Context.MODE_PRIVATE)
                        .edit().remove("reminder_" + reminderId).apply();
            }
            if (title == null || title.trim().isEmpty()) title = "JARVIS reminder";
            if (text == null) text = "You have a reminder.";
        } else {
            return;
        }

        createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(context, 9030, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        Notification notification = builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setContentIntent(content)
                .setAutoCancel(true)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify((int) (System.currentTimeMillis() & 0x7fffffff), notification);
    }

    private void restoreReminders(Context context) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        SharedPreferences saved = context.getSharedPreferences("jarvis_reminders", Context.MODE_PRIVATE);
        long now = System.currentTimeMillis();
        for (Map.Entry<String, ?> entry : saved.getAll().entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (!key.startsWith("reminder_") || !(value instanceof String)) continue;
            try {
                int id = Integer.parseInt(key.substring("reminder_".length()));
                JSONObject item = new JSONObject((String) value);
                long triggerAt = item.optLong("time", 0L);
                if (triggerAt <= 0L) continue;
                String title = item.optString("title", "JARVIS reminder");
                String text = item.optString("text", "You have a reminder.");
                Intent alert = new Intent(context, JarvisNotificationReceiver.class)
                        .setAction(ACTION_REMINDER);
                alert.putExtra(EXTRA_ID, id);
                alert.putExtra(EXTRA_TITLE, title);
                alert.putExtra(EXTRA_TEXT, text);
                PendingIntent pending = PendingIntent.getBroadcast(context, id, alert,
                        PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
                alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                        Math.max(triggerAt, now + 2000L), pending);
            } catch (Exception ignored) {
            }
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "JARVIS Personal Alerts", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Reminders and important device alerts created by JARVIS.");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
