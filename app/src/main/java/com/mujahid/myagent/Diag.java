package com.mujahid.myagent;

/**
 * Apni jaanch (self-diagnosis): har turn me har step ka waqt + aakhri
 * ghaltiyan + keys ki sehat note hoti hai.
 *
 * Jab user apne baare me puche ("kyun late reply kar rahi ho?",
 * "kis cheez ka error aa raha hai?", "keys ka kya masla hai?") to model ko
 * Diag.snapshot() ka EXACT data milta hai — tukka ("network issue hoga")
 * lagane ki zaroorat nahi.
 *
 * Sirf isi app ke andar ki baatein — phone ka tajziya is me nahi.
 */
public class Diag {
    private static final int MAX_EVENTS = 40;
    private static final java.util.ArrayDeque<String> events = new java.util.ArrayDeque<>();
    private static final java.util.Map<String, String> notes = new java.util.HashMap<>();
    private static String lastError = "";
    private static long lastErrorAt = 0;

    private static String ts() {
        return new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.US)
                .format(new java.util.Date());
    }

    public static synchronized void event(String e) {
        if (e == null) return;
        events.addLast(ts() + " " + e);
        while (events.size() > MAX_EVENTS) events.removeFirst();
    }

    /** Step ka waqt (ms): "sunna", "stt", "sochna", "tts". */
    public static synchronized void stage(String name, long ms) {
        notes.put("stage_" + name, ms + "ms");
        event(name + " = " + ms + "ms");
    }

    /** Chhota note: note("tts_door", "gemini") ... */
    public static synchronized void note(String k, String v) {
        if (k == null || v == null) return;
        notes.put(k, v);
        event(k + ": " + v);
    }

    public static synchronized void keys(int alive, int total) {
        notes.put("keys", alive + "/" + total + " khule hain");
        event("raste " + alive + "/" + total + " khule");
    }

    // ---------- v41: Khud-mukhtar Ayesha — sehat + fori error report ----------

    /** Live prompt ke liye chhota sehat-khulasa. */
    public static synchronized String healthSummary() {
        StringBuilder b = new StringBuilder();
        String k = notes.get("keys");
        b.append("Raste: ").append(k == null ? "maloom nahi" : k).append(". ");
        if (!lastError.isEmpty()
                && System.currentTimeMillis() - lastErrorAt < 30L * 60_000L) {
            b.append("Aakhri error (30 min me): ").append(lastError).append(".");
        } else {
            b.append("Koi taaza error nahi.");
        }
        return b.toString().trim();
    }

    /** Fori error report — guftagu ke dauran boli me batane ke liye. */
    public interface ErrorListener {
        void onError(String where, String msg);
    }

    private static volatile ErrorListener errorListener;

    public static void setErrorListener(ErrorListener l) {
        errorListener = l;
    }

    public static void error(String where, String msg) {
        ErrorListener l;
        synchronized (Diag.class) {
            if (msg == null) msg = "";
            if (msg.length() > 120) msg = msg.substring(0, 120);
            lastError = where + ": " + msg;
            lastErrorAt = System.currentTimeMillis();
            event("ERROR @" + where + ": " + msg);
            l = errorListener;
        }
        if (l != null) {
            try { l.onError(where, msg); }
            catch (Exception ignored) { }
        }
    }

    /** Kya ye network (slow/band internet) ki ghalti lagti hai? */
    public static boolean isNetworkError(String msg) {
        if (msg == null) return false;
        String m = msg.toLowerCase(java.util.Locale.ROOT);
        return m.contains("timeout") || m.contains("timed out")
                || m.contains("unable to resolve") || m.contains("failed to connect")
                || m.contains("network") || m.contains("no address")
                || m.contains("ehostunreach") || m.contains("econnrefused")
                || m.contains("socket") || m.contains("unknownhost");
    }

    /** Model ke liye compact snapshot — EXACT sach, isi ko dekh ke jawab de. */
    public static synchronized String snapshot(android.content.Context c) {
        StringBuilder sb = new StringBuilder();
        sb.append("Aakhri poore turn ka waqt: ");
        boolean any = false;
        for (String s : new String[]{"sunna", "stt", "sochna", "tts"}) {
            String v = notes.get("stage_" + s);
            if (v != null) {
                if (any) sb.append(", ");
                sb.append(s).append("=").append(v);
                any = true;
            }
        }
        if (!any) sb.append("(abhi koi poora turn nahi hua)");
        // v35: "keys/API" lafz model ko mana karwa deta hai (safety) — neutral lafz
        sb.append("\nSochne ke raste: ").append(notes.getOrDefault("keys", "maloom nahi"));
        String tk = notes.get("think_key");
        if (tk != null) sb.append(" (aakhri jawab ").append(tk).append(" se aaya)");
        String door = notes.get("tts_door");
        if (door != null) sb.append("\nAakhri awaz: ").append(door).append(" engine se");
        if (!lastError.isEmpty()
                && System.currentTimeMillis() - lastErrorAt < 10 * 60 * 1000) {
            sb.append("\nAakhri ghalti (10 min ke andar): ").append(lastError);
        } else {
            sb.append("\nAakhri ghalti: haal me koi nahi");
        }
        sb.append("\nScreen share: ").append(
                ScreenShare.isOn() ? "ON hai (screen dikha sakti hun)" : "OFF hai");
        sb.append("\nNeon lighting: ")
          .append(SettingsActivity.isLightingOn(c) ? "ON" : "OFF");
        sb.append("\nAuto memory: ")
          .append(SettingsActivity.isAutoMemoryOn(c) ? "ON" : "OFF");
        return sb.toString();
    }

    /** Kya user isi APP ke baare me puch raha hai? (norm kiya hua text do) */
    public static boolean isSelfQuestion(String t) {
        if (t == null) return false;
        String[] phrases = {
            "kyun late", "kyun der", "itni der", "der kyun", "late kyun",
            "slow kyun", "ahista kyun", "tum slow", "tum late",
            "kis cheez ka error", "koi error", "error aa raha", "error hai",
            "error aaya", "error kyun",
            "kya masla", "masla kya", "kya issue", "issue kya", "kya kharabi",
            "tumhare andar", "tum me kya", "tumhen kya hua", "tumhe kya hua",
            "awaz kyun nahi", "voice kyun nahi", "bol kyun nahi", "sunai nahi",
            "lighting", "light kyun",
            "key ka", "keys ka", "kitni keys", "keyen",
            "kaam kyun nahi", "chal kyun nahi", "on kyun nahi",
            "tumhen kya masla", "tumhe kya masla",
            // v35: lafzon ki ulat tarteeb bhi pakro ("keys kitni zinda hai")
            "keys kitni", "key kitni", "kitni zinda", "keys zinda", "key zinda",
            "zinda hain", "kitne khule", "khule hain",
        };
        for (String p : phrases) if (t.contains(p)) return true;
        return false;
    }
}
