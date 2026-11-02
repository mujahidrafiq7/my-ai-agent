package com.mujahid.myagent;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.YuvImage;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Base64;

import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.Arrays;

/**
 * v99 "1.0-nazar": Background Camera Vision — headless Camera2.
 *
 * Koi preview UI nahi: ImageReader se frames aate hain, JPEG ban kar
 * Live session ko jate hain (VoiceService ka stream pump bhejta hai).
 * Screen share ke sath: camera ON ho to pump camera frames bhejta
 * hai, screen frames ruk jate hain (purani v94 manzoor-shuda rule).
 * Permission ek dafa ki hai — us ke baad tools se khud on/off/switch.
 */
public final class CameraVision {

    private static volatile boolean on = false;
    private static volatile String latestB64 = null;
    private static volatile long latestTs = 0;
    private static volatile int facingNow = CameraMetadata.LENS_FACING_BACK;

    private static HandlerThread thread;
    private static Handler handler;
    private static CameraDevice device;
    private static CameraCaptureSession session;
    private static ImageReader reader;
    private static int sensorOrientation = 0;
    private static long lastConvMs = 0;

    public static boolean isOn() {
        return on;
    }

    /** Taaza frame (4 second se purana ho to null — stale nahi bhejna). */
    public static String latestFrame() {
        return (System.currentTimeMillis() - latestTs < 4000) ? latestB64 : null;
    }

    public static synchronized String start(Context ctx, String facingStr) {
        int want = "front".equalsIgnoreCase(facingStr)
                ? CameraMetadata.LENS_FACING_FRONT
                : CameraMetadata.LENS_FACING_BACK;
        if (on) {
            if (facingNow == want) {
                return "Camera pehle se ON hai boss ("
                        + (want == CameraMetadata.LENS_FACING_FRONT ? "front" : "back") + ").";
            }
            stopInternal();
        }
        if (ctx.checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            return "Boss, camera permission abhi ON nahi hai. Ek dafa app "
                    + "ki camera screen se allow kar do — us ke baad main "
                    + "khud on/off karungi, dobara ijazat nahi mangungi.";
        }
        try {
            CameraManager cm = (CameraManager)
                    ctx.getSystemService(Context.CAMERA_SERVICE);
            String id = null;
            for (String cid : cm.getCameraIdList()) {
                Integer f = cm.getCameraCharacteristics(cid)
                        .get(CameraCharacteristics.LENS_FACING);
                if (f != null && f == want) { id = cid; break; }
            }
            if (id == null) return "Is phone par wo camera nahi mila boss.";
            Integer so = cm.getCameraCharacteristics(id)
                    .get(CameraCharacteristics.SENSOR_ORIENTATION);
            sensorOrientation = so != null ? so : 0;
            facingNow = want;

            thread = new HandlerThread("ayesha-camera");
            thread.start();
            handler = new Handler(thread.getLooper());

            reader = ImageReader.newInstance(640, 480, ImageFormat.YUV_420_888, 2);
            reader.setOnImageAvailableListener(r -> onImage(r), handler);
            on = true;
            cm.openCamera(id, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(CameraDevice cam) {
                    device = cam;
                    try {
                        cam.createCaptureSession(
                                Arrays.asList(reader.getSurface()),
                                new CameraCaptureSession.StateCallback() {
                                    @Override
                                    public void onConfigured(CameraCaptureSession s) {
                                        session = s;
                                        try {
                                            CaptureRequest.Builder b = cam
                                                    .createCaptureRequest(
                                                            CameraDevice.TEMPLATE_PREVIEW);
                                            b.addTarget(reader.getSurface());
                                            b.set(CaptureRequest.CONTROL_AF_MODE,
                                                    CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE);
                                            s.setRepeatingRequest(b.build(), null, handler);
                                        } catch (Exception ignored) { }
                                    }

                                    @Override
                                    public void onConfigureFailed(CameraCaptureSession s) {
                                        stopInternal();
                                    }
                                }, handler);
                    } catch (Exception e) {
                        stopInternal();
                    }
                }

                @Override
                public void onDisconnected(CameraDevice cam) {
                    stopInternal();
                }

                @Override
                public void onError(CameraDevice cam, int error) {
                    stopInternal();
                }
            }, handler);
            return (want == CameraMetadata.LENS_FACING_FRONT ? "Front" : "Back")
                    + " camera ON kar diya boss — ab main dekh sakti hun.";
        } catch (Exception e) {
            stopInternal();
            return "Camera khul nahi saka boss: " + e.getMessage();
        }
    }

    public static synchronized String switchTo(Context ctx, String facingStr) {
        stopInternal();
        return start(ctx, facingStr);
    }

    public static synchronized void stop() {
        stopInternal();
    }

    private static void stopInternal() {
        on = false;
        latestB64 = null;
        try { if (session != null) session.close(); } catch (Exception ignored) { }
        session = null;
        try { if (device != null) device.close(); } catch (Exception ignored) { }
        device = null;
        try { if (reader != null) reader.close(); } catch (Exception ignored) { }
        reader = null;
        if (thread != null) {
            try { thread.quitSafely(); } catch (Exception ignored) { }
            thread = null;
            handler = null;
        }
    }

    // ---------- frame conversion ----------

    private static void onImage(ImageReader r) {
        long now = System.currentTimeMillis();
        Image img = null;
        try {
            img = r.acquireLatestImage();
            if (img == null) return;
            if (now - lastConvMs < 900) return; // ~1 frame/sec kaafi hai
            lastConvMs = now;
            byte[] nv21 = toNV21(img);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            new YuvImage(nv21, ImageFormat.NV21, img.getWidth(), img.getHeight(), null)
                    .compressToJpeg(new Rect(0, 0, img.getWidth(), img.getHeight()), 70, out);
            byte[] jpeg = out.toByteArray();
            int rot = facingNow == CameraMetadata.LENS_FACING_FRONT
                    ? (360 - sensorOrientation) % 360 : sensorOrientation;
            if (rot != 0) {
                byte[] rotated = rotateJpeg(jpeg, rot);
                if (rotated != null) jpeg = rotated;
            }
            latestB64 = Base64.encodeToString(jpeg, Base64.NO_WRAP);
            latestTs = System.currentTimeMillis();
        } catch (Exception ignored) {
        } finally {
            if (img != null) img.close();
        }
    }

    private static byte[] toNV21(Image img) {
        int w = img.getWidth(), h = img.getHeight();
        byte[] nv21 = new byte[w * h * 3 / 2];
        Image.Plane[] p = img.getPlanes();
        ByteBuffer yb = p[0].getBuffer();
        int yRow = p[0].getRowStride();
        for (int row = 0; row < h; row++) {
            yb.position(row * yRow);
            yb.get(nv21, row * w, w);
        }
        ByteBuffer ub = p[1].getBuffer(), vb = p[2].getBuffer();
        int uRow = p[1].getRowStride(), uPix = p[1].getPixelStride();
        int vRow = p[2].getRowStride(), vPix = p[2].getPixelStride();
        int base = w * h;
        for (int row = 0; row < h / 2; row++) {
            for (int col = 0; col < w / 2; col++) {
                nv21[base++] = vb.get(row * vRow + col * vPix);
                nv21[base++] = ub.get(row * uRow + col * uPix);
            }
        }
        return nv21;
    }

    private static byte[] rotateJpeg(byte[] jpeg, int degrees) {
        try {
            android.graphics.Bitmap bmp = android.graphics.BitmapFactory
                    .decodeByteArray(jpeg, 0, jpeg.length);
            if (bmp == null) return null;
            android.graphics.Matrix m = new android.graphics.Matrix();
            m.postRotate(degrees);
            android.graphics.Bitmap r = android.graphics.Bitmap.createBitmap(
                    bmp, 0, 0, bmp.getWidth(), bmp.getHeight(), m, true);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            r.compress(android.graphics.Bitmap.CompressFormat.JPEG, 70, out);
            return out.toByteArray();
        } catch (Exception e) {
            return null;
        }
    }
}
