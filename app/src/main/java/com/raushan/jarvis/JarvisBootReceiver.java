package com.raushan.jarvis;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** Restores pending reminders after Android restarts. */
public class JarvisBootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context context, Intent intent) {
        if (intent != null && Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) {
            JarvisInboxStore.restoreScheduledReminders(context);
        }
    }
}
