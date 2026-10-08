package com.mujahid.myagent;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

/**
 * App ko SAAMNE kholo — chahe hamari app background me ho ya screen lock ho.
 * - Hamari app foreground me hai → seedha startActivity.
 * - Background me hai → full-screen intent (Android 10+ ki pabandi ka
 *   official hal: alarm/call wali apps isi se saamne ati hain).
 */
public class ForegroundOpen {

    private static final String CH_OPEN = "ayesha_open";
    private static final int OPEN_NOTIFY_ID = 9001;

    public static void open(Context ctx, Intent intent, String label) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        if (isForeground(ctx)) {
            try {
                ctx.startActivity(intent);
                return;
            } catch (Exception ignored) { }
        }
        // Background: full-screen intent se saamne lao
        try {
            PendingIntent pi = PendingIntent.getActivity(ctx,
                    (int) (System.currentTimeMillis() % 100000),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            try {
                nm.createNotificationChannel(new NotificationChannel(
                        CH_OPEN, "Ayesha Open",
                        NotificationManager.IMPORTANCE_HIGH));
            } catch (Exception ignored) { }
            Notification n = new Notification.Builder(ctx, CH_OPEN)
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Ayesha")
                    .setContentText(label)
                    .setCategory(Notification.CATEGORY_CALL)
                    .setFullScreenIntent(pi, true)
                    .setAutoCancel(true)
                    .build();
            nm.notify(OPEN_NOTIFY_ID, n);
            // 5 second baad notification hata do
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try { nm.cancel(OPEN_NOTIFY_ID); } catch (Exception ignored) { }
            }, 5000);
        } catch (Exception ignored) { }
    }

    private static boolean isForeground(Context ctx) {
        try {
            ActivityManager am = (ActivityManager)
                    ctx.getSystemService(Context.ACTIVITY_SERVICE);
            List<ActivityManager.RunningAppProcessInfo> procs =
                    am.getRunningAppProcesses();
            if (procs == null) return false;
            for (ActivityManager.RunningAppProcessInfo p : procs) {
                if (p.processName.equals(ctx.getPackageName())
                        && p.importance
                        == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND)
                    return true;
            }
        } catch (Exception ignored) { }
        return false;
    }
}
