package com.mujahid.myagent;

import android.app.Activity;
import android.hardware.Camera;
import android.os.Bundle;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;

import java.lang.ref.WeakReference;

/**
 * Ayesha ka camera — "camera on karo" pe live preview, "band karo" pe off.
 * Purana Camera API (simple, har phone pe chalta hai).
 */
public class CameraActivity extends Activity implements SurfaceHolder.Callback {

    private Camera camera;
    private SurfaceView surfaceView;
    private static WeakReference<CameraActivity> current;

    /** Voice command se bahar se band karo. */
    public static void closeIfOpen() {
        try {
            CameraActivity a = current != null ? current.get() : null;
            if (a != null) {
                a.runOnUiThread(() -> {
                    try { a.finish(); } catch (Exception ignored) { }
                });
            }
        } catch (Exception ignored) { }
    }

    public static boolean isOpen() {
        try {
            CameraActivity a = current != null ? current.get() : null;
            return a != null && !a.isFinishing();
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = new WeakReference<>(this);

        FrameLayout root = new FrameLayout(this);
        surfaceView = new SurfaceView(this);
        surfaceView.getHolder().addCallback(this);
        root.addView(surfaceView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        Button close = new Button(this);
        close.setText("Close Camera");
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.gravity = android.view.Gravity.BOTTOM | android.view.Gravity.CENTER_HORIZONTAL;
        lp.bottomMargin = 48;
        close.setLayoutParams(lp);
        close.setOnClickListener(v -> finish());
        root.addView(close);

        setContentView(root);
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        try {
            camera = Camera.open();
            camera.setDisplayOrientation(90);
            camera.setPreviewDisplay(holder);
            camera.startPreview();
        } catch (Exception e) {
            finish();
        }
    }

    @Override public void surfaceChanged(SurfaceHolder h, int f, int w, int hh) { }
    @Override public void surfaceDestroyed(SurfaceHolder holder) { releaseCam(); }

    private void releaseCam() {
        if (camera != null) {
            try { camera.stopPreview(); } catch (Exception ignored) { }
            try { camera.release(); } catch (Exception ignored) { }
            camera = null;
        }
    }

    @Override
    protected void onPause() {
        releaseCam();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        releaseCam();
        if (current != null && current.get() == this) current.clear();
        super.onDestroy();
    }
}
