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
    public static final String EXTRA_ID = "id";
    public static final String EXTRA_TITLE = "title";
    public static final String EXTRA_TEXT = "text";
    private static final String CHANNEL_ID = "jarvis_personal_alerts";

    @Override public void onReceive(Context context, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();
        String title;
        String body;
        long itemId = intent.getLongExtra(EXTRA_ID, 0L);
        if (Intent.ACTION_BATTERY_LOW.equals(action)) {
            title = "Battery running low";
            body = "Sir, your device battery is low. Consider connecting the charger.";
            itemId = JarvisInboxStore.add(context, "ALERT", title, body, 0L, "NEW", true);
        } else if (ACTION_REMINDER.equals(action)) {
            title = intent.getStringExtra(EXTRA_TITLE);
            body = intent.getStringExtra(EXTRA_TEXT);
            if (title == null || title.trim().isEmpty()) title = "A.R.T.I.E. reminder";
            if (body == null) body = "You have a reminder.";
            if (itemId > 0L) JarvisInboxStore.markDelivered(context, itemId);
            else itemId = JarvisInboxStore.add(context, "REMINDER", title, body, 0L, "DELIVERED", true);
        } else {
            return;
        }

        createChannel(context);
        Intent open = new Intent(context, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        open.putExtra(MainActivity.EXTRA_OPEN_INBOX, true);
        PendingIntent content = PendingIntent.getActivity(context, 9030, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(context, CHANNEL_ID) : new Notification.Builder(context);
        Notification notification = builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(body)
                .setStyle(new Notification.BigTextStyle().bigText(body))
                .setContentIntent(content)
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_REMINDER)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) {
            int notificationId = (int) (itemId & 0x7fffffffL);
            if (notificationId == 0) notificationId = 9031;
            try { manager.notify(notificationId, notification); }
            catch (SecurityException ignored) { /* The Inbox entry remains saved locally. */ }
        }
    }

    private void createChannel(Context context) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "A.R.T.I.E. Personal Alerts", NotificationManager.IMPORTANCE_DEFAULT);
            channel.setDescription("Reminders and important device alerts created by A.R.T.I.E.");
            NotificationManager manager = context.getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
