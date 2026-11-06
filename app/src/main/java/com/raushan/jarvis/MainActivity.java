package com.raushan.jarvis;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
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

public class MainActivity extends Activity {
    private static final String WORKER_URL = "https://quiet-scribe-ai.thequietscribe.workers.dev";
    private static final int PERMISSION_AUDIO = 10;
    private static final int PERMISSION_CAMERA = 11;
    private static final int PERMISSION_CLAP = 12;
    private static final int PERMISSION_CONTACTS = 13;
    private static final int MAX_HISTORY_MESSAGES = 20;
    private static final int MAX_SAVED_MEMORIES = 20;

    private static final int BG = Color.rgb(5, 11, 19);
    private static final int PANEL = Color.rgb(9, 22, 35);
    private static final int CYAN = Color.rgb(112, 229, 255);
    private static final int MUTED = Color.rgb(114, 147, 165);
    private static final int WHITE = Color.rgb(232, 247, 255);
    private static final int GOLD = Color.rgb(216, 169, 77);
    private static final String SYSTEM_PROMPT =
            "You are JARVIS, Raushan's personal AI assistant. Address him as Raushan, " +
            "or occasionally Raushi, naturally and not in every reply. Be intelligent, " +
            "calm, concise, warm, and lightly witty when appropriate. He is a student, " +
            "so explain difficult topics clearly without talking down to him. Never " +
            "claim you can control the Android device, access files, or perform actions " +
            "unless the connected tools actually support it. Reply in the language and " +
            "style the user uses, including natural Hinglish when appropriate.";

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
    private SignatureView signatureView;
    private TextView voiceSelectButton;
    private final ArrayList<Voice> availableVoices = new ArrayList<>();
    private String selectedVoiceName;
    private volatile boolean clapWakeEnabled = false;
    private AudioRecord clapRecorder;
    private Thread clapThread;

    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private CameraManager camera;
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

    private final ExecutorService network = Executors.newSingleThreadExecutor();
    private final HandlerClock handlerClock = new HandlerClock();
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

        tts = new TextToSpeech(this, result -> {
            if (tts != null && result == TextToSpeech.SUCCESS) {
                initializeVoiceSelection();
                tts.setSpeechRate(speechRate);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { runOnUiThread(() -> setStatus("SPEAKING")); }
                    @Override public void onDone(String id) { runOnUiThread(() -> { if (!busy) setStatus("SYSTEM STANDBY"); resumeClapWake(); }); }
                    @Override public void onError(String id) { runOnUiThread(() -> { if (!busy) setStatus("SYSTEM STANDBY"); resumeClapWake(); }); }
                    @Override public void onStop(String id, boolean interrupted) { runOnUiThread(() -> { if (!busy) setStatus("SYSTEM STANDBY"); resumeClapWake(); }); }
                });
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
    }

    private void loadUserSettings() {
        SharedPreferences prefs = getSharedPreferences("jarvis_settings", MODE_PRIVATE);
        languageMode = prefs.getInt("speech_language_mode", 0);
        if (languageMode < 0 || languageMode > 2) languageMode = 0;
        speechRate = prefs.getFloat("speech_rate", 1.0f);
        if (speechRate < 0.7f || speechRate > 1.6f) speechRate = 1.0f;
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
        setContentView(page);

        buildTopBar();
        addGap(root, 12);
        buildMainArea();
        addGap(root, 12);
        buildConversation();
        addGap(root, 9);
        buildComposer();
        addGap(root, 8);
        buildDeviceControls();

        TextView footer = text("J.A.R.V.I.S.  /  R-01  •  NATIVE CORE 1.5", 8, MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setLetterSpacing(0.12f);
        LinearLayout.LayoutParams footerLp = new LinearLayout.LayoutParams(-1, -2);
        footerLp.topMargin = dp(10);
        root.addView(footer, footerLp);
    }


    private void buildTopBar() {
        LinearLayout bar = row();
        LinearLayout identity = column();

        TextView brand = text("J.A.R.V.I.S.", 17, CYAN);
        brand.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        brand.setLetterSpacing(0.12f);
        identity.addView(brand);

        TextView sub = text("PERSONAL AI  /  NATIVE SYSTEM", 8, MUTED);
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
        TextView systemButton = smallControl("⚙", () -> toggleSystemPanel());
        LinearLayout.LayoutParams gearLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        gearLp.leftMargin = dp(10);
        systemButton.setLayoutParams(gearLp);
        bar.addView(systemButton);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(46)));
    }


    private void buildMainArea() {
        LinearLayout hero = column();
        hero.setPadding(dp(14), dp(13), dp(14), dp(13));
        GradientDrawable heroBackground = new GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                new int[]{Color.rgb(10, 29, 45), Color.rgb(5, 13, 23)});
        heroBackground.setCornerRadius(dp(7));
        heroBackground.setStroke(dp(1), Color.rgb(37, 91, 113));
        hero.setBackground(heroBackground);

        LinearLayout header = row();
        TextView eyebrow = text("NEURAL INTERFACE  //  CORE 01", 8, MUTED);
        eyebrow.setLetterSpacing(0.16f);
        header.addView(eyebrow, new LinearLayout.LayoutParams(0, -2, 1));

        TextView live = text("● ONLINE", 8, CYAN);
        live.setLetterSpacing(0.12f);
        header.addView(live);
        hero.addView(header);

        LinearLayout body = row();
        body.setGravity(Gravity.CENTER_VERTICAL);

        int screenWidthDp = (int) (getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        int reactorSize = screenWidthDp >= 600 ? 190 : 142;

        reactorView = new ReactorView(this);
        LinearLayout.LayoutParams reactorLp = new LinearLayout.LayoutParams(dp(reactorSize), dp(reactorSize));
        reactorLp.rightMargin = dp(9);
        body.addView(reactorView, reactorLp);

        LinearLayout detail = column();
        detail.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text("At your service.", screenWidthDp >= 600 ? 25 : 21, WHITE);
        heading.setTypeface(sansSerif(), Typeface.NORMAL);
        heading.setLetterSpacing(0.01f);
        detail.addView(heading);

        TextView subtitle = text("Your personal system.", 9, MUTED);
        subtitle.setPadding(0, dp(3), 0, dp(8));
        detail.addView(subtitle);

        LinearLayout signatureRow = row();
        signatureView = new SignatureView(this);
        LinearLayout.LayoutParams signatureLp = new LinearLayout.LayoutParams(dp(58), dp(58));
        signatureLp.rightMargin = dp(8);
        signatureRow.addView(signatureView, signatureLp);

        LinearLayout signatureText = column();
        TextView signatureName = text("R-01", 10, GOLD);
        signatureName.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        signatureText.addView(signatureName);
        TextView signatureCaption = text("CREATOR'S MARK", 7, MUTED);
        signatureCaption.setLetterSpacing(0.08f);
        signatureText.addView(signatureCaption);
        signatureRow.addView(signatureText);
        detail.addView(signatureRow);

        body.addView(detail, new LinearLayout.LayoutParams(0, -2, 1));
        hero.addView(body, new LinearLayout.LayoutParams(-1, -2));

        View divider = new View(this);
        divider.setBackgroundColor(Color.rgb(31, 70, 88));
        LinearLayout.LayoutParams dividerLp = new LinearLayout.LayoutParams(-1, dp(1));
        dividerLp.topMargin = dp(8);
        dividerLp.bottomMargin = dp(8);
        hero.addView(divider, dividerLp);

        LinearLayout readouts = row();
        statusView = text("SYSTEM STANDBY", 8, CYAN);
        statusView.setLetterSpacing(0.08f);
        readouts.addView(statusView, new LinearLayout.LayoutParams(0, -2, 1.1f));

        clockView = text("--:--", 8, WHITE);
        clockView.setGravity(Gravity.CENTER);
        readouts.addView(clockView, new LinearLayout.LayoutParams(0, -2, 0.8f));

        connectionView = text("AI LINK READY", 7, MUTED);
        connectionView.setGravity(Gravity.RIGHT);
        connectionView.setLetterSpacing(0.04f);
        readouts.addView(connectionView, new LinearLayout.LayoutParams(0, -2, 1.1f));
        hero.addView(readouts);

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

        micButton = smallControl("⌁", () -> startListening());
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
        voiceSelectButton.setOnClickListener(v -> showVoicePicker());
        LinearLayout.LayoutParams voiceLp = new LinearLayout.LayoutParams(0, -2, 1.2f);
        voiceLp.setMargins(dp(3), dp(3), dp(3), dp(3));
        rowFour.addView(voiceSelectButton, voiceLp);
        addControl(rowFour, "TEST VOICE", () -> speak("Hello Raushan. This is your selected JARVIS voice."));
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

        LinearLayout.LayoutParams panelLp = new LinearLayout.LayoutParams(-1, -2);
        panelLp.topMargin = dp(4);
        root.addView(systemPanel, panelLp);
    }

    private void toggleSystemPanel() {
        if (systemPanel == null) return;
        boolean show = systemPanel.getVisibility() != View.VISIBLE;
        systemPanel.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) {
            setStatus("LOCAL SYSTEM PANEL");
            systemPanel.post(() -> {
                View parent = (View) systemPanel.getParent();
                if (parent instanceof ScrollView) ((ScrollView) parent).smoothScrollTo(0, systemPanel.getBottom());
            });
        } else {
            setStatus("SYSTEM STANDBY");
        }
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

        String memoryCommand = handleMemoryCommand(input);
        if (memoryCommand != null) return memoryCommand;

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

    private void reply(String message) {
        addMessage("JARVIS", message, false);
        setStatus("RESPONSE READY");
        speak(message);
    }

    private void speak(String message) {
        if (!spokenReplies || tts == null || message == null || message.trim().isEmpty()) return;
        String speechText = cleanSpeechText(message);
        if (speechText.isEmpty()) { resumeClapWake(); return; }
        // Keep the explicitly selected Android TTS voice. Do not reset the voice per reply.
        try {
            int result = tts.speak(speechText, TextToSpeech.QUEUE_FLUSH, null,
                    "jarvis-" + System.currentTimeMillis());
            if (result == TextToSpeech.ERROR) setStatus("VOICE OUTPUT ERROR");
            else setStatus("SPEAKING");
        } catch (Exception error) {
            setStatus("VOICE OUTPUT UNAVAILABLE");
        }
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


    private void initializeVoiceSelection() {
        availableVoices.clear();
        if (tts == null) return;

        Set<Voice> voices = tts.getVoices();
        if (voices != null) {
            availableVoices.addAll(voices);
            Collections.sort(availableVoices, (a, b) ->
                    (a.getLocale().toLanguageTag() + " " + a.getName())
                            .compareToIgnoreCase(b.getLocale().toLanguageTag() + " " + b.getName()));
        }

        SharedPreferences preferences = getSharedPreferences("jarvis_settings", MODE_PRIVATE);
        String savedName = preferences.getString("tts_voice_name", "");
        Voice selected = null;

        if (!savedName.isEmpty()) {
            for (Voice voice : availableVoices) {
                if (savedName.equals(voice.getName())) {
                    selected = voice;
                    break;
                }
            }
        }

        if (selected == null && savedName.isEmpty()) {
            for (Voice voice : availableVoices) {
                if (voice.getName().toLowerCase(Locale.ROOT).contains("heart")) {
                    selected = voice;
                    break;
                }
            }
        }

        if (selected != null && tts.setVoice(selected) == TextToSpeech.SUCCESS) {
            selectedVoiceName = selected.getName();
            preferences.edit().putString("tts_voice_name", selectedVoiceName).apply();
        } else {
            Voice systemVoice = tts.getVoice();
            if (systemVoice == null) {
                tts.setLanguage(new Locale("en", "IN"));
                systemVoice = tts.getVoice();
            }
            selectedVoiceName = systemVoice == null ? "SYSTEM DEFAULT" : systemVoice.getName();
        }
        updateVoiceSelectButton();
    }

    private String voiceDisplayName() {
        if (selectedVoiceName == null || selectedVoiceName.trim().isEmpty()) return "SYSTEM DEFAULT";
        String name = selectedVoiceName;
        int hash = name.indexOf('#');
        if (hash > 0) name = name.substring(0, hash);
        name = name.replace('_', ' ').replace('-', ' ').trim();
        return name.length() > 24 ? name.substring(0, 24) : name;
    }

    private void updateVoiceSelectButton() {
        if (voiceSelectButton != null) {
            voiceSelectButton.setText("VOICE: " + voiceDisplayName().toUpperCase(Locale.ROOT));
        }
    }

    private void showVoicePicker() {
        if (tts == null || availableVoices.isEmpty()) {
            reply("No selectable Android text-to-speech voices are available. Check your device's text-to-speech settings.");
            return;
        }
        String[] labels = new String[availableVoices.size()];
        int selectedIndex = -1;
        for (int i = 0; i < availableVoices.size(); i++) {
            Voice voice = availableVoices.get(i);
            labels[i] = voice.getName() + "  ·  " + voice.getLocale().toLanguageTag()
                    + (voice.isNetworkConnectionRequired() ? "  ·  ONLINE" : "  ·  DEVICE");
            if (voice.getName().equals(selectedVoiceName)) selectedIndex = i;
        }
        new AlertDialog.Builder(this)
                .setTitle("SELECT JARVIS VOICE")
                .setSingleChoiceItems(labels, selectedIndex, (dialog, which) -> {
                    Voice voice = availableVoices.get(which);
                    if (tts.setVoice(voice) == TextToSpeech.SUCCESS) {
                        selectedVoiceName = voice.getName();
                        getSharedPreferences("jarvis_settings", MODE_PRIVATE).edit()
                                .putString("tts_voice_name", selectedVoiceName).apply();
                        updateVoiceSelectButton();
                        dialog.dismiss();
                        speak("Voice profile selected. This is how I will speak from now on.");
                    } else {
                        reply("That voice could not be selected by the Android speech engine.");
                    }
                })
                .setNegativeButton("CANCEL", null)
                .show();
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
        if (voiceButton != null) {
            voiceButton.setText(spokenReplies ? "◖" : "◗");
            voiceButton.setTextColor(spokenReplies ? CYAN : MUTED);
        }
        reply(spokenReplies ? "Spoken replies enabled." : "Spoken replies disabled.");
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
                setStatus("LISTENING");
                if (micButton != null) micButton.setTextColor(GOLD);
            }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { setStatus("PROCESSING VOICE"); }
            @Override public void onError(int error) {
                listening = false;
                if (micButton != null) micButton.setTextColor(CYAN);
                setStatus(error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS
                        ? "MICROPHONE PERMISSION REQUIRED" : "VOICE INPUT READY");
                resumeClapWake();
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                if (micButton != null) micButton.setTextColor(CYAN);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) sendMessage(matches.get(0));
                else {
                    setStatus("NO SPEECH DETECTED");
                    resumeClapWake();
                }
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        };
    }

    private void startListening() {
        pauseClapWakeForManualMic();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, PERMISSION_AUDIO);
            return;
        }
        if (recognizer == null) {
            reply("Speech recognition is not available on this device.");
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
            recognizer.startListening(intent);
            setStatus("LISTENING");
        } catch (Exception error) {
            setStatus("VOICE INPUT UNAVAILABLE");
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


    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_AUDIO) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListening();
            } else {
                reply("Microphone permission was not granted.");
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
        } else if (requestCode == PERMISSION_CONTACTS) {
            String requested = pendingCallContact;
            pendingCallContact = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED
                    && requested != null) {
                reply(openContactDialer(requested));
            } else {
                reply("Contacts permission was not granted. You can still say 'dial' followed by a phone number.");
            }
        }
    }

    private static class ReactorView extends View {

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
        stopClapRecorder();
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumeClapWake();
    }

    @Override
    protected void onDestroy() {
        clapWakeEnabled = false;
        stopClapRecorder();
        if (handlerClock.runnable != null) handlerClock.handler.removeCallbacks(handlerClock.runnable);
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
