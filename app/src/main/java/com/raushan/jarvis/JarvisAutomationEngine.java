package com.raushan.jarvis;

import android.content.Context;

final class JarvisAutomationEngine {
    private JarvisAutomationEngine() { }

    static String startStudySession(Context context, int minutes) throws Exception {
        if (minutes < 5 || minutes > 240) {
            throw new Exception("A study session must be between 5 and 240 minutes.");
        }
        String task = "Study session complete";
        JarvisReminderScheduler.schedule(context, task,
                minutes * 60L * 1000L, 0L);
        context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE).edit()
                .putLong("focus_session_started_at", System.currentTimeMillis())
                .putInt("focus_session_minutes", minutes)
                .putString("focus_session_name", "Study")
                .apply();
        JarvisTaskHistory.record(context, "Start study mode", "STARTED",
                "Focus session scheduled for " + minutes + " minutes.");
        return "Study Mode started for " + minutes + " minutes. JARVIS will notify you when the session ends.";
    }

    static String currentSession(Context context) {
        long started = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
                .getLong("focus_session_started_at", 0L);
        int minutes = context.getSharedPreferences("jarvis_settings", Context.MODE_PRIVATE)
                .getInt("focus_session_minutes", 0);
        if (started <= 0L || minutes <= 0) return "No study session has been started on this device.";
        long end = started + minutes * 60L * 1000L;
        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0L) return "The last study session has reached its scheduled end.";
        long remainingMinutes = (remaining + 59999L) / 60000L;
        return "Study Mode is active. Approximately " + remainingMinutes + " minute(s) remain.";
    }
}
