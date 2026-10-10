package com.mujahid.myagent;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

/**
 * v54 "Orb": Floating Orb — doosri apps ke upar chhota gol button.
 * Dabao to Ayesha khul jati hai, ungli se kahin bhi sarka sakte ho.
 * Settings me toggle + "Display over other apps" permission chahiye.
 */
public class OrbService extends Service {

    private WindowManager wm;
    private View orb;
    private WindowManager.LayoutParams params;

    @Override
    public void onCreate() {
        super.onCreate();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);

        int accent = Themes.get(this)[4];
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(accent);

        orb = new View(this);
        orb.setBackground(bg);

        // Tap = kholo, drag = sarkao
        orb.setOnTouchListener(new View.OnTouchListener() {
            int startX, startY;
            float downX, downY;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = params.x;
                        startY = params.y;
                        downX = e.getRawX();
                        downY = e.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + (int) (e.getRawX() - downX);
                        params.y = startY + (int) (e.getRawY() - downY);
                        wm.updateViewLayout(orb, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        float dx = e.getRawX() - downX;
                        float dy = e.getRawY() - downY;
                        if (dx * dx + dy * dy < 100) { // ungli hili nahi = tap
                            Intent i = new Intent(OrbService.this,
                                    MainActivity.class);
                            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                                    | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(i);
                        }
                        return true;
                }
                return false;
            }
        });

        int size = (int) (56 * getResources().getDisplayMetrics().density);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(size, size, type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 300;
        wm.addView(orb, params);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (wm != null && orb != null) {
            try { wm.removeView(orb); } catch (Exception ignored) { }
            orb = null;
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }
}
