package com.mujahid.myagent;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

/**
 * v96 "1.0-waqt": WhatsApp scheduled messages ki store.
 *
 * Reminder wale aazmaye hue pattern par: SharedPreferences me JSON,
 * har entry ka apna exact alarm (AlarmManager), reboot par reschedule.
 * Contact ka number SCHEDULE karte waqt hi resolve hota hai — waqt
 * par galati ka chance khatam.
 */
public final class WaSchedStore {

    public static final class Entry {
        public long id;
        public String contact; // jaisa naam us ne bola
        public String number;  // normalized (92...)
        public String text;
        public long at;
        public int att;   // v97: bhejne ki nakam koshishon ki ginti
        public int tries; // v97: lock/busy ki wajah se ruke hue chakkar
    }

    private static final String PREFS = "wa_sched_store";
    private static final String KEY = "entries";
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean loaded = false;

    private WaSchedStore() {}

    // ---------- CRUD ----------

    /** Schedule karo. Ghalti ho to error text, warna null. */
    public static synchronized String add(Context ctx, String contact, String number,
                                          String text, long at) {
        load(ctx);
        if (at <= System.currentTimeMillis() + 5000) return "Wo waqt to guzar gaya boss!";
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return "Alarm system nahi mila boss.";
        if (Build.VERSION.SDK_INT >= 31 && !am.canScheduleExactAlarms()) {
            return "Boss, Settings me exact alarm ki permission deni hogi — "
                    + "warna message waqt par nahi ja payega.";
        }
        Entry e = new Entry();
        e.id = System.currentTimeMillis();
        e.contact = contact;
        e.number = number;
        e.text = text;
        e.at = at;
        ENTRIES.add(e);
        save(ctx);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(ctx, e.id));
        } catch (SecurityException se) {
            ENTRIES.remove(e);
            save(ctx);
            return "Exact alarm ki permission nahi mili boss.";
        }
        return null;
    }

    public static synchronized List<Entry> list(Context ctx) {
        load(ctx);
        List<Entry> out = new ArrayList<>(ENTRIES);
        out.sort((x, y) -> Long.compare(x.at, y.at));
        return out;
    }

    /** Waqt aa gaya ho jin ka (pending) — purane pehle. */
    public static synchronized List<Entry> dueList(Context ctx) {
        List<Entry> out = new ArrayList<>();
        long now = System.currentTimeMillis();
        for (Entry e : list(ctx)) if (e.at <= now) out.add(e);
        return out;
    }

    public static synchronized void remove(Context ctx, long id) {
        load(ctx);
        cancelAlarm(ctx, id);
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (ENTRIES.get(i).id == id) ENTRIES.remove(i);
        }
        save(ctx);
    }

    /** Naam/message ke hisse se match karke cancel — jitne mile sab. */
    public static synchronized List<Entry> cancelByMatch(Context ctx, String match) {
        String m = match == null ? "" : match.toLowerCase(Locale.US).trim();
        List<Entry> removed = new ArrayList<>();
        if (m.isEmpty()) return removed;
        for (Entry e : list(ctx)) {
            if (e.contact.toLowerCase(Locale.US).contains(m)
                    || e.text.toLowerCase(Locale.US).contains(m)) {
                removed.add(e);
            }
        }
        for (Entry e : removed) remove(ctx, e.id);
        return removed;
    }

    /** Tool ke liye tayyar list. */
    public static synchronized String listText(Context ctx) {
        List<Entry> l = list(ctx);
        if (l.isEmpty()) return "Koi WhatsApp message schedule nahi hai boss.";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < l.size(); i++) {
            Entry e = l.get(i);
            sb.append(i + 1).append(". ").append(e.contact)
                    .append(" (").append(tail(e.number)).append(") ko ")
                    .append(timeText(e.at)).append(": '").append(e.text).append("'");
            if (i < l.size() - 1) sb.append("\n");
        }
        return sb.toString();
    }

    // ---------- alarms ----------

    private static PendingIntent pending(Context ctx, long id) {
        Intent i = new Intent(ctx, WaSchedReceiver.class)
                .setAction(WaSchedReceiver.ACTION_FIRE)
                .putExtra("id", id);
        return PendingIntent.getBroadcast(ctx, (int) (id % 2000000000),
                i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private static void cancelAlarm(Context ctx, long id) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am != null) am.cancel(pending(ctx, id));
        } catch (Exception ignored) { }
    }

    /** v97: nakam koshish ki ginti barhao (retry faisle ke liye). */
    public static synchronized int bumpAtt(Context ctx, long id) {
        load(ctx);
        for (Entry e : ENTRIES) {
            if (e.id == id) { e.att++; save(ctx); return e.att; }
        }
        return 0;
    }

    /** v97: lock ki wajah se rukne ki ginti barhao. */
    public static synchronized int bumpTries(Context ctx, long id) {
        load(ctx);
        for (Entry e : ENTRIES) {
            if (e.id == id) { e.tries++; save(ctx); return e.tries; }
        }
        return 0;
    }

    /** v97: is entry ke liye thodi dair baad dobara firing ka alarm. */
    public static synchronized void scheduleRetry(Context ctx, long id, long delayMs) {
        try {
            AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            if (am == null) return;
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP,
                    System.currentTimeMillis() + delayMs, pending(ctx, id));
        } catch (Exception ignored) { }
    }

    /** Reboot ke baad: aane wale waqt walon ko dobara alarm lagao. */
    public static synchronized void rescheduleAll(Context ctx) {
        load(ctx);
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        long now = System.currentTimeMillis();
        for (Entry e : ENTRIES) {
            if (e.at > now) {
                try {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, e.at, pending(ctx, e.id));
                } catch (Exception ignored) { }
            }
        }
    }

    // ---------- helpers ----------

    public static String tail(String number) {
        if (number == null) return "";
        return number.length() > 4 ? "..." + number.substring(number.length() - 4) : number;
    }

    public static String timeText(long at) {
        Calendar c = Calendar.getInstance();
        c.setTimeInMillis(at);
        Calendar now = Calendar.getInstance();
        Calendar tom = Calendar.getInstance();
        tom.add(Calendar.DAY_OF_YEAR, 1);
        String hm = new SimpleDateFormat("h:mm a", Locale.US).format(c.getTime());
        if (c.get(Calendar.YEAR) == now.get(Calendar.YEAR)
                && c.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR)) {
            return "aaj " + hm;
        }
        if (c.get(Calendar.YEAR) == tom.get(Calendar.YEAR)
                && c.get(Calendar.DAY_OF_YEAR) == tom.get(Calendar.DAY_OF_YEAR)) {
            return "kal " + hm;
        }
        return new SimpleDateFormat("d MMM, h:mm a", Locale.US).format(c.getTime());
    }

    private static void load(Context ctx) {
        if (loaded) return;
        loaded = true;
        try {
            SharedPreferences sp = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            String raw = sp.getString(KEY, "");
            if (raw == null || raw.isEmpty()) return;
            JSONArray arr = new JSONArray(raw);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.optJSONObject(i);
                if (o == null) continue;
                Entry e = new Entry();
                e.id = o.optLong("id");
                e.contact = o.optString("c");
                e.number = o.optString("n");
                e.text = o.optString("t");
                e.at = o.optLong("at");
                e.att = o.optInt("att");
                e.tries = o.optInt("tr");
                ENTRIES.add(e);
            }
        } catch (Exception ignored) { }
    }

    private static void save(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : ENTRIES) {
                JSONObject o = new JSONObject();
                o.put("id", e.id);
                o.put("c", e.contact);
                o.put("n", e.number);
                o.put("t", e.text);
                o.put("at", e.at);
                o.put("att", e.att);
                o.put("tr", e.tries);
                arr.put(o);
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }
}
