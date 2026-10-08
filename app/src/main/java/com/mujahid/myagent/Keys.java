package com.mujahid.myagent;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.ArrayList;
import java.util.List;

/**
 * API keys — sirf phone ke SharedPreferences me. Code ya GitHub me kabhi nahi.
 * 10 Gemini keys (alag-alag Gmail se) — ek ki limit khatam ho to agli khud lagegi.
 */
public class Keys {
    private static final String PREFS = "myagent_prefs";
    /** Kitni Gemini keys rakhi ja sakti hain. */
    public static final int MAX_KEYS = 10;

    private static SharedPreferences prefs(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Khali na hon wali keys, tarteeb se (Key 1 pehle). */
    public static List<String> geminiKeys(Context c) {
        List<String> out = new ArrayList<>();
        SharedPreferences p = prefs(c);
        // Purani single-key wali save ho to usay Key 1 me le aao (migrate)
        String legacy = p.getString("gemini_key", "").trim();
        for (int i = 1; i <= MAX_KEYS; i++) {
            String k = p.getString("gemini_key_" + i, "").trim();
            if (k.isEmpty() && i == 1 && !legacy.isEmpty()) k = legacy;
            if (!k.isEmpty() && !out.contains(k)) out.add(k);
        }
        return out;
    }

    public static String geminiKeyAt(Context c, int index) {
        return prefs(c).getString("gemini_key_" + index, "").trim();
    }

    public static String groqKey(Context c) {
        return prefs(c).getString("groq_key", "").trim();
    }

    public static void save(Context c, List<String> geminiKeys, String groqKey) {
        SharedPreferences.Editor e = prefs(c).edit();
        for (int i = 1; i <= MAX_KEYS; i++) {
            String k = (geminiKeys != null && i <= geminiKeys.size())
                    ? geminiKeys.get(i - 1).trim() : "";
            e.putString("gemini_key_" + i, k);
        }
        e.putString("groq_key", groqKey == null ? "" : groqKey.trim());
        e.apply();
    }
}
