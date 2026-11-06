package com.raushan.jarvis;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.BatteryManager;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
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
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final String WORKER_URL = "https://quiet-scribe-ai.thequietscribe.workers.dev";
    private static final int PERMISSION_AUDIO = 10;
    private static final int PERMISSION_CAMERA = 11;

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
    private MaskView maskView;

    private TextToSpeech tts;
    private SpeechRecognizer recognizer;
    private CameraManager camera;
    private String cameraId;
    private boolean torchOn = false;
    private boolean spokenReplies = true;
    private boolean listening = false;
    private boolean busy = false;
    private boolean useHindi = false;

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
        resetConversation();
        buildUI();

        tts = new TextToSpeech(this, result -> {
            if (tts != null && result == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("en", "IN"));
                tts.setSpeechRate(1.0f);
            }
        });

        if (SpeechRecognizer.isRecognitionAvailable(this)) {
            recognizer = SpeechRecognizer.createSpeechRecognizer(this);
            recognizer.setRecognitionListener(createRecognitionListener());
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

    private void resetConversation() {
        conversation = new JSONArray();
        try {
            JSONObject system = new JSONObject();
            system.put("role", "system");
            system.put("content", SYSTEM_PROMPT);
            conversation.put(system);
        } catch (Exception ignored) { }
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
        root.setPadding(dp(22), dp(22), dp(22), dp(24));
        page.addView(root, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(page);

        buildTopBar();
        addLine(root);
        buildMainArea();
        addLine(root);
        buildConversation();
        addGap(root, 12);
        buildComposer();
        addGap(root, 10);
        buildDeviceControls();
        addGap(root, 12);
        TextView footer = text("J.A.R.V.I.S.  •  PERSONAL SYSTEM  •  BUILD 1.2", 9, MUTED);
        footer.setGravity(Gravity.CENTER);
        root.addView(footer);
    }

    private void buildTopBar() {
        LinearLayout bar = row();
        TextView brand = text("◉  J.A.R.V.I.S.  /  PERSONAL SYSTEM", 11, CYAN);
        brand.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        brand.setLetterSpacing(0.08f);
        bar.addView(brand, new LinearLayout.LayoutParams(0, -2, 1));

        TextView owner = text("AUTHORIZED USER\nRAUSHAN  ◈", 9, GOLD);
        owner.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        owner.setLetterSpacing(0.08f);
        bar.addView(owner);
        root.addView(bar, new LinearLayout.LayoutParams(-1, dp(42)));
    }

    private void buildMainArea() {
        int widthDp = (int) (getResources().getDisplayMetrics().widthPixels /
                getResources().getDisplayMetrics().density);
        boolean wide = widthDp >= 720;

        LinearLayout main = row();
        main.setGravity(Gravity.TOP);
        LinearLayout left = column();
        LinearLayout right = column();

        TextView eyebrow = text("NEURAL INTERFACE  //  " + "NATIVE CORE", 9, MUTED);
        eyebrow.setLetterSpacing(0.18f);
        left.addView(eyebrow);

        TextView heading = text("At your service.", 27, WHITE);
        heading.setTypeface(sansSerif(), Typeface.NORMAL);
        heading.setLetterSpacing(0.05f);
        LinearLayout.LayoutParams headingLp = new LinearLayout.LayoutParams(-1, -2);
        headingLp.topMargin = dp(7);
        left.addView(heading, headingLp);

        TextView subtitle = text("All systems ready, Raushan.", 11, MUTED);
        subtitle.setLetterSpacing(0.05f);
        left.addView(subtitle);

        reactorView = new ReactorView(this);
        LinearLayout.LayoutParams reactorLp = new LinearLayout.LayoutParams(dp(wide ? 290 : 250), dp(wide ? 290 : 250));
        reactorLp.gravity = Gravity.CENTER_HORIZONTAL;
        reactorLp.topMargin = dp(8);
        left.addView(reactorView, reactorLp);

        TextView readout = text("J.A.R.V.I.S. ONLINE", 10, CYAN);
        readout.setGravity(Gravity.CENTER);
        readout.setLetterSpacing(0.22f);
        left.addView(readout);

        TextView hint = text("LOCAL SYSTEMS  •  AI LINK", 8, MUTED);
        hint.setGravity(Gravity.CENTER);
        hint.setPadding(0, dp(5), 0, 0);
        left.addView(hint);

        TextView rightCaption = text("PERSONAL SIGNATURE  /  R-01", 9, MUTED);
        rightCaption.setGravity(Gravity.CENTER);
        rightCaption.setLetterSpacing(0.16f);
        right.addView(rightCaption);

        maskView = new MaskView(this);
        LinearLayout.LayoutParams maskLp = new LinearLayout.LayoutParams(dp(wide ? 220 : 185), dp(wide ? 220 : 185));
        maskLp.gravity = Gravity.CENTER_HORIZONTAL;
        maskLp.topMargin = dp(10);
        right.addView(maskView, maskLp);

        TextView ownerTitle = text("RAUSHAN", 12, GOLD);
        ownerTitle.setGravity(Gravity.CENTER);
        ownerTitle.setLetterSpacing(0.25f);
        right.addView(ownerTitle);
        TextView ownerSub = text("CREATOR'S SIGNATURE  •  JARVIS CORE", 8, MUTED);
        ownerSub.setGravity(Gravity.CENTER);
        ownerSub.setPadding(0, dp(5), 0, dp(10));
        right.addView(ownerSub);

        LinearLayout statusCard = column();
        statusCard.setPadding(dp(12), dp(10), dp(12), dp(10));
        statusCard.setBackground(shape(PANEL, Color.rgb(37, 82, 101), 4));
        statusView = text("SYSTEM STANDBY", 10, CYAN);
        statusView.setGravity(Gravity.CENTER);
        statusView.setLetterSpacing(0.14f);
        statusCard.addView(statusView);
        clockView = text("--:--:--", 9, MUTED);
        clockView.setGravity(Gravity.CENTER);
        clockView.setPadding(0, dp(5), 0, 0);
        statusCard.addView(clockView);
        LinearLayout.LayoutParams statusLp = new LinearLayout.LayoutParams(-1, -2);
        statusLp.topMargin = dp(8);
        right.addView(statusCard, statusLp);

        if (wide) {
            LinearLayout.LayoutParams leftLp = new LinearLayout.LayoutParams(0, -2, 1.15f);
            leftLp.rightMargin = dp(28);
            main.addView(left, leftLp);
            main.addView(right, new LinearLayout.LayoutParams(0, -2, 0.85f));
        } else {
            main.setOrientation(LinearLayout.VERTICAL);
            main.addView(left, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout.LayoutParams rightLp = new LinearLayout.LayoutParams(-1, -2);
            rightLp.topMargin = dp(12);
            main.addView(right, rightLp);
        }
        root.addView(main, new LinearLayout.LayoutParams(-1, -2));

        TextView quickTitle = text("QUICK ACCESS", 9, MUTED);
        quickTitle.setLetterSpacing(0.18f);
        quickTitle.setPadding(0, dp(13), 0, dp(8));
        right.addView(quickTitle);
        LinearLayout quickRow = row();
        quickRow.setGravity(Gravity.CENTER);
        quickRow.addView(chip("CAPABILITIES", () -> sendMessage("What can you help me with?")));
        View spacer = new View(this);
        quickRow.addView(spacer, new LinearLayout.LayoutParams(dp(7), 1));
        quickRow.addView(chip("BRAINSTORM", () -> sendMessage("Let's brainstorm an idea.")));
        right.addView(quickRow);
    }

    private Typeface sansSerif() {
        return Typeface.create("sans-serif-light", Typeface.NORMAL);
    }

    private void buildConversation() {
        LinearLayout card = column();
        card.setPadding(dp(14), dp(12), dp(14), dp(12));
        card.setBackground(shape(Color.rgb(7, 18, 29), Color.rgb(31, 72, 91), 3));

        LinearLayout header = row();
        TextView title = text("J.A.R.V.I.S.  /  CONVERSATION", 9, CYAN);
        title.setLetterSpacing(0.14f);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        TextView clear = text("CLEAR", 9, GOLD);
        clear.setPadding(dp(8), dp(5), dp(8), dp(5));
        clear.setOnClickListener(v -> clearConversation());
        header.addView(clear);
        card.addView(header);

        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(false);
        chatList = column();
        chatList.setPadding(0, dp(7), 0, 0);
        chatScroll.addView(chatList);
        LinearLayout.LayoutParams chatLp = new LinearLayout.LayoutParams(-1, dp(116));
        chatLp.topMargin = dp(5);
        card.addView(chatScroll, chatLp);

        TextView welcome = text("Good day, Raushan. JARVIS is online. What shall we work on?", 12, Color.rgb(184, 210, 222));
        welcome.setPadding(dp(2), dp(6), dp(2), dp(6));
        chatList.addView(welcome);
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
        languageButton = smallControl("EN", () -> toggleLanguage());
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
        TextView title = text("DEVICE CONTROLS", 9, MUTED);
        title.setLetterSpacing(0.18f);
        title.setPadding(0, dp(5), 0, dp(8));
        root.addView(title);

        LinearLayout rowOne = row();
        addControl(rowOne, "FLASHLIGHT", () -> toggleTorch());
        addControl(rowOne, "BATTERY STATUS", () -> {
            int level = getBatteryPct();
            reply(level < 0 ? "I could not read the battery level." : "Battery level is " + level + " percent.");
        });
        addControl(rowOne, "WI-FI SETTINGS", () -> openSettings(android.provider.Settings.ACTION_WIFI_SETTINGS, "Wi-Fi"));
        root.addView(rowOne);

        LinearLayout rowTwo = row();
        addControl(rowTwo, "BLUETOOTH", () -> openSettings(android.provider.Settings.ACTION_BLUETOOTH_SETTINGS, "Bluetooth"));
        addControl(rowTwo, "DISPLAY", () -> openSettings(android.provider.Settings.ACTION_DISPLAY_SETTINGS, "Display"));
        addControl(rowTwo, "ALL SETTINGS", () -> openSettings(android.provider.Settings.ACTION_SETTINGS, "system"));
        root.addView(rowTwo);

        LinearLayout rowThree = row();
        addControl(rowThree, "STOP SPEECH", () -> stopSpeech());
        addControl(rowThree, "HELP", () -> sendMessage("Show me the available JARVIS commands."));
        addControl(rowThree, "CLEAR CHAT", () -> clearConversation());
        root.addView(rowThree);
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
            clockView.setText(new SimpleDateFormat("HH:mm:ss  •  dd MMM yyyy", Locale.getDefault()).format(new Date()));
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
        TextView line = text(role + "   " + message, 11,
                error ? Color.rgb(242, 150, 150) : role.equals("YOU") ? GOLD : Color.rgb(195, 222, 233));
        line.setPadding(0, dp(6), 0, dp(6));
        line.setLineSpacing(dp(2), 1.0f);
        chatList.addView(line);
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
        } catch (Exception e) {
            busy = false;
            reply("I couldn't prepare that message.");
            return;
        }

        network.execute(() -> {
            String answer;
            try {
                answer = callWorker();
            } catch (Exception error) {
                answer = "I couldn't reach the AI service. " + safeMessage(error);
            }
            final String finalAnswer = answer;
            runOnUiThread(() -> {
                busy = false;
                if (sendButton != null) sendButton.setAlpha(1f);
                if (connectionView != null) {
                    connectionView.setText(finalAnswer.startsWith("I couldn't reach")
                            ? "AI LINK ISSUE" : "AI LINK READY");
                }
                addMessage("JARVIS", finalAnswer, finalAnswer.startsWith("I couldn't reach"));
                try {
                    JSONObject assistant = new JSONObject();
                    assistant.put("role", "assistant");
                    assistant.put("content", finalAnswer);
                    conversation.put(assistant);
                } catch (Exception ignored) { }
                setStatus(finalAnswer.startsWith("I couldn't reach") ? "CONNECTION ISSUE" : "RESPONSE READY");
                speak(finalAnswer);
            });
        });
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
                answer = json.optString("reply", json.optString("response", ""));
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
            while ((line = reader.readLine()) != null) output.append(line);
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

        if (value.equals("stop") || value.equals("bas") || value.equals("chup") ||
                value.contains("stop speech") || value.contains("jarvis stop")) {
            stopSpeech();
            return "Speech stopped.";
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
        if (value.equals("help") || value.contains("available commands")) {
            return "I can check battery, toggle the flashlight, open Wi-Fi, Bluetooth, display and system settings, tell the time and date, and answer general questions through the AI connection.";
        }
        return null;
    }

    private void reply(String message) {
        addMessage("JARVIS", message, false);
        setStatus("RESPONSE READY");
        speak(message);
    }

    private void speak(String message) {
        if (!spokenReplies || tts == null || message == null || message.trim().isEmpty()) return;
        boolean hindiText = message.matches("(?s).*[\\u0900-\\u097F].*");
        tts.setLanguage(hindiText ? new Locale("hi", "IN") : new Locale("en", "IN"));
        tts.speak(message, TextToSpeech.QUEUE_FLUSH, null, "jarvis-" + System.currentTimeMillis());
        setStatus("SPEAKING");
    }

    private void stopSpeech() {
        if (tts != null) tts.stop();
        if (recognizer != null && listening) {
            try { recognizer.cancel(); } catch (Exception ignored) { }
            listening = false;
        }
        setStatus("SPEECH INTERRUPTED");
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
        useHindi = !useHindi;
        if (languageButton != null) languageButton.setText(useHindi ? "HI" : "EN");
        reply(useHindi ? "Hindi voice recognition selected." : "English India voice recognition selected.");
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
            }
            @Override public void onResults(Bundle results) {
                listening = false;
                if (micButton != null) micButton.setTextColor(CYAN);
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) sendMessage(matches.get(0));
                else setStatus("NO SPEECH DETECTED");
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        };
    }

    private void startListening() {
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
            String language = useHindi ? "hi-IN" : "en-IN";
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, language);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, language);
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
        return level >= 0 && scale > 0 ? Math.round(level * 100f / scale) : -1;
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

    private static class MaskView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        MaskView(Context context) { super(context); setLayerType(View.LAYER_TYPE_SOFTWARE, null); }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight();
            float s = Math.min(w, h) / 220f;
            canvas.save();
            canvas.translate((w - 220*s)/2f, (h - 220*s)/2f);
            canvas.scale(s, s);

            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.2f);
            p.setColor(Color.argb(95, 112, 229, 255));
            canvas.drawCircle(110, 110, 101, p);
            p.setPathEffect(new android.graphics.DashPathEffect(new float[]{3, 7}, 0));
            canvas.drawCircle(110, 110, 92, p);
            p.setPathEffect(null);

            Path helmet = new Path();
            helmet.moveTo(110, 23);
            helmet.cubicTo(55, 25, 35, 66, 38, 112);
            helmet.lineTo(48, 164);
            helmet.lineTo(77, 194);
            helmet.lineTo(110, 207);
            helmet.lineTo(143, 194);
            helmet.lineTo(172, 164);
            helmet.lineTo(182, 112);
            helmet.cubicTo(185, 66, 165, 25, 110, 23);
            helmet.close();

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(92, 27, 37));
            p.setShadowLayer(14, 0, 0, Color.argb(80, 55, 194, 233));
            canvas.drawPath(helmet, p);
            p.clearShadowLayer();
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(3);
            p.setColor(GOLD_STATIC);
            canvas.drawPath(helmet, p);

            Path face = new Path();
            face.moveTo(110, 45);
            face.lineTo(151, 61);
            face.lineTo(161, 112);
            face.lineTo(146, 160);
            face.lineTo(110, 190);
            face.lineTo(74, 160);
            face.lineTo(59, 112);
            face.lineTo(69, 61);
            face.close();
            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(139, 43, 47));
            canvas.drawPath(face, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.8f);
            p.setColor(Color.rgb(232, 184, 104));
            canvas.drawPath(face, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(208, 155, 78));
            Path brow = new Path();
            brow.moveTo(68, 76); brow.lineTo(105, 88); brow.lineTo(110, 99);
            brow.lineTo(115, 88); brow.lineTo(152, 76); brow.lineTo(143, 57);
            brow.lineTo(110, 48); brow.lineTo(77, 57); brow.close();
            canvas.drawPath(brow, p);

            Path leftEye = new Path();
            leftEye.moveTo(66, 103); leftEye.lineTo(104, 110); leftEye.lineTo(98, 122); leftEye.lineTo(70, 119); leftEye.close();
            Path rightEye = new Path();
            rightEye.moveTo(154, 103); rightEye.lineTo(116, 110); rightEye.lineTo(122, 122); rightEye.lineTo(150, 119); rightEye.close();
            p.setColor(Color.rgb(4, 20, 31));
            canvas.drawPath(leftEye, p); canvas.drawPath(rightEye, p);
            p.setColor(Color.rgb(112, 229, 255));
            p.setShadowLayer(9, 0, 0, Color.rgb(60, 207, 255));
            Path leftGlow = new Path();
            leftGlow.moveTo(72, 108); leftGlow.lineTo(98, 113); leftGlow.lineTo(95, 116); leftGlow.lineTo(74, 114); leftGlow.close();
            Path rightGlow = new Path();
            rightGlow.moveTo(148, 108); rightGlow.lineTo(122, 113); rightGlow.lineTo(125, 116); rightGlow.lineTo(146, 114); rightGlow.close();
            canvas.drawPath(leftGlow, p); canvas.drawPath(rightGlow, p);
            p.clearShadowLayer();

            Path nose = new Path();
            nose.moveTo(110, 102); nose.lineTo(124, 143); nose.lineTo(110, 151); nose.lineTo(96, 143); nose.close();
            p.setColor(Color.rgb(69, 29, 37));
            canvas.drawPath(nose, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.4f);
            p.setColor(Color.rgb(236, 188, 109));
            canvas.drawPath(nose, p);

            p.setStyle(Paint.Style.FILL);
            p.setColor(Color.rgb(17, 28, 39));
            Path jaw = new Path();
            jaw.moveTo(76, 151); jaw.lineTo(110, 165); jaw.lineTo(144, 151);
            jaw.lineTo(136, 178); jaw.lineTo(110, 192); jaw.lineTo(84, 178); jaw.close();
            canvas.drawPath(jaw, p);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(2);
            p.setColor(Color.rgb(226, 177, 95));
            canvas.drawPath(jaw, p);
            p.setStrokeWidth(2.3f);
            p.setColor(Color.rgb(112, 229, 255));
            canvas.drawLine(91, 174, 129, 174, p);
            canvas.drawLine(96, 180, 124, 180, p);
            p.setStyle(Paint.Style.FILL);

            p.setColor(Color.argb(150, 112, 229, 255));
            canvas.drawCircle(110, 34, 3, p);
            canvas.drawCircle(49, 111, 2, p);
            canvas.drawCircle(171, 111, 2, p);
            canvas.restore();
        }
    }

    private static final int GOLD_STATIC = Color.rgb(216, 169, 77);

    @Override
    protected void onDestroy() {
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
