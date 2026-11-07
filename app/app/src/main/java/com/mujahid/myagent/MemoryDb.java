package com.mujahid.myagent;

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * v98 "1.0-hafiza": Deep Memory — SQLite conversation database.
 *
 * Har guftagu word-by-word, date/time ke sath, hamesha ke liye.
 * Purana system (SharedPreferences turn_log, 1000 turns + prompt me
 * clip) sirf taaza context ke liye tha — purani baat dhoondhna mumkin
 * nahi tha. Ab:
 *  - tables: sessions + turns (indexes: ts, session, speaker)
 *  - LiveTalk/Chat ke har turn ka dual-write yahan (uncropped)
 *  - pehli dafa purana turn_log + ChatHistory khud import (zero loss)
 *  - recall_conversation tool yahin se query karta hai
 *
 * Session: 30 minute se zyada khamoshi ho to nayi session; Live
 * guftagu VoiceService se begin/end bhi hoti hai.
 */
public final class MemoryDb extends SQLiteOpenHelper {

    private static final String DB_NAME = "ayesha_memory.db";
    private static final String STATE_PREFS = "memorydb_state";
    private static final long SESSION_GAP_MS = 30 * 60_000L;
    private static MemoryDb instance;

    private MemoryDb(Context ctx) {
        super(ctx.getApplicationContext(), DB_NAME, null, 1);
    }

    public static synchronized MemoryDb get(Context ctx) {
        if (instance == null) instance = new MemoryDb(ctx);
        return instance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE sessions ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "started_at INTEGER NOT NULL, "
                + "ended_at INTEGER DEFAULT 0, "
                + "mode TEXT NOT NULL)");
        db.execSQL("CREATE TABLE turns ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "session_id INTEGER NOT NULL, "
                + "ts INTEGER NOT NULL, "
                + "speaker TEXT NOT NULL, "
                + "text TEXT NOT NULL, "
                + "source TEXT NOT NULL)");
        db.execSQL("CREATE INDEX idx_turns_ts ON turns(ts)");
        db.execSQL("CREATE INDEX idx_turns_session ON turns(session_id)");
        db.execSQL("CREATE INDEX idx_turns_speaker ON turns(speaker)");
        android.util.Log.d("MemoryDb", "Database created — sessions + turns tables ready");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldV, int newV) { }

    // ---------- sessions ----------

    public static synchronized void beginSession(Context ctx, String mode) {
        ready(ctx);
        try {
            SharedPreferences sp = state(ctx);
            long open = sp.getLong("open_id", 0);
            SQLiteDatabase db = get(ctx).getWritableDatabase();
            if (open > 0) {
                ContentValues cv = new ContentValues();
                cv.put("ended_at", System.currentTimeMillis());
                db.update("sessions", cv, "id=? AND ended_at=0",
                        new String[]{String.valueOf(open)});
            }
            long now = System.currentTimeMillis();
            ContentValues cv = new ContentValues();
            cv.put("started_at", now);
            cv.put("ended_at", 0);
            cv.put("mode", mode);
            long id = db.insert("sessions", null, cv);
            sp.edit().putLong("open_id", id).putString("open_mode", mode)
                    .putLong("last_ts", now).apply();
        } catch (Exception ignored) { }
    }

    public static synchronized void closeSession(Context ctx) {
        try {
            SharedPreferences sp = state(ctx);
            long open = sp.getLong("open_id", 0);
            if (open <= 0) return;
            SQLiteDatabase db = get(ctx).getWritableDatabase();
            ContentValues cv = new ContentValues();
            cv.put("ended_at", System.currentTimeMillis());
            db.update("sessions", cv, "id=? AND ended_at=0",
                    new String[]{String.valueOf(open)});
            sp.edit().putLong("open_id", 0).apply();
        } catch (Exception ignored) { }
    }

    /** Khuli session na ho / mode alag ho / 30 min gap ho → nayi session. */
    private static long ensureSession(Context ctx, SQLiteDatabase db, String mode) {
        SharedPreferences sp = state(ctx);
        long open = sp.getLong("open_id", 0);
        String om = sp.getString("open_mode", "");
        long last = sp.getLong("last_ts", 0);
        long now = System.currentTimeMillis();
        if (open <= 0 || !mode.equals(om) || now - last > SESSION_GAP_MS) {
            if (open > 0) {
                ContentValues cv = new ContentValues();
                cv.put("ended_at", now);
                db.update("sessions", cv, "id=? AND ended_at=0",
                        new String[]{String.valueOf(open)});
            }
            ContentValues cv = new ContentValues();
            cv.put("started_at", now);
            cv.put("ended_at", 0);
            cv.put("mode", mode);
            open = db.insert("sessions", null, cv);
            sp.edit().putLong("open_id", open).putString("open_mode", mode).apply();
        }
        sp.edit().putLong("last_ts", now).apply();
        return open;
    }

    // ---------- turns likhna ----------

    private static void insertTurn(SQLiteDatabase db, long sessionId, long ts,
                                   String speaker, String text, String source) {
        if (text == null || text.trim().isEmpty()) return;
        ContentValues cv = new ContentValues();
        cv.put("session_id", sessionId);
        cv.put("ts", ts);
        cv.put("speaker", speaker);
        cv.put("text", text);
        cv.put("source", source);
        long row = db.insert("turns", null, cv);
        // v98.2: write ka Logcat saboot — user ki di hui format
        if ("live".equals(source) || "chat".equals(source)) {
            android.util.Log.d("MemoryDb", "Turn saved: Speaker=" + speaker
                    + ", Text=" + text);
        }
    }

    /** Live guftagu ka ek turn (Boss + Ayesha) — uncropped. */
    public static synchronized void addTurnPair(Context ctx, String userText, String aiText) {
        try {
            ready(ctx);
            SQLiteDatabase db = get(ctx).getWritableDatabase();
            long sid = ensureSession(ctx, db, "live");
            long now = System.currentTimeMillis();
            // v98.1: explicit transaction — commit pakka, process kill
            // ho jaye to bhi likha hua turn mehfooz
            db.beginTransaction();
            try {
                insertTurn(db, sid, now, "user", userText, "live");
                insertTurn(db, sid, now, "ayesha", aiText, "live");
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception e) {
            // v98.1: khamosh ignore NAHI — Logcat me wajah
            android.util.Log.e("MemoryDb", "addTurnPair FAILED", e);
        }
    }

    /** Chat mode ka ek turn. role: "user" ya "model". */
    public static synchronized void addSingle(Context ctx, String role, String text) {
        try {
            ready(ctx);
            SQLiteDatabase db = get(ctx).getWritableDatabase();
            long sid = ensureSession(ctx, db, "chat");
            insertTurn(db, sid, System.currentTimeMillis(),
                    "user".equals(role) ? "user" : "ayesha", text, "chat");
        } catch (Exception e) {
            android.util.Log.e("MemoryDb", "addSingle FAILED", e);
        }
    }

    // ---------- migration (pehli dafa, zero loss) ----------

    private static boolean migrated = false;

    private static synchronized void ready(Context ctx) {
        if (migrated) return;
        migrated = true;
        try {
            SQLiteDatabase db = get(ctx).getWritableDatabase();
            // v98.1: self-check — tables mawjud na hon to yahin bana do
            db.execSQL("CREATE TABLE IF NOT EXISTS sessions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "started_at INTEGER NOT NULL, "
                    + "ended_at INTEGER DEFAULT 0, mode TEXT NOT NULL)");
            db.execSQL("CREATE TABLE IF NOT EXISTS turns ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "session_id INTEGER NOT NULL, ts INTEGER NOT NULL, "
                    + "speaker TEXT NOT NULL, text TEXT NOT NULL, "
                    + "source TEXT NOT NULL)");
            // turn_log (Live diary) import — pehle se import ho to skip
            if (!hasSource(db, "import-live")) importTurnLog(ctx, db);
            if (!hasSource(db, "import-chat")) importChatHistory(ctx, db);
            android.util.Log.d("MemoryDb", "Ready check — turns table total rows: "
                    + countTurns(db));
        } catch (Exception e) {
            android.util.Log.e("MemoryDb", "ready/migration FAILED", e);
        }
    }

    /** v98.1: database me kul turns — diagnosis ke liye. */
    private static long countTurns(SQLiteDatabase db) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT COUNT(*) FROM turns", null);
            return (c != null && c.moveToFirst()) ? c.getLong(0) : 0;
        } catch (Exception e) {
            return -1;
        } finally {
            if (c != null) c.close();
        }
    }

    private static boolean hasSource(SQLiteDatabase db, String source) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT 1 FROM turns WHERE source=? LIMIT 1",
                    new String[]{source});
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    private static long daySession(SQLiteDatabase db, String mode, long ts,
                                   String[] lastDay, long[] lastSid,
                                   SimpleDateFormat dayFmt) {
        String day = dayFmt.format(new Date(ts));
        if (!day.equals(lastDay[0])) {
            ContentValues cv = new ContentValues();
            cv.put("started_at", ts);
            cv.put("ended_at", ts);
            cv.put("mode", mode);
            lastSid[0] = db.insert("sessions", null, cv);
            lastDay[0] = day;
        }
        return lastSid[0];
    }

    private static void importTurnLog(Context ctx, SQLiteDatabase db) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("turn_log", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString("turns", "[]"));
            SimpleDateFormat dayFmt = pkt("yyyyMMdd");
            String[] lastDay = {""};
            long[] lastSid = {0};
            db.beginTransaction();
            try {
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    long t = o.optLong("t", 0);
                    if (t <= 0) continue;
                    long sid = daySession(db, "live", t, lastDay, lastSid, dayFmt);
                    insertTurn(db, sid, t, "user", o.optString("u", ""), "import-live");
                    insertTurn(db, sid, t, "ayesha", o.optString("a", ""), "import-live");
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ignored) { }
    }

    private static void importChatHistory(Context ctx, SQLiteDatabase db) {
        try {
            List<ChatHistory.Entry> entries = ChatHistory.load(ctx);
            SimpleDateFormat dayFmt = pkt("yyyyMMdd");
            String[] lastDay = {""};
            long[] lastSid = {0};
            db.beginTransaction();
            try {
                for (ChatHistory.Entry e : entries) {
                    if (e.time <= 0) continue;
                    long sid = daySession(db, "chat", e.time, lastDay, lastSid, dayFmt);
                    insertTurn(db, sid, e.time,
                            "user".equals(e.role) ? "user" : "ayesha",
                            e.text, "import-chat");
                }
                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        } catch (Exception ignored) { }
    }

    // ---------- recall (tool ke liye) ----------

    /**
     * Database se asal guftagu nikaalo. query ke alfaaz pehle AND se,
     * na mile to OR se. Result: waqt ke sath mukammal turns (tool
     * result ki hadd ~7000 harf — storage me koi crop NAHI).
     */
    public static String recall(Context ctx, String query, long fromMs, long toMs,
                                String speaker) {
        try {
            ready(ctx);
            SQLiteDatabase db = get(ctx).getReadableDatabase();
            List<String> words = new ArrayList<>();
            if (query != null) {
                for (String w : query.toLowerCase(Locale.ROOT).split("\\s+")) {
                    String t = w.replaceAll("[^a-z0-9\\u0600-\\u06FF]", "");
                    if (t.length() >= 3 && words.size() < 5) words.add(t);
                }
            }
            // v98.2: DATE FILTERS KHATAM — timezone mismatch search ko
            // khali kar deta tha. Alfaaz hon to POORI history me search;
            // warna / na mile to seedha aakhri 30 turns (ORDER BY id DESC).
            List<String[]> rows = new ArrayList<>();
            boolean searched = false;
            if (!words.isEmpty()) {
                rows = search(db, words, 0, Long.MAX_VALUE, speaker, true);
                if (rows.isEmpty() && words.size() > 1) {
                    rows = search(db, words, 0, Long.MAX_VALUE, speaker, false);
                }
                searched = !rows.isEmpty();
            }
            if (rows.isEmpty()) {
                rows = latest(db, 30);
            }
            long total = countTurns(db);
            if (rows.isEmpty()) {
                return "Database bilkul khali hai boss — ek bhi turn "
                        + "mehfooz nahi (kul turns: " + total + ").";
            }
            SimpleDateFormat df = pkt("d MMM yyyy");
            SimpleDateFormat tf = pkt("h:mm a");
            StringBuilder sb = new StringBuilder();
            if (searched) {
                sb.append("Database se ").append(rows.size()).append(" turns mile "
                        + "(kul ").append(total).append(" turns mehfooz hain):\n");
            } else {
                sb.append("Ye database ki sab se TAAZA guftagu hai — aakhri ")
                        .append(rows.size()).append(" turns (kul ").append(total)
                        .append(" turns mehfooz hain):\n");
            }
            int used = 0;
            String lastDay = "";
            for (String[] r : rows) {
                long ts = Long.parseLong(r[0]);
                String day = df.format(new Date(ts));
                String line = (day.equals(lastDay) ? "" : "— " + day + " —\n")
                        + "[" + tf.format(new Date(ts)) + "] "
                        + ("user".equals(r[1]) ? "Boss: " : "Ayesha: ") + r[2] + "\n";
                lastDay = day;
                if (used + line.length() > 7000 && used > 0) {
                    sb.append("... (aur bhi turns hain — query thodi khaas karke "
                            + "phir poochho to baqi bhi nikaal dungi)");
                    break;
                }
                sb.append(line);
                used += line.length();
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "Database parhne me masla aya boss.";
        }
    }

    /** v98.2: seedha latest — SELECT ... ORDER BY id DESC LIMIT n, phir tarteeb se. */
    private static List<String[]> latest(SQLiteDatabase db, int n) {
        List<String[]> out = new ArrayList<>();
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT ts, speaker, text FROM turns "
                    + "ORDER BY id DESC LIMIT " + n, null);
            List<String[]> desc = new ArrayList<>();
            if (c != null) {
                while (c.moveToNext()) {
                    desc.add(new String[]{String.valueOf(c.getLong(0)),
                            c.getString(1), c.getString(2)});
                }
            }
            for (int i = desc.size() - 1; i >= 0; i--) out.add(desc.get(i));
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    // ---------- v100: memory pruning (user ke hukm par delete) ----------

    /**
     * Query ke alfaaz se milte turns database se hamesha ke liye
     * delete karo. Pehle AND match, phir OR (delete me narmi behtar).
     * Return: kitne turns delete hue.
     */
    public static int deleteMatching(Context ctx, String query) {
        try {
            ready(ctx);
            List<String> words = new ArrayList<>();
            if (query != null) {
                for (String w : query.toLowerCase(Locale.ROOT).split("\\s+")) {
                    String t = w.replaceAll("[^a-z0-9\\u0600-\\u06FF]", "");
                    if (t.length() >= 3 && words.size() < 5) words.add(t);
                }
            }
            if (words.isEmpty()) return 0;
            SQLiteDatabase db = get(ctx).getWritableDatabase();
            int n = deleteWords(db, words, true);
            if (n == 0 && words.size() > 1) n = deleteWords(db, words, false);
            android.util.Log.d("MemoryDb", "Prune by query '" + query + "': " + n + " turns deleted");
            return n;
        } catch (Exception e) {
            android.util.Log.e("MemoryDb", "deleteMatching FAILED", e);
            return 0;
        }
    }

    private static int deleteWords(SQLiteDatabase db, List<String> words,
                                   boolean andMode) {
        StringBuilder sel = new StringBuilder("(");
        List<String> args = new ArrayList<>();
        for (int i = 0; i < words.size(); i++) {
            if (i > 0) sel.append(andMode ? " AND " : " OR ");
            sel.append("text LIKE ?");
            args.add("%" + words.get(i) + "%");
        }
        sel.append(")");
        return db.delete("turns", sel.toString(), args.toArray(new String[0]));
    }

    /** Poori conversation history saaf — turns + sessions. Return: turns count. */
    public static int clearAllTurns(Context ctx) {
        try {
            ready(ctx);
            SQLiteDatabase db = get(ctx).getWritableDatabase();
            long n = countTurns(db);
            db.delete("turns", null, null);
            db.delete("sessions", null, null);
            closeSession(ctx);
            android.util.Log.d("MemoryDb", "Prune ALL: " + n + " turns deleted");
            return (int) n;
        } catch (Exception e) {
            android.util.Log.e("MemoryDb", "clearAllTurns FAILED", e);
            return 0;
        }
    }

    // ---------- v98.2: startup verification + diagnostic ----------

    /** App/service start par: DB kholo, tables check karo, Logcat me report. */
    public static String verify(Context ctx) {
        try {
            ready(ctx);
            SQLiteDatabase db = get(ctx).getReadableDatabase();
            boolean sOk = tableExists(db, "sessions");
            boolean tOk = tableExists(db, "turns");
            long turns = countTurns(db);
            android.util.Log.d("MemoryDb", "Startup check — sessions table: "
                    + sOk + ", turns table: " + tOk + ", total turns: " + turns);
            return "sessions table: " + (sOk ? "OK" : "MISSING")
                    + ", turns table: " + (tOk ? "OK" : "MISSING")
                    + ", total turns: " + turns;
        } catch (Exception e) {
            android.util.Log.e("MemoryDb", "verify FAILED", e);
            return "Database check fail ho gaya boss: " + e.getMessage();
        }
    }

    private static boolean tableExists(SQLiteDatabase db, String name) {
        Cursor c = null;
        try {
            c = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' "
                    + "AND name=?", new String[]{name});
            return c != null && c.moveToFirst();
        } catch (Exception e) {
            return false;
        } finally {
            if (c != null) c.close();
        }
    }

    /** get_memory_status tool ke liye: asal ginti + aakhri turn ka waqt. */
    public static String statusText(Context ctx) {
        try {
            ready(ctx);
            SQLiteDatabase db = get(ctx).getReadableDatabase();
            long turns = countTurns(db);
            long sessions = 0;
            long lastTs = 0;
            Cursor c = null;
            try {
                c = db.rawQuery("SELECT COUNT(*) FROM sessions", null);
                if (c != null && c.moveToFirst()) sessions = c.getLong(0);
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
            c = null;
            try {
                c = db.rawQuery("SELECT ts FROM turns ORDER BY id DESC LIMIT 1", null);
                if (c != null && c.moveToFirst()) lastTs = c.getLong(0);
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.close();
            }
            String last = lastTs > 0
                    ? pkt("d MMM yyyy, h:mm a").format(new Date(lastTs))
                    : "koi turn nahi";
            return "Deep Memory database status — kul turns: " + turns
                    + ", kul sessions: " + sessions
                    + ", sab se aakhri saved turn: " + last + ".";
        } catch (Exception e) {
            return "Database status parhne me masla aya boss.";
        }
    }

    /** rows: [ts, speaker, text] — sab se naye 80 pehle gin kar, phir tarteeb se. */
    private static List<String[]> search(SQLiteDatabase db, List<String> words,
                                         long fromMs, long toMs, String speaker,
                                         boolean andMode) {
        List<String[]> out = new ArrayList<>();
        StringBuilder sel = new StringBuilder("ts>=? AND ts<=?");
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(fromMs <= 0 ? 0 : fromMs));
        args.add(String.valueOf(toMs <= 0 ? Long.MAX_VALUE : toMs));
        if (speaker != null && (speaker.equals("user") || speaker.equals("ayesha"))) {
            sel.append(" AND speaker=?");
            args.add(speaker);
        }
        if (!words.isEmpty()) {
            sel.append(" AND (");
            for (int i = 0; i < words.size(); i++) {
                if (i > 0) sel.append(andMode ? " AND " : " OR ");
                sel.append("text LIKE ?");
                args.add("%" + words.get(i) + "%");
            }
            sel.append(")");
        }
        Cursor c = null;
        try {
            c = db.query("turns", new String[]{"ts", "speaker", "text"},
                    sel.toString(), args.toArray(new String[0]),
                    null, null, "ts DESC", "80");
            List<String[]> desc = new ArrayList<>();
            if (c != null) {
                while (c.moveToNext()) {
                    desc.add(new String[]{String.valueOf(c.getLong(0)),
                            c.getString(1), c.getString(2)});
                }
            }
            for (int i = desc.size() - 1; i >= 0; i--) out.add(desc.get(i));
        } catch (Exception ignored) {
        } finally {
            if (c != null) c.close();
        }
        return out;
    }

    // ---------- helpers ----------

    private static SharedPreferences state(Context ctx) {
        return ctx.getSharedPreferences(STATE_PREFS, Context.MODE_PRIVATE);
    }

    private static SimpleDateFormat pkt(String pattern) {
        SimpleDateFormat f = new SimpleDateFormat(pattern, Locale.ENGLISH);
        f.setTimeZone(TimeZone.getTimeZone("Asia/Karachi"));
        return f;
    }
}
