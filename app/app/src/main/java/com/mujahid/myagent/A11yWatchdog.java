package com.mujahid.myagent;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.SystemClock;
import android.provider.Settings;

/**
 * v88 "chowkidar": Accessibility ki sehat ka chowkidar.
 *
 * Boss ki shart: hamesha rehne wali notification NAHI — sirf jab
 * Accessibility asal me band mile, tabhi ek dafa ki khabar.
 *
 * Har ~15 minute ek alarm khud check karta hai ke system ki
 * accessibility list me Ayesha hai ya nahi (phones ki battery-hifazat
 * aksar yahi toggle khud band kar deti hai). Pehli dafa band mile to
 * sirf waqt note hota hai; 10 minute se zyada band rahe to EK
 * notification (English, swipe se hatne wali) — usi me Accessibility
 * settings khulne wala tap aur Battery settings ka button.
 * Drop ka waqt SharedPreferences me save rehta hai (saboot ke liye).
 */
public class A11yWatchdog {

    private static final String PREFS = "a11y_watch";
    private static final int ALARM_ID = 771;
    private static final int NOTIF_ID = 8801;
    private static final String CHANNEL = "ayesha_alerts";
    private static final long INTERVAL = 15 * 60 * 1000L;
    private static final long NOTIFY_AFTER = 10 * 60 * 1000L;

    /** Alarm lagao (idempotent — baar baar bulane se ek hi rehta hai). */
    public static void schedule(Context ctx) {
        try {
            AlarmManager am = (AlarmManager)
                    ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            Intent i = new Intent(ctx, WatchdogReceiver.class);
            PendingIntent pi = PendingIntent.getBroadcast(ctx, ALARM_ID, i,
                    PendingIntent.FLAG_IMMUTABLE
                            | PendingIntent.FLAG_UPDATE_CURRENT);
            am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    SystemClock.elapsedRealtime() + INTERVAL, INTERVAL, pi);
        } catch (Exception ignored) { }
    }

    /** System ki enabled-accessibility list me Ayesha hai? */
    public static boolean settingEnabled(Context ctx) {
        try {
            String list = Settings.Secure.getString(
                    ctx.getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
            return list != null
                    && list.contains(ctx.getPackageName() + "/");
        } catch (Exception e) {
            return false;
        }
    }

    /** A11yService ke unbind/destroy se — drop ka waqt foran note. */
    public static void recordDrop(Context ctx) {
        try {
            SharedPreferences p =
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            SharedPreferences.Editor e = p.edit()
                    .putLong("last_drop", now);
            if (p.getLong("down_since", 0) == 0)
                e.putLong("down_since", now);
            e.apply();
        } catch (Exception ignored) { }
    }

    /** Watchdog ka check — WatchdogReceiver har alarm pe bulata hai. */
    public static void check(Context ctx) {
        try {
            SharedPreferences p =
                    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            long now = System.currentTimeMillis();
            if (settingEnabled(ctx)) {
                // Sehat wapas — purana drop record saaf, waqt mehfooz
                if (p.getLong("down_since", 0) != 0) {
                    p.edit()
                            .putLong("last_drop",
                                    p.getLong("down_since", 0))
                            .remove("down_since")
                            .putBoolean("notified", false)
                            .apply();
                    Diag.event("watchdog: a11y theek hai (wapas ON)");
                }
                return;
            }
            long down = p.getLong("down_since", 0);
            if (down == 0) {
                p.edit().putLong("down_since", now)
                        .putLong("last_drop", now).apply();
                Diag.event("watchdog: a11y BAND mili (pehli dafa)");
                return;
            }
            if (now - down >= NOTIFY_AFTER
                    && !p.getBoolean("notified", false)) {
                notifyDown(ctx);
                p.edit().putBoolean("notified", true).apply();
                Diag.event("watchdog: a11y band ki notification bheji");
            }
        } catch (Exception ignored) { }
    }

    private static void notifyDown(Context ctx) {
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(CHANNEL,
                    "Ayesha alerts", NotificationManager.IMPORTANCE_DEFAULT);
            nm.createNotificationChannel(ch);

            Intent acc = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openAcc = PendingIntent.getActivity(ctx, 1, acc,
                    PendingIntent.FLAG_IMMUTABLE
                            | PendingIntent.FLAG_UPDATE_CURRENT);

            Intent bat = new Intent(
                    Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            PendingIntent openBat = PendingIntent.getActivity(ctx, 2, bat,
                    PendingIntent.FLAG_IMMUTABLE
                            | PendingIntent.FLAG_UPDATE_CURRENT);

            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_alert)
                    .setContentTitle("Ayesha: Accessibility is OFF")
                    .setContentText("Ayesha can't see or tap the screen. "
                            + "Tap to re-enable her.")
                    .setAutoCancel(true)
                    .setContentIntent(openAcc)
                    .addAction(new Notification.Action.Builder(
                            android.R.drawable.ic_menu_preferences,
                            "Battery Fix", openBat).build())
                    .build();
            nm.notify(NOTIF_ID, n);
        } catch (Exception ignored) { }
    }
}
