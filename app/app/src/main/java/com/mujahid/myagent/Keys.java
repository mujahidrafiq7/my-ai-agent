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

    // ----- v99: Channel Analytics vault — sirf phone par, kabhi code/
    // GitHub/chat me nahi. YouTube API key + Channel ID, OAuth token
    // (retention ke liye, optional), Facebook Page ID + Page token. -----

    public static String youtubeApiKey(Context c) {
        return prefs(c).getString("yt_api_key", "").trim();
    }

    public static String youtubeChannelId(Context c) {
        return prefs(c).getString("yt_channel_id", "").trim();
    }

    public static String youtubeOauthToken(Context c) {
        return prefs(c).getString("yt_oauth_token", "").trim();
    }

    public static String facebookPageId(Context c) {
        return prefs(c).getString("fb_page_id", "").trim();
    }

    public static String facebookPageToken(Context c) {
        return prefs(c).getString("fb_page_token", "").trim();
    }

    public static void saveChannels(Context c, String ytKey, String ytId,
                                    String ytTok, String fbId, String fbTok) {
        prefs(c).edit()
                .putString("yt_api_key", ytKey == null ? "" : ytKey.trim())
                .putString("yt_channel_id", ytId == null ? "" : ytId.trim())
                .putString("yt_oauth_token", ytTok == null ? "" : ytTok.trim())
                .putString("fb_page_id", fbId == null ? "" : fbId.trim())
                .putString("fb_page_token", fbTok == null ? "" : fbTok.trim())
                .apply();
    }
}
