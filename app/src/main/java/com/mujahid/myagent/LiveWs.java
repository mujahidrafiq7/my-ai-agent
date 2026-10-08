package com.mujahid.myagent;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Random;

import javax.net.ssl.SSLSocketFactory;

/**
 * Chhota WebSocket client (RFC 6455) — sirf Gemini Live API ke liye.
 * Bahar ki library nahi (OkHttp nahi hai) — haath se handshake + framing.
 *
 * v36 (2026-10-07): Stage 1 — sirf connection test. Audio streaming Stage 2 me.
 */
public class LiveWs {

    public interface Listener {
        void onText(String json);
        void onClose(int code, String reason);
        void onError(String why);
    }

    private Socket socket;
    private InputStream in;
    private OutputStream out;
    private volatile boolean open = false;
    private Listener listener;
    private final StringBuilder fragBuf = new StringBuilder();

    public void setListener(Listener l) { listener = l; }
    public boolean isOpen() { return open; }

    /** wss://host + pathWithQuery (is me ?key=API_KEY hota hai) se joro. */
    public void connect(String host, String pathWithQuery) throws Exception {
        Socket s = SSLSocketFactory.getDefault().createSocket(host, 443);
        s.setSoTimeout(30000);
        InputStream sin = s.getInputStream();
        OutputStream sout = s.getOutputStream();

        byte[] keyBytes = new byte[16];
        new Random().nextBytes(keyBytes);
        String wsKey = android.util.Base64.encodeToString(
                keyBytes, android.util.Base64.NO_WRAP);

        String req = "GET " + pathWithQuery + " HTTP/1.1\r\n"
                + "Host: " + host + "\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Key: " + wsKey + "\r\n"
                + "Sec-WebSocket-Version: 13\r\n\r\n";
        sout.write(req.getBytes("UTF-8"));
        sout.flush();

        String status = readHttpLine(sin);
        if (status == null || !status.contains("101")) {
            try { s.close(); } catch (Exception ignored) { }
            throw new Exception("Handshake fail: " + status);
        }
        String accept = null;
        String line;
        while ((line = readHttpLine(sin)) != null && !line.isEmpty()) {
            if (line.toLowerCase(java.util.Locale.ROOT)
                    .startsWith("sec-websocket-accept:")) {
                accept = line.substring(line.indexOf(':') + 1).trim();
            }
        }
        String expected = android.util.Base64.encodeToString(
                MessageDigest.getInstance("SHA-1").digest(
                        (wsKey + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11")
                                .getBytes("UTF-8")),
                android.util.Base64.NO_WRAP);
        if (!expected.equals(accept)) {
            try { s.close(); } catch (Exception ignored) { }
            throw new Exception("Handshake accept key ghalat.");
        }

        socket = s;
        in = sin;
        out = sout;
        s.setSoTimeout(0); // ab blocking read
        open = true;
        startReader();
    }

    private String readHttpLine(InputStream sin) throws Exception {
        StringBuilder sb = new StringBuilder();
        int c;
        boolean gotAny = false;
        while ((c = sin.read()) != -1) {
            gotAny = true;
            if (c == '\r') {
                int n = sin.read();
                if (n == '\n') break;
                sb.append((char) c);
                if (n != -1) sb.append((char) n);
            } else {
                sb.append((char) c);
            }
            if (sb.length() > 8192) break;
        }
        return gotAny ? sb.toString() : null;
    }

    /** Text frame bhejo (client → server hamesha masked hota hai). */
    public synchronized void sendText(String text) throws Exception {
        if (!open) throw new Exception("WebSocket band hai.");
        byte[] payload = text.getBytes("UTF-8");
        out.write(0x81); // FIN + text opcode
        int len = payload.length;
        byte[] mask = new byte[4];
        new Random().nextBytes(mask);
        if (len < 126) {
            out.write(0x80 | len);
        } else if (len < 65536) {
            out.write(0x80 | 126);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        } else {
            out.write(0x80 | 127);
            out.write(0); out.write(0); out.write(0); out.write(0);
            out.write((len >> 24) & 0xFF);
            out.write((len >> 16) & 0xFF);
            out.write((len >> 8) & 0xFF);
            out.write(len & 0xFF);
        }
        out.write(mask);
        for (int i = 0; i < len; i++) out.write(payload[i] ^ mask[i % 4]);
        out.flush();
    }

    private void startReader() {
        Thread reader = new Thread(() -> {
            try {
                while (open) {
                    int b1 = in.read();
                    int b2 = in.read();
                    if (b1 == -1 || b2 == -1) break;
                    boolean fin = (b1 & 0x80) != 0;
                    int opcode = b1 & 0x0F;
                    boolean masked = (b2 & 0x80) != 0;
                    long len = b2 & 0x7F;
                    if (len == 126) {
                        len = ((in.read() & 0xFF) << 8) | (in.read() & 0xFF);
                    } else if (len == 127) {
                        len = 0;
                        for (int i = 0; i < 8; i++) {
                            int bb = in.read();
                            if (bb == -1) throw new Exception("Connection toot gaya.");
                            len = (len << 8) | (bb & 0xFF);
                        }
                    }
                    byte[] maskKey = null;
                    if (masked) {
                        maskKey = new byte[4];
                        readFully(maskKey);
                    }
                    if (len > 16 * 1024 * 1024) {
                        throw new Exception("Frame bohat bara (" + len + ").");
                    }
                    byte[] payload = new byte[(int) len];
                    readFully(payload);
                    if (masked) {
                        for (int i = 0; i < payload.length; i++) {
                            payload[i] ^= maskKey[i % 4];
                        }
                    }

                    if (opcode == 0x8) { // close
                        int code = payload.length >= 2
                                ? ((payload[0] & 0xFF) << 8) | (payload[1] & 0xFF)
                                : 1000;
                        String reason = payload.length > 2
                                ? new String(payload, 2, payload.length - 2, "UTF-8") : "";
                        open = false;
                        if (listener != null) listener.onClose(code, reason);
                        break;
                    } else if (opcode == 0x9) { // ping → pong wapas
                        sendPong(payload);
                    } else if (opcode == 0xA) { // pong → ignore
                        continue;
                    } else if (opcode == 0x1 || opcode == 0x2 || opcode == 0x0) {
                        // text / binary / continuation — Live API JSON bhejta hai
                        if (opcode == 0x1 || opcode == 0x2) fragBuf.setLength(0);
                        fragBuf.append(new String(payload, "UTF-8"));
                        if (fin) {
                            String msg = fragBuf.toString();
                            fragBuf.setLength(0);
                            if (listener != null) listener.onText(msg);
                        }
                    }
                }
            } catch (Exception e) {
                if (open && listener != null) {
                    try { listener.onError("WS read: " + e.getMessage()); }
                    catch (Exception ignored) { }
                }
            } finally {
                open = false;
                try { socket.close(); } catch (Exception ignored) { }
            }
        });
        reader.setDaemon(true);
        reader.start();
    }

    private void readFully(byte[] buf) throws Exception {
        int off = 0;
        while (off < buf.length) {
            int n = in.read(buf, off, buf.length - off);
            if (n == -1) throw new Exception("Connection toot gaya.");
            off += n;
        }
    }

    private synchronized void sendPong(byte[] payload) {
        try {
            out.write(0x8A); // FIN + pong
            byte[] mask = new byte[4];
            new Random().nextBytes(mask);
            int len = Math.min(payload.length, 125);
            out.write(0x80 | len);
            out.write(mask);
            for (int i = 0; i < len; i++) out.write(payload[i] ^ mask[i % 4]);
            out.flush();
        } catch (Exception ignored) { }
    }

    public void close() {
        open = false;
        try {
            synchronized (this) {
                if (out != null) {
                    out.write(0x88); // FIN + close
                    byte[] mask = new byte[4];
                    new Random().nextBytes(mask);
                    out.write(0x80 | 2);
                    out.write(mask);
                    out.write((1000 >> 8) ^ mask[0]);
                    out.write(1000 ^ mask[1]);
                    out.flush();
                }
            }
        } catch (Exception ignored) { }
        try { if (socket != null) socket.close(); } catch (Exception ignored) { }
    }
}
