package com.mujahid.myagent;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * v39 "1.0-reminder": alarm fire hone pe —
 *  1) tez notification (sound ke saath) — HAMESHA
 *  2) uski apni awaz me yaad dilana — best effort
 *     (Doze/battery-bachat me network ruk sakta hai — Android ka rule)
 *
 * BOOT_COMPLETED pe bache hue reminders dobara schedule karta hai.
 */
public class ReminderReceiver extends BroadcastReceiver {

    public static final String ACTION_FIRE = "com.mujahid.myagent.REMINDER_FIRE";

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (ctx == null || intent == null) return;
        String a = intent.getAction();

        // Phone restart → bache hue reminders dobara lagao
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)) {
            Reminder.rescheduleAll(ctx);
            return;
        }
        if (!ACTION_FIRE.equals(a)) return;

        long id = intent.getLongExtra("id", -1);
        String msg = intent.getStringExtra("msg");
        if (msg == null || msg.trim().isEmpty())
            msg = "yaad dilane ka waqt ho gaya";
        final String say = msg.trim();
        Reminder.remove(ctx, id);

        // 1) Notification — hamesha aayegi
        Reminder.showNotification(ctx, say);

        // 2) Uski awaz — ho saka to (network + app zinda hona zaroori)
        final PendingResult pr = goAsync();
        new Thread(() -> {
            try {
                List<String> keys = Keys.geminiKeys(ctx);
                if (!keys.isEmpty()) {
                    CountDownLatch latch = new CountDownLatch(1);
                    TtsClient.speak(ctx, "Boss! " + say, keys,
                            new TtsClient.Listener() {
                                @Override public void onStatus(String s) { }
                                @Override public void onDone() { latch.countDown(); }
                                @Override public void onError(String r) { latch.countDown(); }
                            });
                    latch.await(45, TimeUnit.SECONDS);
                }
            } catch (Exception ignored) {
            } finally {
                try { pr.finish(); } catch (Exception ignored) { }
            }
        }).start();
    }
}
