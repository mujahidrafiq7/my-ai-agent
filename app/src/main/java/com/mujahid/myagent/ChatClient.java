package com.mujahid.myagent;

import android.graphics.Bitmap;
import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Step 1 — Sochna: Gemini (primary) → Groq (backup).
 * Sirf ZINDA models. Koi retry-toofan nahi — ghalti saaf lafzon me bahar.
 */
public class ChatClient {

    /** Verified live 2026-10-03. Purane band IDs yahan kabhi nahi ayenge. */
    private static final String CHAT_MODEL = "gemini-3.5-flash";
    private static final String[] GROQ_MODELS =
            {"openai/gpt-oss-20b", "openai/gpt-oss-120b"};
    /** 429 ke baad har key apna 30 min ka break leti hai — baqi keys chalti rehengi. */
    private static final java.util.Map<String, Long> keyDeadUntil =
            new java.util.HashMap<>();

    /** Nayi keys lagen to purani "saza" bhool jao. */
    public static void resetBreakers() { keyDeadUntil.clear(); }

    private static boolean keyDead(String k) {
        Long until = keyDeadUntil.get(k);
        return until != null && System.currentTimeMillis() < until;
    }

    private static void markKeyDead(String k) {
        keyDeadUntil.put(k, System.currentTimeMillis() + 30 * 60 * 1000);
    }

    /**
     * Hindi alfaaz ko Pakistani Urdu me badlo — GUARANTEED.
     * Backup dimagh (Groq) kabhi-kabhi Hindi me phisal jata hai ("sahayata");
     * ye map usay pakar kar theek karta hai, chahe model instruction bhool jaye.
     */
    private static final String[][] URDU_FIX = {
            {"sahayata", "madad"}, {"sahayta", "madad"},
            {"kripya", "meherbani"}, {"kripaya", "meherbani"},
            {"dhanyavad", "shukriya"}, {"dhanyavaad", "shukriya"},
            {"namaste", "assalam o alaikum"}, {"namaskar", "assalam o alaikum"},
            {"evam", "aur"}, {"tathapi", "phir bhi"},
            {"kintu", "lekin"}, {"parantu", "lekin"},
            {"yadi", "agar"}, {"prashn", "sawal"}, {"prashan", "sawal"},
            {"uttar", "jawab"}, {"samay", "waqt"}, {"samae", "waqt"},
            {"upayog", "istemaal"}, {"upyog", "istemaal"},
            {"sahayak", "madadgaar"}, {"avashyak", "zaroori"},
            {"shighra", "jaldi"}, {"manoranjan", "dilchaspi"},
            {"gyan", "ilm"}, {"shanti", "sukoon"},
            {"sundar", "khoobsurat"}, {"sundarta", "khoobsurti"},
            {"mitra", "dost"}, {"mitr", "dost"},
            {"safalta", "kamyabi"}, {"asafalta", "nakami"},
            {"prayas", "koshish"}, {"prayaas", "koshish"},
            {"vishesh", "khaas"}, {"vishwas", "bharosa"},
            {"anubhav", "tajurba"}, {"samasya", "masla"},
            {"samadhan", "hal"}, {"nirnay", "faisla"},
            {"suchna", "ittila"}, {"yojana", "mansooba"},
            {"jeevan", "zindagi"}, {"prem", "mohabbat"},
            // Urdu script wale jor (ab jawab Urdu script me ate hain)
            {"سہایتا", "مدد"}, {"سہائتا", "مدد"},
            {"کرپیا", "مہربانی"},
            {"دھنیواد", "شکریہ"}, {"دھنیاواد", "شکریہ"},
            {"نمستے", "السلام علیکم"}, {"نمسکار", "السلام علیکم"},
            {"ایوم", "اور"}, {"تتھاپی", "پھر بھی"},
            {"پرنتو", "لیکن"}, {"کنتو", "لیکن"},
            {"یدی", "اگر"}, {"پرشن", "سوال"},
            {"اتر", "جواب"}, {"سمیہ", "وقت"},
            {"اپیوگ", "استعمال"}, {"سہایک", "مددگار"},
            {"گیان", "علم"}, {"شانتی", "سکون"},
            {"سندر", "خوبصورت"}, {"متر", "دوست"},
            {"سفلتا", "کامیابی"}, {"پریاس", "کوشش"},
            {"وشیش", "خاص"}, {"وشواس", "بھروسہ"},
            {"انبھو", "تجربہ"}, {"سمسیا", "مسئلہ"},
            {"سمادھان", "حل"}, {"نرنے", "فیصلہ"},
            {"سوچنا", "اطلاع"}, {"یوجنا", "منصوبہ"},
            {"جیون", "زندگی"}, {"پریم", "محبت"},
    };

    /** Har jawab ko Urdu-safe banao — screen aur awaz dono ke liye.
     *  FIX (2026-10-04): regex (?U) Android par crash karta tha — ab baghair
     *  regex ke, haath se word-boundary check (koi crash possible nahi). */
    public static String polishUrdu(String s) {
        if (s == null) return s;
        String out = s;
        for (String[] pair : URDU_FIX) {
            out = replaceWord(out, pair[0], pair[1]);
        }
        return out;
    }

    /** Case-insensitive poora-lafz replacement — Unicode-aware boundary. */
    private static String replaceWord(String text, String word, String replacement) {
        String lower = text.toLowerCase(java.util.Locale.ROOT);
        String wLower = word.toLowerCase(java.util.Locale.ROOT);
        int wlen = wLower.length();
        StringBuilder sb = new StringBuilder(text.length());
        int i = 0;
        while (i <= text.length() - wlen) {
            if (lower.regionMatches(i, wLower, 0, wlen)
                    && isBoundary(text, i - 1) && isBoundary(text, i + wlen)) {
                sb.append(replacement);
                i += wlen;
            } else {
                sb.append(text.charAt(i));
                i++;
            }
        }
        sb.append(text.substring(i));
        return sb.toString();
    }

    /** Word boundary: shuru/akhir ya non-letter (Unicode-aware). */
    private static boolean isBoundary(String s, int idx) {
        if (idx < 0 || idx >= s.length()) return true;
        char c = s.charAt(idx);
        return !Character.isLetterOrDigit(c) && c != '_';
    }

    /** Simple professional prompt — ChatGPT/Gemini jaisa: jis language me sawal, usi me jawab. */
    private static final String SYSTEM_PROMPT =
            "You are a friendly female voice assistant. "
            + "Always reply in the SAME language the user speaks: "
            + "Urdu speech → reply in Urdu script, "
            + "English speech → reply in English, "
            + "Punjabi speech → reply in Punjabi (Shahmukhi script). "
            + "Keep replies short (2-4 sentences), warm, and complete — "
            + "never stop mid-sentence. "
            + "Phone tools: you CAN open apps — the app opens them on the user's "
            + "screen by itself, before you ever see the command. "
            + "If a message that looks like an app command (open/scroll/type/tap) "
            + "reaches you, the app did not understand that phrasing — warmly ask "
            + "the user to say it more simply. "
            + "NEVER say you cannot open apps. NEVER claim you opened something "
            + "('khol diya'/'open ho gayi') — if you didn't do it, don't say you did. "
            + "Never talk about any 'background system'. "
            + "Sometimes end with a question to keep the conversation going.";

    /** Voice mode: bohat chhote jawab (1-2 jumle) — quota lambi der chale. */
    private static final String SYSTEM_PROMPT_VOICE =
            "You are a friendly female voice assistant, speaking aloud. "
            + "Always reply in the SAME language the user speaks: "
            + "Urdu speech → reply in Urdu script, "
            + "English speech → reply in English, "
            + "Punjabi speech → reply in Punjabi (Shahmukhi script). "
            + "Keep replies VERY short (1-2 sentences max), warm, conversational — "
            + "like talking on a phone call. Never stop mid-sentence. "
            + "Phone tools: you CAN open apps — the app opens them on the user's "
            + "screen by itself, before you ever see the command. "
            + "If a message that looks like an app command (open/scroll/type/tap) "
            + "reaches you, the app did not understand that phrasing — warmly ask "
            + "the user to say it more simply. "
            + "NEVER say you cannot open apps. NEVER claim you opened something "
            + "('khol diya'/'open ho gayi') — if you didn't do it, don't say you did. "
            + "Never talk about any 'background system'.";

    /** Auto Memory ON ho to isay yaadon ke text ke saath jor do (callers karte hain). */
    public static final String AUTO_MEMORY_INSTRUCTION =
            "\nTum KHUD bhi yaadein sambhalti ho (user ko 'yaad rakho' kehne ki zaroorat nahi). "
            + "Agar user koi NAYI ahem mustaqil baat bataye (naam, jagah, pasand, plan, rishta), "
            + "to apne jawab ke AAKHIR me alag line me likho: [remember] mukhtasar baat. "
            + "Agar koi purani yaad ab ghalat/purani ho gayi ho, to likho: [forget] purani baat. "
            + "Ye lines user ko nazar nahi aayengi — inka jawab me zikr kabhi na karna.";

    /**
     * Model ke jawab se [remember]/[forget] lines nikalo, memory me lagao,
     * aur saaf jawab wapas do. Setting off ho to jawab jaisa hai waisa.
     */
    public static String applyAutoMemory(android.content.Context c, String reply) {
        if (c == null || reply == null) return reply;
        if (!SettingsActivity.isAutoMemoryOn(c)) return reply;
        StringBuilder clean = new StringBuilder();
        for (String line : reply.split("\n")) {
            String t = line.trim();
            if (t.regionMatches(true, 0, "[remember]", 0, 10)) {
                Memory.save(c, t.substring(10).trim());
            } else if (t.regionMatches(true, 0, "[forget]", 0, 8)) {
                Memory.delete(c, t.substring(8).trim());
            } else {
                if (clean.length() > 0) clean.append("\n");
                clean.append(line);
            }
        }
        return clean.toString().trim();
    }

    public interface Listener {
        void onStatus(String s);
    }

    public static class Message {
        public final String role; // "user" ya "model"
        public final String text;
        public Message(String role, String text) {
            this.role = role; this.text = text;
        }
    }

    /** Poori history de kar jawab lao. Na mile to SAFF wajah wala Exception.
     *  voiceMode=true → chhote jawab (quota bachao, lambi guftagu).
     *  memories = user ki yaad rakhi hui baatein ("" ho sakta hai). */
    public static String chat(List<Message> history, List<String> geminiKeys,
                             String groqKey, Listener listener) throws Exception {
        return chat(history, geminiKeys, groqKey, listener, false, "");
    }

    public static String chat(List<Message> history, List<String> geminiKeys,
                             String groqKey, Listener listener,
                             boolean voiceMode) throws Exception {
        return chat(history, geminiKeys, groqKey, listener, voiceMode, "");
    }

    public static String chat(List<Message> history, List<String> geminiKeys,
                             String groqKey, Listener listener,
                             boolean voiceMode, String memories) throws Exception {
        if ((geminiKeys == null || geminiKeys.isEmpty())
                && (groqKey == null || groqKey.isEmpty()))
            throw new Exception("No API key added. Add a Gemini or Groq key in Settings.");

        // v33: trim hata diya — purani wali poori history wapas (boss ka hukm).

        // 10 keys tak: jo zinda ho usay try karo, 429 wali ko 30 min chhoro
        String geminiErr = null;
        int tried = 0;
        if (geminiKeys != null) {
            for (String key : geminiKeys) {
                if (keyDead(key)) continue;
                tried++;
                try {
                    String r = polishUrdu(geminiChat(history, key, voiceMode, memories));
                    Diag.note("think_key", "rasta " + tried);
                    return r;
                } catch (Exception e) {
                    geminiErr = e.getMessage();
                    if (geminiErr != null && geminiErr.contains("429")) {
                        markKeyDead(key); // ye key 30 min soye gi, agli try karo
                        Diag.event("Rasta " + tried + " ki limit khatam (429) — 30min aaram");
                        if (listener != null) {
                            try { listener.onStatus("Key " + tried + " limit over — "
                                    + "trying next key..."); }
                            catch (Exception ignored) { }
                        }
                        continue;
                    }
                    // 429/503/404 → agli key; baqi ghaltiyan foran bahar
                    if (!isRetryable(e) && !isModelMissing(e)) throw e;
                    if (listener != null) {
                        try { listener.onStatus("Gemini: " + geminiErr
                                + " — trying next key..."); }
                        catch (Exception ignored) { }
                    }
                }
            }
        }
        if (tried > 0 && geminiErr != null && geminiErr.contains("429")
                && listener != null) {
            try { listener.onStatus("All Gemini keys' limit over — "
                    + "trying backup brain (Groq)..."); }
            catch (Exception ignored) { }
        }

        // DIAG (v32): kitni keys zinda hain — self-diagnosis ke liye
        if (geminiKeys != null && !geminiKeys.isEmpty()) {
            int alive = 0;
            for (String k : geminiKeys) if (!keyDead(k)) alive++;
            Diag.keys(alive, geminiKeys.size());
        }

        if (groqKey != null && !groqKey.isEmpty()) {
            try {
                String r = polishUrdu(groqChat(history, groqKey, voiceMode, memories));
                Diag.note("think_key", "backup dimagh");
                return r;
            } catch (Exception e) {
                throw new Exception("Both brains failed. Gemini: "
                        + SttClient.shortErr(netFriendly(geminiErr))
                        + " | Groq: " + netFriendly(e.getMessage()));
            }
        }
        throw new Exception("Gemini: " + geminiErr
                + " — with a Groq key the backup would run. Check Settings.");
    }

    // ---------- Gemini ----------

    /**
     * Screen ka screenshot Gemini ko dikhao — wo batayegi screen pe kya hai.
     * Jawab user ki language me, chhota (voice ke liye).
     */
    public static String describeImage(Bitmap bmp, List<String> geminiKeys,
                                      String prompt) throws Exception {
        if (bmp == null) throw new Exception("Screenshot is empty.");
        if (geminiKeys == null || geminiKeys.isEmpty())
            throw new Exception("Gemini key missing.");

        // v45: HIGH quality — chota text saaf parhne ke liye (1080p, JPEG q85)
        int w = bmp.getWidth(), h = bmp.getHeight();
        float scale = Math.min(1f, 1080f / Math.max(w, h));
        Bitmap small = Bitmap.createScaledBitmap(bmp,
                Math.max(1, (int) (w * scale)), Math.max(1, (int) (h * scale)), true);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        small.compress(Bitmap.CompressFormat.JPEG, 85, baos);
        String b64 = Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);

        String lastErr = null;
        for (String key : geminiKeys) {
            if (keyDead(key)) continue;
            HttpURLConnection conn = null;
            try {
                URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                        + CHAT_MODEL + ":generateContent?key=" + key);
                conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000); // SPEED 2026-10-06
                conn.setReadTimeout(25000); // SPEED 2026-10-06

                JSONObject body = new JSONObject();
                JSONArray parts = new JSONArray();
                parts.put(new JSONObject().put("text",
                        prompt == null || prompt.isEmpty()
                                ? "Describe briefly what is visible on this phone screenshot. Reply in the user's language, 1-2 sentences."
                                : prompt));
                parts.put(new JSONObject().put("inline_data", new JSONObject()
                        .put("mime_type", "image/jpeg")
                        .put("data", b64)));
                body.put("contents", new JSONArray().put(new JSONObject()
                        .put("role", "user")
                        .put("parts", parts)));
                body.put("generationConfig", new JSONObject()
                        .put("maxOutputTokens", 512)
                        .put("temperature", 0.4)
                        .put("thinkingConfig", new JSONObject()
                                .put("thinkingBudget", 0)));

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                String resp = SttClient.readAll(code >= 200 && code < 300
                        ? conn.getInputStream() : conn.getErrorStream());
                if (code == 429) {
                    markKeyDead(key);
                    lastErr = "429";
                    continue;
                }
                if (code < 200 || code >= 300)
                    throw new Exception("Gemini HTTP " + code);

                JSONArray rp = new JSONObject(resp).getJSONArray("candidates")
                        .getJSONObject(0).getJSONObject("content").getJSONArray("parts");
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < rp.length(); i++)
                    sb.append(rp.getJSONObject(i).optString("text", ""));
                String out = sb.toString().trim();
                if (out.isEmpty()) throw new Exception("Empty reply.");
                return polishUrdu(out);
            } catch (Exception e) {
                lastErr = e.getMessage();
                String m = lastErr == null ? "" : lastErr;
                if (m.contains("429")) { markKeyDead(key); continue; }
                if (!isRetryable(e)) throw e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw new Exception("Could not see screen: " + lastErr);
    }

    private static String geminiChat(List<Message> history, String key,
                                     boolean voiceMode, String memories)
            throws Exception {
        HttpURLConnection conn = null;
        try {
            URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                    + CHAT_MODEL + ":generateContent?key=" + key);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(10000); // SPEED 2026-10-06
            conn.setReadTimeout(25000); // SPEED 2026-10-06

            JSONObject body = new JSONObject();
            String prompt = (voiceMode ? SYSTEM_PROMPT_VOICE : SYSTEM_PROMPT)
                    + (memories == null ? "" : memories);
            body.put("system_instruction",
                    new JSONObject().put("parts", new JSONArray().put(
                            new JSONObject().put("text", prompt))));
            JSONArray contents = new JSONArray();
            for (Message m : history) {
                String role = "model".equals(m.role) ? "model" : "user";
                contents.put(new JSONObject()
                        .put("role", role)
                        .put("parts", new JSONArray().put(
                                new JSONObject().put("text", m.text))));
            }
            body.put("contents", contents);
            body.put("generationConfig", new JSONObject()
                    .put("maxOutputTokens", 2048)
                    .put("temperature", 0.7)
                    // FIX (2026-10-04): 3.x models "thinking" me token kha jate hain —
                    // 300 token me soch khatam, jawab aadha kat jata tha ("...ek ladki ban").
                    // Thinking OFF (tez jawab) + khula budget = poora jawab, hamesha.
                    .put("thinkingConfig", new JSONObject()
                            .put("thinkingBudget", 0)));

            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.close();

            int code = conn.getResponseCode();
            String resp = SttClient.readAll(code >= 200 && code < 300
                    ? conn.getInputStream() : conn.getErrorStream());
            if (code == 400 && resp.contains("API key not valid"))
                throw new Exception("Gemini key wrong (400). Add the key again in Settings.");
            if (code == 429)
                throw new Exception("Gemini 429 — free limit over. Wait a bit or add a key from another Gmail.");
            if (code == 503)
                throw new Exception("Gemini 503 — Google server is busy.");
            if (code == 404)
                throw new Exception("Gemini 404 — model name changed. Update the app.");
            if (code < 200 || code >= 300)
                throw new Exception("Gemini HTTP " + code + ": " + SttClient.shortErr(resp));

            JSONObject json = new JSONObject(resp);
            JSONArray parts = json.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts");
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < parts.length(); i++)
                sb.append(parts.getJSONObject(i).optString("text", ""));
            String out = sb.toString().trim();
            if (out.isEmpty()) throw new Exception("Gemini gave an empty reply.");
            return out;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    // ---------- Groq (backup) ----------

    private static String groqChat(List<Message> history, String key,
                                    boolean voiceMode, String memories)
            throws Exception {
        Exception last = null;
        for (String model : GROQ_MODELS) {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(
                        "https://api.groq.com/openai/v1/chat/completions").openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json");
                conn.setRequestProperty("Authorization", "Bearer " + key);
                conn.setDoOutput(true);
                conn.setConnectTimeout(10000); // SPEED 2026-10-06
                conn.setReadTimeout(25000); // SPEED 2026-10-06

                JSONObject body = new JSONObject();
                body.put("model", model);
                JSONArray msgs = new JSONArray();
                String prompt = (voiceMode ? SYSTEM_PROMPT_VOICE : SYSTEM_PROMPT)
                        + (memories == null ? "" : memories);
                msgs.put(new JSONObject().put("role", "system")
                        .put("content", prompt));
                for (Message m : history)
                    msgs.put(new JSONObject()
                            .put("role", "model".equals(m.role) ? "assistant" : "user")
                            .put("content", m.text));
                body.put("messages", msgs);
                body.put("temperature", 0.7);
                body.put("max_tokens", 2048); // reasoning me budget na khaye — poora jawab

                OutputStream os = conn.getOutputStream();
                os.write(body.toString().getBytes("UTF-8"));
                os.close();

                int code = conn.getResponseCode();
                String resp = SttClient.readAll(code >= 200 && code < 300
                        ? conn.getInputStream() : conn.getErrorStream());
                if (code == 401)
                    throw new Exception("Groq key wrong or expired (401).");
                if (code == 429)
                    throw new Exception("Groq 429 — free limit over.");
                if (code < 200 || code >= 300) {
                    if (resp.contains("model_not_found") || resp.contains("decommissioned")) {
                        last = new Exception("Groq model off: " + model);
                        continue;
                    }
                    throw new Exception("Groq HTTP " + code + ": "
                            + SttClient.shortErr(resp));
                }
                String out = new JSONObject(resp).getJSONArray("choices")
                        .getJSONObject(0).getJSONObject("message")
                        .optString("content", "").trim();
                if (out.isEmpty()) throw new Exception("Groq gave an empty reply.");
                return out;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("(401)") || msg.contains("(429)")))
                    throw e;
                last = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw last != null ? last : new Exception("Could not get a reply from Groq.");
    }

    private static boolean isRetryable(Exception e) {
        String m = e.getMessage();
        return m != null && (m.contains("429") || m.contains("503"));
    }

    private static boolean isModelMissing(Exception e) {
        String m = e.getMessage();
        return m != null && m.contains("404");
    }

    /** Timeout jaisi network ghalti ko saaf lafzon me batao. */
    private static String netFriendly(String m) {
        if (m == null) return "jawab na ban saka";
        String l = m.toLowerCase(java.util.Locale.ROOT);
        if (l.contains("timeout") || l.contains("timed out"))
            return "Internet slow hai ya server ne der lagayi — dobara try karo";
        return m;
    }

    /** Test ke liye khaali history se shuru karo. */
    public static List<Message> newHistory() {
        return new ArrayList<>();
    }
}
