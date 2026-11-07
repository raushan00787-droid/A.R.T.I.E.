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
        if (token == null || token.trim().isEmpty()) {
            throw new IOException("Neural Core is not paired.");
        }

        HttpURLConnection connection = null;
        try {
            URL url = new URL(BASE_URL + path);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod(method);
            connection.setConnectTimeout(1800);
            connection.setReadTimeout(2500);
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
