package com.mujahid.myagent;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.media.MediaPlayer;
import android.speech.tts.TextToSpeech;

import java.io.File;
import java.io.FileInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Step 1 — Bolna: 3 darwaze, KABHI khamosh nahi. SIMPLE.
 *  1) Gemini TTS ("Kore" pyari awaz — usi Gemini key se)
 *  2) Edge TTS (bina key)
 *  3) Phone ki TTS (AAKHRI SAHARA — hamesha bolti hai)
 * Har naakaami ki wajah listener tak — screen par nazar ayegi.
 */
public class TtsClient {

    public interface Listener {
        void onStatus(String s);   // "kya ho raha hai" — hamesha nazar ayega
        void onDone();             // bolna mukammal
        void onError(String reason); // teenon darwaze band — wajah screen par
    }

    // Mic band → jawab band: jo baj raha ho use foran roko
    private static volatile android.media.AudioTrack curTrack;
    private static volatile MediaPlayer curPlayer;
    private static volatile TextToSpeech curTts;
    private static volatile boolean cancelled = false;

    /** Foran khamosh — jo baj raha hai roko, jo ane wala hai use roko. */
    public static void stop() {
        cancelled = true;
        try { if (curTrack != null) curTrack.stop(); } catch (Exception ignored) { }
        try { if (curPlayer != null) curPlayer.stop(); } catch (Exception ignored) { }
        try { if (curTts != null) curTts.stop(); } catch (Exception ignored) { }
    }

    public static void speak(Context activity, String text,
                             java.util.List<String> geminiKeys,
                             Listener listener) {
        if (text == null || text.trim().isEmpty()) {
            listener.onError("No text to speak.");
            return;
        }
        final String say = text.trim();
        cancelled = false; // naya bol = naya iraada
        try { if (curTts != null) curTts.shutdown(); } catch (Exception ignored) { }
        curTts = null;

        // v34 STREAMING (2026-10-07): ek se zyada jumle hon to PIPELINE —
        // pehla jumla bajte hue doosra banta hai. Pehli awaz ~2s me, poori ka wait nahi.
        List<String> sents = splitSentences(say);
        if (sents.size() > 1) {
            speakPipelined(activity, sents, geminiKeys, listener);
        } else {
            speakSequential(activity, say, geminiKeys, listener);
        }
    }

    /** Purana wala seedha rasta: poori awaz banao → bajao (1 jumla). */
    private static void speakSequential(Context activity, String say,
                                        java.util.List<String> geminiKeys,
                                        Listener listener) {
        new Thread(() -> {
            List<String> reasons = new ArrayList<>();
            final long ttsStart = System.currentTimeMillis();

            // v33 (2026-10-06): RACE hata di — purani wali line wapas.
            // Pehle hamesha uski pasandeeda "Kore" awaz, na mile to Edge, phir phone.
            // (Race me awaz badal jati thi — human-like feel kharab hoti thi.)

            // Darwaza 1: Gemini ki PYARI awaz ("Kore") — keys tarteeb se
            sayStatus(listener, "Trying Gemini voice...");
            try {
                if (cancelled) return;
                byte[] pcm = GeminiTts.synthesize(geminiKeys, say);
                if (cancelled) return;
                Diag.stage("tts", System.currentTimeMillis() - ttsStart);
                Diag.note("tts_door", "gemini");
                playPcmBlocking(pcm);
                listener.onDone();
                return;
            } catch (Exception e) {
                reasons.add("Gemini: " + friendly(e));
            }

            // Darwaza 2: Edge TTS — jis language ka jawab, usi ki awaz
            boolean latin = isLatin(say);
            String voice = latin ? "en-US-AriaNeural" : "ur-PK-UzmaNeural";
            String rate = latin ? "+0%" : "-8%";
            String pitch = latin ? "+8Hz" : "+15Hz";
            sayStatus(listener, "Trying Edge TTS...");
            try {
                if (cancelled) return;
                File mp3 = EdgeTts.synthesize(activity, say, voice, rate, pitch);
                if (cancelled) return;
                Diag.stage("tts", System.currentTimeMillis() - ttsStart);
                Diag.note("tts_door", "edge");
                playMp3Blocking(activity, mp3);
                listener.onDone();
                return;
            } catch (Exception e) {
                reasons.add("Edge: " + friendly(e));
            }

            // Darwaza 3: phone ki TTS — AAKHRI SAHARA, hamesha bolti hai
            sayStatus(listener, "Speaking with phone voice...");
            final String why = join(reasons);
            try {
                if (cancelled) return;
                Diag.note("tts_door", "phone");
                phoneSpeak(activity, say);
            } catch (Exception e) {
                reasons.add("Phone TTS: " + friendly(e));
            }
            // Wajah screen par — khamoshi mana hai
            listener.onError(why);
        }).start();
    }

    /**
     * PIPELINE (v34): jumla 1 bajte hue jumla 2 banta hai — ChatGPT-voice jaisa ehsaas.
     * Pehli awaz ~2s me aa jati hai; poori awaz ka intezar nahi hota.
     * Mic-off (cancelled) beech me bhi foran rokta hai — purana rule barkarar.
     */
    private static void speakPipelined(Context activity, List<String> sents,
                                       java.util.List<String> geminiKeys,
                                       Listener listener) {
        new Thread(() -> {
            final long ttsStart = System.currentTimeMillis();
            boolean firstSound = false;

            Object cur = synthSentence(activity, sents.get(0), geminiKeys);
            int i = 0;
            while (i < sents.size() && !cancelled) {
                if (cur == null) {
                    // Ye jumla na ban saka — baaki sab phone TTS se (offline, hamesha bolti hai)
                    Diag.note("tts_door", "phone-mid");
                    try {
                        phoneSpeak(activity, joinSentences(sents, i));
                        listener.onDone();
                    } catch (Exception e) {
                        listener.onError("Voice failed: " + friendly(e));
                    }
                    return;
                }

                // Agla jumla background me banao, jab ye baj raha ho
                final String nextText = (i + 1 < sents.size()) ? sents.get(i + 1) : null;
                final Object[] nextHolder = new Object[1];
                final boolean[] nextDone = new boolean[1];
                if (nextText != null) {
                    Thread st = new Thread(() -> {
                        nextHolder[0] = synthSentence(activity, nextText, geminiKeys);
                        synchronized (nextDone) {
                            nextDone[0] = true;
                            nextDone.notifyAll();
                        }
                    });
                    st.setDaemon(true);
                    st.start();
                }

                // Ye jumla bajao
                try {
                    if (!firstSound) {
                        Diag.stage("tts", System.currentTimeMillis() - ttsStart);
                        firstSound = true;
                    }
                    if (cur instanceof byte[]) {
                        Diag.note("tts_door", "gemini-pipe");
                        playPcmBlocking((byte[]) cur);
                    } else if (cur instanceof File) {
                        Diag.note("tts_door", "edge-pipe");
                        playMp3Blocking(activity, (File) cur);
                    }
                } catch (Exception e) {
                    cur = null; // bajane me ghalti → baaki phone se
                    continue;
                }

                i++;
                if (nextText != null) {
                    // Agle jumle ki awaz ka wait (max 30s)
                    synchronized (nextDone) {
                        long end = System.currentTimeMillis() + 30000;
                        while (!nextDone[0] && !cancelled
                                && System.currentTimeMillis() < end) {
                            try { nextDone.wait(500); }
                            catch (InterruptedException ignored) { }
                        }
                    }
                    cur = nextHolder[0];
                }
            }
            if (cancelled) return; // mic-off → khamoshi (purana rule)
            listener.onDone();
        }).start();
    }

    /** Ek jumla: pehle Gemini ("Kore"), phir Edge. byte[] ya File; na mile to null. */
    private static Object synthSentence(Context activity, String sentence,
                                        java.util.List<String> geminiKeys) {
        if (cancelled) return null;
        try {
            byte[] pcm = GeminiTts.synthesize(geminiKeys, sentence);
            if (!cancelled && pcm != null) return pcm;
        } catch (Exception ignored) { }
        if (cancelled) return null;
        try {
            boolean latin = isLatin(sentence);
            File mp3 = EdgeTts.synthesize(activity, sentence,
                    latin ? "en-US-AriaNeural" : "ur-PK-UzmaNeural",
                    latin ? "+0%" : "-8%",
                    latin ? "+8Hz" : "+15Hz");
            if (!cancelled && mp3 != null) return mp3;
        } catch (Exception ignored) { }
        return null;
    }

    /** Jawab ko jumlon me todo — nishan jumle ke saath rahe. */
    private static List<String> splitSentences(String s) {
        List<String> out = new ArrayList<>();
        for (String p : s.split("(?<=[.!?۔])\\s+")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        if (out.isEmpty()) out.add(s);
        // Bohat chhota tukra (2 lafz se kam) pichhle jumle se jor do
        List<String> merged = new ArrayList<>();
        for (String p : out) {
            if (!merged.isEmpty() && p.split("\\s+").length < 3) {
                merged.set(merged.size() - 1, merged.get(merged.size() - 1) + " " + p);
            } else {
                merged.add(p);
            }
        }
        return merged;
    }

    private static String joinSentences(List<String> sents, int from) {
        StringBuilder sb = new StringBuilder();
        for (int k = from; k < sents.size(); k++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(sents.get(k));
        }
        return sb.toString();
    }

    /** Zyada Latin letters hon to English samjho, warna Urdu. */
    private static boolean isLatin(String s) {
        int latin = 0, arabic = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')) latin++;
            else if (c >= 0x0600 && c <= 0x06FF) arabic++;
        }
        return latin > arabic;
    }

    // ---------- helpers ----------

    /** Gemini ka PCM (24kHz mono 16-bit) bajao — cancel hote hi ruko. */
    private static void playPcmBlocking(byte[] pcm) throws Exception {
        android.media.AudioTrack track = new android.media.AudioTrack(
                android.media.AudioManager.STREAM_MUSIC, 24000,
                android.media.AudioFormat.CHANNEL_OUT_MONO,
                android.media.AudioFormat.ENCODING_PCM_16BIT,
                pcm.length, android.media.AudioTrack.MODE_STATIC);
        curTrack = track;
        try {
            track.write(pcm, 0, pcm.length);
            if (cancelled) return;
            track.play();
            long total = (pcm.length * 1000L) / (24000 * 2) + 300;
            for (long t = 0; t < total && !cancelled; t += 200)
                Thread.sleep(200);
        } finally {
            try { track.stop(); } catch (Exception ignored) { }
            track.release();
            if (curTrack == track) curTrack = null;
        }
    }

    private static void playMp3Blocking(Context a, File mp3) throws Exception {
        MediaPlayer mp = new MediaPlayer();
        curPlayer = mp;
        try {
            FileInputStream fis = new FileInputStream(mp3);
            mp.setDataSource(fis.getFD());
            fis.close();
            mp.prepare();
            final Object lock = new Object();
            final boolean[] done = {false};
            mp.setOnCompletionListener(m -> { synchronized (lock) {
                done[0] = true; lock.notifyAll(); } });
            mp.start();
            synchronized (lock) {
                long end = System.currentTimeMillis() + 60000;
                while (!done[0] && !cancelled
                        && System.currentTimeMillis() < end)
                    lock.wait(500);
            }
        } finally {
            try { mp.stop(); } catch (Exception ignored) { }
            mp.release();
            if (curPlayer == mp) curPlayer = null;
        }
    }

    /** Phone ki built-in TTS — kabhi khaali haath nahi lotati. */
    private static void phoneSpeak(Context activity, String text) throws Exception {
        final Object lock = new Object();
        final boolean[] ready = {false};
        final Exception[] err = {null};
        new Handler(Looper.getMainLooper()).post(() -> {
            try {
                final TextToSpeech[] holder = new TextToSpeech[1];
                holder[0] = new TextToSpeech(activity, status -> {
                    TextToSpeech tts = holder[0];
                    curTts = tts;
                    synchronized (lock) {
                        try {
                            if (status == TextToSpeech.SUCCESS) {
                                int r = tts.setLanguage(new Locale("ur", "PK"));
                                if (r == TextToSpeech.LANG_MISSING_DATA
                                        || r == TextToSpeech.LANG_NOT_SUPPORTED)
                                    tts.setLanguage(Locale.getDefault());
                                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null,
                                        "myagent-" + System.currentTimeMillis());
                            } else {
                                err[0] = new Exception("TTS engine start na ho saka.");
                            }
                        } catch (Exception e) {
                            err[0] = e;
                        }
                        ready[0] = true;
                        lock.notifyAll();
                    }
                });
            } catch (Exception e) {
                synchronized (lock) {
                    err[0] = e; ready[0] = true; lock.notifyAll();
                }
            }
        });
        synchronized (lock) {
            long end = System.currentTimeMillis() + 15000;
            while (!ready[0] && !cancelled
                    && System.currentTimeMillis() < end) lock.wait(500);
        }
        if (cancelled) throw new Exception("cancelled");
        if (err[0] != null) throw err[0];
        if (!ready[0]) throw new Exception("Phone TTS did not respond (timeout).");
    }

    // ---------- chhoti madadgar ----------

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

    private static void sayStatus(Listener l, String s) {
        try { l.onStatus(s); } catch (Exception ignored) { }
    }

    private static String friendly(Exception e) {
        String m = e.getMessage();
        return m != null ? m : e.toString();
    }

    private static String join(List<String> reasons) {
        StringBuilder sb = new StringBuilder(
                "All 3 voice engines failed: ");
        for (int i = 0; i < reasons.size(); i++) {
            if (i > 0) sb.append(" | ");
            sb.append(reasons.get(i));
        }
        return sb.toString();
    }
}
