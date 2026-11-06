package com.raushan.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.util.ArrayList;
import java.util.Locale;

/**
 * User-started foreground voice session.
 * The recognizer waits for "Hey JARVIS", then accepts follow-up turns for a short session.
 */
public class JarvisLiveService extends Service {
    public static final String ACTION_START = "com.raushan.jarvis.live.START";
    public static final String ACTION_RESUME = "com.raushan.jarvis.live.RESUME";
    public static final String ACTION_HOLD = "com.raushan.jarvis.live.HOLD";
    public static final String ACTION_PAUSE = "com.raushan.jarvis.live.PAUSE";
    public static final String ACTION_STOP = "com.raushan.jarvis.live.STOP";
    public static final String ACTION_TRANSCRIPT = "com.raushan.jarvis.live.TRANSCRIPT";
    public static final String ACTION_STATE = "com.raushan.jarvis.live.STATE";
    public static final String EXTRA_COMMAND = "command";
    public static final String EXTRA_ENABLED = "enabled";
    public static final String EXTRA_PAUSED = "paused";
    private static final String CHANNEL_ID = "jarvis_live_voice";
    private static final int NOTIFICATION_ID = 9020;
    private static final long SESSION_TIMEOUT_MS = 25000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private SpeechRecognizer recognizer;
    private boolean listening;
    private boolean userPaused;
    private boolean speakingHold;
    private boolean sessionActive;
    private boolean foregroundStarted;
    private Runnable sessionTimeout;

    private android.content.SharedPreferences prefs() {
        return getSharedPreferences("jarvis_settings", MODE_PRIVATE);
    }

    @Override public void onCreate() {
        super.onCreate();
        createChannel();
        startForegroundNow("Starting voice channel");
        foregroundStarted = true;
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();
        if (intent == null) {
            userPaused = prefs().getBoolean("live_mode_paused", false);
            if (!prefs().getBoolean("live_mode_enabled", false)) {
                stopForeground(true);
                stopSelf();
                return START_NOT_STICKY;
            }
        }
        if (ACTION_STOP.equals(action)) {
            prefs().edit().putBoolean("live_mode_enabled", false)
                    .putBoolean("live_mode_paused", false).apply();
            publishState(false, false);
            stopListening();
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (ACTION_HOLD.equals(action)) {
            speakingHold = true;
            stopListening();
            updateNotification("JARVIS is speaking. Listening resumes after the reply.", false);
            return START_STICKY;
        }
        if (ACTION_PAUSE.equals(action)) {
            userPaused = true;
            prefs().edit().putBoolean("live_mode_paused", true).apply();
            publishState(true, true);
            stopListening();
            updateNotification("Paused. Tap Resume to listen again.", true);
            return START_STICKY;
        }
        if (ACTION_START.equals(action)) {
            userPaused = false;
            sessionActive = false;
            prefs().edit().putBoolean("live_mode_enabled", true)
                    .putBoolean("live_mode_paused", false).apply();
        } else if (ACTION_RESUME.equals(action)) {
            userPaused = false;
            speakingHold = false;
            prefs().edit().putBoolean("live_mode_paused", false).apply();
            if (sessionActive) scheduleSessionTimeout();
        } else if (intent == null) {
            userPaused = prefs().getBoolean("live_mode_paused", false);
            sessionActive = false;
        }
        if (!userPaused) {
            publishState(true, false);
            ensureRecognizer();
            startListening();
            updateNotification(sessionActive ? "Live conversation active" : "Listening for Hey JARVIS", false);
        } else {
            updateNotification("Paused. Tap Resume to listen again.", true);
        }
        return START_STICKY;
    }

    private void ensureRecognizer() {
        if (recognizer != null) return;
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            updateNotification("Speech recognition is unavailable on this device.", false);
            return;
        }
        try {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(new RecognitionListener() {
                @Override public void onReadyForSpeech(android.os.Bundle params) {
                    listening = true;
                    updateNotification(sessionActive ? "Live conversation active" : "Listening for Hey JARVIS", false);
                }
                @Override public void onBeginningOfSpeech() { }
                @Override public void onRmsChanged(float rmsdB) { }
                @Override public void onBufferReceived(byte[] buffer) { }
                @Override public void onEndOfSpeech() { listening = false; }
                @Override public void onError(int error) {
                    listening = false;
                    if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                        userPaused = true;
                        updateNotification("Microphone permission is required. Open JARVIS to fix it.", true);
                        return;
                    }
                    restartAfter(1100);
                }
                @Override public void onResults(android.os.Bundle results) {
                    listening = false;
                    ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    String spoken = (matches == null || matches.isEmpty()) ? "" : matches.get(0);
                    handleRecognizedText(spoken);
                }
                @Override public void onPartialResults(android.os.Bundle partialResults) { }
                @Override public void onEvent(int eventType, android.os.Bundle params) { }
            });
        } catch (Exception error) {
            recognizer = null;
            updateNotification("Could not start speech recognition.", false);
        }
    }

    private void handleRecognizedText(String spoken) {
        if (spoken == null || spoken.trim().isEmpty()) {
            restartAfter(450);
            return;
        }
        String lower = spoken.toLowerCase(Locale.ROOT);
        String command = spoken.trim();
        if (!sessionActive) {
            int wake = lower.indexOf("hey jarvis");
            if (wake < 0) {
                restartAfter(450);
                return;
            }
            command = spoken.substring(Math.min(spoken.length(), wake + "hey jarvis".length())).trim();
            sessionActive = true;
        }
        if (sessionTimeout != null) handler.removeCallbacks(sessionTimeout);
        if (command.isEmpty()) command = "__JARVIS_WAKE__";
        deliverCommand(command);
        updateNotification("Heard you. Processing command…", false);
    }

    private void publishState(boolean enabled, boolean paused) {
        Intent state = new Intent(ACTION_STATE);
        state.setPackage(getPackageName());
        state.putExtra(EXTRA_ENABLED, enabled);
        state.putExtra(EXTRA_PAUSED, paused);
        sendBroadcast(state);
    }

    private void deliverCommand(String command) {
        speakingHold = true;
        prefs().edit().putString("pending_live_command", command).apply();
        Intent event = new Intent(ACTION_TRANSCRIPT);
        event.setPackage(getPackageName());
        event.putExtra(EXTRA_COMMAND, command);
        sendBroadcast(event);
    }

    private void restartAfter(long delay) {
        handler.postDelayed(() -> {
            if (!userPaused && prefs().getBoolean("live_mode_enabled", false)) startListening();
        }, delay);
    }

    private void startListening() {
        if (userPaused || speakingHold || listening || !prefs().getBoolean("live_mode_enabled", false)) return;
        ensureRecognizer();
        if (recognizer == null) return;
        try {
            Intent request = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            request.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            request.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            request.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600);
            request.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 900);
            int languageMode = prefs().getInt("speech_language_mode", 0);
            if (languageMode == 1) {
                request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
                request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
            } else if (languageMode == 2) {
                request.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
                request.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN");
            }
            recognizer.startListening(request);
        } catch (Exception error) {
            listening = false;
            restartAfter(1300);
        }
    }

    /** Called after JARVIS finishes speaking. */
    private void scheduleSessionTimeout() {
        if (sessionTimeout != null) handler.removeCallbacks(sessionTimeout);
        sessionTimeout = () -> {
            sessionActive = false;
            startListening();
            updateNotification("Listening for Hey JARVIS", false);
        };
        handler.postDelayed(sessionTimeout, SESSION_TIMEOUT_MS);
    }

    private void stopListening() {
        if (sessionTimeout != null) handler.removeCallbacks(sessionTimeout);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
        }
        listening = false;
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "JARVIS Live Mode", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Persistent controls for the user-started JARVIS microphone session.");
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private PendingIntent actionIntent(String action, int requestCode) {
        Intent intent = new Intent(this, JarvisLiveService.class).setAction(action);
        return PendingIntent.getService(this, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private Notification buildNotification(String status, boolean paused) {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent content = PendingIntent.getActivity(this, 9021, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID) : new Notification.Builder(this);
        builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("J.A.R.V.I.S.  •  LIVE MODE")
                .setContentText(status)
                .setContentIntent(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setCategory(Notification.CATEGORY_SERVICE);
        if (paused) {
            builder.addAction(new Notification.Action.Builder(null, "RESUME",
                    actionIntent(ACTION_RESUME, 9022)).build());
        } else {
            builder.addAction(new Notification.Action.Builder(null, "PAUSE",
                    actionIntent(ACTION_PAUSE, 9023)).build());
        }
        builder.addAction(new Notification.Action.Builder(null, "STOP",
                actionIntent(ACTION_STOP, 9024)).build());
        return builder.build();
    }

    private void startForegroundNow(String status) {
        Notification notification = buildNotification(status, false);
        if (Build.VERSION.SDK_INT >= 30) {
            startForeground(NOTIFICATION_ID, notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    private void updateNotification(String status, boolean paused) {
        NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.notify(NOTIFICATION_ID, buildNotification(status, paused));
    }

    @Override public void onDestroy() {
        stopListening();
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
        handler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }
}
