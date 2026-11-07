package com.raushan.jarvis;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.RectF;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

public class JarvisOverlayService extends Service {
    static final String ACTION_SHOW = "com.raushan.jarvis.overlay.SHOW";
    static final String ACTION_HIDE = "com.raushan.jarvis.overlay.HIDE";
    static final String ACTION_STOP = "com.raushan.jarvis.overlay.STOP";
    private static final String CHANNEL_ID = "jarvis_floating_orb";
    private static final int NOTIFICATION_ID = 9011;

    private WindowManager windowManager;
    private OrbView orbView;
    private WindowManager.LayoutParams params;
    private float downRawX, downRawY;
    private int startX, startY;
    private boolean moved;

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIFICATION_ID, buildNotification());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        addOrb();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "A.R.T.I.E. Floating Orb", NotificationManager.IMPORTANCE_LOW);
            channel.setDescription("Keeps the user-enabled A.R.T.I.E. floating Orb available.");
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }

    private Notification buildNotification() {
        Intent open = new Intent(this, MainActivity.class);
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pending = PendingIntent.getActivity(this, 9012, open,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder builder = Build.VERSION.SDK_INT >= 26
                ? new Notification.Builder(this, CHANNEL_ID)
                : new Notification.Builder(this);
        return builder.setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("A.R.T.I.E. is ready")
                .setContentText("Floating Orb enabled. Tap to return to A.R.T.I.E.")
                .setContentIntent(pending)
                .setOngoing(true)
                .setCategory(Notification.CATEGORY_SERVICE)
                .build();
    }

    private void addOrb() {
        if (windowManager == null || orbView != null) return;
        orbView = new OrbView(this);
        int type = Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                dp(68), dp(68), type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = dp(16);
        params.y = dp(230);
        try {
            windowManager.addView(orbView, params);
            orbView.setVisibility(View.INVISIBLE);
            orbView.setOnTouchListener((view, event) -> {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - downRawX;
                        float dy = event.getRawY() - downRawY;
                        if (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6)) moved = true;
                        if (moved) {
                            params.x = Math.max(0, startX - Math.round(dx));
                            params.y = Math.max(0, startY + Math.round(dy));
                            try { windowManager.updateViewLayout(orbView, params); }
                            catch (Exception ignored) { }
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) openArtie();
                        return true;
                }
                return true;
            });
        } catch (Exception error) {
            orbView = null;
            stopSelf();
        }
    }

    private void openArtie() {
        if (orbView != null) orbView.setVisibility(View.INVISIBLE);
        Intent launch = new Intent(this, MainActivity.class);
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try { startActivity(launch); } catch (Exception ignored) { }
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? ACTION_HIDE : intent.getAction();
        if (ACTION_STOP.equals(action)) {
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        if (orbView != null) {
            orbView.setVisibility(ACTION_SHOW.equals(action) ? View.VISIBLE : View.INVISIBLE);
        }
        return START_STICKY;
    }

    @Override public void onDestroy() {
        if (windowManager != null && orbView != null) {
            try { windowManager.removeView(orbView); } catch (Exception ignored) { }
        }
        orbView = null;
        stopForeground(true);
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private static class OrbView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final long start = System.currentTimeMillis();

        OrbView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setContentDescription("A.R.T.I.E. floating Orb. Tap to open A.R.T.I.E.");
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Math.min(getWidth(), getHeight()) * .31f;
            float t = (System.currentTimeMillis() - start) / 1000f;
            float pulse = 1f + .045f * (float) Math.sin(t * 2.4f);

            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, radius * 2.1f,
                    new int[]{Color.argb(90, 66, 188, 255), Color.argb(18, 32, 130, 210), Color.TRANSPARENT},
                    null, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius * 2.1f, paint);
            paint.setShader(null);

            paint.setShader(new RadialGradient(cx - radius * .25f, cy - radius * .3f, radius * 1.45f,
                    new int[]{Color.rgb(246, 255, 255), Color.rgb(120, 221, 255),
                            Color.rgb(27, 131, 218), Color.rgb(4, 30, 67)},
                    new float[]{0f, .22f, .63f, 1f}, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, radius * pulse, paint);
            paint.setShader(null);

            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dpStatic(getContext(), 1.3f));
            paint.setColor(Color.argb(210, 217, 248, 255));
            canvas.drawCircle(cx, cy, radius * pulse, paint);
            paint.setStrokeWidth(dpStatic(getContext(), 1f));
            paint.setColor(Color.argb(125, 94, 204, 255));
            canvas.drawArc(new RectF(cx - radius * 1.55f, cy - radius * 1.55f,
                    cx + radius * 1.55f, cy + radius * 1.55f),
                    (t * 45f) % 360f, 215f, false, paint);
            postInvalidateDelayed(40);
        }

        private static float dpStatic(Context context, float value) {
            return value * context.getResources().getDisplayMetrics().density;
        }
    }
}
