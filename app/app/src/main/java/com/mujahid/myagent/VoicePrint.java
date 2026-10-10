package com.mujahid.myagent;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * v57 "Voice ID" pehla qadam: enrollment + match score. GENUINE tareeqa, koi jhooth nahi:
 * - enroll(): 3 samples ("Main Mujahid hun") → MFCC voiceprint → sirf phone me save.
 * - verify(): 1 sample → template se cosine similarity → 0-100 score.
 * Pure Java (FFT + mel filterbank + DCT), koi model/net nahi. Pehle version me
 * factory-shor/zukam se score hil sakta hai — isi liye test screen me score
 * khula dikhta hai taake mil ke tune karein.
 */
public class VoicePrint {

    private static final int SR = 16000;
    private static final String FILE = "voiceprint.json";
    private static final int ENROLL_SAMPLES = 3;
    private static final int REC_MS = 2500;
    /** Cosine similarity threshold — is se upar = Mujahid. Tune hoga. */
    public static final double MATCH_COS = 0.90;

    public interface Listener {
        void onStatus(String s);
        void onDone(boolean ok, String msg);
    }

    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    private static void post(Listener l, String s) {
        MAIN.post(() -> l.onStatus(s));
    }

    private static void done(Listener l, boolean ok, String msg) {
        MAIN.post(() -> l.onDone(ok, msg));
    }

    public static boolean isEnrolled(Context ctx) {
        try {
            return ctx.getFileStreamPath(FILE).exists();
        } catch (Exception e) {
            return false;
        }
    }

    public static void clear(Context ctx) {
        try { ctx.deleteFile(FILE); } catch (Exception ignored) { }
    }

    /** 3 samples lo, average template banao, save karo. */
    public static void enroll(Context ctx, Listener l) {
        new Thread(() -> {
            double[][] vecs = new double[ENROLL_SAMPLES][];
            for (int i = 0; i < ENROLL_SAMPLES; i++) {
                post(l, "Sample " + (i + 1) + "/" + ENROLL_SAMPLES
                        + " — bolo 'Main Mujahid hun'");
                short[] pcm = record(REC_MS);
                if (pcm == null) {
                    done(l, false, "Mic nahi khula — Live call band karo, phir try karo.");
                    return;
                }
                vecs[i] = mfccMean(pcm);
                if (vecs[i] == null) {
                    done(l, false, "Awaz saaf nahi aayi — thoda zor se bolo.");
                    return;
                }
                post(l, "Sample " + (i + 1) + " done ✓");
                try { Thread.sleep(600); } catch (Exception ignored) { }
            }
            double[] tpl = new double[13];
            for (double[] v : vecs)
                for (int c = 0; c < 13; c++) tpl[c] += v[c] / ENROLL_SAMPLES;
            if (!save(ctx, tpl)) {
                done(l, false, "Save nahi hua — dobara try karo.");
                return;
            }
            done(l, true, "Voiceprint save ho gaya ✓ — ab 'Test' dabao!");
        }).start();
    }

    /** 1 sample lo, template se milao → score + faisla. */
    public static void verify(Context ctx, Listener l) {
        new Thread(() -> {
            double[] tpl = load(ctx);
            if (tpl == null) {
                done(l, false, "Pehle Enroll karo.");
                return;
            }
            post(l, "Bolo kuch (2 second)...");
            short[] pcm = record(REC_MS);
            if (pcm == null) {
                done(l, false, "Mic nahi khula — Live call band karo, phir try karo.");
                return;
            }
            double[] v = mfccMean(pcm);
            if (v == null) {
                done(l, false, "Awaz saaf nahi aayi — thoda zor se bolo.");
                return;
            }
            double cos = cosine(v, tpl);
            int score = (int) Math.round(cos * 100);
            if (cos >= MATCH_COS) {
                done(l, true, "Score: " + score + " — Mujahid ✓");
            } else {
                done(l, true, "Score: " + score + " — Unknown (match nahi hua)");
            }
        }).start();
    }

    // ---------- audio ----------

    private static short[] record(int ms) {
        try {
            int minBuf = AudioRecord.getMinBufferSize(SR,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT);
            if (minBuf <= 0) return null;
            AudioRecord rec = new AudioRecord(MediaRecorder.AudioSource.MIC, SR,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, minBuf * 2);
            if (rec.getState() != AudioRecord.STATE_INITIALIZED) {
                rec.release();
                return null;
            }
            int total = SR * ms / 1000;
            short[] pcm = new short[total];
            rec.startRecording();
            int read = 0;
            while (read < total) {
                int r = rec.read(pcm, read, total - read);
                if (r <= 0) break;
                read += r;
            }
            try { rec.stop(); } catch (Exception ignored) { }
            rec.release();
            return read > SR / 2 ? pcm : null; // kam se kam 0.5s
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- MFCC ----------

    /** 13 MFCC ka average (khamosh frames hata ke). */
    private static double[] mfccMean(short[] pcm) {
        double[] x = new double[pcm.length];
        for (int i = 0; i < pcm.length; i++) x[i] = pcm[i] / 32768.0;
        for (int i = x.length - 1; i > 0; i--) x[i] -= 0.97 * x[i - 1]; // pre-emphasis

        int frameLen = (int) (0.025 * SR); // 400
        int hop = (int) (0.010 * SR);       // 160
        int nfft = 512;
        int nFrames = 1 + (x.length - frameLen) / hop;
        if (nFrames < 5) return null;

        double[] ham = new double[frameLen];
        for (int i = 0; i < frameLen; i++)
            ham[i] = 0.54 - 0.46 * Math.cos(2 * Math.PI * i / (frameLen - 1));
        double[][] mel = melFilters(26, nfft, SR);

        // pehle frame energies (khamoshi hatane ke liye)
        double[] energy = new double[nFrames];
        double maxE = 0;
        for (int f = 0; f < nFrames; f++) {
            double e = 0;
            int s = f * hop;
            for (int i = 0; i < frameLen; i++) {
                double v = x[s + i] * ham[i];
                e += v * v;
            }
            energy[f] = e / frameLen;
            if (e > maxE) maxE = e;
        }
        if (maxE <= 0) return null;

        double[] sum = new double[13];
        int kept = 0;
        double[] re = new double[nfft], im = new double[nfft];
        for (int f = 0; f < nFrames; f++) {
            if (energy[f] < maxE * 0.02) continue; // khamosh frame chhoro
            int s = f * hop;
            for (int i = 0; i < nfft; i++) {
                re[i] = (i < frameLen) ? x[s + i] * ham[i] : 0;
                im[i] = 0;
            }
            fft(re, im);
            double[] logE = new double[26];
            for (int m = 0; m < 26; m++) {
                double e = 0;
                for (int k = 0; k <= nfft / 2; k++) {
                    double mag2 = re[k] * re[k] + im[k] * im[k];
                    e += mag2 * mel[m][k];
                }
                logE[m] = Math.log(Math.max(e, 1e-10));
            }
            for (int c = 0; c < 13; c++) { // DCT-II
                double d = 0;
                for (int m = 0; m < 26; m++)
                    d += logE[m] * Math.cos(Math.PI * c * (2 * m + 1) / 52.0);
                sum[c] += d;
            }
            kept++;
        }
        if (kept < 3) return null;
        for (int c = 0; c < 13; c++) sum[c] /= kept;
        return sum;
    }

    private static double[][] melFilters(int nfilt, int nfft, int sr) {
        double melHigh = 2595 * Math.log10(1 + (sr / 2.0) / 700.0);
        double[][] fb = new double[nfilt][nfft / 2 + 1];
        double[] pts = new double[nfilt + 2];
        for (int i = 0; i < pts.length; i++)
            pts[i] = 700 * (Math.pow(10, (i * melHigh / (nfilt + 1)) / 2595.0) - 1);
        for (int m = 1; m <= nfilt; m++) {
            double f0 = pts[m - 1], f1 = pts[m], f2 = pts[m + 1];
            for (int k = 0; k <= nfft / 2; k++) {
                double freq = (double) k * sr / nfft;
                double w = 0;
                if (freq >= f0 && freq <= f1) w = (freq - f0) / (f1 - f0);
                else if (freq > f1 && freq <= f2) w = (f2 - freq) / (f2 - f1);
                fb[m - 1][k] = w;
            }
        }
        return fb;
    }

    private static void fft(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) j &= ~bit;
            j |= bit;
            if (i < j) {
                double t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang), wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cwr = 1, cwi = 0;
                for (int k = 0; k < len / 2; k++) {
                    double ur = re[i + k], ui = im[i + k];
                    double vr = re[i + k + len / 2] * cwr - im[i + k + len / 2] * cwi;
                    double vi = re[i + k + len / 2] * cwi + im[i + k + len / 2] * cwr;
                    re[i + k] = ur + vr; im[i + k] = ui + vi;
                    re[i + k + len / 2] = ur - vr; im[i + k + len / 2] = ui - vi;
                    double nwr = cwr * wr - cwi * wi;
                    cwi = cwr * wi + cwi * wr;
                    cwr = nwr;
                }
            }
        }
    }

    private static double cosine(double[] a, double[] b) {
        double dot = 0, na = 0, nb = 0;
        for (int i = 0; i < a.length; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        return (na == 0 || nb == 0) ? 0 : dot / Math.sqrt(na * nb);
    }

    // ---------- storage ----------

    private static boolean save(Context ctx, double[] tpl) {
        try {
            JSONArray arr = new JSONArray();
            for (double d : tpl) arr.put(d);
            JSONObject o = new JSONObject();
            o.put("mfcc", arr);
            FileOutputStream out = ctx.openFileOutput(FILE, Context.MODE_PRIVATE);
            out.write(o.toString().getBytes("UTF-8"));
            out.close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static double[] load(Context ctx) {
        try {
            FileInputStream in = ctx.openFileInput(FILE);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[1024];
            int n;
            while ((n = in.read(buf)) != -1) bos.write(buf, 0, n);
            in.close();
            JSONArray arr = new JSONObject(new String(bos.toByteArray(), "UTF-8"))
                    .getJSONArray("mfcc");
            double[] tpl = new double[arr.length()];
            for (int i = 0; i < arr.length(); i++) tpl[i] = arr.getDouble(i);
            return tpl.length == 13 ? tpl : null;
        } catch (Exception e) {
            return null;
        }
    }
}
