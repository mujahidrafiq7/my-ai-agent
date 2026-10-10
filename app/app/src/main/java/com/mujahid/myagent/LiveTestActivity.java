package com.mujahid.myagent;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.List;

/**
 * Stage 1 (v36): Gemini Live connection test.
 * WebSocket handshake → setup → text bhejo → uski AWAZ suno (Kore voice).
 * Ye sabit karta hai: key, model, protocol — sab Live ke liye tayyar hain.
 * Mic streaming Stage 2 me aayegi.
 */
public class LiveTestActivity extends Activity {

    private static final String HOST = "generativelanguage.googleapis.com";
    private static final String WS_PATH =
            "/ws/google.ai.generativelanguage.v1beta.GenerativeService.BidiGenerateContent";
    /** Pehla naya, doosra purana (fallback) — jo chale. */
    private static final String[] LIVE_MODELS = {
            "models/gemini-3.1-flash-live-preview",
            "models/gemini-2.0-flash-live-001",
    };
    private static final String LIVE_VOICE = "Kore"; // uski pasandeeda awaz Live me bhi

    private TextView status;
    private TextView heardView;
    private Button startBtn;
    private Button talkBtn;
    private Button stopBtn;
    private volatile boolean testing = false;
    private volatile boolean talking = false;
    private LiveWs ws;
    private Thread micThread;
    private AudioRecord recorder;

    private final Object lock = new Object();
    private boolean setupDone = false;
    private boolean turnDone = false;
    private String failReason = null;
    private final StringBuilder transcript = new StringBuilder();
    private AudioTrack track;
    private int audioChunks = 0;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        int[] th = Themes.get(this); // v51 "Rang"
        int bg = th[0], card = th[1], txt = th[2];
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(bg);

        TextView title = new TextView(this);
        title.setText("Gemini Live — Connection Test (Stage 1)");
        title.setTextSize(17);
        title.setTextColor(txt);
        title.setPadding(0, 0, 0, pad / 2);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Handshake → setup → salam bhejega → uski awaz sunai degi.\n"
                + "Mic streaming agle stage me.");
        hint.setTextSize(13);
        hint.setTextColor(th[3]);
        hint.setPadding(0, 0, 0, pad / 2);
        root.addView(hint);

        status = new TextView(this);
        status.setTextColor(txt);
        status.setTextSize(13);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(card);
        sv.addView(status);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sv.setLayoutParams(slp);
        root.addView(sv);

        // Wo kya sun rahi hai — live nazar aayega
        heardView = new TextView(this);
        heardView.setText("👂 ...");
        heardView.setTextSize(13);
        heardView.setTextColor(0xFF2E9BFF);
        heardView.setPadding(0, pad / 2, 0, pad / 2);
        root.addView(heardView);

        startBtn = new Button(this);
        startBtn.setText("Start Live Test");
        startBtn.setOnClickListener(v -> startTest());
        root.addView(startBtn);

        talkBtn = new Button(this);
        talkBtn.setText("Start Live Talk (mic)");
        talkBtn.setOnClickListener(v -> startTalk());
        root.addView(talkBtn);

        stopBtn = new Button(this);
        stopBtn.setText("Stop");
        stopBtn.setEnabled(false);
        stopBtn.setOnClickListener(v -> stopTest());
        root.addView(stopBtn);

        setContentView(root);
    }

    private void log(final String s) {
        runOnUiThread(() -> status.append(s + "\n"));
    }

    private void startTest() {
        if (testing) return;
        testing = true;
        status.setText("");
        startBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        new Thread(this::testRun).start();
    }

    private void stopTest() {
        testing = false;
        talking = false;
        synchronized (lock) { lock.notifyAll(); }
        stopMic();
        try { if (ws != null) ws.close(); } catch (Exception ignored) { }
        stopPlayback();
        runOnUiThread(() -> {
            startBtn.setEnabled(true);
            talkBtn.setEnabled(true);
            stopBtn.setEnabled(false);
        });
    }

    @Override
    protected void onDestroy() {
        stopTest();
        super.onDestroy();
    }

    private void testRun() {
        try {
            List<String> keys = Keys.geminiKeys(this);
            if (keys.isEmpty()) {
                log("Key nahi mili — pehle Settings me Gemini key dalo.");
                return;
            }
            String key = keys.get(0);
            log("Key 1 se test (poori key kabhi screen par nahi aayegi).");
            boolean ok = false;
            for (String model : LIVE_MODELS) {
                if (!testing) return;
                log("\n— Model: " + model + " —");
                if (tryModel(key, model)) { ok = true; break; }
            }
            log(ok ? "\n✅ LIVE TEST KAMYAB!" : "\n❌ Dono models fail — wajah upar dekho.");
        } catch (Exception e) {
            log("❌ " + e.getMessage());
        } finally {
            stopTest();
        }
    }

    // ================= STAGE 2: Live Talk (mic streaming) =================

    private void startTalk() {
        if (testing || talking) return;
        testing = true;
        talking = true;
        status.setText("");
        runOnUiThread(() -> heardView.setText("👂 ..."));
        startBtn.setEnabled(false);
        talkBtn.setEnabled(false);
        stopBtn.setEnabled(true);
        new Thread(this::talkRun).start();
    }

    private void talkRun() {
        try {
            List<String> keys = Keys.geminiKeys(this);
            if (keys.isEmpty()) {
                log("Key nahi mili — pehle Settings me Gemini key dalo.");
                return;
            }
            String key = keys.get(0);

            boolean ok = false;
            String usedModel = "";
            for (String model : LIVE_MODELS) {
                if (!talking) return;
                log("\n— Model: " + model + " —");
                ws = new LiveWs();
                resetState();
                attachListener();
                try {
                    log("Live se jor raha hun...");
                    ws.connect(HOST, WS_PATH + "?key=" + key);
                    log("✓ Handshake OK — setup bhej raha hun...");
                    ws.sendText(setupJson(model));
                    waitFor(() -> setupDone, 20000);
                    if (setupDone && talking) {
                        ok = true;
                        usedModel = model;
                        break;
                    }
                    log("⚠ Setup fail, agla model try...");
                } catch (Exception e) {
                    log("❌ " + e.getMessage());
                }
                try { ws.close(); } catch (Exception ignored) { }
            }
            if (!ok) {
                log("\n❌ Live session nahi bani.");
                return;
            }
            log("\n✅ Session tayyar (" + usedModel + ")");
            log("🎤 Bolo! Wo sun rahi hai — rokne ke liye Stop dabao.");
            startPlayback();
            startMic();
            synchronized (lock) {
                while (talking && testing) {
                    try { lock.wait(1000); }
                    catch (InterruptedException ignored) { }
                }
            }
            log("\n⏹ Baat khatam.");
        } catch (Exception e) {
            log("❌ " + e.getMessage());
        } finally {
            stopTest();
        }
    }

    /** Dono (test + talk) ke liye ek jaisa listener. */
    private void attachListener() {
        ws.setListener(new LiveWs.Listener() {
            @Override public void onText(String json) { handleServer(json); }
            @Override public void onClose(int code, String reason) {
                synchronized (lock) {
                    if (failReason == null)
                        failReason = "Server ne band kiya: " + code + " " + reason;
                    lock.notifyAll();
                }
                log("⚠ Close: " + code + " " + reason);
            }
            @Override public void onError(String why) {
                synchronized (lock) {
                    if (failReason == null) failReason = why;
                    lock.notifyAll();
                }
                log("⚠ " + why);
            }
        });
    }

    /** Mic → 16kHz PCM chunks → Live server (50ms ke tukre). */
    private void startMic() {
        stopMic();
        micThread = new Thread(() -> {
            AudioRecord rec = null;
            try {
                int sr = 16000;
                int minBuf = AudioRecord.getMinBufferSize(sr,
                        AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT);
                // VOICE_COMMUNICATION = speaker ki goonj khud kaatta hai (echo cancel)
                rec = new AudioRecord(MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                        sr, AudioFormat.CHANNEL_IN_MONO,
                        AudioFormat.ENCODING_PCM_16BIT,
                        Math.max(minBuf, 6400));
                recorder = rec;
                rec.startRecording();
                log("🎤 Mic ON");
                byte[] buf = new byte[1600]; // 50ms
                while (talking && testing) {
                    int n = rec.read(buf, 0, buf.length);
                    if (n <= 0) continue;
                    if (!talking || ws == null || !ws.isOpen()) break;
                    String b64 = android.util.Base64.encodeToString(
                            buf, 0, n, android.util.Base64.NO_WRAP);
                    JSONObject msg = new JSONObject().put("realtimeInput",
                            new JSONObject().put("audio", new JSONObject()
                                    .put("mimeType", "audio/pcm;rate=16000")
                                    .put("data", b64)));
                    try {
                        ws.sendText(msg.toString());
                    } catch (Exception e) {
                        log("⚠ Mic send ruka: " + e.getMessage());
                        break;
                    }
                }
            } catch (Exception e) {
                log("❌ Mic: " + e.getMessage());
            } finally {
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
        try {
            if (recorder != null) {
                try { recorder.stop(); } catch (Exception ignored) { }
                try { recorder.release(); } catch (Exception ignored) { }
            }
        } catch (Exception ignored) { }
        recorder = null;
        micThread = null;
    }

    private boolean tryModel(String key, String model) {
        ws = new LiveWs();
        resetState();
        try {
            log("1) TLS + WebSocket handshake...");
            ws.setListener(new LiveWs.Listener() {
                @Override public void onText(String json) { handleServer(json); }
                @Override public void onClose(int code, String reason) {
                    synchronized (lock) {
                        if (failReason == null)
                            failReason = "Server ne band kiya: " + code + " " + reason;
                        lock.notifyAll();
                    }
                    log("⚠ Close: " + code + " " + reason);
                }
                @Override public void onError(String why) {
                    synchronized (lock) {
                        if (failReason == null) failReason = why;
                        lock.notifyAll();
                    }
                    log("⚠ " + why);
                }
            });
            ws.connect(HOST, WS_PATH + "?key=" + key);
            log("✓ Handshake OK");

            log("2) Setup bhej raha hun...");
            ws.sendText(setupJson(model));
            waitFor(() -> setupDone, 20000);
            if (!setupDone || !testing) {
                log("❌ setupComplete nahi aaya"
                        + (failReason != null ? ": " + failReason : "."));
                ws.close();
                return false;
            }
            log("✓ setupComplete — Live session tayyar!");

            log("3) Salam bhej raha hun — uski awaz suno...");
            startPlayback();
            ws.sendText(clientTextJson(
                    "Assalam o alaikum! Apna naam ek jumle me batao."));
            waitFor(() -> turnDone, 45000);
            stopPlayback();
            ws.close();
            if (!turnDone || !testing) {
                log("❌ Jawab mukammal nahi hua"
                        + (failReason != null ? ": " + failReason : "."));
                return false;
            }
            log("✓ Jawab mukammal!");
            String t = transcript.toString().trim();
            if (!t.isEmpty()) log("📝 Us ne kaha: " + t);
            log("🔊 Audio chunks mile: " + audioChunks);
            return audioChunks > 0;
        } catch (Exception e) {
            log("❌ " + e.getMessage());
            try { ws.close(); } catch (Exception ignored) { }
            return false;
        }
    }

    private void resetState() {
        synchronized (lock) {
            setupDone = false;
            turnDone = false;
            failReason = null;
            transcript.setLength(0);
            audioChunks = 0;
        }
    }

    private interface Cond { boolean get(); }

    private void waitFor(Cond c, long ms) {
        synchronized (lock) {
            long end = System.currentTimeMillis() + ms;
            while (!c.get() && failReason == null && testing
                    && System.currentTimeMillis() < end) {
                try { lock.wait(500); }
                catch (InterruptedException ignored) { }
            }
        }
    }

    private void handleServer(String json) {
        try {
            JSONObject o = new JSONObject(json);
            if (o.has("setupComplete")) {
                synchronized (lock) {
                    setupDone = true;
                    lock.notifyAll();
                }
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
                            String b64 = p.getJSONObject("inlineData")
                                    .getString("data");
                            byte[] pcm = android.util.Base64.decode(
                                    b64, android.util.Base64.DEFAULT);
                            if (track != null) track.write(pcm, 0, pcm.length);
                            audioChunks++;
                        } else if (p.has("text")) {
                            transcript.append(p.getString("text"));
                        }
                    }
                }
                if (sc.optBoolean("turnComplete", false)) {
                    synchronized (lock) {
                        turnDone = true;
                        lock.notifyAll();
                    }
                }
                return;
            }
            if (o.has("outputTranscription")) {
                String t = o.getJSONObject("outputTranscription")
                        .optString("text", "");
                if (!t.isEmpty()) transcript.append(t);
                return;
            }
            if (o.has("inputTranscription")) {
                // Wo kya sun rahi hai — live nazar aaye
                String t = o.getJSONObject("inputTranscription")
                        .optString("text", "");
                if (!t.isEmpty()) {
                    final String show = t;
                    runOnUiThread(() -> heardView.setText("👂 " + show));
                }
                return;
            }
            if (o.has("goAway")) log("ℹ goAway: session jald band hogi.");
        } catch (Exception e) {
            log("⚠ Parse: " + e.getMessage());
        }
    }

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
            if (track != null) {
                try { track.stop(); } catch (Exception ignored) { }
                track.release();
            }
        } catch (Exception ignored) { }
        track = null;
    }

    private String setupJson(String model) throws Exception {
        JSONObject setup = new JSONObject()
                .put("model", model)
                .put("systemInstruction", new JSONObject().put("parts",
                        new JSONArray().put(new JSONObject().put("text",
                                "You are Ayesha, a friendly female voice assistant. "
                                + "Reply in the same language the user speaks "
                                + "(Urdu, English or Punjabi). Keep replies very short "
                                + "(1-2 sentences), warm and conversational."))))
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
}
