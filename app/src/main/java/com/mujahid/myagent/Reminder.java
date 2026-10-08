package com.mujahid.myagent;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v39 "1.0-reminder" — "yaad dilana", chhota aur simple.
 *
 * Sunna:  "10 minute baad yaad dilana" / "shaam 5 baje yaad dilana ke dawai leni hai"
 * Kaam:   AlarmManager.setExactAndAllowWhileIdle + save (reboot pe dobara schedule)
 * Fire:   ReminderReceiver → notification (sound) + uski awaz (best effort)
 *
 * Note: "kal subah" v39 me NAHI — chhota rakho, baad me.
 */
public class Reminder {

    public static final String CH_ID = "ayesha_reminders";
    private static final String PREFS = "reminders";

    /** Parse ka natija. */
    public static class Parsed {
        public long atMillis;
        public String message;      // yaad dilane wali baat
        public String confirmText;  // foran boli jane wali tasdeeq
    }

    // ---- number words: Roman + Urdu ----

    private static final Map<String, Integer> NUMS = new HashMap<>();
    static {
        String[][] w = {
                {"ek", "1"}, {"aik", "1"}, {"ایک", "1"},
                {"do", "2"}, {"دو", "2"},
                {"teen", "3"}, {"tin", "3"}, {"تین", "3"},
                {"char", "4"}, {"chaar", "4"}, {"چار", "4"},
                {"paanch", "5"}, {"panch", "5"}, {"پانچ", "5"},
                {"che", "6"}, {"chhe", "6"}, {"چھ", "6"},
                {"saat", "7"}, {"سات", "7"},
                {"aath", "8"}, {"آٹھ", "8"},
                {"nau", "9"}, {"نو", "9"},
                {"das", "10"}, {"دس", "10"},
                {"gyarah", "11"}, {"گیارہ", "11"},
                {"barah", "12"}, {"بارہ", "12"},
                {"bees", "20"}, {"بیس", "20"},
                {"tees", "30"}, {"تیس", "30"},
                {"pachaas", "50"}, {"پچاس", "50"},
        };
        for (String[] p : w) NUMS.put(p[0], Integer.parseInt(p[1]));
    }

    private static Integer toNumber(String w) {
        if (w == null) return null;
        w = w.trim();
        try {
            return Integer.parseInt(w);
        } catch (NumberFormatException ignored) { }
        return NUMS.get(w);
    }

    // ---- text normalize: chhota + Urdu digits → ASCII ----

    private static String norm(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c >= '۰' && c <= '۹') b.append((char) ('0' + (c - '۰')));
            else if (c >= '٠' && c <= '٩') b.append((char) ('0' + (c - '٠')));
            else b.append(Character.toLowerCase(c));
        }
        return b.toString().replaceAll("\\s+", " ").trim();
    }

    // ---- trigger ----

    private static final String[] TRIGGERS = {
            "yaad dila dena", "yaad dila do", "yaad dilana",
            "remind me", "reminder laga",
    };

    private static int indexOfTrigger(String t) {
        int best = -1;
        for (String tr : TRIGGERS) {
            int i = t.indexOf(tr);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    private static String stripTrigger(String after) {
        for (String tr : TRIGGERS) {
            if (after.startsWith(tr)) {
                after = after.substring(tr.length()).trim();
                break;
            }
        }
        // "ke/kay/ki" hatao
        return after.replaceFirst("^(ke|kay|ki|ko)\\s+", "").trim();
    }

    // ---- time parse ----

    private static class TimeHit {
        long at;
        String phrase;
    }

    private static TimeHit parseTime(String t) {
        long now = System.currentTimeMillis();

        // 1) relative: "10 minute baad", "do ghante baad", "30 second baad"
        Matcher m = Pattern.compile(
                "(\\d+|[a-z\\u0600-\\u06FF]+)\\s*(seconds?|sec|minutes?|mints?|mins?|ghant[ae]|hours?)\\b")
                .matcher(t);
        while (m.find()) {
            Integer n = toNumber(m.group(1));
            if (n == null || n <= 0) continue;
            String u = m.group(2);
            long unit = u.startsWith("sec") ? 1000L
                    : (u.startsWith("ghant") || u.startsWith("hour")) ? 3600_000L
                    : 60_000L;
            TimeHit h = new TimeHit();
            h.at = now + n * unit;
            h.phrase = m.group(0).trim();
            // "baad" ho ya na ho — unit hi kaafi hai
            return h;
        }

        // 2) absolute: "5 baje", "shaam 7 baje", "subah 6 baje"
        m = Pattern.compile(
                "(\\d+|[a-z\\u0600-\\u06FF]+)\\s*baj[eay]\\b").matcher(t);
        while (m.find()) {
            Integer h = toNumber(m.group(1));
            if (h == null || h < 1 || h > 12) continue;
            int hour = h;
            if (t.contains("shaam") || t.contains("raat")) {
                if (hour < 12) hour += 12;
            } else if (t.contains("dopahar") || t.contains("sepehar")) {
                // 12-4 dopahar — jaisa hai waisa
            }
            // "subah"/sawere → jaisa hai waisa
            Calendar c = Calendar.getInstance();
            c.set(Calendar.HOUR_OF_DAY, hour % 24);
            c.set(Calendar.MINUTE, 0);
            c.set(Calendar.SECOND, 0);
            c.set(Calendar.MILLISECOND, 0);
            long at = c.getTimeInMillis();
            if (at <= now) at += 24L * 3600_000L; // guzar gaya → kal
            TimeHit hit = new TimeHit();
            hit.at = at;
            hit.phrase = m.group(0).trim();
            // period lafz bhi jor do taake confirm me aaye ("shaam 7 baje")
            if (t.contains("shaam")) hit.phrase = "shaam " + hit.phrase;
            else if (t.contains("raat")) hit.phrase = "raat " + hit.phrase;
            else if (t.contains("subah") || t.contains("sawere")) hit.phrase = "subah " + hit.phrase;
            return hit;
        }
        return null;
    }

    /** "yaad dilana" jaisa hukm hai? Parse karo, warna null. */
    public static Parsed parse(String text) {
        if (text == null) return null;
        String t = norm(text);
        int trig = indexOfTrigger(t);
        if (trig < 0) return null;

        String after = stripTrigger(t.substring(trig).trim());

        TimeHit th = parseTime(t);
        if (th == null) return null;

        Parsed p = new Parsed();
        p.atMillis = th.at;
        p.message = after.isEmpty() ? "yaad dilane ka waqt ho gaya" : after;
        p.confirmText = "Theek hai boss, " + th.phrase + " yaad dila dungi.";
        return p;
    }

    // ---- schedule ----

    /**
     * Reminder lagao. null = OK; warna user ko batane wali wajah.
     */
    public static String schedule(Context ctx, long atMillis, String message) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return "Alarm system nahi mila boss.";
        if (atMillis <= System.currentTimeMillis() + 5000)
            return "Wo waqt to guzar gaya boss!";
        // Android 12+: exact alarm ki ijazat
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            return "Boss, Settings me exact alarm ki permission deni hogi — "
                    + "warna waqt pe yaad nahi dila paungi.";
        }
        // Android 13+: notification ki ijazat
        if (Build.VERSION.SDK_INT >= 33 && ctx.checkSelfPermission(
                Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            return "Boss, notification ki permission do taake yaad dila sakun.";
        }

        long id = nextId(ctx);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis,
                    makePendingIntent(ctx, id, message));
        } catch (SecurityException se) {
            return "Exact alarm ki permission nahi mili boss.";
        }
        save(ctx, id, atMillis, message);
        return null;
    }

    private static PendingIntent makePendingIntent(Context ctx, long id, String msg) {
        Intent i = new Intent(ctx, ReminderReceiver.class)
                .setAction(ReminderReceiver.ACTION_FIRE)
                .putExtra("id", id)
                .putExtra("msg", msg);
        return PendingIntent.getBroadcast(ctx, (int) (id % 100000), i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    // ---- storage (reboot-safe) ----

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static long nextId(Context ctx) {
        SharedPreferences p = prefs(ctx);
        long id = p.getLong("last_id", 0) + 1;
        p.edit().putLong("last_id", id).apply();
        return id;
    }

    private static void save(Context ctx, long id, long at, String msg) {
        try {
            SharedPreferences p = prefs(ctx);
            JSONArray arr = new JSONArray(p.getString("list", "[]"));
            arr.put(new JSONObject().put("id", id).put("at", at).put("msg", msg));
            p.edit().putString("list", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Fire hone ke baad list se hatao. */
    public static void remove(Context ctx, long id) {
        try {
            SharedPreferences p = prefs(ctx);
            JSONArray arr = new JSONArray(p.getString("list", "[]"));
            JSONArray keep = new JSONArray();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                if (o.optLong("id", -1) != id) keep.put(o);
            }
            p.edit().putString("list", keep.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Reboot ke baad bache hue reminders dobara lagao. */
    public static void rescheduleAll(Context ctx) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) return;
            SharedPreferences p = prefs(ctx);
            JSONArray arr = new JSONArray(p.getString("list", "[]"));
            JSONArray keep = new JSONArray();
            long now = System.currentTimeMillis();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                long at = o.optLong("at", 0);
                if (at <= now) continue; // guzar gaya — chhoro
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at,
                            makePendingIntent(ctx, o.getLong("id"), o.optString("msg", "")));
                    keep.put(o);
                } catch (Exception ignored) { }
            }
            p.edit().putString("list", keep.toString()).apply();
        } catch (Exception ignored) { }
    }

    // ---- notification ----

    public static void ensureChannel(Context ctx) {
        if (Build.VERSION.SDK_INT < 26) return;
        try {
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            NotificationChannel ch = new NotificationChannel(
                    CH_ID, "Reminders", NotificationManager.IMPORTANCE_HIGH);
            ch.setDescription("Ayesha ki yaad-dahani");
            nm.createNotificationChannel(ch);
        } catch (Exception ignored) { }
    }

    /** Fire hone pe: tez notification (sound ke saath). */
    public static void showNotification(Context ctx, String msg) {
        try {
            ensureChannel(ctx);
            NotificationManager nm = (NotificationManager)
                    ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm == null) return;
            Intent app = new Intent(ctx, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(ctx, 7, app,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(ctx, CH_ID)
                    .setContentTitle("⏰ Ayesha — Yaad dilana")
                    .setContentText(msg)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .setDefaults(Notification.DEFAULT_ALL)
                    .build();
            nm.notify((int) (System.currentTimeMillis() % 100000), n);
        } catch (Exception ignored) { }
    }
}
