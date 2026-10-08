package com.mujahid.myagent;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Stage 3 (v38): Gemini Live conversation engine — shared.
 * Mic streaming (16kHz PCM) → Live server → awaz wapas (24kHz) — real-time.
 *
 * Purana VAD/Whisper/chat/TTS rasta Live mode me istemal nahi hota —
 * fallback ke liye mehfooz hai (Live fail ho to purana rasta).
 *
 * Note: Live mode me auto-memory ke [remember] tags NAHI bheje jate —
 * wo model ki zubaan se bol diye jayenge. Manual "yaad rakho" chalta hai.
 */
public class LiveTalk {

    private static final String HOST = "generativelanguage.googleapis.com";
    private static final String WS_PATH =
            "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
    private static final String[] LIVE_MODELS = {
            "models/gemini-3.1-flash-live-preview",
            "models/gemini-2.0-flash-live-001",
    };
    private static final String LIVE_VOICE = "Kore"; // uski pasandeeda awaz

    private static final String LIVE_SYSTEM =
            "You are Ayesha, a friendly female voice assistant, speaking aloud. "
            + "Always reply in the SAME language the user speaks: "
            + "Urdu speech → reply in Urdu, "
            + "English speech → reply in English, "
            + "Punjabi speech → reply in Punjabi (Shahmukhi script). "
            + "Keep replies VERY short (1-2 sentences max), warm, conversational — "
            + "like talking on a phone call. Never stop mid-sentence. "
            // v49: theater khatam — sach + asal salahiyat
            + "Phone actions — TUM khud kar sakti ho: apps kholna, WhatsApp chat/send/call, "
            + "screen pe scroll, tap, type, home/back. Ye tumhari asal salahiyatein hain — "
            + "kabhi inkar mat karo, kabhi 'nahi kar sakti' mat kaho. "
            + "Jab user koi phone action kahe, phone usko karta hai aur tumhe uska ASAL natija "
            + "ek message me batata hai — hamesha WAHI natija apne warm lafzon me dohrayo. "
            + "Khud se kabhi 'kar diya / khol diya' mat gharo. "
            + "Agar wo puche 'kar sakti ho?', to kaho 'haan boss, bas bolo kya karna hai'. "
            + "Reminders: if the user says 'yaad dilana' (remind me), the phone schedules it "
            + "automatically — just confirm briefly, never say you cannot remind. "
            // v41: khud-mukhtar Ayesha — seedha-saadha, clear
            + "You are also his acting coach and master content creator. "
            + "When he talks about acting, YouTube, videos or content, guide him like an "
            + "expert coach — specific, practical, encouraging. Otherwise just be his warm Ayesha. "
            + "Your memory: the phone saves what he tells you to remember "
            + "('yaad rakho', 'save kar lo') and deletes what he tells you to forget "
            + "('bhool jao', 'delete kar do'). It happens automatically — just confirm "
            + "briefly in your own warm words. Never say you cannot save, remember or delete. "
            + "Places: if he says 'ye meri factory hai, yaad rakho', the phone saves the "
            + "place with its location — just confirm briefly. "
            + "Your app health: you know your own app's features and any recent error — "
            + "if he asks about an error, explain simply what it is. "
            + "Screen sharing: if he says 'screen share on karo', the phone opens the "
            + "front page for the permission — just confirm briefly. If he asks what's "
            + "on his screen ('screen pe kya hai'), the phone captures it — just confirm "
            + "briefly, never say you cannot see. "
            // v48: live vision hata diya — on-demand snapshot only
            + "Never talk about any 'background system'. "
            // v42: chipku line khatam — chhote waqfe ka zikr nahi
            + "If the conversation just paused briefly, never mention it or act clingy "
            + "about it — only a gap of a full day or more is worth mentioning. "
            + "Sometimes end with a question to keep the conversation going.";

    public interface Listener {
        void onReady();
        void onStatus(String s);
        void onInputText(String partial);
        void onUserTurn(String fullText);
        void onError(String why);
    }

    private volatile boolean running = false;
    private volatile LiveWs ws;
    private volatile boolean sessionBroken = false;
    private Thread micThread;
    /** v50: mic waqai khula ya nahi — andaza khatam. */
    private volatile boolean micOk = false;
    private AudioRecord recorder;
    private AudioTrack track;
    private Listener listener;
    private Context ctx;
    private int reconnectTries = 0;
    /** v50: aakhri tootne ki wajah — screen pe dikhegi, andaza khatam. */
    private volatile String lastDropWhy = "";

    private final Object lock = new Object();
    private boolean setupDone = false;
    private String failReason = null;
    private final StringBuilder curInput = new StringBuilder();
    private final StringBuilder curOutput = new StringBuilder();
    /** Pehli session ho chuki? (reconnect pe use batana hai) */
    private boolean everConnected = false;

    public boolean isRunning() { return running; }

    public void start(Context context, Listener l) {
        if (running) return;
        ctx = context.getApplicationContext();
        listener = l;
        running = true;
        reconnectTries = 0;
        everConnected = false;
        new Thread(this::sessionLoop).start();
    }

    public void stop() {
        running = false;
        synchronized (lock) { lock.notifyAll(); }
        stopMic();
        closeWs();
        stopPlayback();
    }

    /** Usey kuch kehna ho (yaad-confirm waghera) — wo apni awaz me bolegi. */
    public void sayText(String text) {
        LiveWs w = ws;
        if (w == null || !w.isOpen() || !running || text == null) return;
        try { w.sendText(clientTextJson(text)); }
        catch (Exception ignored) { }
    }

    // ---------- session ----------

    private void sessionLoop() {
        List<String> keys = Keys.geminiKeys(ctx);
        if (keys.isEmpty()) {
            if (running) error("Koi Gemini key nahi — Settings me key dalo.");
            return;
        }
        String key = keys.get(0);
        Diag.keys(keys.size(), keys.size()); // v41: sehat me raston ki tadad
        while (running) {
            boolean cleanEnd = runSession(key);
            if (!running) break;
            if (cleanEnd) break;
            reconnectTries++;
            if (reconnectTries > 5) {
                // v50: aakhri wajah samet — taake pata chale kyun tooti
                error("Live connection bar bar toot rahi hai."
                        + (lastDropWhy.isEmpty() ? "" : " Aakhri wajah: " + lastDropWhy));
                return;
            }
            status("Dobara jor raha hun (" + reconnectTries + ")..."
                    + (lastDropWhy.isEmpty() ? "" : " [pichli wajah: " + lastDropWhy + "]"));
            sleepQuiet(3000);
        }
    }

    /** Ek session: joro → setup → mic+speaker → tootne/rokne tak. */
    private boolean runSession(String key) {
        sessionBroken = false;
        boolean ready = false;
        for (String model : LIVE_MODELS) {
            if (!running) return true;
            LiveWs w = new LiveWs();
            resetSessionState();
            w.setListener(makeListener());
            try {
                w.connect(HOST, WS_PATH + "?key=" + key);
                w.sendText(setupJson(model));
                if (waitSetup(20000)) {
                    ws = w;
                    ready = true;
                    break;
                }
            } catch (Exception ignored) { }
            try { w.close(); } catch (Exception ignored) { }
        }
        if (!ready || !running) {
            closeWs();
            return false;
        }
        reconnectTries = 0;
        Diag.event("live session ready");
        safe(() -> listener.onReady());
        // v40: reconnect ho to use batao — silent amnesia khatam
        if (everConnected) {
            sayText("Boss, connection toot gaya tha — wapas aagayi hun!");
        }
        everConnected = true;
        startPlayback();
        startMic();
        // v50: 1.5s me mic na khula to wajah SAAMNE — khamoshi ka raaz khatam
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (InterruptedException ignored) { }
            if (running && !sessionBroken && !micOk) {
                String m = "Mic nahi khul saka — wo tumhari awaz nahi sun sakti.";
                Diag.error("mic", m);
                status("live: " + m);
                try {
                    sayText("Boss, mera mic nahi khul saka — tumhari awaz mujh tak "
                            + "nahi pahunch rahi. Ek baar guftagu band karke dobara shuru karo.");
                } catch (Exception ignored) { }
            }
        }).start();
        synchronized (lock) {
            while (running && !sessionBroken) {
                try { lock.wait(1000); }
                catch (InterruptedException ignored) { }
            }
        }
        boolean clean = !running;
        // v40: gap tracking ke liye session ka end note karo
        if (ctx != null) LiveContext.markSessionEnd(ctx);
        stopMic();
        stopPlayback();
        closeWs();
        return clean;
    }

    private void resetSessionState() {
        synchronized (lock) {
            setupDone = false;
            failReason = null;
            curInput.setLength(0);
            curOutput.setLength(0);
            sessionBroken = false;
        }
    }

    private boolean waitSetup(long ms) {
        synchronized (lock) {
            long end = System.currentTimeMillis() + ms;
            while (!setupDone && failReason == null && running
                    && System.currentTimeMillis() < end) {
                try { lock.wait(500); }
                catch (InterruptedException ignored) { }
            }
            return setupDone;
        }
    }

    private LiveWs.Listener makeListener() {
        return new LiveWs.Listener() {
            @Override public void onText(String json) { handleServer(json); }
            @Override public void onClose(int code, String reason) {
                // v50: wajah SAAMNE — chhupao mat
                String why = "closed " + code
                        + (reason != null && !reason.isEmpty() ? " (" + reason + ")" : "");
                synchronized (lock) {
                    sessionBroken = true;
                    if (failReason == null) failReason = why;
                    lock.notifyAll();
                }
                lastDropWhy = why;
                Diag.event("live drop: " + why);
            }
            @Override public void onError(String why) {
                synchronized (lock) {
                    sessionBroken = true;
                    if (failReason == null) failReason = why;
                    lock.notifyAll();
                }
                lastDropWhy = why;
                Diag.event("live drop: " + why); // v50
                Diag.error("live", why);
            }
        };
    }

    // ---------- server messages ----------

    private void handleServer(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("setupComplete")) {
                synchronized (lock) {
                    setupDone = true;
                    lock.notifyAll();
                }
                // v48: continuous streaming HATA DI — sirf on-demand snapshot
                // (quota bachao; "screen pe kya hai" pe hi capture hoga)
                return;
            }
            if (o.has("serverContent")) {
                JSONObject sc = o.getJSONObject("serverContent");
                if (sc.has("modelTurn")) {
                    JSONArray parts = sc.getJSONObject("modelTurn")
                            .getJSONArray("parts");
                    for (int i = 0; i < parts.length(); i++) {
                        JSONObject p = parts.getJSONObject(i);
                        if (p.has("inlineData")) {
                            byte[] pcm = android.util.Base64.decode(
                                    p.getJSONObject("inlineData")
                                            .getString("data"),
                                    android.util.Base64.DEFAULT);
                            AudioTrack t = track;
                            if (t != null) t.write(pcm, 0, pcm.length);
                        }
                    }
                }
                if (sc.optBoolean("turnComplete", false)) {
                    String full, out;
                    synchronized (lock) {
                        full = curInput.toString().trim();
                        curInput.setLength(0);
                        out = curOutput.toString().trim();
                        curOutput.setLength(0);
                    }
                    // v40: guftagu log karo (date/time ke saath) — kabhi na bhoolne ke liye
                    if (ctx != null && !full.isEmpty()) {
                        final String lf = full, lo = out;
                        new Thread(() -> LiveContext.logTurn(ctx, lf, lo)).start();
                    }
                    if (!full.isEmpty() && running) {
                        final String f = full;
                        safe(() -> listener.onUserTurn(f));
                    }
                }
                return;
            }
            if (o.has("outputTranscription")) {
                // v40: uska jawab bhi joro (log ke liye)
                String t = o.getJSONObject("outputTranscription")
                        .optString("text", "");
                if (!t.isEmpty()) accumOutput(t);
                return;
            }
            if (o.has("inputTranscription")) {
                String t = o.getJSONObject("inputTranscription")
                        .optString("text", "");
                if (!t.isEmpty()) {
                    accumInput(t);
                    final String cur = curInput.toString();
                    safe(() -> listener.onInputText(cur));
                }
                return;
            }
            if (o.has("goAway")) {
                // v50: ye NORMAL end hai (server ka waqt poora) — Diag me nishan rahe
                Diag.event("live goAway — server ka waqt poora (normal end), reconnect ayega");
            }
            // v50: anjaan server message — server ka error yahin chhupta hai
            // (goAway upar handle ho chuka — usay dobara mat gino)
            if (!o.has("goAway")) {
                StringBuilder uk = new StringBuilder();
                java.util.Iterator<String> kit = o.keys();
                while (kit.hasNext()) {
                    if (uk.length() > 0) uk.append(',');
                    uk.append(kit.next());
                }
                if (uk.length() > 0) {
                    Diag.event("live unhandled: " + uk + " :: "
                            + json.substring(0, Math.min(220, json.length())));
                }
            }
        } catch (Exception ignored) { }
    }

    /** Input transcript jorna — jo naya lage wahi lo. */
    private void accumInput(String t) {
        synchronized (lock) {
            String cur = curInput.toString();
            if (t.length() >= cur.length() && t.startsWith(cur)) {
                curInput.setLength(0);
                curInput.append(t);
            } else if (!cur.contains(t)) {
                if (curInput.length() > 0) curInput.append(' ');
                curInput.append(t);
            }
        }
    }

    /** Output transcript jorna (uske jawab ka) — log ke liye. */
    private void accumOutput(String t) {
        synchronized (lock) {
            String cur = curOutput.toString();
            if (t.length() >= cur.length() && t.startsWith(cur)) {
                curOutput.setLength(0);
                curOutput.append(t);
            } else if (!cur.contains(t)) {
                if (curOutput.length() > 0) curOutput.append(' ');
                curOutput.append(t);
            }
        }
    }

    // ---------- mic ----------

    private void startMic() {
        stopMic();
        micOk = false; // v50
        micThread = new Thread(() -> {
            AudioRecord rec = null;
            try {
                int sr = 16000;
                int minBuf = AudioRecord.getMinBufferSize(sr,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                // VOICE_COMMUNICATION = speaker ki goonj khud kaatta hai
                rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sr, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuf, 6400));
                recorder = rec;
                // v50: mic waqai khula? — chup chap marne mat do
                if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                    Diag.error("mic", "AudioRecord initialized nahi hua (state="
                            + rec.getState() + ") — mic DEAD");
                    return;
                }
                rec.startRecording();
                if (rec.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
                    Diag.error("mic", "startRecording fail (recState="
                            + rec.getRecordingState() + ") — mic DEAD");
                    return;
                }
                micOk = true;
                byte[] buf = new byte[1600]; // 50ms
                while (running) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    LiveWs w = ws;
                    if (!running || w == null || !w.isOpen()) break;
                    String b64 = android.util.Base64.encodeToString(
                            buf, 0, n, android.util.Base64.NO_WRAP);
                    JSONObject msg = new JSONObject().put("realtimeInput",
                            new JSONObject().put("audio", new JSONObject()
                                    .put("mimeType", "audio/pcm;rate=16000")
                                    .put("data", b64)));
                    try {
                        w.sendText(msg.toString());
                    } catch (Exception e) {
                        break;
                    }
                }
            } catch (Exception e) {
                // v50: mic thread ki maut ab chhup ke nahi hogi
                Diag.error("mic", "mic thread: " + e.getClass().getSimpleName()
                        + (e.getMessage() != null ? " " + e.getMessage() : ""));
            }
            finally {
                try {
                    if (rec != null) {
                        try { rec.stop(); } catch (Exception ignored) { }
                        rec.release();
                    }
                } catch (Exception ignored) { }
                recorder = null;
            }
        });
        micThread.setDaemon(true);
        micThread.start();
    }

    private void stopMic() {
        micOk = false; // v50
        try {
            AudioRecord rec = recorder;
            recorder = null;
            if (rec != null) {
                try { rec.stop(); } catch (Exception ignored) { }
                try { rec.release(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        micThread = null;
    }

    // ---------- speaker ----------

    private void startPlayback() {
        stopPlayback();
        int sr = 24000;
        int minBuf = AudioTrack.getMinBufferSize(sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT);
        track = new AudioTrack(AudioManager.STREAM_MUSIC, sr,
                AudioFormat.CHANNEL_OUT_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                Math.max(minBuf, sr * 2), AudioTrack.MODE_STREAM);
        track.play();
    }

    private void stopPlayback() {
        try {
            AudioTrack t = track;
            track = null;
            if (t != null) {
                try { t.stop(); } catch (Exception ignored) { }
                t.release();
            }
        } catch (Exception ignored) { }
    }

    private void closeWs() {
        LiveWs w = ws;
        ws = null;
        if (w != null) {
            try { w.close(); } catch (Exception ignored) { }
        }
    }

    // ---------- JSON ----------

    private String setupJson(String model) throws Exception {
        // v40 Yaadein: system me taaza context block — date/time, gap, profile,
        // location, mausam, khabrein. Har session me nayi maloomat.
        String systemText = LIVE_SYSTEM;
        if (ctx != null) {
            try {
                String block = LiveContext.buildBlock(ctx);
                if (!block.isEmpty()) systemText += "\n\n" + block;
            } catch (Exception ignored) { }
        }
        final String sysFinal = systemText;
        JSONObject setup = new JSONObject()
                .put("model", model)
                .put("systemInstruction", new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject()
                                .put("text", sysFinal))))
                .put("generationConfig", new JSONObject()
                        .put("responseModalities",
                                new JSONArray().put("AUDIO"))
                        .put("speechConfig", new JSONObject()
                                .put("voiceConfig", new JSONObject()
                                        .put("prebuiltVoiceConfig",
                                                new JSONObject().put(
                                                        "voiceName", LIVE_VOICE)))))
                .put("inputAudioTranscription", new JSONObject())
                .put("outputAudioTranscription", new JSONObject());
        return new JSONObject().put("setup", setup).toString();
    }

    private String clientTextJson(String text) throws Exception {
        JSONObject turn = new JSONObject()
                .put("role", "user")
                .put("parts", new JSONArray()
                        .put(new JSONObject().put("text", text)));
        JSONObject cc = new JSONObject()
                .put("turns", new JSONArray().put(turn))
                .put("turnComplete", true);
        return new JSONObject().put("clientContent", cc).toString();
    }

    // ---------- chhoti madadgar ----------

    private interface SafeRun { void run(); }

    private void safe(SafeRun r) {
        try { r.run(); } catch (Exception ignored) { }
    }

    private void status(final String s) {
        safe(() -> listener.onStatus(s));
    }

    private void error(final String why) {
        running = false;
        synchronized (lock) { lock.notifyAll(); }
        Diag.event("live error: " + why);
        Diag.error("live", why); // v41: sehat + fori report ke liye
        safe(() -> listener.onError(why));
    }

    private void sleepQuiet(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
