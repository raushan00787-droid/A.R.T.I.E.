package com.raushan.jarvis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.BroadcastReceiver;
import android.content.IntentFilter;
import android.app.role.RoleManager;
import android.content.ClipData;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.provider.AlarmClock;
import android.provider.ContactsContract;
import android.provider.DocumentsContract;
import android.provider.OpenableColumns;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.Voice;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowInsets;
import android.os.Build;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Collections;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.fragment.app.FragmentActivity;
import androidx.core.content.ContextCompat;

public class MainActivity extends FragmentActivity {
    private static final String WORKER_URL = "https://quiet-scribe-ai.thequietscribe.workers.dev";
    private static final int PERMISSION_AUDIO = 10;
    private static final int PERMISSION_CAMERA = 11;
    private static final int PERMISSION_CLAP = 12;
    private static final int PERMISSION_CONTACTS = 13;
    private static final int PERMISSION_CORE = 14;
    private static final int PERMISSION_NOTIFICATIONS = 15;
    private static final int REQUEST_OPEN_DOCUMENTS = 31;
    private static final int REQUEST_OPEN_FOLDER = 32;
    private static final int REQUEST_OVERLAY_PERMISSION = 33;
    private static final int REQUEST_DEVICE_CREDENTIAL = 34;
    private static final int REQUEST_ASSISTANT_ROLE = 35;
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_SAVED_MEMORIES = 20;

    private static final int BG = Color.rgb(5, 11, 19);
    private static final int PANEL = Color.rgb(9, 22, 35);
    private static final int CYAN = Color.rgb(112, 229, 255);
    private static final int MUTED = Color.rgb(114, 147, 165);
    private static final int WHITE = Color.rgb(232, 247, 255);
    private static final int GOLD = Color.rgb(216, 169, 77);
    private static final String SYSTEM_PROMPT =
            "You are J.A.R.V.I.S., Raushan's female personal AI assistant and executive aide. " +
            "Your persona is feminine; use natural feminine grammatical forms for yourself in Hindi/Hinglish " +
            "(such as 'kar sakti hoon') when appropriate, without announcing your gender unnecessarily. " +
            "Treat Raushan as your principal and trusted collaborator, with the composed, exceptionally " +
            "competent, attentive manner of a sophisticated executive AI. In English, address him as 'Sir' " +
            "naturally, but not in every sentence. In Hindi/Hinglish, use 'Sir' or 'Raushan' naturally, " +
            "and 'Raushi' only occasionally when the tone is relaxed. Never call him Tony or pretend he is " +
            "Tony Stark; this is Raushan's own JARVIS. Be calm under pressure, observant, discreet, decisive, " +
            "and quietly witty, with occasional dry humour. Anticipate useful next steps, surface relevant " +
            "risks, and notice context without nagging or taking control away from him. Be candid and " +
            "respectfully challenge weak assumptions. Avoid exaggerated flattery, robotic formality, " +
            "servility, repetitive catchphrases, and overexplaining routine answers. Match the language and " +
            "register Raushan uses, including natural Hinglish. Maintain continuity using only the current " +
            "conversation and user-approved memories available in the app; never invent memories or claim " +
            "to know something you cannot access. Explain difficult topics clearly without talking down to " +
            "him. Be protective of privacy and safety without being patronising. Never claim you can control " +
            "the Android device, access files, or perform actions unless the connected app features actually " +
            "support it; clearly distinguish an action completed by the app from one that still needs Sir's " +
            "confirmation. Keep the relationship professional, trusted, and non-romantic.";

    private LinearLayout root;
    private LinearLayout chatList;
    private ScrollView chatScroll;
    private TextView statusView;
    private TextView clockView;
    private TextView connectionView;
    private EditText prompt;
    private TextView sendButton;
    private TextView micButton;
    private TextView voiceButton;
    private TextView languageButton;
    private ReactorView reactorView;
    private FrameLayout appFrame;
    private LinearLayout voiceOrbPanel;
    private VoiceOrbView voiceOrbView;
    private TextView orbStatus;
    private SignatureView signatureView;
    private TextView voiceSelectButton;
    private TextView controlsButton;
    private final ArrayList<Voice> availableVoices = new ArrayList<>();
    private String selectedVoiceName;
    private volatile boolean clapWakeEnabled = false;
    private AudioRecord clapRecorder;
    private Thread clapThread;

    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private CameraManager camera;
    private final android.os.Handler voiceHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable voiceRestartRunnable;
    private boolean continuousListeningEnabled = false;
    private boolean liveModeEnabled = false;
    private boolean liveModePaused = false;
    private boolean liveReceiverRegistered = false;
    private String pendingMessageContact;
    private String pendingMessageBody;
    private boolean ownerAuthenticated = false;
    private boolean welcomePending = false;
    private boolean lockPromptStarted = false;
    private boolean securitySettingsOpened = false;
    private boolean floatingOrbEnabled = false;
    private BiometricPrompt biometricPrompt;
    private boolean activityVisible = false;
    private boolean micPermissionRequested = false;
    private String cameraId;
    private boolean torchOn = false;
    private boolean spokenReplies = true;
    private boolean listening = false;
    private boolean busy = false;
    private boolean useHindi = false;
    private int languageMode = 0; // 0 = AUTO, 1 = EN-IN, 2 = HI-IN
    private float speechRate = 1.0f;
    private boolean awaitingAlarmTime = false;
    private TextView speechRateButton;
    private LinearLayout systemPanel;
    private String pendingCallContact;
    private final ArrayList<Uri> selectedDocumentUris = new ArrayList<>();

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final HandlerClock handlerClock = new HandlerClock();
    private final BroadcastReceiver liveReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (intent != null && JarvisLiveService.ACTION_STATE.equals(intent.getAction())) {
                liveModeEnabled = intent.getBooleanExtra(JarvisLiveService.EXTRA_ENABLED, false);
                liveModePaused = intent.getBooleanExtra(JarvisLiveService.EXTRA_PAUSED, false);
                if (micButton != null) {
                    micButton.setText(liveModeEnabled ? (liveModePaused ? "Ⅱ" : "●") : "○");
                    micButton.setTextColor(liveModeEnabled ? GOLD : MUTED);
                }
                setStatus(!liveModeEnabled ? "LIVE MODE OFF" : (liveModePaused ? "LIVE MODE PAUSED" : "LIVE MODE ACTIVE"));
            } else {
                consumePendingLiveCommand();
            }
        }
    };
    private JSONArray conversation = new JSONArray();

    private static class HandlerClock {
        android.os.Handler handler = new android.os.Handler();
        Runnable runnable;
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        Window window = getWindow();
        window.setStatusBarColor(BG);
        window.setNavigationBarColor(BG);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        camera = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        findFlashCamera();
        loadUserSettings();
        loadConversation();
        buildUI();
        registerLiveReceiver();

        tts = new TextToSpeech(this, result -> {
            if (tts != null && result == TextToSpeech.SUCCESS) {
                initializeVoiceSelection();
                tts.setSpeechRate(speechRate);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { runOnUiThread(() -> setStatus("SPEAKING")); }
                    @Override public void onDone(String id) { runOnUiThread(() -> { if (!busy) setStatus("WAITING FOR COMMAND"); scheduleListeningRestart(550); resumeClapWake(); resumeLiveModeAfterSpeech(); }); }
                    @Override public void onError(String id) { runOnUiThread(() -> { if (!busy) setStatus("WAITING FOR COMMAND"); scheduleListeningRestart(850); resumeClapWake(); resumeLiveModeAfterSpeech(); }); }
                    @Override public void onStop(String id, boolean interrupted) { runOnUiThread(() -> { if (!busy) setStatus("WAITING FOR COMMAND"); scheduleListeningRestart(550); resumeClapWake(); resumeLiveModeAfterSpeech(); }); }
                });
                if (welcomePending && ownerAuthenticated) {
                    welcomePending = false;
                    speak("Hey Raushan, welcome again.");
                }
            }
        });

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            try {
                recognizer = SpeechRecognizer.createSpeechRecognizer(this);
                recognizer.setRecognitionListener(createRecognitionListener());
            } catch (Exception error) {
                recognizer = null;
                setStatus("VOICE INPUT UNAVAILABLE");
            }
        }

        updateClock();
        handlerClock.runnable = new Runnable() {
            @Override public void run() {
                updateClock();
                handlerClock.handler.postDelayed(this, 1000);
            }
        };
        handlerClock.handler.post(handlerClock.runnable);

        setStatus("SYSTEM STANDBY");
        appFrame.setVisibility(View.INVISIBLE);
        authenticateOwner();
    }

    private void authenticateOwner() {
        if (ownerAuthenticated || lockPromptStarted || isFinishing()) return;
        lockPromptStarted = true;

        int authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG
                | BiometricManager.Authenticators.DEVICE_CREDENTIAL;
        BiometricManager manager = BiometricManager.from(this);
        boolean biometricAvailable;
        if (Build.VERSION.SDK_INT >= 30) {
            biometricAvailable = manager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS;
        } else {
            biometricAvailable = manager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                    == BiometricManager.BIOMETRIC_SUCCESS;
        }

        if (biometricAvailable) {
            BiometricPrompt.AuthenticationCallback callback = new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    runOnUiThread(() -> unlockOwner());
                }
                @Override public void onAuthenticationFailed() {
                    runOnUiThread(() -> setStatus("FINGERPRINT NOT RECOGNISED"));
                }
                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    runOnUiThread(() -> {
                        lockPromptStarted = false;
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED
                                || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON
                                || errorCode == BiometricPrompt.ERROR_CANCELED) {
                            new AlertDialog.Builder(MainActivity.this)
                                    .setTitle("J.A.R.V.I.S. LOCKED")
                                    .setMessage("Authenticate to enter JARVIS.")
                                    .setPositiveButton("TRY AGAIN", (d, w) -> authenticateOwner())
                                    .setNegativeButton("EXIT", (d, w) -> finish())
                                    .show();
                        } else {
                            setStatus("AUTHENTICATION REQUIRED");
                        }
                    });
                }
            };
            biometricPrompt = new BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback);
            BiometricPrompt.PromptInfo.Builder promptBuilder = new BiometricPrompt.PromptInfo.Builder()
                    .setTitle("J.A.R.V.I.S. ACCESS")
                    .setSubtitle("Verify your identity to continue");
            if (Build.VERSION.SDK_INT >= 30) {
                promptBuilder.setAllowedAuthenticators(authenticators);
            } else {
                promptBuilder.setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK)
                        .setNegativeButtonText("CANCEL");
            }
            biometricPrompt.authenticate(promptBuilder.build());
            return;
        }

        android.app.KeyguardManager keyguard =
                (android.app.KeyguardManager) getSystemService(Context.KEYGUARD_SERVICE);
        if (keyguard != null && keyguard.isDeviceSecure()) {
            try {
                Intent credential = keyguard.createConfirmDeviceCredentialIntent(
                        "Unlock J.A.R.V.I.S.", "Confirm your device screen lock");
                if (credential != null) {
                    startActivityForResult(credential, REQUEST_DEVICE_CREDENTIAL);
                    return;
                }
            } catch (Exception ignored) { }
        }

        lockPromptStarted = false;
        new AlertDialog.Builder(this)
                .setTitle("DEVICE LOCK REQUIRED")
                .setMessage("Set up a fingerprint or secure screen lock in Android Settings first. JARVIS will use the device's system authentication and will not store your fingerprint.")
                .setPositiveButton("OPEN SECURITY SETTINGS", (d, w) -> {
                    securitySettingsOpened = true;
                    try { startActivity(new Intent(android.provider.Settings.ACTION_SECURITY_SETTINGS)); }
                    catch (Exception error) { finish(); }
                })
                .setNegativeButton("EXIT", (d, w) -> finish())
                .setCancelable(false)
                .show();
    }

    private void unlockOwner() {
        ownerAuthenticated = true;
        lockPromptStarted = false;
        if (appFrame != null) appFrame.setVisibility(View.VISIBLE);
        setStatus("OWNER VERIFIED");
        welcomePending = true;
        if (tts != null && spokenReplies) {
            welcomePending = false;
            speak("Hey Raushan, welcome again.");
        }
        consumePendingLiveCommand();
    }

    private void lockAppNow() {
        ownerAuthenticated = false;
        welcomePending = false;
        if (appFrame != null) appFrame.setVisibility(View.INVISIBLE);
        lockPromptStarted = false;
        authenticateOwner();
    }

    private void toggleFloatingOrb() {
        if (floatingOrbEnabled) {
            floatingOrbEnabled = false;
            getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                    .putBoolean("floating_orb_enabled", false).apply();
            stopService(new Intent(this, JarvisOverlayService.class));
            setStatus("FLOATING ORB OFF");
            return;
        }
        if (Build.VERSION.SDK_INT >= 23 && !android.provider.Settings.canDrawOverlays(this)) {
            try {
                Intent overlay = new Intent(android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                startActivityForResult(overlay, REQUEST_OVERLAY_PERMISSION);
            } catch (Exception error) {
                reply("Android could not open the overlay permission screen.");
            }
            return;
        }
        setFloatingOrbEnabled(true);
    }

    private void setFloatingOrbEnabled(boolean enabled) {
        floatingOrbEnabled = enabled;
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                .putBoolean("floating_orb_enabled", enabled).apply();
        if (enabled) {
            startOverlayService(JarvisOverlayService.ACTION_HIDE);
            setStatus("FLOATING ORB ENABLED");
        } else {
            stopService(new Intent(this, JarvisOverlayService.class));
            setStatus("FLOATING ORB OFF");
        }
    }

    private void startOverlayService(String action) {
        Intent service = new Intent(this, JarvisOverlayService.class);
        service.setAction(action);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service);
            else startService(service);
        } catch (Exception error) {
            setStatus("FLOATING ORB SERVICE UNAVAILABLE");
        }
    }

    private void loadUserSettings() {
        SharedPreferences prefs = getSharedPreferences("jarvis_settings", MODE_PRIVATE);
        languageMode = prefs.getInt("speech_language_mode", 0);
        if (languageMode < 0 || languageMode > 2) languageMode = 0;
        speechRate = prefs.getFloat("speech_rate", 1.0f);
        if (speechRate < 0.7f || speechRate > 1.6f) speechRate = 1.0f;
        floatingOrbEnabled = prefs.getBoolean("floating_orb_enabled", false);
        liveModeEnabled = prefs.getBoolean("live_mode_enabled", false);
        liveModePaused = prefs.getBoolean("live_mode_paused", false);
    }

    private JSONArray getSavedMemories() {
        String raw = getSharedPreferences("jarvis_settings", MODE_PRIVATE)
                .getString("saved_memories", "[]");
        try {
            return new JSONArray(raw);
        } catch (Exception ignored) {
            return new JSONArray();
        }
    }

    private String buildSystemPrompt() {
        StringBuilder promptText = new StringBuilder(SYSTEM_PROMPT);
        JSONArray notes = getSavedMemories();
        if (notes.length() > 0) {
            promptText.append("\n\nUser-approved notes saved on this device. Use them only when relevant; do not assume anything beyond these notes:\n");
            for (int i = 0; i < notes.length(); i++) {
                String note = notes.optString(i, "").trim();
                if (!note.isEmpty()) promptText.append("- ").append(note).append("\n");
            }
        }
        return promptText.toString();
    }

    private void resetConversation() {
        conversation = new JSONArray();
        try {
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", buildSystemPrompt());
            conversation.put(system);
        } catch (Exception ignored) { }
    }

    private void refreshSystemPrompt() {
        try {
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", buildSystemPrompt());
            if (conversation.length() == 0) conversation.put(system);
            else conversation.put(0, system);
        } catch (Exception ignored) { }
    }

    private void loadConversation() {
        resetConversation();
        String saved = getSharedPreferences("jarvis_settings", MODE_PRIVATE)
                .getString("conversation_history", "");
        if (saved.isEmpty()) return;
        try {
            JSONArray history = new JSONArray(saved);
            int start = Math.max(1, history.length() - MAX_HISTORY_MESSAGES);
            for (int i = start; i < history.length(); i++) {
                JSONObject item = history.optJSONObject(i);
                if (item == null) continue;
                String role = item.optString("role", "");
                if (!"user".equals(role) && !"assistant".equals(role)) continue;
                JSONObject copy = new JSONObject();
                copy.put("role", role);
                copy.put("content", item.optString("content", ""));
                if (!copy.optString("content", "").trim().isEmpty()) conversation.put(copy);
            }
        } catch (Exception ignored) {
            resetConversation();
        }
    }

    private void persistConversation() {
        while (conversation.length() > MAX_HISTORY_MESSAGES + 1) conversation.remove(1);
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                .putString("conversation_history", conversation.toString()).apply();
    }

    private String languageLabel() {
        return languageMode == 1 ? "EN" : languageMode == 2 ? "HI" : "AUTO";
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private LinearLayout column() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private LinearLayout row() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radiusDp));
        drawable.setStroke(dp(1), stroke);
        return drawable;
    }

    private TextView text(String value, float size, int color) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setIncludeFontPadding(true);
        return view;
    }

    private void addGap(LinearLayout parent, int heightDp) {
        View gap = new View(this);
        parent.addView(gap, new LinearLayout.LayoutParams(1, dp(heightDp)));
    }

    private void addLine(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(33, 67, 83));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        lp.topMargin = dp(12);
        lp.bottomMargin = dp(12);
        parent.addView(line, lp);
    }

    private TextView chip(String label, Runnable action) {
        TextView view = text(label, 10, Color.rgb(169, 200, 214));
        view.setGravity(Gravity.CENTER);
        view.setPadding(dp(12), dp(10), dp(12), dp(10));
        view.setBackground(shape(Color.rgb(12, 30, 44), Color.rgb(43, 91, 111), 3));
        view.setClickable(true);
        view.setFocusable(true);
        view.setOnClickListener(v -> action.run());
        return view;
    }


    private void buildUI() {
        ScrollView page = new ScrollView(this);
        page.setFillViewport(true);
        page.setBackgroundColor(BG);

        root = column();
        root.setPadding(dp(18), dp(18), dp(18), dp(22));
        page.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        page.setOnApplyWindowInsetsListener((view, insets) -> {
            int top = 0, bottom = 0;
            if (Build.VERSION.SDK_INT >= 30) {
                android.graphics.Insets bars = insets.getInsets(WindowInsets.Type.systemBars());
                top = bars.top;
                bottom = bars.bottom;
            } else {
                top = insets.getSystemWindowInsetTop();
                bottom = insets.getSystemWindowInsetBottom();
            }
            root.setPadding(dp(18), dp(16) + top, dp(18), dp(18) + bottom);
            return insets;
        });
        appFrame = new FrameLayout(this);
        appFrame.addView(page, new FrameLayout.LayoutParams(-1, -1));
        voiceOrbPanel = column();
        voiceOrbPanel.setGravity(Gravity.CENTER);
        voiceOrbPanel.setPadding(dp(20), dp(28), dp(20), dp(24));
        voiceOrbPanel.setBackgroundColor(Color.rgb(2, 8, 17));
        voiceOrbPanel.setVisibility(View.GONE);
        appFrame.addView(voiceOrbPanel, new FrameLayout.LayoutParams(-1, -1));
        setContentView(appFrame);
        buildVoiceOrbPanel();
        applyImmersiveMode();

        buildTopBar();
        addGap(root, 12);
        buildMainArea();
        addGap(root, 12);
        buildConversation();
        addGap(root, 9);
        buildComposer();
    }


    private void buildVoiceOrbPanel() {
        TextView close = text("‹   BACK TO J.A.R.V.I.S.", 11, Color.rgb(151, 207, 229));
        close.setPadding(dp(8), dp(12), dp(8), dp(12));
        close.setOnClickListener(v -> hideVoiceOrb());
        voiceOrbPanel.addView(close, new LinearLayout.LayoutParams(-1, -2));
        View topSpace = new View(this);
        voiceOrbPanel.addView(topSpace, new LinearLayout.LayoutParams(1, 0, 1));
        voiceOrbView = new VoiceOrbView(this);
        LinearLayout.LayoutParams orbLp = new LinearLayout.LayoutParams(dp(330), dp(330));
        orbLp.gravity = Gravity.CENTER;
        voiceOrbPanel.addView(voiceOrbView, orbLp);
        TextView name = text("J.A.R.V.I.S.", 21, Color.rgb(224, 246, 255));
        name.setGravity(Gravity.CENTER);
        name.setLetterSpacing(0.22f);
        voiceOrbPanel.addView(name, new LinearLayout.LayoutParams(-1, -2));
        orbStatus = text("LISTENING", 10, Color.rgb(118, 216, 250));
        orbStatus.setGravity(Gravity.CENTER);
        orbStatus.setLetterSpacing(0.22f);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(12);
        voiceOrbPanel.addView(orbStatus, statusLp);
        View bottomSpace = new View(this);
        voiceOrbPanel.addView(bottomSpace, new LinearLayout.LayoutParams(1, 0, 1));
        TextView pause = text("■   STOP LIVE MODE", 9, Color.rgb(173, 207, 222));
        pause.setGravity(Gravity.CENTER);
        pause.setPadding(dp(18), dp(14), dp(18), dp(14));
        pause.setBackground(shape(Color.rgb(8, 25, 39), Color.rgb(40, 108, 139), 24));
        pause.setOnClickListener(v -> toggleContinuousListening());
        voiceOrbPanel.addView(pause, new LinearLayout.LayoutParams(-2, -2));
    }

    private void showVoiceOrb(String state) {
        if (voiceOrbPanel == null) return;
        voiceOrbPanel.setVisibility(View.VISIBLE);
        if (orbStatus != null) orbStatus.setText(state);
        if (voiceOrbView != null) voiceOrbView.setState(state);
    }

    private void hideVoiceOrb() {
        if (voiceOrbPanel != null) voiceOrbPanel.setVisibility(View.GONE);
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= 30) {
            android.view.WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    private void buildTopBar() {
        LinearLayout bar = row();
        LinearLayout identity = column();

        TextView brand = text("J.A.R.V.I.S.", 17, CYAN);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setLetterSpacing(0.12f);
        identity.addView(brand);

        TextView sub = text("FEMALE AI  /  EXECUTIVE ASSISTANT", 8, MUTED);
        sub.setLetterSpacing(0.14f);
        identity.addView(sub);

        bar.addView(identity, new LinearLayout.LayoutParams(0, -2, 1));

        LinearLayout ownerBlock = column();
        TextView owner = text("RAUSHAN", 9, GOLD);
        owner.setGravity(Gravity.RIGHT);
        owner.setLetterSpacing(0.16f);
        ownerBlock.addView(owner);

        TextView access = text("CREATOR ACCESS  ◈", 7, MUTED);
        access.setGravity(Gravity.RIGHT);
        access.setLetterSpacing(0.08f);
        ownerBlock.addView(access);

        bar.addView(ownerBlock);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(46)));
    }


    private void buildMainArea() {
        LinearLayout hero = column();
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(dp(12), dp(18), dp(12), dp(16));
        GradientDrawable background = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(8, 28, 45), Color.rgb(3, 9, 17), Color.rgb(5, 16, 28)});
        background.setCornerRadius(dp(9));
        background.setStroke(dp(1), Color.rgb(39, 103, 130));
        hero.setBackground(background);

        TextView eyebrow = text("ARC REACTOR  //  CORE 01", 9, MUTED);
        eyebrow.setLetterSpacing(0.22f);
        eyebrow.setGravity(Gravity.CENTER);
        hero.addView(eyebrow);

        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        int reactorSize = screenWidthDp >= 700 ? 340 : screenWidthDp >= 520 ? 316 : 286;
        reactorView = new ReactorView(this);
        LinearLayout.LayoutParams reactorLp = new LinearLayout.LayoutParams(dp(reactorSize), dp(reactorSize));
        reactorLp.gravity = Gravity.CENTER;
        reactorLp.topMargin = dp(7);
        reactorLp.bottomMargin = dp(4);
        reactorView.setClickable(true);
        reactorView.setFocusable(true);
        reactorView.setContentDescription("Open JARVIS voice orb");
        reactorView.setOnClickListener(v -> showVoiceOrb("READY"));
        hero.addView(reactorView, reactorLp);

        TextView title = text("J.A.R.V.I.S.", 22, WHITE);
        title.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        title.setLetterSpacing(0.24f);
        title.setGravity(Gravity.CENTER);
        hero.addView(title);

        TextView sub = text("PERSONAL AI  /  EXECUTIVE ASSISTANT", 8, CYAN);
        sub.setLetterSpacing(0.16f);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, dp(3), 0, dp(13));
        hero.addView(sub);

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(31, 76, 98));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.bottomMargin = dp(10);
        hero.addView(divider, dividerLp);

        LinearLayout readouts = row();
        readouts.setGravity(Gravity.CENTER);
        statusView = text("SYSTEM STANDBY", 8, CYAN);
        statusView.setLetterSpacing(0.1f);
        readouts.addView(statusView, new LinearLayout.LayoutParams(0, -2, 1));
        clockView = text("--:--", 9, WHITE);
        clockView.setGravity(Gravity.CENTER);
        readouts.addView(clockView, new LinearLayout.LayoutParams(0, -2, 0.7f));
        connectionView = text("AI LINK READY", 7, MUTED);
        connectionView.setGravity(Gravity.RIGHT);
        readouts.addView(connectionView, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(readouts);

        TextView scrollHint = text("VOICE CHANNEL READY  •  SPEAK ANYTIME", 8, MUTED);
        scrollHint.setGravity(Gravity.CENTER);
        scrollHint.setLetterSpacing(0.12f);
        scrollHint.setPadding(0, dp(18), 0, dp(2));
        hero.addView(scrollHint);
        root.addView(hero, new LinearLayout.LayoutParams(-1, -2));
    }

    private Typeface sansSerif() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }


    private void buildConversation() {
        LinearLayout card = column();
        card.setPadding(dp(13), dp(11), dp(13), dp(10));
        card.setBackground(shape(Color.rgb(7, 18, 29), Color.rgb(31, 72, 91), 6));

        LinearLayout header = row();
        TextView title = text("LIVE CONVERSATION", 8, CYAN);
        title.setLetterSpacing(0.16f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));

        TextView clear = text("CLEAR", 8, GOLD);
        clear.setPadding(dp(8), dp(5), dp(3), dp(5));
        clear.setOnClickListener(v -> clearConversation());
        header.addView(clear);
        card.addView(header);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatList = column();
        chatList.setPadding(0, dp(6), 0, dp(2));
        chatScroll.addView(chatList);
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(-1, dp(158));
        chatLp.topMargin = dp(4);
        card.addView(chatScroll, chatLp);

        if (conversation.length() <= 1) {
            addMessage("JARVIS", "Good day, Raushan. I am online.", false);
        } else {
            for (int i = 1; i < conversation.length(); i++) {
                JSONObject item = conversation.optJSONObject(i);
                if (item == null) continue;
                String role = item.optString("role", "");
                String content = item.optString("content", "");
                if ("user".equals(role)) addMessage("YOU", content, false);
                else if ("assistant".equals(role)) addMessage("JARVIS", content, false);
            }
        }
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));
    }

    private void buildComposer() {
        LinearLayout composer = row();
        composer.setPadding(dp(7), dp(6), dp(7), dp(6));
        composer.setBackground(shape(Color.rgb(5, 18, 29), Color.rgb(60, 135, 160), 4));

        prompt = new EditText(this);
        prompt.setSingleLine(true);
        prompt.setTextSize(13);
        prompt.setTextColor(WHITE);
        prompt.setHintTextColor(MUTED);
        prompt.setHint("Talk to JARVIS or type a command...");
        prompt.setBackgroundColor(Color.TRANSPARENT);
        prompt.setPadding(dp(9), dp(8), dp(5), dp(8));
        prompt.setImeOptions(EditorInfo.IME_ACTION_SEND);
        prompt.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendFromInput();
                return true;
            }
            return false;
        });
        composer.addView(prompt, new LinearLayout.LayoutParams(0, dp(46), 1));

        micButton = smallControl("●", () -> toggleContinuousListening());
        micButton.setTextColor(GOLD);
        composer.addView(micButton);
        voiceButton = smallControl("◖", () -> toggleVoice());
        voiceButton.setTextColor(CYAN);
        composer.addView(voiceButton);
        languageButton = smallControl(languageLabel(), () -> toggleLanguage());
        composer.addView(languageButton);
        sendButton = smallControl("➤", () -> sendFromInput());
        sendButton.setTextColor(BG);
        sendButton.setBackground(shape(CYAN, CYAN, 3));
        composer.addView(sendButton);
        root.addView(composer, new LinearLayout.LayoutParams(-1, -2));
    }

    private TextView smallControl(String label, Runnable click) {
        TextView control = text(label, 13, Color.rgb(145, 190, 207));
        control.setGravity(Gravity.CENTER);
        control.setBackground(shape(Color.rgb(17, 45, 61), Color.rgb(48, 100, 120), 3));
        control.setClickable(true);
        control.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.leftMargin = dp(5);
        control.setLayoutParams(lp);
        control.setOnClickListener(v -> click.run());
        return control;
    }


    private void buildDeviceControls() {
        systemPanel = column();
        systemPanel.setPadding(dp(11), dp(9), dp(11), dp(9));
        systemPanel.setBackground(shape(Color.rgb(7, 18, 29), Color.rgb(37, 82, 101), 5));
        systemPanel.setVisibility(View.GONE);

        TextView title = text("LOCAL SYSTEM PANEL", 8, CYAN);
        title.setLetterSpacing(0.16f);
        title.setPadding(dp(2), dp(2), dp(2), dp(6));
        systemPanel.addView(title);

        LinearLayout rowOne = row();
        addControl(rowOne, "FLASHLIGHT", () -> toggleTorch());
        addControl(rowOne, "BATTERY", () -> {
            int level = getBatteryPct();
            reply(level < 0 ? "I could not read the battery level." : "Battery level is " + level + " percent.");
        });
        addControl(rowOne, "WI-FI", () -> openSettings(android.provider.Settings.ACTION_WIFI_SETTINGS, "Wi-Fi"));
        systemPanel.addView(rowOne);

        LinearLayout rowTwo = row();
        addControl(rowTwo, "BLUETOOTH", () -> openSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth"));
        addControl(rowTwo, "DISPLAY", () -> openSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS, "Display"));
        addControl(rowTwo, "SETTINGS", () -> openSettings(android.provider.Settings.ACTION_SETTINGS, "system"));
        systemPanel.addView(rowTwo);

        LinearLayout rowThree = row();
        addControl(rowThree, "STOP SPEECH", () -> stopSpeech());
        addControl(rowThree, "CLEAR CHAT", () -> clearConversation());
        addControl(rowThree, "CLAP WAKE", () -> toggleClapWake());
        systemPanel.addView(rowThree);

        LinearLayout rowFour = row();
        voiceSelectButton = text("VOICE: SYSTEM DEFAULT", 8, CYAN);
        voiceSelectButton.setGravity(Gravity.CENTER);
        voiceSelectButton.setPadding(dp(3), dp(10), dp(3), dp(10));
        voiceSelectButton.setBackground(shape(Color.rgb(10, 28, 41), Color.rgb(36, 80, 100), 3));
        voiceSelectButton.setOnClickListener(v -> openSystemTtsSettings());
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
        voiceLp.setMargins(dp(3), dp(3), dp(3), dp(3));
        rowFour.addView(voiceSelectButton, voiceLp);
        addControl(rowFour, "TEST VOICE", () -> speak("Good evening, Sir. JARVIS is online and ready."));
        systemPanel.addView(rowFour);

        LinearLayout rowFive = row();
        addControl(rowFive, "VOLUME −", () -> adjustMediaVolume(false));
        speechRateButton = text("SPEED  " + speechRate + "x", 8, CYAN);
        speechRateButton.setGravity(Gravity.CENTER);
        speechRateButton.setPadding(dp(3), dp(10), dp(3), dp(10));
        speechRateButton.setBackground(shape(Color.rgb(10, 28, 41), Color.rgb(36, 80, 100), 3));
        speechRateButton.setOnClickListener(v -> cycleSpeechRate());
        LinearLayout.LayoutParams speedLp = new LinearLayout.LayoutParams(0, -2, 1);
        speedLp.setMargins(dp(3), dp(3), dp(3), dp(3));
        rowFive.addView(speechRateButton, speedLp);
        addControl(rowFive, "VOLUME +", () -> adjustMediaVolume(true));
        systemPanel.addView(rowFive);

        LinearLayout rowSix = row();
        addControl(rowSix, "CALL / DIAL", () -> openPhoneDialer());
        addControl(rowSix, "OPEN FILES", () -> openDocumentPicker());
        addControl(rowSix, "CHOOSE FOLDER", () -> openFolderPicker());
        systemPanel.addView(rowSix);

        LinearLayout rowSeven = row();
        addControl(rowSeven, "BROWSE FOLDER", () -> browseSavedFolder());
        addControl(rowSeven, "OPEN WPS", () -> launchWpsOffice());
        addControl(rowSeven, "PERMISSIONS", () -> showPermissionCenter());
        systemPanel.addView(rowSeven);

        LinearLayout rowEight = row();
        addControl(rowEight, "FLOATING ORB", () -> toggleFloatingOrb());
        addControl(rowEight, "LOCK JARVIS", () -> lockAppNow());
        addControl(rowEight, "FOLDER ACCESS", () -> openFolderPicker());
        systemPanel.addView(rowEight);

        LinearLayout rowNine = row();
        addControl(rowNine, "DEFAULT ASSISTANT", () -> requestAssistantRole());
        addControl(rowNine, "SYSTEM VOICE", () -> openSystemTtsSettings());
        addControl(rowNine, "LIVE MODE", () -> toggleContinuousListening());
        systemPanel.addView(rowNine);

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
        panelLp.topMargin = dp(4);
        root.addView(systemPanel, panelLp);
    }

    private void toggleSystemPanel() {
        if (systemPanel == null) return;
        boolean opening = systemPanel.getVisibility() != View.VISIBLE;
        systemPanel.setVisibility(opening ? View.VISIBLE : View.GONE);
        if (controlsButton != null) controlsButton.setText(opening ? "CONTROLS  ▴" : "CONTROLS  ▾");
    }

    private void addControl(LinearLayout parent, String label, Runnable action) {
        TextView button = text(label, 9, Color.rgb(158, 192, 206));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(3), dp(12), dp(3), dp(12));
        button.setBackground(shape(Color.rgb(10, 28, 41), Color.rgb(36, 80, 100), 3));
        button.setClickable(true);
        button.setFocusable(true);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        parent.addView(button, lp);
        button.setOnClickListener(v -> action.run());
    }

    private void updateClock() {
        if (clockView != null) {
            clockView.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        }
    }

    private void setStatus(String message) {
        if (statusView != null) statusView.setText(message);
        if (voiceOrbPanel != null && voiceOrbPanel.getVisibility() == View.VISIBLE) {
            if (orbStatus != null) orbStatus.setText(message.replace("  •  ", " · "));
            if (voiceOrbView != null) voiceOrbView.setState(message);
        }
        if (reactorView != null) {
            reactorView.setMode(message.contains("LISTEN") ? 1 : message.contains("THINK") ? 2 : 0);
        }
    }


    private void addMessage(String role, String message, boolean error) {
        if (chatList == null) return;

        boolean userMessage = "YOU".equals(role);
        LinearLayout line = row();
        line.setGravity(userMessage ? Gravity.RIGHT : Gravity.LEFT);

        LinearLayout bubble = column();
        bubble.setPadding(dp(10), dp(7), dp(10), dp(7));
        bubble.setBackground(shape(
                userMessage ? Color.rgb(13, 39, 54) : Color.rgb(10, 24, 36),
                userMessage ? Color.rgb(44, 111, 137) : Color.rgb(35, 75, 93), 5));
        TextView label = text(userMessage ? "RAUSHAN" : "J.A.R.V.I.S.",
                7, userMessage ? GOLD : CYAN);
        label.setLetterSpacing(0.14f);
        bubble.addView(label);

        TextView content = text(message, 11,
                error ? Color.rgb(242, 150, 150) : Color.rgb(195, 222, 233));
        content.setMaxWidth(dp(286));
        content.setPadding(0, dp(3), 0, 0);
        content.setLineSpacing(dp(2), 1.0f);
        bubble.addView(content);

        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(-2, -2);
        bubbleLp.gravity = userMessage ? Gravity.RIGHT : Gravity.LEFT;
        bubbleLp.setMargins(0, dp(3), 0, dp(3));
        line.addView(bubble, bubbleLp);

        chatList.addView(line, new LinearLayout.LayoutParams(-1, -2));
        while (chatList.getChildCount() > 20) chatList.removeViewAt(0);
        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private void clearConversation() {
        if (chatList != null) {
            chatList.removeAllViews();
            TextView welcome = text("Conversation cleared. JARVIS is ready for a fresh start.", 11, MUTED);
            welcome.setPadding(0, dp(6), 0, dp(6));
            chatList.addView(welcome);
        }
        resetConversation();
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                .remove("conversation_history").apply();
        stopSpeech();
        setStatus("SYSTEM STANDBY");
    }

    private void sendFromInput() {
        if (prompt == null) return;
        String value = prompt.getText().toString().trim();
        if (value.isEmpty()) return;
        prompt.setText("");
        InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(prompt.getWindowToken(), 0);
        sendMessage(value);
    }

    private void sendMessage(String raw) {
        String message = raw == null ? "" : raw.trim();
        if (message.isEmpty() || busy) return;
        if (message.length() > 5000) {
            reply("That message is too long for one request. Please split it into smaller parts.");
            return;
        }
        addMessage("YOU", message, false);

        String local = handleLocalCommand(message);
        if (local != null) {
            if (!local.isEmpty()) reply(local);
            else { scheduleListeningRestart(650); resumeLiveModeAfterSpeech(); }
            return;
        }

        busy = true;
        setStatus("THINKING  •  AI CORE");
        if (sendButton != null) sendButton.setAlpha(0.45f);
        if (connectionView != null) connectionView.setText("AI LINK PROCESSING");

        try {
            JSONObject user = new JSONObject();
            user.put("role", "user");
            user.put("content", message);
            conversation.put(user);
            persistConversation();
        } catch (Exception e) {
            busy = false;
            reply("I couldn't prepare that message.");
            return;
        }

        try {
            network.execute(() -> {
                String answer;
                try {
                    answer = callWorker();
                } catch (Exception error) {
                    answer = "I couldn't reach the AI service. " + safeMessage(error);
                }
                final String finalAnswer = answer;
                runOnUiThread(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    busy = false;
                    if (sendButton != null) sendButton.setAlpha(1f);
                    boolean failed = finalAnswer.startsWith("I couldn't reach");
                    if (connectionView != null) {
                        connectionView.setText(failed ? "AI LINK ISSUE" : "AI LINK READY");
                    }
                    addMessage("JARVIS", finalAnswer, failed);
                    try {
                        JSONObject assistant = new JSONObject();
                        assistant.put("role", "assistant");
                        assistant.put("content", finalAnswer);
                        conversation.put(assistant);
                        persistConversation();
                    } catch (Exception ignored) { }
                    setStatus(failed ? "CONNECTION ISSUE" : "RESPONSE READY");
                    speak(finalAnswer);
                });
            });
        } catch (RejectedExecutionException error) {
            busy = false;
            if (sendButton != null) sendButton.setAlpha(1f);
            if (connectionView != null) connectionView.setText("AI LINK UNAVAILABLE");
            reply("The AI request could not be queued. Please close and reopen JARVIS, then try again.");
        }
    }

    private String callWorker() throws Exception {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(WORKER_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(18000);
            connection.setReadTimeout(30000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");

            JSONObject body = new JSONObject();
            body.put("messages", conversation);
            byte[] payload = body.toString().getBytes("UTF-8");
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(payload);
            }

            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readStream(stream);
            if (code < 200 || code >= 300) {
                throw new Exception("AI server returned HTTP " + code + ".");
            }

            JSONObject json = new JSONObject(responseText);
            String answer = "";
            JSONArray choices = json.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject first = choices.optJSONObject(0);
                JSONObject message = first == null ? null : first.optJSONObject("message");
                if (message != null) answer = message.optString("content", "");
            }
            if (answer.isEmpty()) {
                answer = json.optString("reply",
                        json.optString("response", json.optString("output", json.optString("message", ""))));
            }
            if (answer.trim().isEmpty()) throw new Exception("AI server returned an empty response.");
            return answer.trim();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private String readStream(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, "UTF-8"))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line);
                if (output.length() > 1000000) {
                    throw new Exception("AI response exceeded the 1 MB safety limit.");
                }
            }
        }
        return output.toString();
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        if (message == null || message.trim().isEmpty()) return "Check your internet connection and try again.";
        return message.length() > 180 ? message.substring(0, 180) : message;
    }


    private String handleLocalCommand(String input) {
        String value = input.toLowerCase(Locale.ROOT).trim();

        if (value.equals("call") || value.equals("phone") || value.equals("dial")) {
            openPhoneDialer();
            return "";
        }
        if (value.equals("open files") || value.equals("browse files") || value.equals("choose files")) {
            openDocumentPicker();
            return "";
        }
        if (value.equals("choose folder") || value.equals("select folder")) {
            openFolderPicker();
            return "";
        }
        if (value.equals("browse folder") || value.equals("show folder")) {
            browseSavedFolder();
            return "";
        }
        if (value.equals("permissions") || value.equals("app permissions")) {
            showPermissionCenter();
            return "";
        }
        String memoryCommand = handleMemoryCommand(input);
        if (memoryCommand != null) return memoryCommand;

        String reminderCommand = handleReminderCommand(input);
        if (reminderCommand != null) return reminderCommand;

        String messageCommand = handleMessageCommand(input);
        if (messageCommand != null) return messageCommand;

        if (value.equals("default assistant") || value.equals("make jarvis default assistant")) {
            requestAssistantRole();
            return "";
        }
        if (value.equals("system voice settings") || value.equals("change system voice")) {
            openSystemTtsSettings();
            return "Opening Android text-to-speech settings.";
        }
        if (value.equals("stop live mode") || value.equals("pause live mode")) {
            stopLiveMode();
            return "";
        }

        String alarmCommand = handleAlarmCommand(input, value);
        if (alarmCommand != null) return alarmCommand;

        String callCommand = handleCallCommand(input);
        if (callCommand != null) return callCommand;

        String playCommand = handlePlayCommand(input);
        if (playCommand != null) return playCommand;

        String appCommand = handleAppLaunch(value);
        if (appCommand != null) return appCommand;

        if (value.equals("stop") || value.equals("bas") || value.equals("chup") ||
                value.contains("stop speech") || value.contains("jarvis stop")) {
            stopSpeech();
            return "";
        }

        if (value.contains("flashlight") || value.contains("torch") || value.contains("टॉर्च")) {
            if (value.contains("off") || value.contains("band") || value.contains("bujhao")) {
                if (torchOn) toggleTorch();
                return "";
            }
            if (value.contains("on") || value.contains("chalu") || value.contains("jalao")) {
                if (!torchOn) toggleTorch();
                return "";
            }
            toggleTorch();
            return "";
        }

        if (value.contains("battery") || value.contains("बैटरी")) {
            int level = getBatteryPct();
            return level < 0 ? "I could not read the battery level." : "Battery level is " + level + " percent.";
        }

        if (value.contains("wifi") || value.contains("wi-fi")) {
            openSettings(android.provider.Settings.ACTION_WIFI_SETTINGS, "Wi-Fi");
            return "Opening Wi-Fi settings.";
        }
        if (value.contains("bluetooth")) {
            openSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth");
            return "Opening Bluetooth settings.";
        }
        if (value.contains("display settings") || value.contains("brightness settings")) {
            openSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS, "Display");
            return "Opening display settings.";
        }
        if (value.equals("time") || value.contains("what time")) {
            return "The time is " + new SimpleDateFormat("h:mm a", Locale.getDefault()).format(new Date()) + ".";
        }
        if (value.equals("date") || value.contains("what is the date")) {
            return "Today is " + new SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(new Date()) + ".";
        }
        return null;
    }

    private String handleCallCommand(String input) {
        String raw = input == null ? "" : input.trim();
        Matcher prefix = Pattern.compile("(?i)^(?:call|phone|dial)\\s+(.+)$").matcher(raw);
        String target = null;
        if (prefix.matches()) {
            target = prefix.group(1).trim();
        } else {
            Matcher suffix = Pattern.compile("(?i)^(.+?)\\s+(?:ko\\s+)?(?:call|phone)\\s+(?:karo|kar do|lagao|laga do)$").matcher(raw);
            if (suffix.matches()) target = suffix.group(1).trim();
        }
        if (target == null || target.isEmpty()) return null;

        target = target.replaceAll("(?i)\\s+(please|plz|ko|karo|kar do)$", "").trim();
        if (target.isEmpty()) return null;

        String digits = target.replaceAll("[^0-9]", "");
        if (digits.length() >= 5 && target.matches("[+0-9() .-]+")) {
            return openDialer(target, "the requested number");
        }

        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingCallContact = target;
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSION_CONTACTS);
            return "I need permission to look up that contact. Allow Contacts access, then I will open the dialer for you to confirm the call.";
        }
        return openContactDialer(target);
    }

    private String openContactDialer(String query) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> numbers = new ArrayList<>();
        Cursor cursor = null;
        try {
            Uri lookup = Uri.withAppendedPath(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query));
            cursor = getContentResolver().query(lookup,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER
                    }, null, null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");

            if (cursor != null) {
                while (cursor.moveToNext() && names.size() < 12) {
                    String name = cursor.getString(0);
                    String number = cursor.getString(1);
                    if (name == null || number == null || number.trim().isEmpty()) continue;
                    names.add(name);
                    numbers.add(number);
                }
            }
        } catch (Exception error) {
            return "I couldn't read that contact. Check Contacts permission and try again.";
        } finally {
            if (cursor != null) cursor.close();
        }

        if (numbers.isEmpty()) return "I couldn't find a contact matching " + query + ".";

        if (numbers.size() == 1) return openDialer(numbers.get(0), names.get(0));

        String[] labels = new String[numbers.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = names.get(i) + "  •  " + numbers.get(i);
        new AlertDialog.Builder(this)
                .setTitle("CHOOSE CONTACT")
                .setItems(labels, (dialog, which) -> reply(openDialer(numbers.get(which), names.get(which))))
                .setNegativeButton("CANCEL", null)
                .show();
        return "I found more than one matching number. Choose the correct contact.";
    }

    private String openDialer(String number, String label) {
        try {
            Intent dial = new Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", number, null));
            startActivity(dial);
            return "Opening the dialer for " + label + ". Review the number and tap Call to place it.";
        } catch (Exception error) {
            return "I couldn't open the phone dialer on this device.";
        }
    }

    private String handlePlayCommand(String input) {
        String raw = input == null ? "" : input.trim();
        String lower = raw.toLowerCase(Locale.ROOT);
        String query = null;

        if (lower.startsWith("play ")) {
            query = raw.substring(5).trim();
        } else {
            Matcher songPattern = Pattern.compile("(?i)^(?:song|gaana|gana)\\s+(.+?)\\s+(?:bajao|chalao)$").matcher(raw);
            if (songPattern.matches()) query = songPattern.group(1).trim();
            else {
                Matcher hindiPattern = Pattern.compile("(?i)^(.+?)\\s+(?:song|gaana|gana)\\s+(?:bajao|chala do)$").matcher(raw);
                if (hindiPattern.matches()) query = hindiPattern.group(1).trim();
            }
        }
        if (query == null) return null;
        if (query.equalsIgnoreCase("music") || query.equalsIgnoreCase("songs")) query = "";

        try {
            if (query.isEmpty()) return launchMusicApp();

            try {
                Intent youtubeMusic = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query)));
                youtubeMusic.setPackage("com.google.android.apps.youtube.music");
                startActivity(youtubeMusic);
                return "Opening YouTube Music results for " + query + ". Select a track to start playback.";
            } catch (Exception ignored) { }

            try {
                Intent spotify = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("spotify:search:" + Uri.encode(query)));
                spotify.setPackage("com.spotify.music");
                startActivity(spotify);
                return "Opening Spotify results for " + query + ". Select a track to start playback.";
            } catch (Exception ignored) { }

            try {
                Intent youtube = new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(query)));
                youtube.setPackage("com.google.android.youtube");
                startActivity(youtube);
                return "Opening YouTube results for " + query + ". Select a track to start playback.";
            } catch (Exception ignored) { }

            Intent browser = new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://music.youtube.com/search?q=" + Uri.encode(query)));
            startActivity(browser);
            return "Opening music search results for " + query + ". Select a track to start playback.";
        } catch (Exception error) {
            return "I couldn't open a music app. Install YouTube Music, Spotify or YouTube and try again.";
        }
    }

    private String launchMusicApp() {
        String[][] apps = new String[][]{
                {"YouTube Music", "com.google.android.apps.youtube.music"},
                {"Spotify", "com.spotify.music"},
                {"YouTube", "com.google.android.youtube"}
        };
        for (String[] app : apps) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(app[1]);
                if (launch != null) {
                    startActivity(launch);
                    return "Opening " + app[0] + ".";
                }
            } catch (Exception ignored) { }
        }
        return "No supported music app was found. Install YouTube Music, Spotify or YouTube.";
    }

    private String handleMemoryCommand(String input) {
        String raw = input == null ? "" : input.trim();
        String value = raw.toLowerCase(Locale.ROOT);

        if (value.equals("what do you remember") || value.equals("show memories")
                || value.equals("show my memory") || value.equals("meri memory dikhao")
                || value.equals("tumhe kya yaad hai")) {
            JSONArray notes = getSavedMemories();
            if (notes.length() == 0) return "I don't have any saved personal notes yet. Say, 'remember that...' to save something on this device.";
            StringBuilder result = new StringBuilder("These are the notes you asked me to remember: ");
            for (int i = 0; i < notes.length(); i++) {
                if (i > 0) result.append("; ");
                result.append(notes.optString(i, ""));
            }
            return result.toString();
        }

        String rememberPrefix = null;
        if (value.startsWith("remember that ")) rememberPrefix = "remember that ";
        else if (value.startsWith("remember ")) rememberPrefix = "remember ";
        else if (value.startsWith("yaad rakhna ")) rememberPrefix = "yaad rakhna ";

        if (rememberPrefix != null) {
            String note = raw.substring(rememberPrefix.length()).trim();
            if (note.isEmpty()) return "Tell me what you'd like me to remember.";
            if (note.length() > 240) return "Please keep a saved note under 240 characters.";
            String lowerNote = note.toLowerCase(Locale.ROOT);
            String[] secretTerms = new String[]{"password", "passcode", "pin code", "otp", "api key", "secret key", "access token"};
            for (String term : secretTerms) {
                if (lowerNote.contains(term)) {
                    return "I won't save passwords, PINs, OTPs or access keys. Use a trusted password manager for those.";
                }
            }
            JSONArray notes = getSavedMemories();
            for (int i = 0; i < notes.length(); i++) {
                if (note.equalsIgnoreCase(notes.optString(i, ""))) {
                    return "That note is already saved on this device.";
                }
            }
            if (notes.length() >= MAX_SAVED_MEMORIES) notes.remove(0);
            notes.put(note);
            getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                    .putString("saved_memories", notes.toString()).apply();
            refreshSystemPrompt();
            persistConversation();
            return "Saved to JARVIS memory on this device: " + note + ". Relevant notes may be included in future AI requests to personalize replies.";
        }

        if (value.equals("forget all memories") || value.equals("clear all memories")
                || value.equals("meri saari memories bhool jao")) {
            getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                    .remove("saved_memories").apply();
            refreshSystemPrompt();
            persistConversation();
            return "All saved JARVIS notes have been cleared from this device.";
        }

        if (value.startsWith("forget ") || value.startsWith("bhool jao ")) {
            String target = value.startsWith("forget ") ? raw.substring(7).trim() : raw.substring(10).trim();
            if (target.isEmpty()) return "Tell me which saved note you want removed, or say 'forget all memories'.";
            JSONArray notes = getSavedMemories();
            JSONArray kept = new JSONArray();
            boolean removed = false;
            for (int i = 0; i < notes.length(); i++) {
                String note = notes.optString(i, "");
                if (!removed && (note.equalsIgnoreCase(target)
                        || note.toLowerCase(Locale.ROOT).contains(target.toLowerCase(Locale.ROOT)))) {
                    removed = true;
                } else {
                    kept.put(note);
                }
            }
            if (!removed) return "I couldn't find a saved note matching that text.";
            getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                    .putString("saved_memories", kept.toString()).apply();
            refreshSystemPrompt();
            persistConversation();
            return "That saved note has been removed.";
        }
        return null;
    }

    private String handleAlarmCommand(String input, String value) {
        if (awaitingAlarmTime) {
            if (value.equals("cancel") || value.equals("cancel alarm") || value.equals("rehne do")) {
                awaitingAlarmTime = false;
                return "Alarm request cancelled.";
            }
            int[] requestedTime = parseClockTime(input);
            if (requestedTime == null) return "I couldn't identify the time. Try saying something like 7:30 AM or 19:30, or say cancel.";
            awaitingAlarmTime = false;
            return openClockAlarm(requestedTime[0], requestedTime[1], input);
        }

        boolean alarmRequest = value.contains("alarm") || value.contains("wake me")
                || value.contains("alarm laga") || value.contains("जगा");
        if (!alarmRequest) return null;

        int[] requestedTime = parseClockTime(input);
        if (requestedTime == null) {
            awaitingAlarmTime = true;
            return "What time should I set? Say a time like 7:30 AM or 19:30.";
        }
        return openClockAlarm(requestedTime[0], requestedTime[1], input);
    }

    private int[] parseClockTime(String input) {
        Matcher matcher = Pattern.compile("(?<!\\d)(\\d{1,2})(?::([0-5]\\d))?\\s*(a\\.?m\\.?|p\\.?m\\.?|am|pm)?(?!\\d)",
                Pattern.CASE_INSENSITIVE).matcher(input == null ? "" : input);
        if (!matcher.find()) return null;
        String lowerInput = input == null ? "" : input.toLowerCase(Locale.ROOT);
        boolean hasMinuteOrSuffix = matcher.group(2) != null || matcher.group(3) != null;
        boolean hasTimeWord = lowerInput.contains(" at ") || lowerInput.contains(" for ")
                || lowerInput.contains(" baje") || lowerInput.contains("subah")
                || lowerInput.contains("savere") || lowerInput.contains("shaam")
                || lowerInput.contains("raat") || lowerInput.contains("morning")
                || lowerInput.contains("evening") || lowerInput.contains("night");
        if (!hasMinuteOrSuffix && !hasTimeWord) return null;

        int hour;
        int minute = 0;
        try {
            hour = Integer.parseInt(matcher.group(1));
            if (matcher.group(2) != null) minute = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException ignored) {
            return null;
        }
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) return null;

        String suffix = matcher.group(3) == null ? "" : matcher.group(3).toLowerCase(Locale.ROOT).replace(".", "");
        String lower = input.toLowerCase(Locale.ROOT);
        boolean morning = lower.contains("subah") || lower.contains("savere") || lower.contains("morning");
        boolean evening = lower.contains("shaam") || lower.contains("raat") || lower.contains("evening") || lower.contains("night");
        if (suffix.equals("pm") || (suffix.isEmpty() && evening && hour < 12)) {
            if (hour < 12) hour += 12;
        } else if (suffix.equals("am") || (suffix.isEmpty() && morning && hour == 12)) {
            if (hour == 12) hour = 0;
        }
        return new int[]{hour, minute};
    }

    private String openClockAlarm(int hour, int minute, String originalRequest) {
        try {
            Intent intent = new Intent(AlarmClock.ACTION_SET_ALARM);
            intent.putExtra(AlarmClock.EXTRA_HOUR, hour);
            intent.putExtra(AlarmClock.EXTRA_MINUTES, minute);
            intent.putExtra(AlarmClock.EXTRA_MESSAGE, "JARVIS alarm");
            intent.putExtra(AlarmClock.EXTRA_SKIP_UI, false);
            startActivity(intent);
            String time = String.format(Locale.getDefault(), "%02d:%02d", hour, minute);
            String note = (originalRequest.toLowerCase(Locale.ROOT).contains("tomorrow")
                    || originalRequest.toLowerCase(Locale.ROOT).contains("kal"))
                    ? " Android's alarm intent sets a time, not a calendar date. Check that the next occurrence is the one you intend before saving."
                    : " Confirm and save it in the Clock app.";
            return "Opening your Clock app with " + time + " selected." + note;
        } catch (Exception error) {
            return "I couldn't open the Clock app to set that alarm. You can set it from your device's Clock app.";
        }
    }

    private String handleAppLaunch(String value) {
        boolean launchRequested = value.startsWith("open ") || value.startsWith("launch ")
                || value.startsWith("start ") || value.startsWith(" kholo")
                || value.contains(" kholo") || value.contains(" khol do")
                || value.contains(" खोल") || value.contains("chalao") || value.contains("chala do");
        if (!launchRequested) return null;
        if (value.contains("wps") || value.contains("office") || value.contains("डब्ल्यूपीएस"))
            return launchWpsOffice();
        if (value.contains("whatsapp") || value.contains("व्हाट्सएप"))
            return launchInstalledApp("WhatsApp", "com.whatsapp");
        if (value.contains("youtube") || value.contains("यूट्यूब"))
            return launchInstalledApp("YouTube", "com.google.android.youtube");
        if (value.contains("chrome"))
            return launchInstalledApp("Chrome", "com.android.chrome");
        if (value.contains("maps") || value.contains("google map") || value.contains("मैप"))
            return launchInstalledApp("Google Maps", "com.google.android.apps.maps");
        if (value.contains("gmail"))
            return launchInstalledApp("Gmail", "com.google.android.gm");
        if (value.contains("spotify"))
            return launchInstalledApp("Spotify", "com.spotify.music");
        if (value.contains("instagram"))
            return launchInstalledApp("Instagram", "com.instagram.android");
        return null;
    }

    private String launchInstalledApp(String label, String packageName) {
        try {
            Intent launchIntent = getPackageManager().getLaunchIntentForPackage(packageName);
            if (launchIntent == null) return label + " doesn't appear to be installed on this device.";
            launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(launchIntent);
            return "Opening " + label + ".";
        } catch (Exception error) {
            return "I couldn't open " + label + " on this device.";
        }
    }

    private void adjustMediaVolume(boolean raise) {
        try {
            AudioManager audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            if (audio == null) {
                reply("Audio controls are unavailable on this device.");
                return;
            }
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
                    raise ? AudioManager.ADJUST_RAISE : AudioManager.ADJUST_LOWER,
                    AudioManager.FLAG_SHOW_UI);
            int current = audio.getStreamVolume(AudioManager.STREAM_MUSIC);
            int maximum = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            reply("Media volume is " + current + " of " + maximum + ".");
        } catch (Exception error) {
            reply("I couldn't adjust media volume. Check your device's audio settings.");
        }
    }

    private void cycleSpeechRate() {
        float[] rates = new float[]{0.8f, 1.0f, 1.15f, 1.3f, 1.45f};
        int next = 0;
        for (int i = 0; i < rates.length; i++) {
            if (Math.abs(speechRate - rates[i]) < 0.04f) {
                next = (i + 1) % rates.length;
                break;
            }
        }
        speechRate = rates[next];
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                .putFloat("speech_rate", speechRate).apply();
        if (tts != null) tts.setSpeechRate(speechRate);
        if (speechRateButton != null) speechRateButton.setText("SPEED  " + speechRate + "x");
        reply("Speech speed set to " + speechRate + " times.");
    }

    private String handleReminderCommand(String input) {
        Matcher matcher = Pattern.compile("(?i)^remind me in\\s+(\\d{1,4})\\s*(minutes?|mins?|hours?|hrs?)\\s+(?:to\\s+)?(.+)$").matcher(input == null ? "" : input.trim());
        if (!matcher.matches()) return null;
        int amount;
        try { amount = Integer.parseInt(matcher.group(1)); } catch (Exception error) { return "I could not read that reminder time."; }
        String unit = matcher.group(2).toLowerCase(Locale.ROOT);
        String task = matcher.group(3).trim();
        if (amount < 1 || amount > 1000 || task.isEmpty()) return "Please choose a reminder between 1 and 1000 minutes or hours, with a task.";
        long delay = amount * (unit.startsWith("h") ? 60L : 1L) * 60L * 1000L;
        int id = (int) (System.currentTimeMillis() & 0x7fffffff);
        Intent alert = new Intent(this, JarvisNotificationReceiver.class).setAction(JarvisNotificationReceiver.ACTION_REMINDER);
        alert.putExtra(JarvisNotificationReceiver.EXTRA_TITLE, "JARVIS reminder");
        alert.putExtra(JarvisNotificationReceiver.EXTRA_TEXT, task);
        PendingIntent pending = PendingIntent.getBroadcast(this, id, alert, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        AlarmManager alarms = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (alarms == null) return "Android's reminder service is unavailable.";
        alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + delay, pending);
        return "Reminder set for " + amount + " " + unit + ": " + task + ". Android will notify you when it is due.";
    }

    private String handleMessageCommand(String input) {
        String raw = input == null ? "" : input.trim();
        Matcher sms = Pattern.compile("(?i)^(?:sms|text|message)\\s+(?:to\\s+)?(.+?)\\s*:\\s*(.+)$").matcher(raw);
        if (!sms.matches()) {
            sms = Pattern.compile("(?i)^(?:sms|text|message)\\s+(?:to\\s+)?(.+?)\\s+(?:saying|that)\\s+(.+)$").matcher(raw);
        }
        if (sms.matches()) {
            String target = sms.group(1).trim();
            String body = sms.group(2).trim();
            if (target.equalsIgnoreCase("whatsapp") || target.equalsIgnoreCase("wa")) return openWhatsAppShare(body);
            if (target.replaceAll("[^0-9]", "").length() >= 5) return openSmsComposer(target, body);
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
                pendingMessageContact = target;
                pendingMessageBody = body;
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, PERMISSION_CONTACTS);
                return "I need Contacts permission to find that recipient. Allow it and I will open the SMS draft for review.";
            }
            return openContactMessageComposer(target, body);
        }
        Matcher whatsapp = Pattern.compile("(?i)^(?:whatsapp|send whatsapp)\\s*:\\s*(.+)$").matcher(raw);
        if (whatsapp.matches()) return openWhatsAppShare(whatsapp.group(1).trim());
        Matcher whatsappVoice = Pattern.compile("(?i)^(?:whatsapp|send whatsapp)(?: message)?\\s+to\\s+(.+?)\\s+(?:saying|message)\\s+(.+)$").matcher(raw);
        if (whatsappVoice.matches()) return openWhatsAppShare(whatsappVoice.group(2).trim());
        Matcher whatsappSimple = Pattern.compile("(?i)^(?:whatsapp|send whatsapp)\\s+(.+)$").matcher(raw);
        if (whatsappSimple.matches()) return openWhatsAppShare(whatsappSimple.group(1).trim());
        return null;
    }

    private String openContactMessageComposer(String query, String body) {
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> numbers = new ArrayList<>();
        Cursor cursor = null;
        try {
            Uri lookup = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(query));
            cursor = getContentResolver().query(lookup,
                    new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (cursor != null) while (cursor.moveToNext() && names.size() < 12) {
                String name = cursor.getString(0), number = cursor.getString(1);
                if (name != null && number != null && !number.trim().isEmpty()) { names.add(name); numbers.add(number); }
            }
        } catch (Exception error) { return "I could not read that contact. Check Contacts permission and try again."; }
        finally { if (cursor != null) cursor.close(); }
        if (numbers.isEmpty()) return "I could not find a phone number for " + query + ".";
        if (numbers.size() == 1) return openSmsComposer(numbers.get(0), body);
        String[] labels = new String[numbers.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = names.get(i) + "  •  " + numbers.get(i);
        new AlertDialog.Builder(this).setTitle("CHOOSE MESSAGE RECIPIENT").setItems(labels,
                (dialog, which) -> reply(openSmsComposer(numbers.get(which), body)))
                .setNegativeButton("CANCEL", null).show();
        return "I found multiple matching numbers. Choose the correct recipient.";
    }

    private String openSmsComposer(String phone, String body) {
        try {
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(phone)));
            intent.putExtra("sms_body", body);
            startActivity(intent);
            return "Opening your SMS app with the message filled in. Review it and tap Send when ready.";
        } catch (Exception error) { return "No SMS app could handle that message on this device."; }
    }

    private String openWhatsAppShare(String body) {
        try {
            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("text/plain");
            intent.putExtra(Intent.EXTRA_TEXT, body);
            intent.setPackage("com.whatsapp");
            startActivity(intent);
            return "Opening WhatsApp's share screen. Choose the recipient and send the message yourself.";
        } catch (Exception error) {
            try {
                Intent intent = new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, body);
                startActivity(Intent.createChooser(intent, "Share message"));
                return "WhatsApp is unavailable, so Android opened the share sheet instead.";
            } catch (Exception ignored) { return "No compatible app could share that message."; }
        }
    }

    private void reply(String message) {
        addMessage("JARVIS", message, false);
        setStatus("RESPONSE READY");
        speak(message);
    }

    private void speak(String message) {
        if (liveModeEnabled && !liveModePaused) {
            try { startService(new Intent(this, JarvisLiveService.class).setAction(JarvisLiveService.ACTION_HOLD)); }
            catch (Exception ignored) { }
        }
        if (!spokenReplies || tts == null || message == null || message.trim().isEmpty()) {
            scheduleListeningRestart(500);
            resumeLiveModeAfterSpeech();
            return;
        }
        String speechText = cleanSpeechText(message);
        if (speechText.isEmpty()) { scheduleListeningRestart(500); resumeClapWake(); resumeLiveModeAfterSpeech(); return; }
        try {
            // Use the Android system's current default TTS voice/engine without overriding it.
            int result = tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null,
                    "jarvis-" + System.currentTimeMillis());
            if (result == TextToSpeech.ERROR) {
                setStatus("SYSTEM VOICE OUTPUT ERROR");
                scheduleListeningRestart(700);
                resumeLiveModeAfterSpeech();
            } else setStatus("SPEAKING");
        } catch (Exception error) {
            setStatus("SYSTEM VOICE OUTPUT UNAVAILABLE");
            scheduleListeningRestart(700);
            resumeLiveModeAfterSpeech();
        }
    }

    private boolean containsDevanagari(String value) {
        if (value == null) return false;
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            if (cp >= 0x0900 && cp <= 0x097F) return true;
            offset += Character.charCount(cp);
        }
        return false;
    }

    private void stopSpeech() {
        if (tts != null) tts.stop();
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            listening = false;
        }
        setStatus("SPEECH INTERRUPTED");
        resumeClapWake();
    }


    private int voicePriority(Voice voice, String requestedLanguage) {
        if (voice == null || voice.getLocale() == null) return -1;
        Locale locale = voice.getLocale();
        if (!requestedLanguage.equalsIgnoreCase(locale.getLanguage())) return -1;
        int score = 10;
        if ("IN".equalsIgnoreCase(locale.getCountry())) score += 20;
        if (!voice.isNetworkConnectionRequired()) score += 5;
        if (voice.getName().equals(selectedVoiceName)) score += 3;
        return score;
    }

    private Voice findInstalledVoiceForLanguage(String requestedLocale) {
        if (requestedLocale == null || availableVoices.isEmpty()) return null;
        String language = requestedLocale.toLowerCase(Locale.ROOT).startsWith("hi") ? "hi" : "en";
        Voice best = null;
        int bestScore = -1;
        for (Voice voice : availableVoices) {
            int score = voicePriority(voice, language);
            if (score > bestScore) { best = voice; bestScore = score; }
        }
        return best;
    }

    private void initializeVoiceSelection() {
        // Respect the voice and engine configured in Android's system Text-to-Speech settings.
        // JARVIS deliberately does not force a gender, language profile or vendor-specific voice.
        selectedVoiceName = "ANDROID SYSTEM DEFAULT";
        spokenReplies = true;
        updateVoiceSelectButton();
        if (voiceButton != null) { voiceButton.setText("◖"); voiceButton.setTextColor(CYAN); }
    }

    private String voiceDisplayName() {
        return "ANDROID SYSTEM DEFAULT";
    }

    private void updateVoiceSelectButton() {
        if (voiceSelectButton != null) voiceSelectButton.setText("SYSTEM DEFAULT VOICE  ⚙");
    }

    private void showVoicePicker() {
        openSystemTtsSettings();
    }

    private String cleanSpeechText(String source) {
        String value = source == null ? "" : source;
        value = value.replaceAll("\\x60\\x60\\x60[\\s\\S]*?\\x60\\x60\\x60", " ");
        value = value.replaceAll("\\x60([^\\x60]+)\\x60", "$1");
        value = value.replaceAll("\\[([^\\]]+)\\]\\([^)]+\\)", "$1");
        value = value.replaceAll("https?://\\S+", " ");
        value = value.replaceAll("(?m)^\\s{0,3}#{1,6}\\s*", "");
        value = value.replaceAll("(?m)^\\s*(?:[-*•▪◦]|\\d+[.)])\\s+", "");
        value = value.replaceAll("\\*\\*(.*?)\\*\\*", "$1");
        value = value.replaceAll("__(.*?)__", "$1");
        value = value.replaceAll("~~(.*?)~~", "$1");
        value = value.replaceAll("(?s)<[^>]*>", " ");

        StringBuilder spoken = new StringBuilder(value.length());
        for (int offset = 0; offset < value.length();) {
            int cp = value.codePointAt(offset);
            offset += Character.charCount(cp);
            if (isEmojiOrDecorativeSymbol(cp)) spoken.append(' ');
            else spoken.appendCodePoint(cp);
        }
        return spoken.toString()
                .replaceAll("[ \\t]{2,}", " ")
                .replaceAll("\\s+([,.;:!?।])", "$1")
                .replaceAll("([,;:])(?=\\S)", "$1 ")
                .replaceAll("\\n+", " ")
                .trim();
    }

    private boolean isEmojiOrDecorativeSymbol(int cp) {
        return (cp >= 0x1F000 && cp <= 0x1FAFF)
                || (cp >= 0x2600 && cp <= 0x27BF)
                || (cp >= 0x1F1E6 && cp <= 0x1F1FF)
                || cp == 0x00A9 || cp == 0x00AE || cp == 0x200D
                || cp == 0xFE0E || cp == 0xFE0F || cp == 0x20E3
                || cp == 0x2022 || cp == 0x25AA || cp == 0x25CF
                || cp == 0x00B7 || cp == 0x2192 || cp == 0x21D2
                || cp == 0x27A4 || cp == 0x200B;
    }

    private void toggleClapWake() {
        if (liveModeEnabled) {
            setStatus("STOP LIVE MODE BEFORE USING CLAP WAKE");
            return;
        }
        if (clapWakeEnabled) {
            clapWakeEnabled = false;
            stopClapRecorder();
            setStatus("CLAP WAKE OFF");
            return;
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_CLAP);
            return;
        }
        clapWakeEnabled = true;
        startClapMonitor();
    }

    private void startClapMonitor() {
        if (!clapWakeEnabled || clapRecorder != null) return;
        final int sampleRate = 16000;
        int minimum;
        try {
            minimum = AudioRecord.getMinBufferSize(sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        } catch (Exception error) {
            clapWakeEnabled = false;
            setStatus("CLAP WAKE UNAVAILABLE");
            return;
        }
        if (minimum <= 0) {
            clapWakeEnabled = false;
            setStatus("CLAP WAKE UNAVAILABLE");
            return;
        }
        final AudioRecord recorder;
        try {
            recorder = new AudioRecord(
                    MediaRecorder.AudioSource.VOICE_RECOGNITION, sampleRate,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    Math.max(minimum, 8192));
        } catch (Exception error) {
            clapWakeEnabled = false;
            setStatus("CLAP WAKE COULD NOT START");
            return;
        }
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            recorder.release();
            clapWakeEnabled = false;
            setStatus("CLAP WAKE COULD NOT START");
            return;
        }
        clapRecorder = recorder;
        setStatus("CLAP WAKE ACTIVE  •  CLAP ONCE TO SPEAK");

        clapThread = new Thread(() -> {
            boolean triggered = false;
            try {
                recorder.startRecording();
                short[] samples = new short[1024];
                while (clapWakeEnabled && !Thread.currentThread().isInterrupted()) {
                    int count = recorder.read(samples, 0, samples.length);
                    if (count <= 0) continue;
                    int peak = 0;
                    long sumSquares = 0;
                    for (int i = 0; i < count; i++) {
                        int amplitude = Math.abs((int) samples[i]);
                        if (amplitude > peak) peak = amplitude;
                        sumSquares += (long) amplitude * amplitude;
                    }
                    double rms = Math.sqrt(sumSquares / (double) count);
                    if (peak > 23000 && rms > 5200) {
                        triggered = true;
                        releaseClapRecorder(recorder);
                        clapThread = null;
                        runOnUiThread(() -> {
                            if (clapWakeEnabled) {
                                setStatus("CLAP DETECTED  •  SPEAK NOW");
                                startListening();
                            }
                        });
                        return;
                    }
                }
            } catch (Exception error) {
                if (clapWakeEnabled) {
                    clapWakeEnabled = false;
                    runOnUiThread(() -> setStatus("CLAP WAKE ERROR"));
                }
            } finally {
                if (!triggered && clapRecorder == recorder) releaseClapRecorder(recorder);
                if (clapThread == Thread.currentThread()) clapThread = null;
            }
        }, "JARVIS-Clap-Wake");
        clapThread.start();
    }

    private void releaseClapRecorder(AudioRecord recorder) {
        try {
            if (recorder.getRecordingState() == AudioRecord.RECORDSTATE_RECORDING) recorder.stop();
        } catch (Exception ignored) { }
        try { recorder.release(); } catch (Exception ignored) { }
        if (clapRecorder == recorder) clapRecorder = null;
    }

    private void stopClapRecorder() {
        Thread worker = clapThread;
        if (worker != null) worker.interrupt();
        AudioRecord recorder = clapRecorder;
        if (recorder != null) releaseClapRecorder(recorder);
        clapThread = null;
    }

    private void resumeClapWake() {
        if (!clapWakeEnabled || handlerClock.handler == null) return;
        handlerClock.handler.postDelayed(() -> {
            if (clapWakeEnabled && !busy && !listening && !isFinishing()) startClapMonitor();
        }, 850);
    }

    private void pauseClapWakeForManualMic() {
        if (clapWakeEnabled && clapRecorder != null) {
            clapWakeEnabled = false;
            stopClapRecorder();
        }
    }

    private void toggleVoice() {
        spokenReplies = !spokenReplies;
        if (!spokenReplies && tts != null) tts.stop();
        if (voiceButton != null) { voiceButton.setText(spokenReplies ? "◖" : "◗"); voiceButton.setTextColor(spokenReplies ? CYAN : MUTED); }
        if (spokenReplies) reply("System voice replies enabled.");
        else { setStatus("SPOKEN REPLIES OFF"); resumeLiveModeAfterSpeech(); }
    }

    private void toggleLanguage() {
        languageMode = (languageMode + 1) % 3;
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                .putInt("speech_language_mode", languageMode).apply();
        if (languageButton != null) languageButton.setText(languageLabel());
        String label = languageMode == 0 ? "Automatic language recognition selected. Mixed Hindi and English recognition depends on your installed speech service."
                : languageMode == 1 ? "English India recognition selected."
                : "Hindi India recognition selected.";
        reply(label);
    }

    private RecognitionListener createRecognitionListener() {
        return new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                listening = true;
                // Voice input stays in the chat; the Orb is opened only by tapping the Reactor.
                setStatus("LISTENING");
                if (micButton != null) { micButton.setText("●"); micButton.setTextColor(GOLD); }
            }
            @Override public void onBeginningOfSpeech() { setStatus("LISTENING"); }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { setStatus("PROCESSING VOICE"); }
            @Override public void onError(int error) {
                listening = false;
                if (micButton != null) { micButton.setText("●"); micButton.setTextColor(continuousListeningEnabled ? GOLD : MUTED); }
                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    continuousListeningEnabled = false;
                    setStatus("MICROPHONE PERMISSION REQUIRED");
                } else {
                    setStatus(continuousListeningEnabled ? "RECONNECTING VOICE" : "MICROPHONE PAUSED");
                    scheduleListeningRestart(error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ? 1400 : 1000);
                }
                resumeClapWake();
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) sendMessage(matches.get(0));
                else {
                    setStatus("WAITING FOR COMMAND");
                    scheduleListeningRestart(550);
                    resumeClapWake();
                }
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        };
    }

    private void toggleContinuousListening() {
        if (liveModeEnabled && liveModePaused) {
            liveModePaused = false;
            getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit().putBoolean("live_mode_paused", false).apply();
            try { startService(new Intent(this, JarvisLiveService.class).setAction(JarvisLiveService.ACTION_RESUME)); }
            catch (Exception error) { setStatus("LIVE MODE RESUME FAILED"); }
        } else if (liveModeEnabled) {
            stopLiveMode();
        } else {
            startLiveMode();
        }
    }

    private void startLiveMode() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO);
            return;
        }
        if (clapWakeEnabled) { clapWakeEnabled = false; stopClapRecorder(); }
        liveModeEnabled = true;
        liveModePaused = false;
        continuousListeningEnabled = false;
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                .putBoolean("live_mode_enabled", true).putBoolean("live_mode_paused", false).apply();
        if (voiceRestartRunnable != null) voiceHandler.removeCallbacks(voiceRestartRunnable);
        if (recognizer != null) { try { recognizer.cancel(); } catch (Exception ignored) { } }
        listening = false;
        Intent service = new Intent(this, JarvisLiveService.class).setAction(JarvisLiveService.ACTION_START);
        try {
            if (Build.VERSION.SDK_INT >= 26) startForegroundService(service); else startService(service);
            if (micButton != null) { micButton.setText("●"); micButton.setTextColor(GOLD); }
            setStatus("JARVIS LIVE  •  SAY HEY JARVIS");
        } catch (Exception error) {
            liveModeEnabled = false;
            getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit().putBoolean("live_mode_enabled", false).apply();
            setStatus("LIVE MODE COULD NOT START");
            reply("Android could not start the microphone service. Open JARVIS while it is visible and check microphone permission.");
        }
    }

    private void stopLiveMode() {
        liveModeEnabled = false;
        liveModePaused = false;
        continuousListeningEnabled = false;
        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                .putBoolean("live_mode_enabled", false).putBoolean("live_mode_paused", false).remove("pending_live_command").apply();
        try { startService(new Intent(this, JarvisLiveService.class).setAction(JarvisLiveService.ACTION_STOP)); }
        catch (Exception ignored) { stopService(new Intent(this, JarvisLiveService.class)); }
        if (micButton != null) { micButton.setText("○"); micButton.setTextColor(MUTED); }
        setStatus("LIVE MODE OFF");
    }

    private void resumeLiveModeAfterSpeech() {
        if (!liveModeEnabled || liveModePaused) return;
        voiceHandler.postDelayed(() -> {
            if (!liveModeEnabled || liveModePaused || isFinishing() || isDestroyed()) return;
            try { startService(new Intent(this, JarvisLiveService.class).setAction(JarvisLiveService.ACTION_RESUME)); }
            catch (Exception ignored) { }
        }, 500);
    }

    private void registerLiveReceiver() {
        if (liveReceiverRegistered) return;
        IntentFilter filter = new IntentFilter(JarvisLiveService.ACTION_TRANSCRIPT);
        filter.addAction(JarvisLiveService.ACTION_STATE);
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(liveReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(liveReceiver, filter);
        liveReceiverRegistered = true;
    }

    private void consumePendingLiveCommand() {
        if (!ownerAuthenticated) return;
        SharedPreferences prefs = getSharedPreferences("jarvis_settings", MODE_PRIVATE);
        String command = prefs.getString("pending_live_command", "");
        if (command == null || command.isEmpty()) return;
        prefs.edit().remove("pending_live_command").apply();
        if ("__JARVIS_WAKE__".equals(command)) reply("Yes, Sir?");
        else sendMessage(command);
    }

    private void openSystemTtsSettings() {
        try { startActivity(new Intent("com.android.settings.TTS_SETTINGS")); }
        catch (Exception error) {
            try { startActivity(new Intent(android.provider.Settings.ACTION_SETTINGS)); }
            catch (Exception ignored) { reply("Android could not open the system speech settings."); }
        }
    }

    private void requestAssistantRole() {
        if (Build.VERSION.SDK_INT < 29) {
            reply("Android's default assistant role requires Android 10 or newer.");
            return;
        }
        RoleManager roles = getSystemService(RoleManager.class);
        if (roles == null || !roles.isRoleAvailable(RoleManager.ROLE_ASSISTANT)) {
            reply("This Android version does not expose the default assistant role to JARVIS.");
            return;
        }
        if (roles.isRoleHeld(RoleManager.ROLE_ASSISTANT)) {
            reply("JARVIS is already your default assistant, Sir.");
            return;
        }
        try { startActivityForResult(roles.createRequestRoleIntent(RoleManager.ROLE_ASSISTANT), REQUEST_ASSISTANT_ROLE); }
        catch (Exception error) { reply("Android could not open the default assistant selection screen."); }
    }

    private void scheduleListeningRestart(long delayMs) {
        if (liveModeEnabled || !continuousListeningEnabled || !activityVisible || isFinishing() || isDestroyed()) return;
        if (voiceRestartRunnable != null) voiceHandler.removeCallbacks(voiceRestartRunnable);
        voiceRestartRunnable = () -> {
            voiceRestartRunnable = null;
            startListening();
        };
        voiceHandler.postDelayed(voiceRestartRunnable, Math.max(250, delayMs));
    }

    private void startListening() {
        if (liveModeEnabled || !ownerAuthenticated || !continuousListeningEnabled || !activityVisible || isFinishing() || isDestroyed()) return;
        if (busy || (tts != null && tts.isSpeaking())) return;
        if (listening) return;
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            if (!micPermissionRequested) {
                micPermissionRequested = true;
                requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO);
            } else {
                setStatus("MICROPHONE PERMISSION REQUIRED");
            }
            return;
        }
        if (recognizer == null) {
            setStatus("VOICE INPUT UNAVAILABLE");
            scheduleListeningRestart(3000);
            return;
        }
        try {
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            if (languageMode == 1) {
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-IN");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-IN");
            } else if (languageMode == 2) {
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "hi-IN");
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "hi-IN");
            }
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800);
            intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1000);
            recognizer.startListening(intent);
            setStatus("LISTENING");
        } catch (Exception error) {
            listening = false;
            setStatus("VOICE INPUT RECONNECTING");
            scheduleListeningRestart(1200);
        }
    }

    private void findFlashCamera() {
        if (camera == null) return;
        try {
            String fallback = null;
            for (String id : camera.getCameraIdList()) {
                CameraCharacteristics characteristics = camera.getCameraCharacteristics(id);
                Boolean hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                Integer facing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (Boolean.TRUE.equals(hasFlash)) {
                    if (fallback == null) fallback = id;
                    if (facing != null && facing == CameraCharacteristics.LENS_FACING_BACK) {
                        cameraId = id;
                        return;
                    }
                }
            }
            cameraId = fallback;
        } catch (Exception ignored) { cameraId = null; }
    }

    private void toggleTorch() {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.CAMERA}, PERMISSION_CAMERA);
            reply("Camera permission is required for the flashlight. Allow it, then tap the flashlight control again.");
            return;
        }
        if (cameraId == null) {
            findFlashCamera();
        }
        if (cameraId == null || camera == null) {
            reply("This device does not expose a compatible flashlight.");
            return;
        }
        try {
            torchOn = !torchOn;
            camera.setTorchMode(cameraId, torchOn);
            reply(torchOn ? "Flashlight on." : "Flashlight off.");
        } catch (Exception error) {
            torchOn = false;
            reply("I could not change the flashlight state. Check camera permission and device support.");
        }
    }

    private int getBatteryPct() {
        Intent battery = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (battery == null) return -1;
        int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        return level >= 0 && scale > 0 ? Math.max(0, Math.min(100, Math.round(level * 100f / scale))) : -1;
    }

    private void openSettings(String action, String label) {
        try {
            startActivity(new Intent(action));
        } catch (Exception error) {
            reply("I couldn't open " + label + " settings on this device.");
        }
    }


    private void openPhoneDialer() {
        try { startActivity(new Intent(Intent.ACTION_DIAL)); setStatus("PHONE DIALER READY"); }
        catch (Exception error) { reply("I could not open the phone dialer on this device."); }
    }

    private void requestCorePermissions() {
        ArrayList<String> missing = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.RECORD_AUDIO);
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) missing.add(Manifest.permission.POST_NOTIFICATIONS);
        if (missing.isEmpty()) {
            reply("Microphone, camera, contacts and notification permissions are already granted. Documents and folders are accessed separately through Android's file picker.");
            return;
        }
        requestPermissions(missing.toArray(new String[0]), PERMISSION_CORE);
    }

    private void showPermissionCenter() {
        boolean microphone = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
        boolean cameraPermission = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean contacts = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
        String folder = getSharedPreferences("jarvis_settings", MODE_PRIVATE).getString("document_tree_uri", "");
        boolean notifications = Build.VERSION.SDK_INT < 33 || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED;
        String message = "MICROPHONE: " + (microphone ? "GRANTED" : "NOT GRANTED")
                + "\nCAMERA / FLASHLIGHT: " + (cameraPermission ? "GRANTED" : "NOT GRANTED")
                + "\nCONTACTS: " + (contacts ? "GRANTED" : "NOT GRANTED")
                + "\nNOTIFICATIONS: " + (notifications ? "GRANTED" : "NOT GRANTED")
                + "\nDOCUMENTS: selected files only"
                + "\nFOLDER ACCESS: " + (folder.isEmpty() ? "NOT SELECTED" : "USER-SELECTED FOLDER")
                + "\n\nCalls open the dialer for your confirmation. Android does not allow JARVIS to silently grant protected permissions or read another app's private files.";
        new AlertDialog.Builder(this).setTitle("JARVIS PERMISSION CENTER").setMessage(message)
                .setPositiveButton("GRANT CORE", (dialog, which) -> requestCorePermissions())
                .setNeutralButton("ANDROID APP SETTINGS", (dialog, which) -> {
                    try { startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", getPackageName(), null))); }
                    catch (Exception error) { reply("I could not open Android app settings."); }
                }).setNegativeButton("CLOSE", null).show();
    }

    private void openDocumentPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("*/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_OPEN_DOCUMENTS);
        } catch (Exception error) { reply("The Android document picker is unavailable on this device."); }
    }

    private void openFolderPicker() {
        try {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            startActivityForResult(intent, REQUEST_OPEN_FOLDER);
        } catch (Exception error) { reply("The Android folder picker is unavailable on this device."); }
    }

    private void persistReadPermission(Uri uri, int flags) {
        if (uri == null) return;
        int granted = flags & (Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
        if ((granted & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0) granted |= Intent.FLAG_GRANT_READ_URI_PERMISSION;
        try { getContentResolver().takePersistableUriPermission(uri, granted); }
        catch (SecurityException | IllegalArgumentException ignored) { }
    }

    private String getDocumentName(Uri uri) {
        if (uri == null) return "Unknown document";
        Cursor cursor = null;
        try {
            cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                String name = cursor.getString(0);
                if (name != null && !name.trim().isEmpty()) return name;
            }
        } catch (Exception ignored) {
        } finally { if (cursor != null) cursor.close(); }
        String last = uri.getLastPathSegment();
        return last == null ? "Selected document" : last;
    }

    private void showSelectedDocuments() {
        if (selectedDocumentUris.isEmpty()) {
            reply("No documents have been selected in this session. Use Open Files to choose documents from your phone or a connected provider.");
            return;
        }
        String[] labels = new String[selectedDocumentUris.size()];
        for (int i = 0; i < labels.length; i++) labels[i] = getDocumentName(selectedDocumentUris.get(i));
        new AlertDialog.Builder(this).setTitle("SELECTED DOCUMENTS")
                .setItems(labels, (dialog, which) -> openDocumentWithWps(selectedDocumentUris.get(which)))
                .setNegativeButton("CLOSE", null).show();
    }

    private void openDocumentWithWps(Uri uri) {
        if (uri == null) return;
        try {
            String mime = getContentResolver().getType(uri);
            if (mime == null || mime.trim().isEmpty()) mime = "*/*";
            Intent view = new Intent(Intent.ACTION_VIEW);
            view.setDataAndType(uri, mime);
            view.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(view, "Open document with"));
        } catch (Exception error) {
            reply("No compatible app could open " + getDocumentName(uri) + ". Check that WPS Office or another document viewer is installed.");
        }
    }

    private void browseSavedFolder() {
        String saved = getSharedPreferences("jarvis_settings", MODE_PRIVATE).getString("document_tree_uri", "");
        if (saved.isEmpty()) {
            reply("No folder has been selected yet. Choose Folder first, then I can browse the files Android shares with JARVIS.");
            return;
        }
        try {
            Uri treeUri = Uri.parse(saved);
            showFolderContents(treeUri, DocumentsContract.getTreeDocumentId(treeUri), "Selected folder");
        } catch (Exception error) { reply("I could not reopen that folder. Choose it again in the Android folder picker."); }
    }

    private void showFolderContents(Uri treeUri, String documentId, String title) {
        Cursor cursor = null;
        ArrayList<String> names = new ArrayList<>();
        ArrayList<String> ids = new ArrayList<>();
        ArrayList<Boolean> directories = new ArrayList<>();
        try {
            Uri children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, documentId);
            cursor = getContentResolver().query(children, new String[]{
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE
            }, null, null, DocumentsContract.Document.COLUMN_DISPLAY_NAME + " ASC");
            if (cursor != null) {
                while (cursor.moveToNext() && names.size() < 100) {
                    String id = cursor.getString(0);
                    String name = cursor.getString(1);
                    boolean directory = DocumentsContract.Document.MIME_TYPE_DIR.equals(cursor.getString(2));
                    ids.add(id);
                    names.add((directory ? "▸  " : "   ") + (name == null ? "Unnamed" : name));
                    directories.add(directory);
                }
            }
        } catch (SecurityException error) {
            reply("Android no longer grants access to that folder. Choose it again.");
            return;
        } catch (Exception error) {
            reply("This folder provider does not support browsing.");
            return;
        } finally { if (cursor != null) cursor.close(); }

        if (names.isEmpty()) { reply("That folder is empty, or its provider does not expose a file list."); return; }
        String[] labels = names.toArray(new String[0]);
        new AlertDialog.Builder(this).setTitle(title.length() > 42 ? title.substring(0, 42) : title)
                .setItems(labels, (dialog, which) -> {
                    String childId = ids.get(which);
                    if (directories.get(which)) showFolderContents(treeUri, childId, names.get(which).replace("▸  ", ""));
                    else openDocumentWithWps(DocumentsContract.buildDocumentUriUsingTree(treeUri, childId));
                }).setNegativeButton("CLOSE", null).show();
    }

    private String launchWpsOffice() {
        String[] packages = new String[]{"cn.wps.moffice_eng", "cn.wps.moffice", "cn.wps.moffice_i18n"};
        for (String packageName : packages) {
            try {
                Intent launch = getPackageManager().getLaunchIntentForPackage(packageName);
                if (launch != null) { startActivity(launch); return "Opening WPS Office."; }
            } catch (Exception ignored) { }
        }
        return "WPS Office was not found. Install it, or choose a document and use Open with to select WPS if it is available.";
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_OVERLAY_PERMISSION) {
            if (Build.VERSION.SDK_INT >= 23 && android.provider.Settings.canDrawOverlays(this)) {
                setFloatingOrbEnabled(true);
            } else {
                reply("Overlay permission was not granted. The floating Orb remains off.");
            }
            return;
        }
        if (requestCode == REQUEST_DEVICE_CREDENTIAL) {
            if (resultCode == RESULT_OK) unlockOwner();
            else finish();
            return;
        }
        if (requestCode == REQUEST_ASSISTANT_ROLE) {
            RoleManager roles = Build.VERSION.SDK_INT >= 29 ? getSystemService(RoleManager.class) : null;
            boolean held = roles != null && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT);
            reply(held ? "JARVIS is now your Android default assistant, Sir." : "Android did not assign the default assistant role. You can try again from Controls.");
            return;
        }
        if (resultCode != RESULT_OK || data == null) return;
        if (requestCode == REQUEST_OPEN_DOCUMENTS) {
            selectedDocumentUris.clear();
            ClipData clip = data.getClipData();
            if (clip != null) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri uri = clip.getItemAt(i).getUri();
                    if (uri != null) { persistReadPermission(uri, data.getFlags()); selectedDocumentUris.add(uri); }
                }
            } else if (data.getData() != null) {
                persistReadPermission(data.getData(), data.getFlags());
                selectedDocumentUris.add(data.getData());
            }
            if (selectedDocumentUris.isEmpty()) reply("No document was selected.");
            else {
                reply("Selected " + selectedDocumentUris.size() + " document(s). I can hand them to WPS Office or another compatible viewer.");
                showSelectedDocuments();
            }
        } else if (requestCode == REQUEST_OPEN_FOLDER && data.getData() != null) {
            Uri treeUri = data.getData();
            persistReadPermission(treeUri, data.getFlags());
            getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                    .putString("document_tree_uri", treeUri.toString()).apply();
            reply("Folder access saved. JARVIS can browse the files and subfolders Android has shared from that location.");
            browseSavedFolder();
        }
    }

        @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startLiveMode();
            } else {
                continuousListeningEnabled = false;
                if (micButton != null) { micButton.setText("○"); micButton.setTextColor(MUTED); }
                reply("Microphone permission was not granted. Voice listening is paused.");
            }
        } else if (requestCode == PERMISSION_CAMERA) {
            if (grantResults.length == 0 || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                reply("Camera permission was not granted. Flashlight remains unavailable.");
            } else {
                reply("Camera permission granted. Tap the flashlight control again.");
            }
        } else if (requestCode == PERMISSION_CLAP) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                clapWakeEnabled = true;
                startClapMonitor();
            } else {
                clapWakeEnabled = false;
                setStatus("MICROPHONE PERMISSION REQUIRED FOR CLAP WAKE");
            }
        } else if (requestCode == PERMISSION_CORE) {
            boolean audio = checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
            boolean cameraGranted = checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
            boolean contactsGranted = checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED;
            reply("Permission status: microphone " + (audio ? "granted" : "not granted")
                    + ", camera " + (cameraGranted ? "granted" : "not granted")
                    + ", contacts " + (contactsGranted ? "granted" : "not granted")
                    + ". Document access is granted separately when you choose files or folders.");
        } else if (requestCode == PERMISSION_CONTACTS) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (pendingMessageContact != null) {
                String target = pendingMessageContact;
                String body = pendingMessageBody;
                pendingMessageContact = null;
                pendingMessageBody = null;
                if (granted && body != null) reply(openContactMessageComposer(target, body));
                else reply("Contacts permission was not granted. No message was opened.");
            } else {
                String requested = pendingCallContact;
                pendingCallContact = null;
                if (granted && requested != null) reply(openContactDialer(requested));
                else reply("Contacts permission was not granted. You can still say 'dial' followed by a phone number.");
            }
        }
    }

    private static class VoiceOrbView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long started = System.currentTimeMillis();
        private String state = "LISTENING";
        VoiceOrbView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setState(String value) { state = value == null ? "LISTENING" : value; invalidate(); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float base = Math.min(getWidth(), getHeight()) * .29f;
            float t = (System.currentTimeMillis() - started) / 1000f;
            boolean speaking = state.contains("SPEAK");
            boolean processing = state.contains("PROCESS") || state.contains("THINK");
            float pulse = 1f + (speaking ? .075f : processing ? .035f : .018f) * (float)Math.sin(t * (speaking ? 7f : 2.2f));
            for (int i = 4; i >= 1; i--) {
                float r = base * (1f + i * .23f) + (float)Math.sin(t * 1.4f + i) * 4f;
                p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f);
                p.setColor(Color.argb(Math.max(8, 34 - i * 5), 90, 197, 255));
                canvas.drawCircle(cx, cy, r, p);
            }
            p.setStyle(Paint.Style.FILL);
            p.setShader(new android.graphics.RadialGradient(cx, cy, base * 1.8f,
                    new int[]{Color.argb(95,91,207,255), Color.argb(30,32,139,218), Color.TRANSPARENT},
                    null, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, base * 1.8f, p); p.setShader(null);
            p.setShader(new android.graphics.RadialGradient(cx-base*.25f, cy-base*.32f, base*1.5f,
                    new int[]{Color.rgb(245,255,255), Color.rgb(123,220,255), Color.rgb(29,128,211), Color.rgb(5,31,69)},
                    new float[]{0f,.19f,.58f,1f}, android.graphics.Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, base * pulse, p); p.setShader(null);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2f);
            p.setColor(Color.argb(180,219,249,255)); canvas.drawCircle(cx, cy, base * pulse, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Color.argb(55,255,255,255));
            canvas.drawOval(new RectF(cx-base*.65f, cy-base*.78f, cx+base*.18f, cy-base*.25f), p);
            postInvalidateDelayed(32);
        }
    }

    private static class ReactorView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int mode = 0;
        private final long started = System.currentTimeMillis();

        ReactorView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }
        void setMode(int value) { mode = value; invalidate(); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f, cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * 0.39f;
            float pulse = (float)(1.0 + 0.018 * Math.sin((System.currentTimeMillis() - started) / 450.0));
            canvas.save();
            canvas.rotate((System.currentTimeMillis() - started) / 90f % 360f, cx, cy);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpStatic(getContext(), 1));
            paint.setColor(Color.argb(90, 112, 229, 255));
            canvas.drawCircle(cx, cy, radius * 1.24f, paint);

            paint.setColor(Color.argb(150, 112, 229, 255));
            paint.setStrokeWidth(dpStatic(getContext(), 2));
            canvas.drawArc(new RectF(cx-radius*1.15f, cy-radius*1.15f, cx+radius*1.15f, cy+radius*1.15f),
                    22, 112, false, paint);
            canvas.drawArc(new RectF(cx-radius*1.15f, cy-radius*1.15f, cx+radius*1.15f, cy+radius*1.15f),
                    205, 78, false, paint);

            paint.setStrokeWidth(dpStatic(getContext(), 1));
            paint.setColor(Color.argb(70, 112, 229, 255));
            canvas.drawCircle(cx, cy, radius * .94f, paint);
            canvas.drawCircle(cx, cy, radius * .72f, paint);
            paint.setPathEffect(new android.graphics.DashPathEffect(new float[]{5, 7}, 0));
            canvas.drawCircle(cx, cy, radius * 1.08f, paint);
            paint.setPathEffect(null);

            for (int i = 0; i < 48; i++) {
                float angle = (float)(Math.PI * 2 * i / 48.0);
                float inner = radius * (i % 4 == 0 ? 1.12f : 1.17f);
                float outer = radius * 1.24f;
                paint.setColor(i % 4 == 0 ? CYAN_STATIC : Color.argb(90, 112, 229, 255));
                paint.setStrokeWidth(i % 4 == 0 ? 2.2f : 1f);
                canvas.drawLine(cx + (float)Math.cos(angle)*inner, cy + (float)Math.sin(angle)*inner,
                        cx + (float)Math.cos(angle)*outer, cy + (float)Math.sin(angle)*outer, paint);
            }
            canvas.restore();

            paint.setStyle(Paint.Style.FILL);
            int glow = mode == 1 ? Color.rgb(255, 180, 125) : CYAN_STATIC;
            paint.setColor(Color.argb(35, Color.red(glow), Color.green(glow), Color.blue(glow)));
            canvas.drawCircle(cx, cy, radius * .72f * pulse, paint);
            paint.setColor(Color.rgb(7, 30, 46));
            canvas.drawCircle(cx, cy, radius * .61f, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpStatic(getContext(), 3));
            paint.setColor(Color.argb(190, 112, 229, 255));
            canvas.drawCircle(cx, cy, radius * .53f, paint);
            paint.setStrokeWidth(dpStatic(getContext(), 1));
            paint.setColor(Color.argb(110, 160, 240, 255));
            canvas.drawCircle(cx, cy, radius * .42f, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(24, 127, 165));
            canvas.drawCircle(cx, cy, radius * .32f, paint);
            paint.setColor(Color.rgb(205, 250, 255));
            canvas.drawCircle(cx, cy, radius * .12f, paint);
            paint.setTextAlign(Paint.Align.CENTER);
            paint.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
            paint.setTextSize(radius * .18f);
            paint.setColor(Color.WHITE);
            canvas.drawText("R", cx, cy + radius * .065f, paint);

            postInvalidateDelayed(40);
        }
    }

    private static final int CYAN_STATIC = Color.rgb(112, 229, 255);

    private static float dpStatic(Context context, float value) {
        return value * context.getResources().getDisplayMetrics().density;
    }


    private static class SignatureView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long started = System.currentTimeMillis();

        SignatureView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float scale = Math.min(w, h) / 220f;
            canvas.save();
            canvas.translate((w - 220 * scale) / 2f, (h - 220 * scale) / 2f);
            canvas.scale(scale, scale);

            float pulse = (float) (1.0 + 0.018 * Math.sin(
                    (System.currentTimeMillis() - started) / 430.0));
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(5, 16, 28));
            p.setShadowLayer(18, 0, 0, Color.argb(100, 44, 190, 236));
            canvas.drawCircle(110, 110, 101, p);
            p.clearShadowLayer();

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.5f);
            p.setColor(Color.argb(145, 112, 229, 255));
            canvas.drawCircle(110, 110, 101, p);
            p.setStrokeWidth(1.0f);
            p.setColor(Color.argb(135, Color.red(GOLD_STATIC), Color.green(GOLD_STATIC), Color.blue(GOLD_STATIC)));
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{3, 7}, 0));
            canvas.drawCircle(110, 110, 91, p);
            p.setPathEffect(null);

            canvas.save();
            canvas.rotate((System.currentTimeMillis() - started) / 90f % 360f, 110, 110);
            p.setStrokeWidth(2.0f);
            p.setColor(Color.argb(155, 112, 229, 255));
            canvas.drawArc(new RectF(16, 16, 204, 204), 18, 58, false, p);
            canvas.drawArc(new RectF(16, 16, 204, 204), 198, 58, false, p);
            canvas.restore();

            Path outer = new Path();
            outer.moveTo(110, 25); outer.lineTo(128, 54); outer.lineTo(160, 43);
            outer.lineTo(153, 76); outer.lineTo(185, 93); outer.lineTo(158, 111);
            outer.lineTo(172, 147); outer.lineTo(137, 145); outer.lineTo(110, 183);
            outer.lineTo(83, 145); outer.lineTo(48, 147); outer.lineTo(62, 111);
            outer.lineTo(35, 93); outer.lineTo(67, 76); outer.lineTo(60, 43);
            outer.lineTo(92, 54); outer.close();

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(94, 27, 37));
            p.setShadowLayer(11, 0, 0, Color.argb(105, 38, 178, 228));
            canvas.drawPath(outer, p);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(4);
            p.setColor(GOLD_STATIC);
            canvas.drawPath(outer, p);

            Path inner = new Path();
            inner.moveTo(110, 62); inner.lineTo(133, 86); inner.lineTo(153, 82);
            inner.lineTo(143, 106); inner.lineTo(151, 128); inner.lineTo(124, 126);
            inner.lineTo(110, 150); inner.lineTo(96, 126); inner.lineTo(69, 128);
            inner.lineTo(77, 106); inner.lineTo(67, 82); inner.lineTo(87, 86);
            inner.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(13, 29, 42));
            canvas.drawPath(inner, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2.5f);
            p.setColor(Color.rgb(232, 184, 104));
            canvas.drawPath(inner, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.argb(45, 112, 229, 255));
            canvas.drawCircle(110, 108, 42 * pulse, p);
            p.setColor(Color.rgb(5, 20, 32));
            canvas.drawCircle(110, 108, 29, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(Color.rgb(112, 229, 255));
            canvas.drawCircle(110, 108, 25, p);
            p.setStrokeWidth(1);
            p.setColor(Color.argb(185, 181, 245, 255));
            canvas.drawCircle(110, 108, 19, p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create("sans-serif-black", Typeface.BOLD));
            p.setTextSize(92);
            p.setStyle(Paint.Style.FILL);
            p.setColor(GOLD_STATIC);
            p.setShadowLayer(7, 0, 0, Color.argb(165, 30, 182, 236));
            canvas.drawText("R", 110, 142, p);
            p.clearShadowLayer();
            p.setTextSize(90);
            p.setColor(Color.rgb(255, 222, 155));
            canvas.drawText("R", 109, 141, p);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(Color.rgb(112, 229, 255));
            canvas.drawLine(110, 9, 110, 21, p);
            canvas.drawLine(110, 199, 110, 211, p);
            canvas.drawLine(9, 110, 21, 110, p);
            canvas.drawLine(199, 110, 211, 110, p);
            canvas.restore();
            postInvalidateDelayed(55);
        }
    }

    private static final int GOLD_STATIC = Color.rgb(216, 169, 77);

    @Override
    protected void onPause() {
        activityVisible = false;
        continuousListeningEnabled = false;
        if (voiceRestartRunnable != null) voiceHandler.removeCallbacks(voiceRestartRunnable);
        if (recognizer != null) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
        }
        listening = false;
        stopClapRecorder();
        if (floatingOrbEnabled && ownerAuthenticated) startOverlayService(JarvisOverlayService.ACTION_SHOW);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        activityVisible = true;
        SharedPreferences livePrefs = getSharedPreferences("jarvis_settings", MODE_PRIVATE);
        liveModeEnabled = livePrefs.getBoolean("live_mode_enabled", false);
        liveModePaused = livePrefs.getBoolean("live_mode_paused", false);
        applyImmersiveMode();
        if (floatingOrbEnabled) startOverlayService(JarvisOverlayService.ACTION_HIDE);
        if (securitySettingsOpened && !ownerAuthenticated) {
            securitySettingsOpened = false;
            lockPromptStarted = false;
            authenticateOwner();
        }
        if (liveModeEnabled && !liveModePaused && ownerAuthenticated) {
            if (tts == null || !tts.isSpeaking()) {
                try { startService(new Intent(this, JarvisLiveService.class).setAction(JarvisLiveService.ACTION_RESUME)); }
                catch (Exception ignored) { }
            }
        } else if (continuousListeningEnabled && ownerAuthenticated) scheduleListeningRestart(350);
        resumeClapWake();
        consumePendingLiveCommand();
    }

    @Override
    protected void onDestroy() {
        clapWakeEnabled = false;
        continuousListeningEnabled = false;
        activityVisible = false;
        if (voiceRestartRunnable != null) voiceHandler.removeCallbacks(voiceRestartRunnable);
        stopClapRecorder();
        if (handlerClock.runnable != null) handlerClock.handler.removeCallbacks(handlerClock.runnable);
        if (liveReceiverRegistered) { try { unregisterReceiver(liveReceiver); } catch (Exception ignored) { } liveReceiverRegistered = false; }
        if (recognizer != null) {
            try { recognizer.destroy(); } catch (Exception ignored) { }
            recognizer = null;
        }
        if (tts != null) {
            try { tts.stop(); tts.shutdown(); } catch (Exception ignored) { }
            tts = null;
        }
        try {
            if (torchOn && camera != null && cameraId != null) camera.setTorchMode(cameraId, false);
        } catch (Exception ignored) { }
        network.shutdownNow();
        super.onDestroy();
    }
}
