package com.raushan.jarvis;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Base64;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

/**
 * Local-only bridge client for A.R.T.I.E. Neural Core.
 * The bearer token is encrypted with a non-exportable Android Keystore key.
 */
public final class NeuralCoreClient {
    private static final String BASE_URL = "http://127.0.0.1:8767";
    private static final String PREFS = "artie_neural_core";
    private static final String TOKEN_PREF = "encrypted_bridge_token";
    private static final String KEY_ALIAS = "artie_neural_core_bridge_key";
    private static final int MAX_RESPONSE_CHARS = 65536;

    private NeuralCoreClient() { }

    public static boolean hasSavedToken(Context context) {
        return !context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TOKEN_PREF, "").isEmpty();
    }

    public static void saveToken(Context context, String rawToken) throws Exception {
        String token = rawToken == null ? "" : rawToken.trim();
        if (token.length() < 20 || token.length() > 256) {
            throw new IllegalArgumentException("The bridge token does not look valid.");
        }

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey());
        byte[] encrypted = cipher.doFinal(token.getBytes(StandardCharsets.UTF_8));
        String stored = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
                + "."
                + Base64.encodeToString(encrypted, Base64.NO_WRAP);

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(TOKEN_PREF, stored).apply();
    }

    public static void clearToken(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(TOKEN_PREF).apply();
        try {
            KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
            keyStore.load(null);
            if (keyStore.containsAlias(KEY_ALIAS)) keyStore.deleteEntry(KEY_ALIAS);
        } catch (Exception ignored) { }
    }

    public static JSONObject testToken(String token) throws Exception {
        JSONObject health = request(token, "GET", "/v1/health", null);
        if (!"online".equalsIgnoreCase(health.optString("status"))) {
            throw new IOException("The Neural Core bridge did not report online status.");
        }
        return health;
    }

    public static JSONObject testSavedToken(Context context) throws Exception {
        return testToken(loadToken(context));
    }

    /** Checks Cloudflare memory availability through the paired local Neural Core. */
    public static JSONObject cloudMemoryStatus(Context context) throws Exception {
        String token = loadToken(context);
        if (token.isEmpty()) return null;
        JSONObject response = request(token, "GET", "/v1/cloud/status", null, 1800, 12000);
        if (!response.optBoolean("success", false)) {
            throw new IOException("The Neural Core could not read cloud memory status.");
        }
        JSONObject result = response.optJSONObject("result");
        if (result == null) throw new IOException("The cloud memory status response was empty.");
        return result;
    }

    /** Retries pending local-to-cloud uploads and queued remote deletions. */
    public static JSONObject syncCloudMemories(Context context) throws Exception {
        String token = loadToken(context);
        if (token.isEmpty()) return null;
        JSONObject response = request(token, "POST", "/v1/cloud/sync",
                new JSONObject(), 1800, 35000);
        if (!response.optBoolean("success", false)) {
            throw new IOException("The Neural Core did not accept the cloud sync request.");
        }
        JSONObject result = response.optJSONObject("result");
        if (result == null) throw new IOException("The cloud sync response was empty.");
        return result;
    }

    /** Sends speech to the paired Termux voice router (Cori/Piper or Roopa/Sarvam). */
    public static JSONObject speak(Context context, String text, String language) throws Exception {
        String token = loadToken(context);
        if (token.isEmpty()) throw new IOException("Neural Core is not paired.");
        if (text == null || text.trim().isEmpty()) throw new IllegalArgumentException("Speech text is empty.");
        JSONObject payload = new JSONObject();
        payload.put("text", text.trim());
        payload.put("language", language == null ? "auto" : language);
        JSONObject response = request(token, "POST", "/v1/speak", payload, 2500, 100000);
        if (!response.optBoolean("success", false)) {
            throw new IOException("The Termux voice router did not accept the speech request.");
        }
        JSONObject result = response.optJSONObject("result");
        if (result == null || !"spoken".equalsIgnoreCase(result.optString("status"))) {
            throw new IOException("The Termux voice router returned no playback confirmation.");
        }
        return result;
    }

    /**
     * Adds the latest owner message to Neural Core's local context and returns
     * its memory snapshot. A null result means the app has not been paired.
     */
    public static JSONObject prepareContext(Context context, String message) throws Exception {
        String token = loadToken(context);
        if (token.isEmpty()) return null;

        JSONObject payload = new JSONObject();
        payload.put("role", "user");
        payload.put("content", message == null ? "" : message);

        JSONObject response = request(token, "POST", "/v1/context", payload);
        if (!response.optBoolean("success", false)) {
            throw new IOException("The Neural Core bridge rejected the context request.");
        }

        JSONObject result = response.optJSONObject("result");
        if (result == null) throw new IOException("The Neural Core returned an empty context.");
        return result;
    }


    /** Runs the original local ReasoningEngine against the supplied request and known context. */
    public static JSONObject analyze(Context context, String problem, org.json.JSONArray knownFacts) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("problem", problem == null ? "" : problem);
        payload.put("known_facts", knownFacts == null ? new org.json.JSONArray() : knownFacts);
        return postResult(context, "/v1/analyze", payload);
    }

    /** Creates a structured plan in the local Planner. */
    public static JSONObject createPlan(Context context, String goal, org.json.JSONArray steps) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("goal", goal == null ? "" : goal);
        payload.put("steps", steps == null ? new org.json.JSONArray() : steps);
        return postResult(context, "/v1/plan", payload);
    }

    /** Runs the local weighted DecisionEngine. */
    public static JSONObject decide(Context context, org.json.JSONArray options,
                                    org.json.JSONArray criteria, JSONObject scores,
                                    JSONObject weights) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("options", options == null ? new org.json.JSONArray() : options);
        payload.put("criteria", criteria == null ? new org.json.JSONArray() : criteria);
        if (scores != null) payload.put("scores", scores);
        if (weights != null) payload.put("weights", weights);
        return postResult(context, "/v1/decide", payload);
    }

    /** Submits an explicit owner preference for approval by the local FeedbackEngine. */
    public static JSONObject submitFeedback(Context context, String feedback) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("feedback", feedback == null ? "" : feedback);
        return postResult(context, "/v1/feedback", payload);
    }

    /** Approves explicitly requested feedback so it becomes a long-term preference. */
    public static JSONObject approveFeedback(Context context, long feedbackId) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("feedback_id", feedbackId);
        return postResult(context, "/v1/feedback/approve", payload);
    }

    /** Stores a completed chat exchange in local episodic memory on Termux. */
    public static JSONObject recordExperience(Context context, String task,
                                               String outcome, String lesson) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("task", task == null ? "" : task);
        payload.put("outcome", outcome == null ? "" : outcome);
        payload.put("lesson", lesson == null ? "" : lesson);
        return postResult(context, "/v1/experience", payload);
    }

    /** Removes one exact owner-approved preference from the local Neural Core. */
    public static JSONObject forgetPreference(Context context, String content) throws Exception {
        JSONObject payload = new JSONObject();
        payload.put("category", "preference");
        payload.put("content", content == null ? "" : content);
        return postResult(context, "/v1/memory/forget", payload);
    }

    /** Clears long-term Neural Core records after an explicit owner request. */
    public static JSONObject clearNeuralMemory(Context context) throws Exception {
        return postResult(context, "/v1/memory/clear", new JSONObject());
    }

    private static JSONObject postResult(Context context, String path, JSONObject payload) throws Exception {
        String token = loadToken(context);
        if (token.isEmpty()) return null;
        JSONObject response = request(token, "POST", path, payload);
        if (!response.optBoolean("success", false)) {
            throw new IOException("The Neural Core did not accept " + path + ".");
        }
        JSONObject result = response.optJSONObject("result");
        if (result == null) throw new IOException("The Neural Core returned an empty result for " + path + ".");
        return result;
    }

    private static String loadToken(Context context) throws Exception {
        String stored = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(TOKEN_PREF, "");
        if (stored == null || stored.isEmpty()) return "";

        String[] parts = stored.split("\\.", 2);
        if (parts.length != 2) throw new IOException("Saved Neural Core pairing data is invalid.");

        byte[] iv = Base64.decode(parts[0], Base64.NO_WRAP);
        byte[] encrypted = Base64.decode(parts[1], Base64.NO_WRAP);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
    }

    private static SecretKey getOrCreateKey() throws Exception {
        KeyStore keyStore = KeyStore.getInstance("AndroidKeyStore");
        keyStore.load(null);
        if (keyStore.containsAlias(KEY_ALIAS)) {
            return (SecretKey) keyStore.getKey(KEY_ALIAS, null);
        }

        KeyGenerator generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }

    private static JSONObject request(String token, String method, String path,
                                       JSONObject payload) throws Exception {
        return request(token, method, path, payload, 1800, 2500);
    }

    private static JSONObject request(String token, String method, String path,
                                       JSONObject payload, int connectTimeout,
                                       int readTimeout) throws Exception {
        if (token == null || token.trim().isEmpty()) {
            throw new IOException("Neural Core is not paired.");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(connectTimeout);
            connection.setReadTimeout(readTimeout);
            connection.setUseCaches(false);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + token);

            if (payload != null) {
                connection.setDoOutput(true);
                connection.setRequestProperty(
                        "Content-Type", "application/json; charset=utf-8");
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                connection.setFixedLengthStreamingMode(body.length);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }
            }

            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            String responseText = readBounded(stream);
            if (status < 200 || status >= 300) {
                throw new IOException("Neural Core bridge returned HTTP " + status + ".");
            }
            return new JSONObject(responseText);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static String readBounded(InputStream stream) throws Exception {
        if (stream == null) return "";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                text.append(line);
                if (text.length() > MAX_RESPONSE_CHARS) {
                    throw new IOException("Neural Core response exceeded the size limit.");
                }
            }
        }
        return text.toString();
    }
}
