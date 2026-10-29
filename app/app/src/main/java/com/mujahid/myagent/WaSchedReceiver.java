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
 * v96 "1.0-waqt": scheduled WhatsApp messages chalane wala receiver.
 *
 * Triggers: exact alarm (ACTION_FIRE), BOOT_COMPLETED (reschedule +
 * guzre hue bhej do), USER_PRESENT (unlock hote hi pending bhej do).
 *
 * Hifazati usool (us ke manzoor-shuda plan):
 *  - Ek waqt me EK hi daur (queue lock) — messages ek ek karke.
 *  - Live guftagu chal rahi ho to intezar (max ~150s), phir bhi busy
 *    ho to pending rakho + notification (screen hijack kabhi nahi).
 *  - Phone locked ho to bhejna hi nahi — pending + notification;
 *    unlock hote hi USER_PRESENT se khud chala jata hai.
 *  - Bhejne ke baad Home screen, aur result ki notification har haal me.
 */
public class WaSchedReceiver extends BroadcastReceiver {

    public static final String ACTION_FIRE = "com.mujahid.myagent.WA_SCHED_FIRE";
    private static final String CHANNEL = "ayesha_wa_sched";
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String action = intent == null ? "" : String.valueOf(intent.getAction());
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
            WaSchedStore.rescheduleAll(ctx);
        }
        // FIRE / BOOT / USER_PRESENT — teeno par due messages check karo
        flushDue(ctx);
    }

    /** Due messages ek ek karke bhejo (background thread + WakeLock). */
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
                    // 1) Live guftagu? Screen hijack nahi — intezar karo
                    int waited = 0;
                    while (VoiceService.isConversationOn() && waited < 150_000) {
                        sleep(5000);
                        waited += 5000;
                    }
                    if (VoiceService.isConversationOn()) {
                        notifyResult(app, e, "WhatsApp message roka gaya",
                                e.contact + " ko message bhejna tha lekin hamari "
                                        + "guftagu chal rahi thi — free hote hi "
                                        + "bhej dungi, schedule mehfooz hai.");
                        break; // baqi bhi abhi rukenge
                    }
                    // 2) Lock? Bhejna mumkin nahi — pending rakho
                    KeyguardManager km = (KeyguardManager)
                            app.getSystemService(Context.KEYGUARD_SERVICE);
                    if (km != null && km.isKeyguardLocked()) {
                        notifyResult(app, e, "WhatsApp message pending hai",
                                e.contact + " ko message bhejna tha lekin phone "
                                        + "locked hai — unlock karte hi foran "
                                        + "bhej dungi.");
                        break; // lock sab ke liye ek hai
                    }
                    // 3) Bhejo — purana azmaya hua WhatsAppAction
                    WhatsAppAction.Result r =
                            WhatsAppAction.sendToNumber(app, e.number, e.text);
                    boolean sent = r != null && r.reply != null
                            && r.reply.startsWith("Bhej diya");
                    WaSchedStore.remove(app, e.id);
                    try { A11yService.goHome(); } catch (Exception ignored) { }
                    if (sent) {
                        notifyResult(app, e, "WhatsApp message chala gaya",
                                e.contact + " ko: '" + e.text + "'");
                    } else {
                        notifyResult(app, e, "WhatsApp message nahi gaya",
                                e.contact + " ko message nahi ja saka — "
                                        + (r == null ? "" : r.reply));
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
                    .build();
            nm.notify(4500 + (int) (e.id % 400), n);
        } catch (Exception ignored) { }
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
