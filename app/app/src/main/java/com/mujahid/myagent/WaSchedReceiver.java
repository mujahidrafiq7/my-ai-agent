package com.mujahid.myagent;

import android.app.KeyguardManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * v97 "1.0-tez": scheduled WhatsApp messages — SEEDHA FIRING, koi hold nahi.
 *
 * v96 ki ghalti (us ki test report): Live guftagu ki "hifazat" ki wajah
 * se message ruk gaya aur session khatam hone par koi trigger hi nahi
 * tha — message mar gaya. v97 me:
 *  - Live protection KHATAM: guftagu ho ya call, waqt par message
 *    foran jata hai. Bhejne wala (WhatsAppAction) Live session se
 *    bilkul alag hai — session crash/cut nahi hota.
 *  - Lock ho to WakeActivity screen jagati hai; swipe lock khud
 *    dismiss ho jata hai aur message usi pal jata hai. Secure lock
 *    (PIN/pattern) Android kisi app ko khud kholne nahi deta — wahan
 *    screen jagti hai, unlock ki darkhwast samne aati hai, aur
 *    unlock hote hi (USER_PRESENT) message foran jata hai; sath
 *    retry alarms bhi lage rehte hain taake pending kabhi mare na.
 *  - Nakam bhejna (button na mila waghera) → 60s baad dobara koshish,
 *    kul 3 dafa; phir aakhri report.
 *  - Triggers: exact alarm, BOOT, USER_PRESENT, conversation END
 *    (VoiceService.stopConversation), app khulna (MainActivity) —
 *    pending kahin atak nahi sakta.
 */
public class WaSchedReceiver extends BroadcastReceiver {

    public static final String ACTION_FIRE = "com.mujahid.myagent.WA_SCHED_FIRE";
    private static final String CHANNEL = "ayesha_wa_sched";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final int MAX_SEND_ATTEMPTS = 3;
    private static final int MAX_LOCK_TRIES = 40; // ~1 ghanta retry, phir sirf unlock/app trigger

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            WaSchedStore.rescheduleAll(ctx);
        }
        flushDue(ctx);
    }

    /** Due messages ek ek karke — koi hold nahi, seedha firing. */
    public static void flushDue(final Context ctx) {
        final List<WaSchedStore.Entry> due = WaSchedStore.dueList(ctx);
        if (due.isEmpty()) return;
        if (!RUNNING.compareAndSet(false, true)) return; // daur chal raha hai
        final Context app = ctx.getApplicationContext();
        PowerManager pm = (PowerManager) app.getSystemService(Context.POWER_SERVICE);
        final PowerManager.WakeLock wl = pm == null ? null
                : pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "ayesha:wa-sched");
        if (wl != null) {
            try { wl.acquire(240_000L); } catch (Exception ignored) { }
        }
        new Thread(() -> {
            try {
                for (WaSchedStore.Entry e : due) {
                    // 1) Lock hai? Pehle screen jagao / lock khulwao
                    KeyguardManager km = (KeyguardManager)
                            app.getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) {
                        int tries = WaSchedStore.bumpTries(app, e.id);
                        // WakeActivity: screen ON + swipe lock dismiss;
                        // secure lock par unlock ki darkhwast samne
                        Intent wi = new Intent(app, WakeActivity.class);
                        wi.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ForegroundOpen.open(app, wi, "Ayesha — message ka waqt ho gaya");
                        if (tries == 1) {
                            notifyResult(app, e, "WhatsApp message ka waqt ho gaya",
                                    e.contact + " ko message bhejna hai — phone "
                                            + "unlock hote hi foran chala jayega.");
                        }
                        if (tries < MAX_LOCK_TRIES) {
                            WaSchedStore.scheduleRetry(app, e.id, 60_000L);
                        }
                        break; // lock sab entries ke liye ek hai
                    }
                    // 2) SEEDHA BHEJO — Live ho ya kuch bhi, koi intezar nahi
                    WhatsAppAction.Result r =
                            WhatsAppAction.sendToNumber(app, e.number, e.text);
                    boolean sent = r != null && r.reply != null
                            && r.reply.startsWith("Bhej diya");
                    try { A11yService.goHome(); } catch (Exception ignored) { }
                    if (sent) {
                        WaSchedStore.remove(app, e.id);
                        notifyResult(app, e, "WhatsApp message chala gaya",
                                e.contact + " ko: '" + e.text + "'");
                    } else {
                        int att = WaSchedStore.bumpAtt(app, e.id);
                        if (att < MAX_SEND_ATTEMPTS) {
                            WaSchedStore.scheduleRetry(app, e.id, 60_000L);
                            notifyResult(app, e, "WhatsApp message — dobara koshish hogi",
                                    e.contact + " ko pehli koshish me nahi gaya ("
                                            + (r == null ? "" : r.reply)
                                            + ") — 1 minute me khud dobara bhejungi.");
                        } else {
                            WaSchedStore.remove(app, e.id);
                            notifyResult(app, e, "WhatsApp message nahi gaya",
                                    e.contact + " ko 3 koshishon ke baad bhi nahi "
                                            + "gaya — " + (r == null ? "" : r.reply));
                        }
                    }
                    sleep(2000); // agle se pehle saans
                }
            } catch (Exception ignored) {
            } finally {
                RUNNING.set(false);
                if (wl != null && wl.isHeld()) {
                    try { wl.release(); } catch (Exception ignored) { }
                }
            }
        }, "wa-sched").start();
    }

    private static void notifyResult(Context ctx, WaSchedStore.Entry e,
                                     String title, String body) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    "Ayesha WhatsApp Schedule", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);
            Intent open = new Intent(ctx, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(ctx, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(android.R.drawable.stat_notify_chat)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setStyle(new Notification.BigTextStyle().bigText(body))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setOnlyAlertOnce(true)
                    .build();
            nm.notify(4500 + (int) (e.id % 400), n);
        } catch (Exception ignored) { }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
