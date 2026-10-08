package com.mujahid.myagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;

import java.nio.ByteBuffer;

/**
 * v43: Screen capture ka ghar — foreground service (type mediaProjection,
 * Android 14 ka rule). Projection + VirtualDisplay + ImageReader yahin rehte
 * hain jab tak share ON hai. On-demand ek frame deta hai.
 */
public class ProjectionService extends Service {

    private static final String CH = "ayesha_share";
    private static final int NOTIFY_ID = 4102;

    private static volatile boolean running = false;
    private static volatile MediaProjection projection;
    // v44: VirtualDisplay har capture pe NAYA banta hai (purani frame ka masla khatam)
    private static volatile int vw = 0, vh = 0, densityDpi = 320;
    // v47: live streaming ke liye persistent pipeline (1 fps)

    public static boolean isRunning() {
        return running && projection != null;
    }

    public static void start(Context ctx, int resultCode, Intent data) {
        Intent i = new Intent(ctx, ProjectionService.class);
        i.setAction("START");
        i.putExtra("code", resultCode);
        i.putExtra("data", data);
        try {
            ctx.startForegroundService(i);
        } catch (Exception ignored) { }
    }

    public static void stop(Context ctx) {
        try {
            ctx.stopService(new Intent(ctx, ProjectionService.class));
        } catch (Exception ignored) { }
        ScreenShare.markOff();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null || !"START".equals(intent.getAction())) {
            stopSelf();
            return START_NOT_STICKY;
        }
        int code = intent.getIntExtra("code", 0);
        Intent data = intent.getParcelableExtra("data");
        if (data == null) {
            stopSelf();
            return START_NOT_STICKY;
        }
        try {
            startForegroundWithType();
            MediaProjectionManager mpm = (MediaProjectionManager)
                    getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            projection = mpm.getMediaProjection(code, data);
            if (projection == null) {
                stopSelf();
                return START_NOT_STICKY;
            }
            projection.registerCallback(new MediaProjection.Callback() {
                @Override
                public void onStop() {
                    // User ne system se revoke kiya
                    stopSelf();
                }
            }, new Handler(Looper.getMainLooper()));

            DisplayMetrics m = getResources().getDisplayMetrics();
            // v45: NATIVE resolution — 720p wala dabao khatam, text saaf nazar aayega
            vw = m.widthPixels;
            vh = m.heightPixels;
            densityDpi = m.densityDpi;
            // v44: VirtualDisplay ab har capture pe banta hai — yahan sirf projection
            running = true;
            // v47: service taiyaar — share state dobara batado (listener ke liye)
            try { ScreenShare.notifyState(); }
            catch (Exception ignored) { }
        } catch (Exception e) {
            stopSelf();
            return START_NOT_STICKY;
        }
        return START_STICKY;
    }

    private void startForegroundWithType() {
        NotificationManager nm = (NotificationManager)
                getSystemService(Context.NOTIFICATION_SERVICE);
        try {
            nm.createNotificationChannel(new NotificationChannel(
                    CH, "Screen Share", NotificationManager.IMPORTANCE_LOW));
        } catch (Exception ignored) { }
        Notification n = new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Ayesha — Screen Share ON")
                .setContentText("Tumhari screen Ayesha dekh sakti hai")
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIFY_ID, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFY_ID, n);
            }
        } catch (Exception e) {
            startForeground(NOTIFY_ID, n);
        }
    }

    /**
     * v44: TAAZA frame — har baar naya VirtualDisplay + ImageReader.
     * Purani atki hui frame ka masla khatam. Bg thread pe (1-2s).
     * Null = frame nahi mila.
     */
    public static Bitmap capture() {
        MediaProjection p = projection;
        if (p == null || !running || vw <= 0 || vh <= 0) return null;
        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(1);
        final Image[] out = new Image[1];
        ImageReader r = null;
        VirtualDisplay vd = null;
        try {
            r = ImageReader.newInstance(vw, vh, PixelFormat.RGBA_8888, 2);
            r.setOnImageAvailableListener(rd -> {
                try {
                    Image im = rd.acquireLatestImage();
                    if (im != null) {
                        if (out[0] == null) out[0] = im;
                        else { try { im.close(); } catch (Exception ignored) { } }
                    }
                } catch (Exception ignored) { }
                latch.countDown();
            }, new Handler(Looper.getMainLooper()));
            vd = p.createVirtualDisplay("ayesha-cap", vw, vh, densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    r.getSurface(), null, null);
            try {
                latch.await(4000, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ignored) { }
            Image img = out[0];
            if (img == null) {
                try { img = r.acquireLatestImage(); }
                catch (Exception ignored) { }
            }
            if (img == null) return null;
            long age = frameAgeMs(img);
            Diag.event("screenshot: frame umar=" + age + "ms");
            Bitmap bmp = imageToBitmap(img);
            try { img.close(); } catch (Exception ignored) { }
            return bmp;
        } catch (Exception e) {
            return null;
        } finally {
            try { if (vd != null) vd.release(); } catch (Exception ignored) { }
            try { if (r != null) r.close(); } catch (Exception ignored) { }
        }
    }

    /** Frame kitni purani hai (ms). Ajeeb value ho to 0 (taaza mano). */
    private static long frameAgeMs(Image img) {
        try {
            long ts = img.getTimestamp();
            if (ts <= 0) return 0;
            long ageNs = System.nanoTime() - ts;
            if (ageNs < 0 || ageNs > 3_600_000_000_000L) return 0;
            return ageNs / 1_000_000;
        } catch (Exception e) {
            return 0;
        }
    }

    private static Bitmap imageToBitmap(Image img) {
        try {
            Image.Plane[] planes = img.getPlanes();
            ByteBuffer buf = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * img.getWidth();
            Bitmap bmp = Bitmap.createBitmap(
                    img.getWidth() + rowPadding / pixelStride,
                    img.getHeight(), Bitmap.Config.ARGB_8888);
            bmp.copyPixelsFromBuffer(buf);
            if (rowPadding > 0) {
                return Bitmap.createBitmap(bmp, 0, 0, img.getWidth(), img.getHeight());
            }
            return bmp;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onDestroy() {
        running = false;
        try { if (projection != null) projection.stop(); }
        catch (Exception ignored) { }
        projection = null;
        ScreenShare.markOff();
        try { stopForeground(true); } catch (Exception ignored) { }
        super.onDestroy();
    }
}
