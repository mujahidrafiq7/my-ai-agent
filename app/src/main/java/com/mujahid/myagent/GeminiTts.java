package com.mujahid.myagent;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * Gemini ki PYARI awaz — "Kore" female voice (Stonic jaisi meethi).
 * Model: gemini-3.1-flash-tts-preview (live model).
 * 24kHz 16-bit mono PCM wapas deta hai — AudioTrack se bajta hai.
 *
 * Quota chat ke SAATH share hoti hai (ek hi key/project).
 * 429 aaye to 30 min ka breaker — har reply par waqt zaya nahi hoga,
 * Edge backup foran bolegi. Settings me Save dabane par breaker reset.
 */
public class GeminiTts {

    private static final String MODEL = "gemini-3.1-flash-tts-preview";
    private static final String VOICE = "Kore"; // clear, calming female voice
    /** Har key apna 30 min ka 429-break leti hai — baqi keys chalti rehengi. */
    private static final java.util.Map<String, Long> keyDeadUntil =
            new java.util.HashMap<>();

    public static void resetBreaker() { keyDeadUntil.clear(); }

    /**
     * 24kHz 16-bit mono PCM bytes. Keys tarteeb se try hoti hain —
     * 429 wali 30 min ke liye chhor di jati hai, agli key sambhalti hai.
     * Sab fail hon to foran Exception — taake Edge backup sambhale.
     */
    public static byte[] synthesize(java.util.List<String> keys, String text)
            throws Exception {
        if (keys == null || keys.isEmpty())
            throw new Exception("Gemini key missing");
        String lastErr = null;
        for (String key : keys) {
            Long until = keyDeadUntil.get(key);
            if (until != null && System.currentTimeMillis() < until) continue;
            try {
                return synthesizeOne(key, text);
            } catch (Exception e) {
                lastErr = e.getMessage();
                if (lastErr != null && lastErr.contains("429")) {
                    keyDeadUntil.put(key,
                            System.currentTimeMillis() + 30 * 60 * 1000);
                    continue; // agli key
                }
                throw e;
            }
        }
        throw new Exception(lastErr != null ? lastErr
                : "429 (all Gemini keys' voice quota is off)");
    }

    /** Ek key se PCM lao. */
    private static byte[] synthesizeOne(String key, String text) throws Exception {

        URL url = new URL("https://generativelanguage.googleapis.com/v1beta/models/"
                + MODEL + ":generateContent?key=" + key);
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod("POST");
        c.setRequestProperty("Content-Type", "application/json");
        c.setDoOutput(true);
        c.setConnectTimeout(10000); // SPEED 2026-10-06: slow TTS foran fail → jaldi fallback
        c.setReadTimeout(25000); // SPEED 2026-10-06

        String safe = text.replace("\\", " ").replace("\"", "'")
                .replace("\n", " ");
        if (safe.length() > 800) safe = safe.substring(0, 800);

        JSONObject body = new JSONObject()
                .put("contents", new JSONArray().put(new JSONObject()
                        .put("parts", new JSONArray().put(new JSONObject()
                                .put("text", safe)))))
                .put("generationConfig", new JSONObject()
                        .put("responseModalities", new JSONArray().put("AUDIO"))
                        .put("speechConfig", new JSONObject()
                                .put("voiceConfig", new JSONObject()
                                        .put("prebuiltVoiceConfig", new JSONObject()
                                                .put("voiceName", VOICE)))));

        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();

        int code = c.getResponseCode();
        if (code == 429) {
            // 429 yahan mark hoga — synthesize() wali loop agli key legi
            throw new Exception("429 — Gemini voice quota over (30 min break)");
        }
        if (code < 200 || code >= 300)
            throw new Exception("Gemini TTS HTTP " + code);

        String resp = readAll(c.getInputStream());
        String b64 = new JSONObject(resp)
                .getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts").getJSONObject(0)
                .getJSONObject("inlineData").getString("data");
        byte[] pcm = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
        if (pcm.length < 1000) throw new Exception("Gemini TTS: empty audio received");
        return pcm;
    }

    private static String readAll(InputStream in) throws Exception {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
        in.close();
        return new String(b.toByteArray(), StandardCharsets.UTF_8);
    }
}
