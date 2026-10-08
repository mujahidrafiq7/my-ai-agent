package com.mujahid.myagent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;

import java.util.ArrayList;
import java.util.List;

/**
 * Ayesha ki YAADDAASHT — user ki batai hui baatein hamesha ke liye save.
 * - "yaad rakho ke ..." → fact save, app band karke kholo tab bhi yaad
 * - Har jawab se pehle yaadein dimagh me daal di jati hain (system prompt)
 * - Sirf phone me rehti hain — kahin upload nahi hotin
 */
public class Memory {

    private static final String PREFS = "myagent_prefs";
    private static final String KEY = "agent_memories_json";
    private static final int MAX_MEMORIES = 100;

    /** Pehli dafa app khule to Ayesha ko user ke baare me bunyadi baatein sikhao. */
    public static void seedDefaults(Context c) {
        migrateSensitive(c); // purani sensitive yaadein pehle saaf karo
        if (!getAll(c).isEmpty()) return; // pehle se yaadein hain
        String[] seeds = {
                "Mera naam Mujahid Rafiq hai, aur mujhe 'boss' keh kar bulati ho.",
                "Me 18 saal ka hun, Vehari se hun, ab Phalia me factory me kaam karta hun.",
                "Me content creator hun — YouTube aur Facebook par videos banata hun.",
                "Me Roman Urdu, Urdu aur Punjabi bolta hun — jis me bolun usi me jawab deti ho.",
        };
        for (String s : seeds) save(c, s);
    }

    /**
     * Purani sensitive yaadein hatao — ek dafa (app update par).
     * Girlfriend aur ghar-walon wali baatein SIRF user aur Ayesha ke darmiyan
     * hain — app ki memory me nahi honi chahiye (privacy).
     */
    private static void migrateSensitive(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean("seeds_v2_migrated", false)) return;
        List<String> keep = new ArrayList<>();
        for (String m : getAll(c)) {
            String low = m.toLowerCase(java.util.Locale.ROOT);
            if (low.contains("girlfriend") || low.contains("jannat")
                    || low.contains("brother-in-law")) {
                continue; // sensitive — hatao
            }
            keep.add(m);
        }
        JSONArray arr = new JSONArray();
        for (String m : keep) arr.put(m);
        p.edit().putString(KEY, arr.toString())
                .putBoolean("seeds_v2_migrated", true).apply();
    }

    /** "yaad rakho ke ..." jaisa jumla ho to asal baat nikalo, warna null. */
    public static String extractMemoryCommand(String text) {
        if (text == null) return null;
        String t = text.trim();
        if (t.isEmpty()) return null;
        String low = t.toLowerCase(java.util.Locale.ROOT);
        // Lambe triggers pehle (taake "yaad rakho ke" poora pakra jaye)
        String[] triggers = {
                "ye baat yaad rakhna ke", "ye baat yaad rakhna k",
                "yaad rakho ke", "yaad rakho k",
                "yaad rakhna ke", "yaad rakhna k",
                "yaad kar lo ke", "yaad kar lo k",
                // v41: "save kar lo" bhi — khud-mukhtar memory
                "save kar lo ke", "save kar lo k", "save karlo ke", "save karlo k",
                "note kar lo ke", "note kar lo k", "note karlo ke", "note karlo k",
                "save kar lo", "save karlo", "save karo",
                "note kar lo", "note karlo",
                "remember that", "yaad rakho", "yaad rakhna",
                "yaad kar lo", "remember",
        };
        for (String tr : triggers) {
            int i = low.indexOf(tr);
            if (i >= 0) {
                String fact = t.substring(i + tr.length()).trim();
                // "ke"/"k" reh gaya ho to saaf karo
                if (fact.toLowerCase(java.util.Locale.ROOT).startsWith("ke "))
                    fact = fact.substring(3).trim();
                else if (fact.toLowerCase(java.util.Locale.ROOT).startsWith("k "))
                    fact = fact.substring(2).trim();
                if (fact.isEmpty()) {
                    // Baat trigger se PEHLE thi: "Me Phalia me hun, ye yaad rakho"
                    fact = t.substring(0, i).trim();
                    // Aakhir me dangling "ye"/"yeh"/comma saaf karo
                    String fl = fact.toLowerCase(java.util.Locale.ROOT);
                    if (fl.endsWith(", ye")) fact = fact.substring(0, fact.length() - 4).trim();
                    else if (fl.endsWith(", yeh")) fact = fact.substring(0, fact.length() - 5).trim();
                    else if (fl.endsWith(" ye")) fact = fact.substring(0, fact.length() - 3).trim();
                    else if (fl.endsWith(",")) fact = fact.substring(0, fact.length() - 1).trim();
                }
                // "ye baat" jaisa filler hatado
                if (fact.toLowerCase(java.util.Locale.ROOT).startsWith("ye baat"))
                    fact = fact.substring(7).trim();
                return fact.isEmpty() ? null : fact;
            }
        }
        return null;
    }

    /** Nayi yaad save karo (duplicate nahi). */
    public static void save(Context c, String fact) {
        if (fact == null || fact.trim().isEmpty()) return;
        fact = fact.trim();
        List<String> all = getAll(c);
        for (String m : all) {
            if (m.equalsIgnoreCase(fact)) return; // pehle se yaad hai
        }
        all.add(0, fact); // nayi yaad sab se upar
        while (all.size() > MAX_MEMORIES) all.remove(all.size() - 1);
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        JSONArray arr = new JSONArray();
        for (String m : all) arr.put(m);
        p.edit().putString(KEY, arr.toString()).apply();
    }

    /** Sab yaadein, nayi pehle. */
    public static List<String> getAll(Context c) {
        List<String> out = new ArrayList<>();
        try {
            String json = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .getString(KEY, "");
            if (json == null || json.isEmpty()) return out;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getString(i));
        } catch (Exception ignored) { }
        return out;
    }

    /** Sab kuch bhool jao. */
    public static void clear(Context c) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().remove(KEY).apply();
    }

    /** Ek purani yaad hatao — text ka koi hissa match ho to wo entry nikal do. */
    public static void delete(Context c, String text) {
        if (text == null || text.trim().isEmpty()) return;
        String t = text.trim().toLowerCase(java.util.Locale.ROOT);
        List<String> all = getAll(c);
        List<String> keep = new ArrayList<>();
        for (String m : all) {
            String ml = m.toLowerCase(java.util.Locale.ROOT);
            if (ml.contains(t) || t.contains(ml)) continue; // ye wali bhool jao
            keep.add(m);
        }
        if (keep.size() == all.size()) return; // kuch match nahi hua
        JSONArray arr = new JSONArray();
        for (String m : keep) arr.put(m);
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, arr.toString()).apply();
    }

    /** System prompt ke liye yaadon ka text. Khali ho to "". */
    public static String buildContext(Context c) {
        List<String> all = getAll(c);
        if (all.isEmpty()) return "";
        StringBuilder sb = new StringBuilder(
                "\nTum in baaton ko HAMESHA yaad rakhti ho (user ne khud batayi hain, "
                + "kabhi mat bhoolna):\n");
        for (String m : all) sb.append("- ").append(m).append("\n");
        return sb.toString();
    }
}
