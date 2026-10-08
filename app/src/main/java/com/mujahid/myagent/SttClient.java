package com.mujahid.myagent;

import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.net.HttpURLConnection;
import java.io.OutputStream;
import java.net.URL;

/**
 * Step 1 — Sun'na: Groq Whisper se awaz → text.
 * Fail-fast: retry ka toofan nahi, ghalti foran saaf lafzon me bahar.
 */
public class SttClient {

    private static final String[] MODELS = {"whisper-large-v3-turbo", "whisper-large-v3"};
    private static final String ENDPOINT =
            "https://api.groq.com/openai/v1/audio/transcriptions";

    /** Audio file ko text me badlo. Na ho to Exception me SAFF wajah. */
    public static String transcribe(File audioFile, String groqKey) throws Exception {
        if (groqKey == null || groqKey.isEmpty())
            throw new Exception("Groq key missing. Add Groq key in Settings.");
        if (audioFile == null || !audioFile.exists())
            throw new Exception("Recording file missing — speak again.");

        Exception last = null;
        for (String model : MODELS) {
            HttpURLConnection conn = null;
            try {
                String boundary = "----MyAgent" + System.currentTimeMillis();
                conn = (HttpURLConnection) new URL(ENDPOINT).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Authorization", "Bearer " + groqKey);
                conn.setRequestProperty("Content-Type",
                        "multipart/form-data; boundary=" + boundary);
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                OutputStream os = conn.getOutputStream();
                writeField(os, boundary, "model", model);
                writeField(os, boundary, "language", "ur"); // Urdu sunni hai — script fix
                // Whisper ko pehle se batao ke ye lafz sunne hain (ghalat-sunna kam)
                writeField(os, boundary, "prompt",
                        "WhatsApp kholo, WhatsApp open karo, YouTube kholo, "
                        + "wapas jao, home pe jao, message bhejo, call karo");
                writeField(os, boundary, "response_format", "json");
                String fh = "--" + boundary + "\r\n"
                        + "Content-Disposition: form-data; name=\"file\"; "
                        + "filename=\"voice.m4a\"\r\n"
                        + "Content-Type: audio/mp4\r\n\r\n";
                os.write(fh.getBytes("UTF-8"));
                FileInputStream fis = new FileInputStream(audioFile);
                byte[] buf = new byte[8192];
                int n;
                while ((n = fis.read(buf)) != -1) os.write(buf, 0, n);
                fis.close();
                os.write("\r\n".getBytes("UTF-8"));
                os.write(("--" + boundary + "--\r\n").getBytes("UTF-8"));
                os.flush();
                os.close();

                int code = conn.getResponseCode();
                String resp = readAll(code >= 200 && code < 300
                        ? conn.getInputStream() : conn.getErrorStream());

                if (code == 401)
                    throw new Exception("Groq key wrong or expired (401). "
                            + "Add a new key in Settings.");
                if (code == 429)
                    throw new Exception("Groq free limit hit (429). "
                            + "Wait a bit or add another key.");
                if (code < 200 || code >= 300) {
                    String m = "Groq Whisper HTTP " + code + ": " + shortErr(resp);
                    // Model ka naam purana ho to agla model try karo, warna bahar
                    if (resp != null && (resp.contains("model_not_found")
                            || resp.contains("does not exist")
                            || resp.contains("decommissioned"))) {
                        last = new Exception(m);
                        continue;
                    }
                    throw new Exception(m);
                }

                String text = new JSONObject(resp).optString("text", "").trim();
                if (text.isEmpty())
                    throw new Exception("Whisper heard nothing — "
                            + "speak near the mic and try again.");
                return text;
            } catch (Exception e) {
                // Saaf wajah wali ghaltiyan foran bahar — chupana mana hai
                String msg = e.getMessage();
                if (msg != null && (msg.contains("(401)") || msg.contains("(429)")
                        || msg.contains("key nahi lagi") || msg.contains("kuch suna hi nahi")))
                    throw e;
                last = e;
            } finally {
                if (conn != null) conn.disconnect();
            }
        }
        throw last != null ? last
                : new Exception("Could not hear — reason unknown.");
    }

    private static void writeField(OutputStream os, String boundary,
                                   String name, String value) throws Exception {
        String s = "--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n"
                + value + "\r\n";
        os.write(s.getBytes("UTF-8"));
    }

    static String readAll(java.io.InputStream in) throws Exception {
        if (in == null) return "";
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
        in.close();
        return baos.toString("UTF-8");
    }

    static String shortErr(String s) {
        if (s == null) return "";
        s = s.replaceAll("\\s+", " ").trim();
        return s.length() > 160 ? s.substring(0, 160) + "..." : s;
    }
}
