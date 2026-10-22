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
            A11yWatchdog.schedule(ctx); // v88: boot pe chowkidar bhi lagao
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

        // 2) Uski apni awaz — v91: pehle Gemini wali awaz (Aoede),
        //    na ho sake to phone ki offline TTS; CPU ko jagaye rakhne
        //    ke liye wakelock (60s tak)
        final PendingResult pr = goAsync();
        final android.os.PowerManager.WakeLock wl;
        android.os.PowerManager pm = (android.os.PowerManager)
                ctx.getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wl = pm.newWakeLock(
                    android.os.PowerManager.PARTIAL_WAKE_LOCK,
                    "ayesha:reminder-speak");
            try { wl.acquire(60_000L); } catch (Exception ignored) { }
        } else {
            wl = null;
        }
        final Context appCtx = ctx.getApplicationContext();
        new Thread(() -> {
            boolean spoke = false;
            try {
                List<String> keys = Keys.geminiKeys(appCtx);
                if (!keys.isEmpty()) {
                    CountDownLatch latch = new CountDownLatch(1);
                    final boolean[] done = {false};
                    TtsClient.speak(appCtx,
                            "Boss! Waqt ho gaya hai — aap ne kaha tha: "
                                    + say,
                            keys,
                            new TtsClient.Listener() {
                                @Override public void onStatus(String s) { }
                                @Override public void onDone() {
                                    done[0] = true;
                                    latch.countDown();
                                }
                                @Override public void onError(String r) {
                                    latch.countDown();
                                }
                            });
                    latch.await(20, TimeUnit.SECONDS);
                    spoke = done[0];
                }
            } catch (Exception ignored) { }
            if (!spoke) {
                // Fallback: phone ki apni offline awaz (internet baghair)
                try { speakOffline(appCtx, say); }
                catch (Exception ignored) { }
            }
            try { if (wl != null && wl.isHeld()) wl.release(); }
            catch (Exception ignored) { }
            try { pr.finish(); } catch (Exception ignored) { }
        }).start();
    }

    /** v91 fallback: Android TextToSpeech — bol kar rukna, phir band. */
    private void speakOffline(Context ctx, String say)
            throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        final android.speech.tts.TextToSpeech[] box = new android.speech.tts.TextToSpeech[1];
        box[0] = new android.speech.tts.TextToSpeech(ctx, status -> {
            try {
                if (status == android.speech.tts.TextToSpeech.SUCCESS) {
                    android.speech.tts.TextToSpeech t = box[0];
                    java.util.Locale ur = new java.util.Locale("ur");
                    if (t.isLanguageAvailable(ur)
                            >= android.speech.tts.TextToSpeech.LANG_AVAILABLE) {
                        t.setLanguage(ur);
                    }
                    t.setOnUtteranceProgressListener(
                            new android.speech.tts.UtteranceProgressListener() {
                                @Override public void onStart(String id) { }
                                @Override public void onDone(String id) {
                                    latch.countDown();
                                }
                                @Override public void onError(String id) {
                                    latch.countDown();
                                }
                            });
                    t.speak("Boss! Waqt ho gaya hai — aap ne kaha tha: "
                                    + say,
                            android.speech.tts.TextToSpeech.QUEUE_FLUSH,
                            null, "ayesha-reminder");
                } else {
                    latch.countDown();
                }
            } catch (Exception e) {
                latch.countDown();
            }
        });
        latch.await(15, TimeUnit.SECONDS);
        try { box[0].shutdown(); } catch (Exception ignored) { }
    }
}
