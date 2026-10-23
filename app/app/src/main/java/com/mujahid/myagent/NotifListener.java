package com.mujahid.myagent;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.RemoteInput;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * v94 "1.0-khabar": Universal Notification Reader.
 *
 * Har app ki notification parhta hai (NotificationListenerService).
 *  - Store: NotifStore me (sensitive text masked).
 *  - Live guftagu chal rahi ho to VoiceService ko note → wo khud bolti hai.
 *  - Warna message/email type notification pe Ayesha ki APNI ek hi
 *    (group wali) notification — shor nahi, ek slot me update hoti hai.
 *  - Direct reply: RemoteInput wali action se background me.
 *
 * Ijazat: user ko Settings se "Notification Access" ek dafa ON karna
 * hota hai (Settings screen pe is ka button hai) — Android ka rule.
 */
public class NotifListener extends NotificationListenerService {

    private static final String ALERT_CHANNEL = "ayesha_alerts";
    private static final int ALERT_ID = 4401;

    /** Active notifications (reply/open inhi se hote hain). */
    private static final Map<String, StatusBarNotification> ACTIVE =
            new LinkedHashMap<>();
    private static final Map<String, String> LABEL_CACHE = new HashMap<>();
    public static volatile NotifListener instance;

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @Override
    public void onListenerDisconnected() {
        super.onListenerDisconnected();
        if (instance == this) instance = null;
    }

    // ---------- ijazat ----------

    public static boolean isAccessEnabled(Context ctx) {
        try {
            String flat = Settings.Secure.getString(ctx.getContentResolver(),
                    "enabled_notification_listeners");
            if (flat == null) return false;
            String mine = new ComponentName(ctx, NotifListener.class).flattenToString();
            return flat.contains(mine) || flat.contains(ctx.getPackageName());
        } catch (Exception e) {
            return false;
        }
    }

    public static void openAccessSettings(Context ctx) {
        try {
            Intent i = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            ctx.startActivity(i);
        } catch (Exception ignored) { }
    }

    // ---------- aane wali notification ----------

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        try {
            if (sbn == null) return;
            String pkg = sbn.getPackageName();
            if (pkg == null || pkg.equals(getPackageName())) return; // apni nahi
            Notification n = sbn.getNotification();
            if (n == null) return;
            if ((n.flags & Notification.FLAG_ONGOING_EVENT) != 0) return; // music/download waghera
            if ((n.flags & Notification.FLAG_GROUP_SUMMARY) != 0) return; // summary duplicate

            Bundle ex = n.extras;
            String title = str(ex.getCharSequence(Notification.EXTRA_TITLE));
            String text = str(ex.getCharSequence(Notification.EXTRA_TEXT));
            if (text.isEmpty()) text = str(ex.getCharSequence(Notification.EXTRA_BIG_TEXT));
            if (text.isEmpty() && n.tickerText != null) text = n.tickerText.toString();
            if (title.isEmpty() && text.isEmpty()) return;

            boolean sensitive = NotifStore.isSensitive(title, text);

            NotifStore.Entry e = new NotifStore.Entry();
            e.key = sbn.getKey();
            e.pkg = pkg;
            e.app = appLabel(pkg);
            e.title = title;
            e.text = sensitive ? "" : text;
            e.time = sbn.getPostTime() > 0 ? sbn.getPostTime() : System.currentTimeMillis();
            e.reply = !sensitive && findReplyAction(n) != null;
            e.sensitive = sensitive;
            e.category = n.category == null ? "" : n.category;
            NotifStore.add(this, e);

            synchronized (ACTIVE) {
                ACTIVE.put(sbn.getKey(), sbn);
                while (ACTIVE.size() > 40) {
                    String first = ACTIVE.keySet().iterator().next();
                    ACTIVE.remove(first);
                }
            }
            if (sensitive) return; // OTP/bank: sirf store, na elan na alert

            String summary = e.app + " par " + (title.isEmpty() ? "" : title + " ka ")
                    + "message/notification aaya hai: '" + NotifStore.trim(text, 100) + "'";
            // 1) Live guftagu chal rahi hai? → wo khud pyaar se bataye
            if (VoiceService.tryAnnounceLive(summary)) return;
            // 2) Warna sirf message/email type pe Ayesha ki apni notification
            if (Notification.CATEGORY_MESSAGE.equals(e.category)
                    || Notification.CATEGORY_EMAIL.equals(e.category)
                    || Notification.CATEGORY_SOCIAL.equals(e.category)) {
                postGroupedAlert(e);
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        try {
            synchronized (ACTIVE) { ACTIVE.remove(sbn.getKey()); }
        } catch (Exception ignored) { }
    }

    // ---------- reply / open ----------

    private static Notification.Action findReplyAction(Notification n) {
        if (n.actions == null) return null;
        for (Notification.Action a : n.actions) {
            if (a == null) continue;
            RemoteInput[] ris = a.getRemoteInputs();
            if (ris != null && ris.length > 0) return a;
        }
        return null;
    }

    /** Background direct reply (RemoteInput). Kaamyab ho to true. */
    public static boolean sendReply(NotifStore.Entry e, String text) {
        StatusBarNotification sbn;
        synchronized (ACTIVE) { sbn = ACTIVE.get(e.key); }
        if (sbn == null) return false;
        Notification.Action a = findReplyAction(sbn.getNotification());
        if (a == null || a.actionIntent == null) return false;
        try {
            RemoteInput[] ris = a.getRemoteInputs();
            Intent intent = new Intent();
            Bundle bundle = new Bundle();
            for (RemoteInput ri : ris) bundle.putCharSequence(ri.getResultKey(), text);
            RemoteInput.addResultsToIntent(ris, intent, bundle);
            a.actionIntent.send(sbn.getPackageName() != null ? instance : null,
                    0, intent);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    /** Notification tap karke us ki app kholna. */
    public static boolean openEntry(NotifStore.Entry e) {
        StatusBarNotification sbn;
        synchronized (ACTIVE) { sbn = ACTIVE.get(e.key); }
        if (sbn == null) return false;
        PendingIntent pi = sbn.getNotification().contentIntent;
        if (pi == null) return false;
        try {
            pi.send();
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    // ---------- helpers ----------

    private String appLabel(String pkg) {
        String cached = LABEL_CACHE.get(pkg);
        if (cached != null) return cached;
        String label = pkg;
        try {
            PackageManager pm = getPackageManager();
            ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence cs = pm.getApplicationLabel(ai);
            if (cs != null) label = cs.toString();
        } catch (Exception ignored) { }
        LABEL_CACHE.put(pkg, label);
        return label;
    }

    private static String str(CharSequence cs) {
        return cs == null ? "" : cs.toString().trim();
    }

    /** Ayesha ki apni EK group notification (id fixed — update hoti hai, dher nahi lagta). */
    private void postGroupedAlert(NotifStore.Entry latest) {
        try {
            NotificationManager nm = (NotificationManager)
                    getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(ALERT_CHANNEL,
                    "Ayesha Messages", NotificationManager.IMPORTANCE_DEFAULT);
            ch.setDescription("Naye messages ki khabar");
            nm.createNotificationChannel(ch);

            Intent open = new Intent(this, MainActivity.class);
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            PendingIntent pi = PendingIntent.getActivity(this, 0, open,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

            String line = latest.app + (latest.title.isEmpty() ? "" : " — " + latest.title)
                    + ": " + NotifStore.trim(latest.text, 80);
            Notification notif = new Notification.Builder(this, ALERT_CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_dialog_email)
                    .setContentTitle("Ayesha — naye messages aaye hain")
                    .setContentText(line)
                    .setStyle(new Notification.BigTextStyle().bigText(line
                            + "\nApp khol kar mujh se kaho: 'messages parho'"))
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(ALERT_ID, notif);
        } catch (Exception ignored) { }
    }
}
