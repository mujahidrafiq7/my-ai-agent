package com.mujahid.myagent;

import android.content.Context;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Microsoft Edge ki neural TTS — BINA kisi key ke, human-like awaz.
 * Gemini TTS par 429 (free limit) aaye to ye backup bolti hai,
 * taake robotic phone-TTS kabhi na sunni pare.
 * Awaz: ur-PK-UzmaNeural (larki ki Urdu awaz). Output: MP3.
 *
 * WebSocket protocol haath se implement kiya hai (koi library nahi).
 */
public class EdgeTts {

    private static final String HOST = "speech.platform.bing.com";
    private static final String WS_PATH = "/consumer/speech/synthesize/readaloud/edge/v1"
            + "?TrustedClientToken=6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    private static final String TOKEN = "6A5AA1D4EAFF4E9FB37E23D68491D6F4";
    /** Chromium full version — Sec-MS-GEC-Version isi se banta hai. */
    private static final String GEC_VERSION = "1-143.0.3650.75";
    private static final int TIMEOUT_MS = 20000; // SPEED 2026-10-06: 30s → 20s

    /** Text ko MP3 file me badlo. voice: "ur-PK-UzmaNeural" / "en-US-AriaNeural".
     *  rate: "-8%" jaisa; pitch: "+15Hz" jaisa (oonchi pitch = young/cute awaz). */
    public static File synthesize(Context ctx, String text, String voice,
                                  String rate, String pitch) throws Exception {
        String safe = text.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;").replace("\n", " ");
        if (safe.length() > 800) safe = cutAtSentence(safe, 800);
        if (safe.trim().isEmpty()) throw new Exception("EdgeTts: empty text");

        String connId = UUID.randomUUID().toString().replace("-", "");
        String reqId = UUID.randomUUID().toString().replace("-", "");
        // Microsoft ka naya Sec-MS-GEC DRM token — purana static token ab 403 deta hai
        String drm = drmQuery();

        SSLSocket sock = (SSLSocket) SSLSocketFactory.getDefault().createSocket();
        sock.connect(new InetSocketAddress(HOST, 443), 15000);
        sock.setSoTimeout(TIMEOUT_MS);
        InputStream in = sock.getInputStream();
        OutputStream out = sock.getOutputStream();

        try {
            // ---- WebSocket handshake (X-ConnectionId sath bhejo — official client jaisa) ----
            byte[] keyBytes = new byte[16];
            new SecureRandom().nextBytes(keyBytes);
            String wsKey = android.util.Base64.encodeToString(
                    keyBytes, android.util.Base64.NO_WRAP);
            String endpoint = WS_PATH + drm + "&ConnectionId=" + connId;
            String req = "GET " + endpoint + " HTTP/1.1\r\n"
                    + "Host: " + HOST + "\r\n"
                    + "User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
                    + "AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/143.0.0.0 Safari/537.36 Edg/143.0.0.0\r\n"
                    + "Accept-Encoding: gzip, deflate, br\r\n"
                    + "Accept-Language: en-US,en;q=0.9\r\n"
                    + "Cache-Control: no-cache\r\n"
                    + "Pragma: no-cache\r\n"
                    + "Connection: Upgrade\r\n"
                    + "Upgrade: websocket\r\n"
                    + "Sec-WebSocket-Version: 13\r\n"
                    + "Sec-WebSocket-Key: " + wsKey + "\r\n"
                    + "Origin: chrome-extension://jdiccldimpdaibmpdkjnbmckianbfoldl\r\n\r\n";
            out.write(req.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            String resp = readHttpHeaders(in);
            if (!resp.startsWith("HTTP/1.1 101")) {
                throw new Exception("EdgeTts handshake fail: "
                        + resp.substring(0, Math.min(60, resp.length())));
            }

            String ts = timestamp();

            // ---- speech.config ----
            String config = "X-Timestamp:" + ts + "\r\n"
                    + "Content-Type:application/json; charset=utf-8\r\n"
                    + "Path:speech.config\r\n\r\n"
                    + "{\"context\":{\"synthesis\":{\"audio\":{\"metadataoptions\":"
                    + "{\"sentenceBoundaryEnabled\":\"false\",\"wordBoundaryEnabled\":\"false\"},"
                    + "\"outputFormat\":\"audio-24khz-96kbitrate-mono-mp3\"}}}}";
            sendText(out, config);

            // ---- SSML ----
            String lang = voice.length() >= 5 ? voice.substring(0, 5) : "ur-PK";
            String ssml = "<speak version='1.0' "
                    + "xmlns='http://www.w3.org/2001/10/synthesis' xml:lang='" + lang + "'>"
                    + "<voice name='" + voice + "'>"
                    + "<prosody pitch='" + pitch + "' rate='" + rate + "' volume='+0%'>"
                    + safe + "</prosody></voice></speak>";
            String ssmlMsg = "X-RequestId:" + reqId + "\r\n"
                    + "Content-Type:application/ssml+xml\r\n"
                    + "X-Timestamp:" + ts + "\r\n"
                    + "Path:ssml\r\n\r\n" + ssml;
            sendText(out, ssmlMsg);

            // ---- Jawab parho: audio jama karo, turn.end par ruko ----
            ByteArrayOutputStream audio = new ByteArrayOutputStream();
            String serverErr = null; // server ne error bheja to wajah qaid karo
            long deadline = System.currentTimeMillis() + TIMEOUT_MS;
            while (System.currentTimeMillis() < deadline) {
                WsFrame f = readFrame(in, out);
                if (f == null) break;
                if (f.opcode == 0x1) { // text
                    String msg = new String(f.payload, StandardCharsets.UTF_8);
                    if (msg.contains("Path:turn.end")) break;
                    if (serverErr == null
                            && (msg.contains("\"code\"") || msg.contains("error"))) {
                        serverErr = msg.substring(0, Math.min(220, msg.length()))
                                .replace("\r", " ").replace("\n", " ");
                    }
                } else if (f.opcode == 0x2) { // binary
                    // FIX (2026-10-04): asal format = [2-byte header len][headers][audio].
                    // Beech me "\r\n\r\n" hota HI NAHI — purana code isi liye
                    // har audio chunk phenk deta tha ("audio nahi aaya").
                    byte[] p = f.payload;
                    if (p.length > 2) {
                        int headerLen = ((p[0] & 0xFF) << 8) | (p[1] & 0xFF);
                        int start = 2 + headerLen;
                        if (headerLen > 0 && start < p.length) {
                            String headers = new String(p, 2, headerLen,
                                    StandardCharsets.US_ASCII);
                            if (headers.contains("Path:audio")) {
                                audio.write(p, start, p.length - start);
                            }
                        }
                    }
                } else if (f.opcode == 0x8) { // close
                    break;
                }
            }

            byte[] mp3 = audio.toByteArray();
            if (mp3.length < 1000) {
                throw new Exception("EdgeTts: no audio received"
                        + (serverErr != null ? " — server: " + serverErr : ""));
            }
            File out3 = new File(ctx.getCacheDir(), "edge_tts.mp3");
            FileOutputStream fos = new FileOutputStream(out3);
            fos.write(mp3);
            fos.close();
            return out3;
        } finally {
            try { sock.close(); } catch (Exception ignored) { }
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)",
                Locale.US).format(new Date());
    }

    /**
     * Microsoft ka Sec-MS-GEC DRM token (2026 se lazmi — is ke baghair 403).
     * Windows ticks (har 3 sec ka window) + TrustedClientToken ka
     * SHA-256 (UPPERCASE hex). edge-fx-tts wala verified tariqa.
     */
    private static String drmQuery() throws Exception {
        long ticks = (System.currentTimeMillis() / 1000 + 11644473600L)
                * 10000000L;
        ticks = ticks - (ticks % 3000000000L);
        java.security.MessageDigest md =
                java.security.MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest((ticks + TOKEN).getBytes(
                java.nio.charset.StandardCharsets.US_ASCII));
        StringBuilder hex = new StringBuilder(64);
        for (byte b : hash) hex.append(String.format(Locale.US, "%02X", b));
        return "&Sec-MS-GEC=" + hex + "&Sec-MS-GEC-Version=" + GEC_VERSION;
    }

    /** Jumle ke beech me mat kato — limit ke andar aakhri poora jumla lo. */
    private static String cutAtSentence(String s, int limit) {
        if (s.length() <= limit) return s;
        int cut = -1;
        for (String end : new String[]{". ", "? ", "! ", "۔ "}) {
            int i = s.lastIndexOf(end, limit);
            if (i > cut) cut = i;
        }
        if (cut > limit / 4) return s.substring(0, cut + 1).trim();
        int sp = s.lastIndexOf(' ', limit);
        return (sp > limit / 2 ? s.substring(0, sp) : s.substring(0, limit)).trim();
    }

    private static String readHttpHeaders(InputStream in) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] last4 = new byte[4];
        int count = 0, b;
        while ((b = in.read()) != -1) {
            bos.write(b);
            last4[count % 4] = (byte) b;
            count++;
            if (count >= 4
                    && last4[(count - 4) % 4] == '\r' && last4[(count - 3) % 4] == '\n'
                    && last4[(count - 2) % 4] == '\r' && last4[(count - 1) % 4] == '\n') {
                break;
            }
            if (count > 16384) break;
        }
        return new String(bos.toByteArray(), StandardCharsets.US_ASCII);
    }

    private static void sendText(OutputStream out, String msg) throws Exception {
        byte[] payload = msg.getBytes(StandardCharsets.UTF_8);
        ByteArrayOutputStream frame = new ByteArrayOutputStream();
        frame.write(0x81); // FIN + text opcode
        byte[] mask = new byte[4];
        new SecureRandom().nextBytes(mask);
        int len = payload.length;
        if (len < 126) {
            frame.write(0x80 | len);
        } else if (len < 65536) {
            frame.write(0x80 | 126);
            frame.write((len >> 8) & 0xFF);
            frame.write(len & 0xFF);
        } else {
            frame.write(0x80 | 127);
            frame.write(new byte[]{0, 0, 0, 0,
                    (byte) ((len >> 24) & 0xFF), (byte) ((len >> 16) & 0xFF),
                    (byte) ((len >> 8) & 0xFF), (byte) (len & 0xFF)});
        }
        frame.write(mask);
        for (int i = 0; i < len; i++) {
            frame.write(payload[i] ^ mask[i % 4]);
        }
        out.write(frame.toByteArray());
        out.flush();
    }

    private static class WsFrame {
        int opcode;
        byte[] payload;
    }

    private static WsFrame readFrame(InputStream in, OutputStream out)
            throws Exception {
        int b0 = in.read();
        int b1 = in.read();
        if (b0 == -1 || b1 == -1) return null;
        int opcode = b0 & 0x0F;
        boolean masked = (b1 & 0x80) != 0;
        long len = b1 & 0x7F;
        if (len == 126) {
            len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
        } else if (len == 127) {
            len = 0;
            for (int i = 0; i < 8; i++) len = (len << 8) | (in.read() & 0xFF);
        }
        byte[] mask = null;
        if (masked) {
            mask = new byte[4];
            readFully(in, mask);
        }
        if (len > 8 * 1024 * 1024) throw new Exception("EdgeTts: frame too large");
        byte[] payload = new byte[(int) len];
        readFully(in, payload);
        if (masked) {
            for (int i = 0; i < payload.length; i++) payload[i] ^= mask[i % 4];
        }
        if (opcode == 0x9) { // ping -> pong
            ByteArrayOutputStream pong = new ByteArrayOutputStream();
            pong.write(0x8A);
            pong.write(payload.length);
            pong.write(payload);
            out.write(pong.toByteArray());
            out.flush();
            return readFrame(in, out);
        }
        WsFrame f = new WsFrame();
        f.opcode = opcode;
        f.payload = payload;
        return f;
    }

    private static void readFully(InputStream in, byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n == -1) throw new Exception("EdgeTts: connection broke");
            off += n;
        }
    }

    private static int indexOf(byte[] hay, byte[] needle) {
        outer:
        for (int i = 0; i <= hay.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (hay[i + j] != needle[j]) continue outer;
            }
            return i;
        }
        return -1;
    }
}
