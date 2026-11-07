package com.raushan.jarvis;

import android.app.Notification;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/**
 * Mirrors user-authorised, readable notifications into the local JARVIS Inbox.
 * Ongoing system notifications and JARVIS's own notifications are excluded.
 */
public class JarvisNotificationListenerService extends NotificationListenerService {
    private static final String PREFS = "jarvis_settings";

    @Override
    public void onNotificationPosted(StatusBarNotification posted) {
        if (posted == null || posted.getNotification() == null) return;
        String sourcePackage = posted.getPackageName();
        if (sourcePackage == null || sourcePackage.equals(getPackageName())) return;

        Notification notification = posted.getNotification();
        int flags = notification.flags;
        if ((flags & Notification.FLAG_ONGOING_EVENT) != 0
                || (flags & Notification.FLAG_FOREGROUND_SERVICE) != 0
                || (flags & Notification.FLAG_GROUP_SUMMARY) != 0) return;
        if (notification.visibility == Notification.VISIBILITY_SECRET) return;

        Bundle extras = notification.extras;
        if (extras == null) return;
        CharSequence titleValue = extras.getCharSequence(Notification.EXTRA_TITLE);
        if (titleValue == null) titleValue = extras.getCharSequence(Notification.EXTRA_TITLE_BIG);
        CharSequence bodyValue = extras.getCharSequence(Notification.EXTRA_BIG_TEXT);
        if (bodyValue == null) bodyValue = extras.getCharSequence(Notification.EXTRA_TEXT);

        StringBuilder bodyBuilder = new StringBuilder();
        if (bodyValue != null) bodyBuilder.append(bodyValue);
        if (bodyBuilder.length() == 0) {
            CharSequence[] lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES);
            if (lines != null) {
                for (CharSequence line : lines) {
                    if (line == null) continue;
                    if (bodyBuilder.length() > 0) bodyBuilder.append('\n');
                    bodyBuilder.append(line);
                }
            }
        }

        String title = titleValue == null ? "" : titleValue.toString().trim();
        String body = bodyBuilder.toString().trim();
        if (title.isEmpty() && body.isEmpty()) return;
        if (title.length() > 180) title = title.substring(0, 180);
        if (body.length() > 700) body = body.substring(0, 700);

        String notificationKey = posted.getKey();
        if (notificationKey == null) notificationKey = sourcePackage + ":" + posted.getPostTime();
        String cacheKey = "jarvis_external_notif_" + Integer.toHexString(notificationKey.hashCode());
        String fingerprint = posted.getPostTime() + ":" + Integer.toHexString((title + "|" + body).hashCode());
        android.content.SharedPreferences preferences = getSharedPreferences(PREFS, MODE_PRIVATE);
        if (fingerprint.equals(preferences.getString(cacheKey, ""))) return;
        preferences.edit().putString(cacheKey, fingerprint).apply();

        String appLabel = sourcePackage;
        try {
            PackageManager manager = getPackageManager();
            ApplicationInfo info = manager.getApplicationInfo(sourcePackage, 0);
            CharSequence label = manager.getApplicationLabel(info);
            if (label != null && !label.toString().trim().isEmpty()) appLabel = label.toString().trim();
        } catch (Exception ignored) { }

        String inboxTitle = title.isEmpty() ? appLabel : appLabel + " • " + title;
        JarvisInboxStore.add(this, "ALERT", inboxTitle, body, 0L, "NEW", true);
    }
}
