package com.mujahid.myagent;

import android.app.Activity;
import android.app.KeyguardManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.Gravity;
import android.widget.TextView;

/**
 * v97: scheduled message ke waqt screen jagane wali activity.
 *
 * - Screen ON karti hai aur lock screen ke UPAR nazar aati hai.
 * - Swipe lock ho to requestDismissKeyguard se khud khul jata hai
 *   → message usi pal chala jata hai.
 * - Secure lock (PIN/pattern) Android kisi app ko khud kholne nahi
 *   deta — activity lock ke upar darkhwast dikhati hai; unlock hote
 *   hi flushDue message foran bhej deta hai.
 */
public class WakeActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true);
            setTurnScreenOn(true);
        }
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km == null || !km.isKeyguardLocked()) {
            flushSoon();
            finish();
            return;
        }
        TextView tv = new TextView(this);
        tv.setText("Ayesha — scheduled WhatsApp message ka waqt ho gaya hai.\n\n"
                + "Phone unlock karo, message usi pal khud chala jayega.");
        tv.setTextSize(18);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(56, 56, 56, 56);
        setContentView(tv);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                km.requestDismissKeyguard(this,
                        new KeyguardManager.KeyguardDismissCallback() {
                            @Override
                            public void onDismissSucceeded() {
                                flushSoon();
                                finish();
                            }

                            @Override
                            public void onDismissError() { }

                            @Override
                            public void onDismissCancelled() { }
                        });
            } catch (Exception ignored) { }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        KeyguardManager km = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        if (km != null && !km.isKeyguardLocked()) {
            flushSoon();
            finish();
        }
    }

    /** Receiver ka daur khatam hone ka halka sa waqfa de kar flush. */
    private void flushSoon() {
        new Handler(getMainLooper()).postDelayed(
                () -> WaSchedReceiver.flushDue(this), 800);
    }
}
