package com.mujahid.myagent;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import java.io.File;
import java.util.List;

/**
 * Foreground service — conversation TAB tak chalti rahe jab tak user khud band na kare:
 * - App background me ho (WhatsApp khula ho) → chalti rahegi
 * - Screen LOCK ho → chalti rahegi (wake lock)
 * - Notification me "Band karo" button → kahin se bhi band
 *
 * Mic foregroundServiceType + partial wake lock + ongoing notification.
 */
public class VoiceService extends Service {

    public static final String ACTION_START = "com.mujahid.myagent.action.START";
    public static final String ACTION_STOP = "com.mujahid.myagent.action.STOP";
    private static final String CH_ID = "ayesha_voice";
    private static final int NOTIF_ID = 101;

    private static boolean running = false;
    public static boolean isRunning() { return running; }

    private static VoiceService instance;

    /**
     * Kahin se bhi (button waghera) Ayesha se bulwao.
     * Conversation ON ho to bolti hai, warna khamosh rehti hai.
     */
    public static void say(String text) {
        VoiceService s = instance;
        if (s == null || text == null) return;
        try {
            TtsClient.speak(s, text, Keys.geminiKeys(s),
                    new TtsClient.Listener() {
                        @Override public void onStatus(String x) { }
                        @Override public void onDone() { }
                        @Override public void onError(String r) { }
                    });
        } catch (Exception ignored) { }
    }

    /** Screen (MainActivity) ka status listener — screen khuli ho to update. */
    public interface StatusListener { void onStatus(String s); }
    private static StatusListener statusListener;
    public static void setStatusListener(StatusListener l) { statusListener = l; }

    private Handler mainHandler;
    private PowerManager.WakeLock wakeLock;
    private MediaRecorder recorder;
    private File audioFile;
    private boolean recording = false;
    private boolean busy = false;
    private boolean conversationOn = false;
    private final List<ChatClient.Message> history = ChatClient.newHistory();
    /** v38 Stage 3: Live conversation engine (null = purana rasta). */
    private LiveTalk liveTalk;
    /** Neon border jo conversation ke dauran KISI BHI app ke upar chamakta hai. */
    private View edgeLightView;

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        mainHandler = new Handler(Looper.getMainLooper());
        Memory.seedDefaults(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Ayesha:Voice");
        try { wakeLock.acquire(); } catch (Exception ignored) { }
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent != null ? intent.getAction() : null;
        if (ACTION_STOP.equals(action)) {
            stopConversation();
            stopSelf();
            return START_NOT_STICKY;
        }
        // START (ya system-restart)
        goForeground("Getting ready...");
        running = true;
        startConversation();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        conversationOn = false;
        hideEdgeLight();
        TtsClient.stop(); // mic band = poori khamoshi, kuch na bole
        stopRecorderQuiet();
        try {
            if (wakeLock != null && wakeLock.isHeld()) wakeLock.release();
        } catch (Exception ignored) { }
        running = false;
        instance = null;
        emitStatus("ready");
        super.onDestroy();
    }

    // ---------- Neon edge lighting (kisi bhi app ke upar) ----------

    /** Conversation ON → border har screen ke upar. OFF → hata do. */
    private void showEdgeLight() {
        if (edgeLightView != null) return;
        if (!SettingsActivity.isLightingOn(this)) return;
        if (!Settings.canDrawOverlays(this)) {
            // Pehli baar: system page kholo taake ijazat de sake
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:" + getPackageName()));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Exception ignored) { }
            toast("For lighting, please allow 'Display over other apps'.");
            return;
        }
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            WindowManager.LayoutParams p = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                            | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            edgeLightView = new NeonBorderView(this, null);
            wm.addView(edgeLightView, p);
        } catch (Exception ignored) { }
    }

    private void hideEdgeLight() {
        if (edgeLightView == null) return;
        try {
            WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
            if (wm != null) wm.removeView(edgeLightView);
        } catch (Exception ignored) { }
        edgeLightView = null;
    }

    // ---------- Notification ----------

    private void createChannel() {
        NotificationManager nm =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return;
        NotificationChannel ch = new NotificationChannel(
                CH_ID, "Ayesha Voice", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Conversation in progress");
        try { nm.createNotificationChannel(ch); } catch (Exception ignored) { }
    }

    private PendingIntent stopPending() {
        Intent i = new Intent(this, VoiceService.class).setAction(ACTION_STOP);
        return PendingIntent.getService(this, 1, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void goForeground(String text) {
        Notification n = buildNotification(text, null, null);
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                startForeground(NOTIF_ID, n,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE);
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Exception e) {
            // foreground na ho saka to service ka koi faida nahi
            stopSelf();
        }
    }

    /** text = status line; openIntent/openLabel = "↗ X kholo" fallback action. */
    private Notification buildNotification(String text, Intent openIntent, String openLabel) {
        Notification.Builder b = new Notification.Builder(this, CH_ID)
                .setContentTitle("Ayesha 🎙")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setOngoing(true)
                .addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_close_clear_cancel,
                        "Stop", stopPending()).build());
        if (openIntent != null && openLabel != null) {
            try {
                PendingIntent pi = PendingIntent.getActivity(this, 2, openIntent,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
                b.addAction(new Notification.Action.Builder(
                        android.R.drawable.ic_menu_view, openLabel, pi).build());
            } catch (Exception ignored) { }
        }
        // Tap → app kholo
        try {
            Intent app = new Intent(this, MainActivity.class);
            PendingIntent pi = PendingIntent.getActivity(this, 3, app,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            b.setContentIntent(pi);
        } catch (Exception ignored) { }
        return b.build();
    }

    private void updateNotification(String text, Intent openIntent, String openLabel) {
        try {
            NotificationManager nm =
                    (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (nm != null) nm.notify(NOTIF_ID, buildNotification(text, openIntent, openLabel));
        } catch (Exception ignored) { }
    }

    // ---------- Conversation ----------

    private void startConversation() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            toast("Mic permission missing — allow it in Settings.");
            emitStatus("mic error");
            stopSelf();
            return;
        }
        List<String> geminiKeys = Keys.geminiKeys(this);
        String groqKey = Keys.groqKey(this);
        if (geminiKeys.isEmpty() && groqKey.isEmpty()) {
            emitStatus("no keys");
            toast("Add API keys in Settings.");
            stopSelf();
            return;
        }
        conversationOn = true;
        emitStatus("listening");
        showEdgeLight();
        // v48: continuous streaming hata di — yahan listener ki zaroorat nahi
        // v41: guftagu ke dauran error aaye to wo khud boli me bataye (fori report)
        Diag.setErrorListener((where, msg) -> {
            if (conversationOn && liveTalk != null) {
                try {
                    liveTalk.sayText("Boss, ek chhota masla aaya: " + msg
                            + " — main sambhal rahi hun, fikar na karo.");
                } catch (Exception ignored) { }
            }
        });
        // v38 Stage 3: Live Voice ON ho to direct Gemini Live — purana rasta fallback
        if (SettingsActivity.isLiveVoiceOn(this)) startLiveMode();
        else startRecordingWithVad();
    }

    /** Stage 3 (v38): mic button → seedha Gemini Live conversation. */
    private void startLiveMode() {
        emitStatus("connecting live...");
        liveTalk = new LiveTalk();
        liveTalk.start(this, new LiveTalk.Listener() {
            @Override public void onReady() {
                emitStatus("live");
            }
            @Override public void onStatus(String s) {
                emitStatus("live: " + s);
            }
            @Override public void onInputText(String partial) {
                // live "suna" — toast spam se bachne ke liye khamosh
            }
            @Override public void onUserTurn(String text) {
                handleLiveTurn(text);
            }
            @Override public void onError(String why) {
                // Live fail → purane raste pe wapas, guftagu jaari
                Diag.event("live fallback: " + why);
                toast("Live: " + why + " — purana rasta.");
                liveTalk = null;
                if (conversationOn) startRecordingWithVad();
            }
        });
    }

    /** v43: screen ka ek frame pakdo → Gemini vision se describe. Bg thread pe.
     *  v49 refresh: har stage ka pata — agli baar foran maloom hoga kahan toota. */
    private String describeScreen() {
        try {
            android.graphics.Bitmap bmp = ScreenShare.capture();
            if (bmp == null) {
                Diag.event("screen: capture NULL — projection toot gaya?");
                return "Boss, screen share ka connection toot gaya lagta hai — "
                        + "ek baar screen share OFF karke phir ON karo, phir pucho.";
            }
            String seen = ChatClient.describeImage(bmp, Keys.geminiKeys(this),
                    smartVisionPrompt());
            if (seen == null || seen.trim().isEmpty()) {
                Diag.event("screen: vision API ne khaali jawab diya");
                return "Boss, screen dekhi lekin samajh nahi aaya — ek baar phir try karo.";
            }
            return seen.trim();
        } catch (Exception e) {
            Diag.event("screen: exception " + e.getClass().getSimpleName());
            return "Boss, screen dekhne me masla aaya.";
        }
    }

    /**
     * v46 Smart Vision prompt — boss ka spec:
     * 1) OCR first (text/headings parho), 2) app signatures,
     * 3) strict grounding (andaza nahi), 4) foreground app context injection.
     */
    private String smartVisionPrompt() {
        StringBuilder p = new StringBuilder("Ye phone ka screenshot hai. ");
        String ctx = foregroundAppContext();
        if (!ctx.isEmpty()) p.append(ctx);
        p.append("VISION RULES (sakhti se follow karo):\n")
         .append("1. PEHLE TEXT PARHO: screen pe jo text, headings aur action-bar titles "
                 + "nazar aa rahe hain unhe ghaur se parho — app ka naam wahin se confirm karo "
                 + "(jaise 'WhatsApp', 'YouTube', 'Settings').\n")
         .append("2. APP SIGNATURES:\n")
         .append("- WhatsApp: green theme, upar search bar, chat list (naam + time + gol DP), "
                 + "neeche gol green message button.\n")
         .append("- Home Screen: app icons ka grid, neeche dock, wallpaper.\n")
         .append("- Settings: upar search bar, list me options aur toggle switches.\n")
         .append("- YouTube: video thumbnails ki list, neeche Home/Shorts buttons.\n")
         .append("3. NO GUESSING: jis text ya naam pe poora yaqeen na ho, uska andaza mat lagao "
                 + "aur jhoote chat naam mat banao. Sirf wahi batao jo screen pe waqai "
                 + "likha ya dikh raha hai. Saaf na ho to keh do 'saaf nazar nahi aa raha'.\n")
         .append("Jawab: pehle EXACT app ka naam, phir 1-2 jumlon me screen pe kya ho raha hai. "
                 + "Roman Urdu me jawab do.");
        return p.toString();
    }

    /** v46: context injection — is waqt kaunsi app khuli hai (A11yService se). */
    private String foregroundAppContext() {
        try {
            String pkg = A11yService.getForegroundPackage();
            if (pkg == null || pkg.isEmpty()) return "";
            if (pkg.equals(getPackageName())) return ""; // khud ki app — bekaar context
            String label = pkg;
            try {
                android.content.pm.PackageManager pm = getPackageManager();
                android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                CharSequence l = pm.getApplicationLabel(ai);
                if (l != null && l.length() > 0) label = l.toString();
            } catch (Exception ignored) { }
            return "Is waqt khuli hui app: " + label + ". Is baat ko madde-nazar rakho. ";
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * Live turn mukammal: us ne kya kaha (transcript) — memory/command check.
     * Guftagu ka jawab Live model ne khud de diya hai; yahan sirf phone ke kaam.
     */
    private void handleLiveTurn(String text) {        if (text == null || text.trim().isEmpty() || !conversationOn) return;
        final String said = text.trim();
        toast("Suna: " + RomanUrdu.toRoman(said)); // hamesha Roman Urdu me dikhao

        // v39: Reminder — "yaad dilana" (phone khud schedule karta hai)
        Reminder.Parsed rp = Reminder.parse(said);
        if (rp != null) {
            String err = Reminder.schedule(this, rp.atMillis, rp.message);
            if (liveTalk != null)
                liveTalk.sayText(err == null ? rp.confirmText : err);
            return;
        }

        // v40: Taaza khabrein — "taaza khabar batao" (Google News RSS, baghair key)
        String khNorm = PhoneTools.norm(said);
        if (khNorm.contains("taaza khabar") || khNorm.contains("taaza khabrein")
                || khNorm.contains("taaza khabren") || khNorm.contains("khabar batao")
                || khNorm.contains("khabrein batao") || khNorm.contains("khabrein sunao")
                || khNorm.contains("news batao") || khNorm.contains("aaj ki khabar")) {
            if (liveTalk != null) {
                final LiveTalk lt = liveTalk;
                new Thread(() -> {
                    String news = LiveContext.fetchNewsSpoken(3);
                    try { lt.sayText(news); } catch (Exception ignored) { }
                }).start();
            }
            return;
        }

        // v41: Khud-mukhtar memory — save/delete/edit/jagah (pehle, taake model se na takraye)
        String memReply = MemCommands.tryFast(this, said);
        if (memReply != null) {
            if (liveTalk != null) liveTalk.sayText(memReply);
            return;
        }

        // v41: "koi error hai?" / "app theek hai?" — apni sehat khud bataye
        if (MemCommands.isHealthQuestion(said)) {
            if (liveTalk != null)
                liveTalk.sayText("Boss, " + Diag.healthSummary());
            return;
        }

        // v41: "main kahan hun?" — jagah ka naam (thread me, Geocoder net mangta hai)
        if (MemCommands.isWhereQuestion(said)) {
            if (liveTalk != null) {
                final LiveTalk lt = liveTalk;
                new Thread(() -> {
                    String where = LiveContext.whereAmI(VoiceService.this);
                    try { lt.sayText(where); } catch (Exception ignored) { }
                }).start();
            }
            return;
        }

        // v43: Screen Share — on/off/dekho (boss ka hukm, wapas ON)
        if (ScreenShare.isOnCmd(said)) {
            android.content.Intent si = new android.content.Intent(
                    this, MainActivity.class);
            si.putExtra(ScreenShare.EXTRA_AUTO_SHARE, true);
            ForegroundOpen.open(this, si, "Screen Share ON karo");
            if (liveTalk != null) liveTalk.sayText("Front page khol rahi hun boss — "
                    + "wahan screen share ki permission allow karna, "
                    + "phir main tumhari screen dekh sakungi!");
            return;
        }
        if (ScreenShare.isOffCmd(said)) {
            ScreenShare.turnOff(this);
            if (liveTalk != null)
                liveTalk.sayText("Screen share band kar diya boss.");
            return;
        }
        if (ScreenShare.isDekhoCmd(said)) {
            if (!ScreenShare.isOn()) {
                if (liveTalk != null) liveTalk.sayText("Boss, screen share abhi OFF hai — "
                        + "pehle front page pe Screen Share ON karo, phir main dekh sakungi!");
            } else if (liveTalk != null) {
                final LiveTalk lt = liveTalk;
                new Thread(() -> {
                    String seen = describeScreen();
                    try { lt.sayText(seen); } catch (Exception ignored) { }
                }).start();
            }
            return;
        }

        // v48: "screen parho" — LOCAL, zero quota (Accessibility tree)
        String low48 = said.toLowerCase(java.util.Locale.ROOT);
        if (low48.contains("screen parho") || low48.contains("screen padho")
                || low48.contains("screen pe kya likha")
                || low48.contains("text parho") || low48.contains("screen ka text")) {
            String read = A11yService.readScreen();
            if (liveTalk != null) {
                liveTalk.sayText(read != null ? read
                        : "Boss, screen parhne ke liye Accessibility me Ayesha ON karo.");
            }
            return;
        }

        // Phone commands — phone khud karta hai (model ne pehle hi jawab de diya)
        WhatsAppAction.Result wa = WhatsAppAction.tryHandle(
                VoiceService.this, PhoneTools.norm(said));
        if (wa.handled) {
            // v49: handler ka SACH uski awaz me — khud se mat gharo
            if (liveTalk != null && wa.reply != null && !wa.reply.isEmpty())
                liveTalk.sayText(wa.reply);
            return;
        }

        PhoneTools.Result cmd = PhoneTools.tryHandle(VoiceService.this, said);
        if (cmd.handled) {
            // v49: handler ka SACH uski awaz me — "khol diya" sirf jab sach me khula
            if (liveTalk != null && cmd.reply != null && !cmd.reply.isEmpty())
                liveTalk.sayText(cmd.reply);
            if (cmd.intent != null) {
                // v49: launch tryHandle→fire() me ho chuka (single path) — yahan sirf manual backup button
                updateNotification("↗ " + cmd.reply, cmd.intent, "Kholo");
            }
            return;
        }

        UiControl.Result u = UiControl.tryHandle(
                VoiceService.this, PhoneTools.norm(said));
        if (u.handled) {
            // v49: scroll/tap ka asal natija uski awaz me — inkar khatam
            if (liveTalk != null && u.reply != null && !u.reply.isEmpty())
                liveTalk.sayText(u.reply);
            return;
        }
        // Baaki guftagu Live ne sambhal li — kuch nahi karna.
    }

    private void stopConversation() {
        conversationOn = false;
        hideEdgeLight();
        Diag.setErrorListener(null); // v41: guftagu khatam — khamosh
        if (liveTalk != null) { // v38: Live session band karo
            try { liveTalk.stop(); } catch (Exception ignored) { }
            liveTalk = null;
        }
        TtsClient.stop(); // jo jawab baj raha/ane wala hai — foran band
        stopRecorderQuiet();
        recording = false;
        emitStatus("ready");
    }

    /** Mic kholo + khamoshi-pehchan: bolo → 2.5s chup → turn khatam.
     *  v33 (2026-10-06): purani wali aaram wali speed wapas — boss ka hukm.
     *  Tez VAD beech me tokta tha, is liye original jaisa. */
    private void startRecordingWithVad() {
        if (!conversationOn || recording) return;
        try {
            audioFile = new File(getCacheDir(), "voice.m4a");
            if (audioFile.exists()) audioFile.delete();
            recorder = new MediaRecorder();
            recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            recorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            recorder.setAudioEncodingBitRate(32000);
            recorder.setOutputFile(audioFile.getAbsolutePath());
            recorder.prepare();
            recorder.start();
            recording = true;
            emitStatus("listening");
        } catch (Exception e) {
            emitStatus("mic error");
            toast("Mic: " + e.getMessage());
            stopConversation();
            stopSelf();
            return;
        }

        new Thread(() -> {
            try {
                int floor = Integer.MAX_VALUE;
                for (int i = 0; i < 6; i++) {
                    Thread.sleep(100);
                    if (!recording || !conversationOn) return;
                    try {
                        int amp = recorder.getMaxAmplitude();
                        if (amp < floor) floor = amp;
                    } catch (Exception e) { return; }
                }
                if (floor == Integer.MAX_VALUE || floor < 0) floor = 0;

                int silenceThreshold = Math.min(Math.max(1500, floor * 3), 8000);
                int speechThreshold = Math.min(Math.max(3000, floor * 6), 15000);

                boolean heardSpeech = false;
                int quietScore = 0;
                long start = System.currentTimeMillis();

                while (recording && conversationOn) {
                    Thread.sleep(100);
                    int amp;
                    try { amp = recorder.getMaxAmplitude(); }
                    catch (Exception e) { break; }
                    long elapsed = System.currentTimeMillis() - start;

                    if (!heardSpeech) {
                        if (amp > speechThreshold) {
                            heardSpeech = true;
                        } else if (elapsed > 10000) {
                            break;
                        }
                    } else {
                        if (amp < silenceThreshold) quietScore++;
                        else quietScore = Math.max(0, quietScore - 3);
                        if (quietScore >= 25) break; // v33: wapas 2.5s chup — aaram se suno
                    }
                    if (elapsed > 30000) break;
                }
            } catch (InterruptedException ignored) { }
            if (recording && conversationOn) processTurn();
        }).start();
    }

    private void stopRecorderQuiet() {
        if (recorder != null) {
            try { recorder.stop(); } catch (Exception ignored) { }
            try { recorder.release(); } catch (Exception ignored) { }
            recorder = null;
        }
    }

    /** Ek turn: suno → socho → bolo → agla turn (screen band ho tab bhi). */
    private void processTurn() {
        if (!conversationOn || busy) return;
        stopRecorderQuiet();
        recording = false;
        busy = true;

        final List<String> geminiKeys = Keys.geminiKeys(this);
        final String groqKey = Keys.groqKey(this);

        new Thread(() -> {
            try {
                emitStatus("thinking");
                String userText;
                long tStt = System.currentTimeMillis();
                try {
                    userText = SttClient.transcribe(audioFile, groqKey);
                } catch (Exception e) {
                    throw new Exception("Listening error: " + e.getMessage());
                }
                Diag.stage("stt", System.currentTimeMillis() - tStt);
                Diag.note("stt_engine", "groq-whisper");
                if (userText == null || userText.trim().isEmpty())
                    throw new Exception("Heard nothing — speak near the mic.");
                final String said = userText.trim();
                toast("Suna: " + RomanUrdu.toRoman(said)); // hamesha Roman Urdu me dikhao

                // v39: Reminder — "yaad dilana" (phone khud schedule karta hai)
                Reminder.Parsed rp = Reminder.parse(said);
                if (rp != null) {
                    String err = Reminder.schedule(VoiceService.this, rp.atMillis, rp.message);
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model",
                            err == null ? rp.confirmText : err));
                    speakAndContinue(err == null ? rp.confirmText : err, geminiKeys);
                    return;
                }

                // v40: Taaza khabrein — "taaza khabar batao"
                String khNorm2 = PhoneTools.norm(said);
                if (khNorm2.contains("taaza khabar") || khNorm2.contains("taaza khabrein")
                        || khNorm2.contains("taaza khabren") || khNorm2.contains("khabar batao")
                        || khNorm2.contains("khabrein batao") || khNorm2.contains("khabrein sunao")
                        || khNorm2.contains("news batao") || khNorm2.contains("aaj ki khabar")) {
                    final String newsSpoken = LiveContext.fetchNewsSpoken(3);
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", newsSpoken));
                    speakAndContinue(newsSpoken, geminiKeys);
                    return;
                }

                String memReply = MemCommands.tryFast(VoiceService.this, said);
                if (memReply != null) {
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", memReply));
                    speakAndContinue(memReply, geminiKeys);
                    return;
                }

                // v41: "koi error hai?" — apni sehat khud bataye
                if (MemCommands.isHealthQuestion(said)) {
                    final String health = "Boss, " + Diag.healthSummary();
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", health));
                    speakAndContinue(health, geminiKeys);
                    return;
                }

                // v41: "main kahan hun?" — jagah ka naam
                if (MemCommands.isWhereQuestion(said)) {
                    final String where = LiveContext.whereAmI(VoiceService.this);
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", where));
                    speakAndContinue(where, geminiKeys);
                    return;
                }

                // v43: Screen Share — on/off/dekho (boss ka hukm, wapas ON)
                if (ScreenShare.isOnCmd(said)) {
                    android.content.Intent si = new android.content.Intent(
                            VoiceService.this, MainActivity.class);
                    si.putExtra(ScreenShare.EXTRA_AUTO_SHARE, true);
                    ForegroundOpen.open(VoiceService.this, si, "Screen Share ON karo");
                    final String r1 = "Front page khol rahi hun boss — wahan screen share "
                            + "ki permission allow karna, phir main tumhari screen dekh sakungi!";
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", r1));
                    speakAndContinue(r1, geminiKeys);
                    return;
                }
                if (ScreenShare.isOffCmd(said)) {
                    ScreenShare.turnOff(VoiceService.this);
                    final String r2 = "Screen share band kar diya boss.";
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", r2));
                    speakAndContinue(r2, geminiKeys);
                    return;
                }
                if (ScreenShare.isDekhoCmd(said)) {
                    final String seen;
                    if (!ScreenShare.isOn()) {
                        seen = "Boss, screen share abhi OFF hai — pehle front page pe "
                                + "Screen Share ON karo, phir main dekh sakungi!";
                    } else {
                        seen = describeScreen();
                    }
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", seen));
                    speakAndContinue(seen, geminiKeys);
                    return;
                }

                // v48: "screen parho" — LOCAL, zero quota (Accessibility tree)
                String low48b = said.toLowerCase(java.util.Locale.ROOT);
                if (low48b.contains("screen parho") || low48b.contains("screen padho")
                        || low48b.contains("screen pe kya likha")
                        || low48b.contains("text parho")
                        || low48b.contains("screen ka text")) {
                    String read = A11yService.readScreen();
                    final String r3 = read != null ? read
                            : "Boss, screen parhne ke liye Accessibility me Ayesha ON karo.";
                    history.add(new ChatClient.Message("user", said));
                    history.add(new ChatClient.Message("model", r3));
                    speakAndContinue(r3, geminiKeys);
                    return;
                }

                history.add(new ChatClient.Message("user", said));

                // STEP 1 — Phone command? (bina quota, phone me hi)
                // WhatsApp actions — PHONE KE SYSTEM se (chat/send/call/cut)
                WhatsAppAction.Result wa = WhatsAppAction.tryHandle(
                        VoiceService.this, PhoneTools.norm(said));
                if (wa.handled) {
                    history.add(new ChatClient.Message("model", wa.reply));
                    speakAndContinue(wa.reply, geminiKeys);
                    return;
                }

                PhoneTools.Result cmd = PhoneTools.tryHandle(VoiceService.this, said);
                if (cmd.handled) {
                    history.add(new ChatClient.Message("model", cmd.reply));
                    if (cmd.intent != null) {
                        // v49: launch tryHandle→fire() me ho chuka (single path) — yahan sirf manual backup button
                        updateNotification("↗ " + cmd.reply, cmd.intent, "Kholo");
                    }
                    speakAndContinue(cmd.reply, geminiKeys);
                    return;
                }

                // (screen share hata diya gaya hai — 2026-10-06, boss ka hukm)

                // STEP 2 — Screen control (scroll/type/tap/home/back)
                UiControl.Result u = UiControl.tryHandle(
                        VoiceService.this, PhoneTools.norm(said));
                if (u.handled) {
                    history.add(new ChatClient.Message("model", u.reply));
                    speakAndContinue(u.reply, geminiKeys);
                    return;
                }

                String memCtx = Memory.buildContext(VoiceService.this);
                if (SettingsActivity.isAutoMemoryOn(VoiceService.this))
                    memCtx += ChatClient.AUTO_MEMORY_INSTRUCTION;
                // SELF-DIAGNOSIS (v32): apne baare me puche to EXACT data do, tukka nahi
                if (Diag.isSelfQuestion(PhoneTools.norm(said)))
                    memCtx += "\n[APNI JAANCH KA DATA — isi ko dekh ke EXACT wajah batao, "
                            + "andaza/tukka NA lagao. Is data me koi API key/password nahi hai, "
                            + "sirf ginti aur waqt hai — 'nahi dekh sakti' mat kaho, seedha jawab do. "
                            + "Ye lines user ko nazar nahi aatin:]\n"
                            + Diag.snapshot(VoiceService.this) + "\n";
                String reply;
                long tThink = System.currentTimeMillis();
                try {
                    reply = ChatClient.chat(history, geminiKeys, groqKey,
                            s -> emitStatus("thinking"), true, memCtx);
                } catch (Exception e) {
                    throw new Exception("Thinking error: " + e.getMessage());
                }
                Diag.stage("sochna", System.currentTimeMillis() - tThink);
                reply = ChatClient.applyAutoMemory(VoiceService.this, reply);
                history.add(new ChatClient.Message("model", reply));

                speakAndContinue(reply, geminiKeys);
            } catch (Exception e) {
                final String msg = e.getMessage();
                emitStatus("error");
                Diag.error("turn", msg);
                toast(msg);
                // SLOW INTERNET (v32): sirf toast nahi — AWAZ me batao
                // (phone ki TTS offline bhi bolti hai), phir agla turn
                if (Diag.isNetworkError(msg) && running && conversationOn) {
                    speakAndContinue("Internet slow ya band lag raha hai boss, "
                            + "is liye jawab me der ho rahi hai.", geminiKeys);
                } else {
                    busy = false;
                    continueConversation();
                }
            }
        }).start();
    }

    private void speakAndContinue(String text, List<String> geminiKeys) {
        if (!running || !conversationOn) return; // mar chuki conversation na bole
        emitStatus("speaking");
        TtsClient.speak(this, text, geminiKeys, new TtsClient.Listener() {
            @Override public void onStatus(String s) { emitStatus("speaking"); }
            @Override public void onDone() {
                busy = false;
                if (conversationOn) continueConversation();
                else emitStatus("ready");
            }
            @Override public void onError(String reason) {
                busy = false;
                emitStatus("error");
                toast(reason);
                if (conversationOn) continueConversation();
            }
        });
    }

    private void continueConversation() {
        if (!conversationOn) return;
        emitStatus("listening");
        new Thread(() -> {
            try { Thread.sleep(700); } catch (InterruptedException ignored) { }
            startRecordingWithVad();
        }).start();
    }

    // ---------- chhoti madadgar ----------

    private void emitStatus(String s) {
        updateNotification(s, null, null);
        StatusListener l = statusListener;
        if (l != null) {
            try { l.onStatus(s); } catch (Exception ignored) { }
        }
    }

    private void toast(String s) {
        mainHandler.post(() -> {
            try { Toast.makeText(VoiceService.this, s, Toast.LENGTH_LONG).show(); }
            catch (Exception ignored) { }
        });
    }
}
