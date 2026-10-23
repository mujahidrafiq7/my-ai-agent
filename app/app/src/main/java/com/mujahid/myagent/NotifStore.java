package com.mujahid.myagent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * v94 "1.0-khabar": phone ki aane wali notifications ka store.
 *
 * NotifListener har nayi notification yahan rakhta hai (aakhri 40).
 * Tools (get_notifications waghera) yahin se parhte hain. Sensitive
 * (OTP/bank) notifications ka text yahan MASK hota hai — asal text
 * store hi nahi hota (us ki manzoor-shuda safety tajweez).
 */
public final class NotifStore {

    public static final class Entry {
        public String key;      // StatusBarNotification key (reply/open ke liye)
        public String pkg;
        public String app;      // app ka label (WhatsApp, Gmail...)
        public String title;    // sender / subject
        public String text;     // message body (sensitive ho to masked)
        public long time;
        public boolean reply;   // direct-reply (RemoteInput) action hai
        public boolean sensitive;
        public String category; // Notification.CATEGORY_*
    }

    private static final int MAX = 40;
    private static final String PREFS = "notif_store";
    private static final String KEY = "entries";
    private static final List<Entry> ENTRIES = new ArrayList<>();
    private static boolean loaded = false;

    private NotifStore() {}

    /** OTP / bank jaisi hifazati pehchan — text mask karne ke liye. */
    public static boolean isSensitive(String title, String text) {
        String s = ((title == null ? "" : title) + " " + (text == null ? "" : text))
                .toLowerCase(Locale.US);
        String[] words = {"otp", "one-time", "one time password", "verification code",
                "security code", "pin code", "bank", "debit", "credit card",
                "transaction alert", "transaction of rs", "a/c ", "account alert"};
        for (String w : words) if (s.contains(w)) return true;
        return false;
    }

    public static synchronized void add(Context ctx, Entry e) {
        load(ctx);
        // purani same-key entry hata do (update wali notification)
        for (int i = ENTRIES.size() - 1; i >= 0; i--) {
            if (ENTRIES.get(i).key != null && ENTRIES.get(i).key.equals(e.key)) {
                ENTRIES.remove(i);
            }
        }
        ENTRIES.add(0, e);
        while (ENTRIES.size() > MAX) ENTRIES.remove(ENTRIES.size() - 1);
        save(ctx);
    }

    public static synchronized List<Entry> list(Context ctx) {
        load(ctx);
        return new ArrayList<>(ENTRIES);
    }

    /** Tools ke liye tayyar list — sab se nayi pehle. */
    public static synchronized String listText(Context ctx, int limit) {
        List<Entry> l = list(ctx);
        if (l.isEmpty()) return "Abhi tak koi notification nahi aaya (ya Notification Access band hai).";
        StringBuilder sb = new StringBuilder();
        int n = Math.min(limit, l.size());
        for (int i = 0; i < n; i++) {
            Entry e = l.get(i);
            sb.append(i + 1).append(". ").append(e.app);
            if (e.title != null && !e.title.isEmpty()) sb.append(" — ").append(e.title);
            sb.append(": ").append(e.sensitive ? "(protected — content chhupaya gaya hai)" : trim(e.text, 120));
            sb.append(" [").append(ago(e.time)).append("]");
            if (e.reply && !e.sensitive) sb.append(" [direct reply ho sakta hai]");
            if (i < n - 1) sb.append("\n");
        }
        return sb.toString();
    }

    /** Reply/open ke liye sab se nayi matching entry (active hona NotifListener check karega). */
    public static synchronized Entry find(Context ctx, String appHint, String senderHint,
                                          boolean needReply) {
        List<Entry> l = list(ctx);
        String a = appHint == null ? "" : appHint.toLowerCase(Locale.US);
        String s = senderHint == null ? "" : senderHint.toLowerCase(Locale.US);
        for (Entry e : l) {
            if (e.sensitive) continue;
            if (needReply && !e.reply) continue;
            if (!a.isEmpty() && (e.app == null || !e.app.toLowerCase(Locale.US).contains(a))) continue;
            if (!s.isEmpty() && (e.title == null || !e.title.toLowerCase(Locale.US).contains(s))) continue;
            return e;
        }
        return null;
    }

    static String trim(String s, int max) {
        if (s == null) return "";
        s = s.replace('\n', ' ').trim();
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    static String ago(long t) {
        long mins = Math.max(0, (System.currentTimeMillis() - t) / 60000);
        if (mins < 1) return "abhi";
        if (mins < 60) return mins + " min pehle";
        long h = mins / 60;
        if (h < 24) return h + " ghante pehle";
        return (h / 24) + " din pehle";
    }

    // ---------- persistence (SharedPreferences, JSON) ----------

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
                e.key = o.optString("k");
                e.pkg = o.optString("p");
                e.app = o.optString("a");
                e.title = o.optString("t");
                e.text = o.optString("x");
                e.time = o.optLong("tm");
                e.reply = o.optBoolean("r");
                e.sensitive = o.optBoolean("s");
                e.category = o.optString("c");
                ENTRIES.add(e);
            }
        } catch (Exception ignored) { }
    }

    private static void save(Context ctx) {
        try {
            JSONArray arr = new JSONArray();
            for (Entry e : ENTRIES) {
                JSONObject o = new JSONObject();
                o.put("k", e.key);
                o.put("p", e.pkg);
                o.put("a", e.app);
                o.put("t", e.title);
                o.put("x", e.text);
                o.put("tm", e.time);
                o.put("r", e.reply);
                o.put("s", e.sensitive);
                o.put("c", e.category);
                arr.put(o);
            }
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }
}
