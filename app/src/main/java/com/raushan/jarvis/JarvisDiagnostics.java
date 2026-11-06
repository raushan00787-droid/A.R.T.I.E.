package com.raushan.jarvis;

import android.Manifest;
import android.app.ActivityManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.BatteryManager;
import android.os.Build;
import android.os.Environment;
import android.os.StatFs;
import android.provider.Settings;

import java.util.Locale;

final class JarvisDiagnostics {
    private JarvisDiagnostics() { }

    static String buildReport(Context context, boolean ttsReady, boolean speechRecognitionReady,
                              boolean liveModeEnabled, boolean liveModePaused,
                              boolean floatingOrbEnabled, boolean clapWakeEnabled, boolean torchOn) {
        StringBuilder report = new StringBuilder("J.A.R.V.I.S. SYSTEM DIAGNOSTICS\n");
        report.append(Build.MANUFACTURER).append(" ").append(Build.MODEL)
                .append(" • Android ").append(Build.VERSION.RELEASE).append("\n");

        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
            report.append("App: ").append(info.versionName).append(" (")
                    .append(info.versionCode).append(")\n");
        } catch (Exception ignored) { }

        report.append("Battery: ").append(readBatteryPercent(context)).append("\n");
        report.append("RAM available: ").append(readAvailableRam(context)).append("\n");
        report.append("Storage available: ").append(readAvailableStorage()).append("\n");
        report.append("Network: ").append(readNetwork(context)).append("\n");

        report.append("Microphone: ").append(granted(context, Manifest.permission.RECORD_AUDIO)).append("\n");
        report.append("Camera: ").append(granted(context, Manifest.permission.CAMERA)).append("\n");
        report.append("Contacts: ").append(granted(context, Manifest.permission.READ_CONTACTS)).append("\n");
        report.append("Notifications: ").append(Build.VERSION.SDK_INT < 33
                ? "AVAILABLE BY PLATFORM DEFAULT" : granted(context, Manifest.permission.POST_NOTIFICATIONS)).append("\n");
        report.append("Overlay: ").append(Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(context)
                ? "ALLOWED" : "NOT ALLOWED").append("\n");

        boolean assistant = false;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                RoleManager roles = context.getSystemService(RoleManager.class);
                assistant = roles != null && roles.isRoleHeld(RoleManager.ROLE_ASSISTANT);
            } catch (Exception ignored) { }
        }
        report.append("Default assistant: ").append(assistant ? "JARVIS" : "NOT ASSIGNED").append("\n");
        report.append("Speech output: ").append(ttsReady ? "READY" : "UNAVAILABLE").append("\n");
        report.append("Speech recognition: ").append(speechRecognitionReady ? "READY" : "UNAVAILABLE").append("\n");
        report.append("Live mode: ").append(!liveModeEnabled ? "OFF" : liveModePaused ? "PAUSED" : "ON").append("\n");
        report.append("Clap wake: ").append(clapWakeEnabled ? "ON" : "OFF").append("\n");
        report.append("Floating Orb: ").append(floatingOrbEnabled ? "ON" : "OFF").append("\n");
        report.append("Flashlight: ").append(torchOn ? "ON" : "OFF");
        return report.toString();
    }

    private static String granted(Context context, String permission) {
        return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED
                ? "GRANTED" : "NOT GRANTED";
    }

    private static String readBatteryPercent(Context context) {
        try {
            Intent battery = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (battery == null) return "UNKNOWN";
            int level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            if (level < 0 || scale <= 0) return "UNKNOWN";
            return Math.round(level * 100f / scale) + "%";
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private static String readAvailableRam(Context context) {
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return "UNKNOWN";
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            return formatBytes(info.availMem);
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private static String readAvailableStorage() {
        try {
            StatFs stats = new StatFs(Environment.getDataDirectory().getAbsolutePath());
            return formatBytes(stats.getAvailableBytes());
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private static String readNetwork(Context context) {
        try {
            ConnectivityManager manager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
            if (manager == null) return "UNKNOWN";
            Network network = manager.getActiveNetwork();
            if (network == null) return "OFFLINE";
            NetworkCapabilities caps = manager.getNetworkCapabilities(network);
            if (caps == null) return "CONNECTED (TYPE UNKNOWN)";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return "CONNECTED (WI-FI)";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)) return "CONNECTED (MOBILE DATA)";
            if (caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "CONNECTED (ETHERNET)";
            return "CONNECTED";
        } catch (SecurityException error) {
            return "PERMISSION UNAVAILABLE";
        } catch (Exception ignored) {
            return "UNKNOWN";
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "UNKNOWN";
        double value = bytes / (1024.0 * 1024.0 * 1024.0);
        return String.format(Locale.getDefault(), "%.1f GB", value);
    }
}
