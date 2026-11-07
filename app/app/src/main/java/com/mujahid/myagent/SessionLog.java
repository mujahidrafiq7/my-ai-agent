package com.mujahid.myagent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

/**
 * v100 "1.0-control": Session Continuity Log.
 *
 * Har Live session ka record: session_id, start time, heartbeat.
 * Drop ho (network/server) to [SESSION_DROPPED] log, reconnect ho
 * to gap ke sath entry. User poochhe "kya connection toota tha?"
 * to get_session_status tool isi record se SACH batata hai —
 * andaza nahi.
 */
public final class SessionLog {

    private static final String PREFS = "session_log";
    private static final int MAX_EVENTS = 20;

    private static SharedPreferences p(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String fmt(long ts) {
        SimpleDateFormat f = new SimpleDateFormat("d MMM, h:mm:ss a", Locale.ENGLISH);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Karachi"));
        return f.format(new Date(ts));
    }

    public static synchronized void begin(Context ctx) {
        try {
            SharedPreferences sp = p(ctx);
            int id = sp.getInt("seq", 0) + 1;
            long now = System.currentTimeMillis();
            sp.edit().putInt("seq", id).putInt("cur_id", id)
                    .putLong("cur_start", now).putLong("last_beat", now)
                    .putBoolean("active", true).putLong("pending_drop", 0)
                    .apply();
            android.util.Log.d("SessionLog", "Session #" + id + " started at " + fmt(now));
        } catch (Exception ignored) { }
    }

    public static synchronized void end(Context ctx) {
        try {
            p(ctx).edit().putBoolean("active", false).apply();
        } catch (Exception ignored) { }
    }

    /** Stream pump har ~1s bulata hai — andar 10s throttle hai. */
    public static synchronized void heartbeat(Context ctx) {
        try {
            SharedPreferences sp = p(ctx);
            if (!sp.getBoolean("active", false)) return;
            long now = System.currentTimeMillis();
            if (now - sp.getLong("last_beat", 0) < 10_000) return;
            sp.edit().putLong("last_beat", now).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized void noteDrop(Context ctx, String why) {
        try {
            SharedPreferences sp = p(ctx);
            long now = System.currentTimeMillis();
            android.util.Log.d("SessionLog",
                    "[SESSION_DROPPED: reconnect_attempt_at " + fmt(now) + "] " + why);
            if (sp.getLong("pending_drop", 0) == 0) {
                sp.edit().putLong("pending_drop", now).apply();
                addEvent(ctx, "drop", now, why, -1);
            }
        } catch (Exception ignored) { }
    }

    public static synchronized void noteRehandshake(Context ctx) {
        try {
            long now = System.currentTimeMillis();
            android.util.Log.d("SessionLog",
                    "[SESSION_REHANDSHAKE at " + fmt(now) + "] server goAway — seamless");
            if (p(ctx).getLong("pending_drop", 0) == 0) {
                p(ctx).edit().putLong("pending_drop", now).apply();
            }
            addEvent(ctx, "rehandshake", now, "server ne session renew kiya (goAway)", -1);
        } catch (Exception ignored) { }
    }

    public static synchronized void noteReconnect(Context ctx) {
        try {
            SharedPreferences sp = p(ctx);
            long pending = sp.getLong("pending_drop", 0);
            long now = System.currentTimeMillis();
            if (pending > 0) {
                long gapSec = Math.max(1, (now - pending) / 1000);
                sp.edit().putLong("pending_drop", 0)
                        .putLong("last_drop_at", pending)
                        .putLong("last_gap_sec", gapSec).apply();
                addEvent(ctx, "reconnected", now, "", gapSec);
                android.util.Log.d("SessionLog",
                        "Reconnected — gap ~" + gapSec + "s");
            }
            sp.edit().putLong("last_beat", now).apply();
        } catch (Exception ignored) { }
    }

    private static void addEvent(Context ctx, String kind, long at,
                                 String info, long gapSec) {
        try {
            SharedPreferences sp = p(ctx);
            JSONArray arr = new JSONArray(sp.getString("events", "[]"));
            JSONObject o = new JSONObject();
            o.put("at", at);
            o.put("kind", kind);
            o.put("info", info == null ? "" : info);
            o.put("gap", gapSec);
            arr.put(o);
            while (arr.length() > MAX_EVENTS) {
                JSONArray cut = new JSONArray();
                for (int i = arr.length() - MAX_EVENTS; i < arr.length(); i++) {
                    cut.put(arr.get(i));
                }
                arr = cut;
            }
            sp.edit().putString("events", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** get_session_status tool ke liye — asal record, koi andaza nahi. */
    public static String statusText(Context ctx) {
        try {
            SharedPreferences sp = p(ctx);
            boolean active = sp.getBoolean("active", false);
            int id = sp.getInt("cur_id", 0);
            long start = sp.getLong("cur_start", 0);
            long beat = sp.getLong("last_beat", 0);
            long now = System.currentTimeMillis();
            StringBuilder sb = new StringBuilder();
            if (id == 0) {
                return "Abhi tak koi Live session record nahi hai boss.";
            }
            sb.append("Session #").append(id)
                    .append(active ? " (chal raha hai)" : " (band ho chuka hai)")
                    .append(" — shuru: ").append(fmt(start));
            if (active && start > 0) {
                sb.append(", ab tak ").append((now - start) / 60000).append(" minute");
            }
            if (beat > 0) {
                sb.append(", aakhri heartbeat ").append(Math.max(0, (now - beat) / 1000))
                        .append(" second pehle");
            }
            sb.append(".\n");
            JSONArray arr = new JSONArray(sp.getString("events", "[]"));
            int drops = 0;
            StringBuilder ev = new StringBuilder();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String kind = o.optString("kind");
                if ("drop".equals(kind)) drops++;
                if (i >= arr.length() - 4) { // aakhri 4 events
                    ev.append("- ").append(fmt(o.optLong("at"))).append(": ");
                    if ("drop".equals(kind)) {
                        ev.append("connection drop hua (").append(o.optString("info")).append(")");
                    } else if ("reconnected".equals(kind)) {
                        ev.append("dobara connect ho gaya — gap ~")
                                .append(o.optLong("gap")).append(" second");
                    } else if ("rehandshake".equals(kind)) {
                        ev.append("session renew (re-handshake) — baat nahi kati");
                    }
                    ev.append("\n");
                }
            }
            if (drops == 0 && arr.length() == 0) {
                sb.append("Is record me koi drop NAHI hai — connection "
                        + "aur stream bilkul stable rahe.");
            } else {
                sb.append("Drop events (kul record): ").append(drops).append("\n");
                sb.append(ev.toString().trim());
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Session log parhne me masla aya boss.";
        }
    }
}
