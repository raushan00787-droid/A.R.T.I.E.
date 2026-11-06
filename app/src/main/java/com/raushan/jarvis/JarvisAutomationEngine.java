package com.raushan.jarvis;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local, user-triggered routine engine.
 *
 * Routines contain only typed, allow-listed steps. They cannot execute arbitrary
 * text, scripts, communications, file deletion, or protected device operations.
 */
final class JarvisAutomationEngine {
    private static final String PREFS = "jarvis_settings";
    private static final String KEY = "automation_workflows";
    private static final String NEXT_ID = "automation_next_id";
    private static final int MAX_ROUTINES = 25;
    private static final int MAX_STEPS = 12;
    private static final long MAX_DELAY = 365L * 24L * 60L * 60L * 1000L;

    private JarvisAutomationEngine() { }

    static synchronized int createRoutine(Context context, String name, String definition) throws Exception {
        String cleanName = name == null ? "" : name.trim().replaceAll("\\s+", " ");
        if (cleanName.isEmpty() || cleanName.length() > 48
                || cleanName.contains(":") || cleanName.contains(";")) {
            throw new Exception("Routine names must be 1 to 48 characters and cannot contain ':' or ';'.");
        }
        JSONArray steps = parseSteps(definition);
        JSONArray routines = load(context);
        if (routines.length() >= MAX_ROUTINES) throw new Exception("The routine library is full (25 routines).");
        for (int i = 0; i < routines.length(); i++) {
            JSONObject existing = routines.optJSONObject(i);
            if (existing != null && existing.optString("name", "").equalsIgnoreCase(cleanName)) {
                throw new Exception("A routine with that name already exists. Choose another name.");
            }
        }

        SharedPreferences prefs = prefs(context);
        int id = prefs.getInt(NEXT_ID, 30000) + 1;
        if (id <= 0) id = 30001;
        prefs.edit().putInt(NEXT_ID, id).apply();

        JSONObject routine = new JSONObject();
        routine.put("id", id);
        routine.put("name", cleanName);
        routine.put("created_at", System.currentTimeMillis());
        routine.put("enabled", true);
        routine.put("steps", steps);
        routine.put("run_count", 0);
        routine.put("last_run_at", 0L);
        routine.put("last_status", "NEVER_RUN");
        routine.put("last_step", 0);
        routines.put(routine);
        save(context, routines);
        JarvisTaskHistory.record(context, "Create routine: " + cleanName, "SAVED",
                steps.length() + " allow-listed step(s).");
        return id;
    }

    private static JSONArray parseSteps(String definition) throws Exception {
        if (definition == null || definition.trim().isEmpty()) {
            throw new Exception("Add at least one step after the colon.");
        }
        String[] parts = definition.split(";");
        if (parts.length < 1 || parts.length > MAX_STEPS) {
            throw new Exception("A routine must contain between 1 and 12 steps.");
        }
        JSONArray steps = new JSONArray();
        for (String part : parts) {
            String command = part == null ? "" : part.trim();
            if (command.isEmpty()) throw new Exception("A routine cannot contain an empty step.");

            Matcher add = Pattern.compile("(?i)^(?:add|create) task\\s*:?\\s*(.{1,240})$").matcher(command);
            if (add.matches()) {
                JSONObject step = new JSONObject();
                step.put("type", "ADD_TASK");
                step.put("title", add.group(1).trim());
                if (step.optString("title").isEmpty()) throw new Exception("A task step needs a title.");
                steps.put(step);
                continue;
            }

            Matcher reminder = Pattern.compile(
                    "(?i)^remind me in\\s+(\\d{1,4})\\s*(minutes?|mins?|hours?|hrs?|days?|weeks?)\\s+(?:to\\s+)?(.{1,300})$")
                    .matcher(command);
            if (reminder.matches()) {
                int amount;
                try { amount = Integer.parseInt(reminder.group(1)); }
                catch (Exception error) { throw new Exception("A reminder interval is invalid."); }
                String unit = reminder.group(2).toLowerCase(Locale.ROOT);
                String title = reminder.group(3).trim();
                long multiplier = unit.startsWith("w") ? 7L * 24L * 60L * 60L * 1000L
                        : unit.startsWith("d") ? 24L * 60L * 60L * 1000L
                        : unit.startsWith("h") ? 60L * 60L * 1000L : 60L * 1000L;
                long delay = amount * multiplier;
                if (amount < 1 || title.isEmpty() || delay > MAX_DELAY) {
                    throw new Exception("Reminder steps must be positive, include text, and be no more than 365 days away.");
                }
                JSONObject step = new JSONObject();
                step.put("type", "REMINDER");
                step.put("title", title);
                step.put("delay_ms", delay);
                steps.put(step);
                continue;
            }

            Matcher study = Pattern.compile(
                    "(?i)^(?:start\\s+)?study(?:\\s+mode)?(?:\\s+for)?\\s+(\\d{1,3})\\s*(?:minutes?|mins?)$")
                    .matcher(command);
            if (study.matches()) {
                int minutes = Integer.parseInt(study.group(1));
                if (minutes < 5 || minutes > 240) {
                    throw new Exception("Study steps must be between 5 and 240 minutes.");
                }
                JSONObject step = new JSONObject();
                step.put("type", "STUDY");
                step.put("minutes", minutes);
                steps.put(step);
                continue;
            }

            throw new Exception("Unsupported routine step: '" + clip(command, 90)
                    + "'. Allowed steps: add task ..., remind me in ... to ..., or study ... minutes.");
        }
        return steps;
    }

    static synchronized String listRoutines(Context context) {
        JSONArray routines = load(context);
        if (routines.length() == 0) {
            return "No saved routines yet. Example: save routine Evening : add task Revise science ; remind me in 30 minutes to take a break ; study 25 minutes";
        }
        StringBuilder output = new StringBuilder("J.A.R.V.I.S. ROUTINES\n");
        for (int i = 0; i < routines.length(); i++) {
            JSONObject routine = routines.optJSONObject(i);
            if (routine == null) continue;
            String status = routine.optBoolean("enabled", true) ? "ENABLED" : "PAUSED";
            String last = routine.optString("last_status", "NEVER_RUN");
            if ("RUNNING".equals(last)) {
                last = "INTERRUPTED (previous run stopped before completion)";
                routine.put("last_status", "INTERRUPTED");
            }
            output.append(i + 1).append(". ").append(routine.optString("name", "Unnamed"))
                    .append(" • ").append(status)
                    .append(" • ").append(routine.optJSONArray("steps") == null ? 0
                            : routine.optJSONArray("steps").length()).append(" step(s)")
                    .append(" • ").append(last).append("\n");
        }
        save(context, routines);
        output.append("\nUse 'run routine NAME', 'pause routine NAME', or 'enable routine NAME'.");
        return output.toString().trim();
    }

    static synchronized String runRoutine(Context context, String name) {
        JSONArray routines = load(context);
        int index = findRoutine(routines, name);
        if (index < 0) return "I couldn't find that routine. Say 'list routines' to see saved names.";
        JSONObject routine = routines.optJSONObject(index);
        if (routine == null) return "That saved routine is invalid.";
        String routineName = routine.optString("name", "Unnamed");
        if (!routine.optBoolean("enabled", true)) {
            return "That routine is paused. Say 'enable routine " + routineName + "' before running it.";
        }

        JSONArray steps = routine.optJSONArray("steps");
        if (steps == null || steps.length() < 1 || steps.length() > MAX_STEPS) {
            return "That routine has invalid steps and was not run.";
        }

        int completed = 0;
        long startedAt = System.currentTimeMillis();
        try {
            routine.put("last_status", "RUNNING");
            routine.put("last_step", 0);
            routine.put("last_run_at", startedAt);
            save(context, routines);
            JarvisTaskHistory.record(context, "Run routine: " + routineName, "STARTED",
                    steps.length() + " step(s).");

            StringBuilder report = new StringBuilder("Routine ").append(routineName).append(":\n");
            for (int i = 0; i < steps.length(); i++) {
                JSONObject step = steps.optJSONObject(i);
                if (step == null) throw new Exception("A saved routine step is invalid.");
                String type = step.optString("type", "");
                String result;
                if ("ADD_TASK".equals(type)) {
                    String title = step.optString("title", "").trim();
                    if (title.isEmpty() || title.length() > 240) throw new Exception("Invalid task step.");
                    int id = JarvisTodoStore.add(context, title);
                    result = "Added task: " + title + " (ID " + id + ")";
                } else if ("REMINDER".equals(type)) {
                    String title = step.optString("title", "").trim();
                    long delay = step.optLong("delay_ms", 0L);
                    if (title.isEmpty() || title.length() > 300 || delay < 1000L || delay > MAX_DELAY) {
                        throw new Exception("Invalid reminder step.");
                    }
                    int id = JarvisReminderScheduler.schedule(context, title, delay, 0L);
                    result = "Scheduled reminder: " + title + " (ID " + id + ")";
                } else if ("STUDY".equals(type)) {
                    int minutes = step.optInt("minutes", 0);
                    result = startStudySession(context, minutes);
                } else {
                    throw new Exception("Blocked an unsupported action type: " + type);
                }
                completed++;
                report.append(completed).append("/").append(steps.length()).append("  ").append(result).append("\n");
                routine.put("last_step", completed);
                save(context, routines);
                JarvisTaskHistory.record(context, "Routine " + routineName + " step " + completed,
                        "COMPLETED", result);
            }

            routine.put("last_status", "COMPLETED");
            routine.put("run_count", routine.optInt("run_count", 0) + 1);
            routine.put("last_step", completed);
            save(context, routines);
            JarvisTaskHistory.record(context, "Run routine: " + routineName, "COMPLETED",
                    completed + " step(s) completed.");
            report.append("All ").append(completed).append(" step(s) completed.");
            return report.toString().trim();
        } catch (Exception error) {
            try {
                routine.put("last_status", completed > 0 ? "PARTIAL_FAILURE" : "FAILED");
                routine.put("last_step", completed);
                routine.put("last_error", clip(error.getMessage(), 180));
                save(context, routines);
            } catch (Exception ignored) { }
            JarvisTaskHistory.record(context, "Run routine: " + routineName,
                    completed > 0 ? "PARTIAL_FAILURE" : "FAILED",
                    completed + " of " + steps.length() + " step(s) completed. " + clip(error.getMessage(), 180));
            return "Routine stopped after " + completed + " of " + steps.length()
                    + " step(s). " + clip(error.getMessage(), 180)
                    + (completed > 0 ? " Earlier steps were not rolled back; review tasks and reminders." : "");
        }
    }

    static synchronized boolean setEnabled(Context context, String name, boolean enabled) {
        JSONArray routines = load(context);
        int index = findRoutine(routines, name);
        if (index < 0) return false;
        JSONObject routine = routines.optJSONObject(index);
        if (routine == null) return false;
        try {
            routine.put("enabled", enabled);
            save(context, routines);
            JarvisTaskHistory.record(context, (enabled ? "Enable routine: " : "Pause routine: ")
                    + routine.optString("name", name), enabled ? "ENABLED" : "PAUSED", "");
            return true;
        } catch (Exception ignored) {
            return false;
        }
    }

    static synchronized boolean deleteRoutine(Context context, String name) {
        JSONArray routines = load(context);
        int index = findRoutine(routines, name);
        if (index < 0) return false;
        JSONObject routine = routines.optJSONObject(index);
        String cleanName = routine == null ? name : routine.optString("name", name);
        JSONArray updated = new JSONArray();
        for (int i = 0; i < routines.length(); i++) {
            if (i != index) updated.put(routines.optJSONObject(i));
        }
        save(context, updated);
        JarvisTaskHistory.record(context, "Delete routine: " + cleanName, "DELETED", "");
        return true;
    }

    private static int findRoutine(JSONArray routines, String name) {
        String key = name == null ? "" : name.trim();
        if (key.isEmpty()) return -1;
        for (int i = 0; i < routines.length(); i++) {
            JSONObject routine = routines.optJSONObject(i);
            if (routine != null && routine.optString("name", "").equalsIgnoreCase(key)) return i;
        }
        return -1;
    }

    private static JSONArray load(Context context) {
        try {
            return new JSONArray(prefs(context).getString(KEY, "[]"));
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private static void save(Context context, JSONArray routines) {
        prefs(context).edit().putString(KEY, routines.toString()).apply();
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String clip(String value, int max) {
        String text = value == null ? "" : value.trim();
        return text.length() <= max ? text : text.substring(0, max);
    }

    static String startStudySession(Context context, int minutes) throws Exception {
        if (minutes < 5 || minutes > 240) {
            throw new Exception("A study session must be between 5 and 240 minutes.");
        }
        JarvisReminderScheduler.schedule(context, "Study session complete",
                minutes * 60L * 1000L, 0L);
        prefs(context).edit()
                .putLong("focus_session_started_at", System.currentTimeMillis())
                .putInt("focus_session_minutes", minutes)
                .putString("focus_session_name", "Study")
                .apply();
        JarvisTaskHistory.record(context, "Start study mode", "STARTED",
                "Focus session scheduled for " + minutes + " minutes.");
        return "Started a " + minutes + "-minute Study Mode session.";
    }

    static String currentSession(Context context) {
        SharedPreferences settings = prefs(context);
        long started = settings.getLong("focus_session_started_at", 0L);
        int minutes = settings.getInt("focus_session_minutes", 0);
        if (started <= 0L || minutes <= 0) return "No study session has been started on this device.";
        long end = started + minutes * 60L * 1000L;
        long remaining = end - System.currentTimeMillis();
        if (remaining <= 0L) return "The last study session has reached its scheduled end.";
        long remainingMinutes = (remaining + 59999L) / 60000L;
        return "Study Mode is active. Approximately " + remainingMinutes + " minute(s) remain.";
    }
}
