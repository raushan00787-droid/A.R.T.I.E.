package com.raushan.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class JarvisNotificationReceiver extends BroadcastReceiver {
    public static final String ACTION_REMINDER = "com.raushan.jarvis.notification.REMINDER";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";
    public static final String EXTRA_ID = "reminder_id";
    private static final String CHANNEL_ID = "jarvis_personal_alerts";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String title;
        String text;
        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            title = "Battery running low";
            text = "Sir, your device battery is low. Consider connecting the charger.";
        } else if (ACTION_REMINDER.equals(action)) {
            title = intent.getStringExtra(EXTRA_TITLE);
            text = intent.getStringExtra(EXTRA_TEXT);
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
        if (ACTION_REMINDER.equals(action)) {
            JarvisReminderScheduler.onDelivered(context, intent.getIntExtra(EXTRA_ID, -1));
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
