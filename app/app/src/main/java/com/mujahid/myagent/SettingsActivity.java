package com.mujahid.myagent;

import android.app.Activity;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.view.Gravity;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/** Settings: 10 Gemini keys (alag-alag Gmail) + Groq key. Bina rebuild ke. */
public class SettingsActivity extends Activity {


    /** Neon lighting on/off — VoiceService + home screen dono parhte hain. */
    public static boolean isLightingOn(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("app_settings",
                Context.MODE_PRIVATE);
        return p.getBoolean("neon_lighting", true);
    }

    private static void setLightingOn(Context ctx, boolean on) {
        ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("neon_lighting", on).apply();
    }

    /** Auto Memory — ON ho to Ayesha khud ahem baatein yaad rakhe/bhool jaye. */
    public static boolean isAutoMemoryOn(Context ctx) {
        return ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("auto_memory", true);
    }

    private static void setAutoMemoryOn(Context ctx, boolean on) {
        ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("auto_memory", on).apply();
    }

    /** Live Voice — ON ho to mic button seedha Gemini Live se jurega (v38). */
    public static boolean isLiveVoiceOn(Context ctx) {
        return ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("live_voice", true);
    }

    private static void setLiveVoiceOn(Context ctx, boolean on) {
        ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("live_voice", on).apply();
    }

    // v51 "Rang": ye rang ab Themes se aate hain (user ki pasand)
    private int BG, CARD, TXT, HINT, ACCENT;

    private TextView darkLabel(String s, int size) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(TXT);
        return t;
    }

    /** v86 glass: rang ko transparent bana do (sheesha look). */
    private static int glassOf(int c) {
        return Color.argb(115, Color.red(c), Color.green(c), Color.blue(c));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    /** v55: MYRA-style section header — chhote caps, halka rang. */
    private TextView sectionHeader(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(12);
        t.setTextColor(HINT);
        t.setLetterSpacing(0.12f);
        t.setPadding(dp(4), dp(14), 0, dp(6));
        return t;
    }

    /** v55: MYRA-style gol card — title (+subtitle), control daayein. */
    private LinearLayout settingCard(String title, String subtitle) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setFocusable(true);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(glassOf(CARD));
        gd.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        gd.setCornerRadius(dp(28));
        card.setBackground(gd);
        int p = dp(16);
        card.setPadding(p, (int) (p * 0.8), p, (int) (p * 0.8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        card.setLayoutParams(lp);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        col.setLayoutParams(clp);
        col.addView(darkLabel(title, 15));
        if (subtitle != null && !subtitle.isEmpty()) {
            TextView sub = darkLabel(subtitle, 12);
            sub.setTextColor(HINT);
            col.addView(sub);
        }
        card.addView(col);
        return card;
    }

    /** v55: card me daayein taraf accent rang ki value (jaise MYRA). */
    private TextView valueLabel(String s) {
        TextView v = darkLabel(s, 14);
        v.setTextColor(ACCENT);
        return v;
    }

    private EditText darkInput(String hint) {
        EditText in = new EditText(this);
        in.setHint(hint);
        in.setHintTextColor(HINT);
        in.setTextColor(TXT);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(glassOf(BG));
        gd.setStroke(dp(1), Color.argb(50, 255, 255, 255));
        gd.setCornerRadius(dp(20));
        in.setBackground(gd);
        int pad = dp(12);
        in.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(6);
        in.setLayoutParams(lp);
        return in;
    }

    private Button darkButton(String s) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(TXT);
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(glassOf(CARD));
        gd.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        gd.setCornerRadius(dp(28));
        b.setBackground(gd);
        int p = dp(14);
        b.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(10);
        b.setLayoutParams(lp);
        return b;
    }

    // v84: Theme ab dialog nahi — PickerActivity ke page pe (kind="theme").
    // (Save Themes.set se hota hai; Settings onResume me naye rangon se recreate hoti hai.)

    /** "Keys" — sari saved keys ek dialog me dikhao. */
    // v84: Weather key ab dialog nahi — PickerActivity ke page pe
    // (kind="weather"; key LiveContext.setWeatherKey se waisi hi save hoti hai).

    /** v54 "Orb": Floating Orb on/off — doosri apps ke upar chhota button. */
    public static boolean isOrbOn(Context ctx) {
        return ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("floating_orb", false);
    }

    private static void setOrbOn(Context ctx, boolean on) {
        ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("floating_orb", on).apply();
    }

    /** v56: Emotion picker — v59: "Auto" sab se upar (awaz ka lehja sun ke khud). */
    public static final String[] EMOTIONS =
            {"Auto", "Normal", "Happy", "Sad", "Angry", "Romantic", "Emotional", "Excited"};

    public static int emotionIdx(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (!p.getBoolean("emotion_v2", false)) { // v59: Auto upar aaya — purani choice +1
            int v = p.contains("emotion_idx") ? p.getInt("emotion_idx", 0) + 1 : 0;
            if (v >= EMOTIONS.length) v = 0;
            p.edit().putInt("emotion_idx", v).putBoolean("emotion_v2", true).apply();
            return v;
        }
        int i = p.getInt("emotion_idx", 0);
        return (i >= 0 && i < EMOTIONS.length) ? i : 0;
    }

    /** System prompt me jodne wali line — Live + Chat dono me lagti hai. */
    public static String emotionPrompt(Context ctx) {
        switch (emotionIdx(ctx)) {
            case 0: return "EMOTION AUTO: The user is speaking to you with their voice. "
                    + "Listen carefully to their TONE in every message — happy, sad, angry, "
                    + "romantic, emotional, excited or calm — and automatically speak in the "
                    + "matching emotional tone: gentle and comforting if they sound sad, "
                    + "calm and steady if they sound angry, tender and loving if romantic, "
                    + "cheerful if happy. Never announce that you are detecting emotion; "
                    + "just naturally speak in the matching tone.";
            case 2: return "Emotional tone: cheerful and upbeat — happy, lively, full of energy.";
            case 3: return "Emotional tone: sad and gentle — soft, a little heavy-hearted, comforting.";
            case 4: return "Emotional tone: angry — sharp, short-tempered, fiery words (never abusive).";
            case 5: return "Emotional tone: romantic — sweet, tender, loving words.";
            case 6: return "Emotional tone: deeply emotional — heartfelt, expressive, moves the heart.";
            case 7: return "Emotional tone: excited — enthusiastic, thrilled, high energy.";
            default: return "Emotional tone: natural and balanced.";
        }
    }

    /** v59: Voice Auto — Emotion setting se awaz (beech call me awaz nahi badal sakti). */
    public static final String[] VOICE_IDS = {"Sulafat", "Aoede", "Kore", "Puck"};
    public static final String[] VOICE_LABELS =
            {"Auto — mood ke hisaab se", "Sulafat – Warm", "Aoede – Soft", "Kore – Bright", "Puck – Deep"};

    public static int voiceIdx(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (!p.getBoolean("voice_v2", false)) { // v59: Auto upar aaya — purani choice +1
            int v = p.contains("voice_idx") ? p.getInt("voice_idx", 2) + 1 : 0;
            if (v >= VOICE_LABELS.length) v = 0;
            p.edit().putInt("voice_idx", v).putBoolean("voice_v2", true).apply();
            return v;
        }
        int i = p.getInt("voice_idx", 0);
        return (i >= 0 && i < VOICE_LABELS.length) ? i : 0;
    }

    public static String liveVoiceName(Context ctx) {
        int vi = voiceIdx(ctx);
        if (vi == 0) {
            switch (emotionIdx(ctx)) {
                case 3: return "Aoede";   // Sad → naram
                case 4: return "Kore";    // v91: Angry — Puck khatam, Kore (firm)
                case 5: return "Sulafat"; // Romantic → warm
                case 6: return "Sulafat"; // Emotional → warm
                case 7: return "Kore";     // v91: Excited — Puck khatam, Kore (bright)
                case 2: return "Kore";    // Happy → bright
                default: return "Aoede";   // v91: Auto/Normal → Aoede (nayi default)
            }
        }
        return VOICE_IDS[vi - 1];
    }

    /** v59: Personality Auto — bolne ke andaz se khud dhal jao. */
    public static final String[] PERSONALITIES =
            {"Auto", "Friendly", "GF Mode", "Boss Mode", "Funny", "Calm"};

    public static int personalityIdx(Context ctx) {
        SharedPreferences p = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE);
        if (!p.getBoolean("pers_v2", false)) { // v59: Auto upar aaya — purani choice +1
            int v = p.contains("personality_idx") ? p.getInt("personality_idx", 0) + 1 : 0;
            if (v >= PERSONALITIES.length) v = 0;
            p.edit().putInt("personality_idx", v).putBoolean("pers_v2", true).apply();
            return v;
        }
        int i = p.getInt("personality_idx", 0);
        return (i >= 0 && i < PERSONALITIES.length) ? i : 0;
    }

    /** System prompt me jodne wali line — Live + Chat dono me lagti hai. */
    public static String personalityPrompt(Context ctx) {
        String style;
        String names;
        switch (personalityIdx(ctx)) {
            case 0:
                style = "PERSONALITY AUTO: Listen to HOW the user talks — their style, mood "
                        + "and energy from their voice and words. Automatically adapt your "
                        + "personality to match the moment: playful and funny if they are joking, "
                        + "warm and caring if they sound emotional, sharp and direct if they are "
                        + "serious, sweet and loving if they are romantic. Never announce the "
                        + "adaptation; just naturally be what the moment needs.";
                names = "'Mujahid', 'Hero' or 'Raja'";
                break;
            case 2:
                style = "Personality style: like a sweet, caring girlfriend — "
                        + "affectionate, a little playful, warm. Make him smile.";
                names = "'Jaan', 'Mujahid' or 'Dilbar'";
                break;
            case 3:
                style = "Personality style: confident and to-the-point, respectful boss-like tone.";
                names = "'Sarkar', 'Janab' or 'Malik'";
                break;
            case 4:
                style = "Personality style: light-hearted and playful — keep it fun, never rude.";
                names = "'Chumiii', 'Mujju' or 'Munna'";
                break;
            case 5:
                style = "Personality style: calm and soothing — gentle, unhurried.";
                names = "'Mian', 'Sahab' or 'Mujahid'";
                break;
            default:
                style = "Personality style: warm and friendly, like a caring friend.";
                names = "'Dost', 'Yaar' or 'Jigar'";
                break;
        }
        return style + " ADDRESS NAME RULE (set by Boss himself — this overrides any other "
                + "name for him written anywhere else, including example sentences): in THIS "
                + "mode, address him as " + names + ". Use these names often and naturally "
                + "in your replies; use ALL of these names — alternate between them from "
                + "reply to reply, never stick to only one.";
    }

    // v84: Voice ab dialog nahi — PickerActivity ke page pe (kind="voice").

    /** v53: Personality chuno — agli baat se naya andaz. */
    // v83/v84: pickers ab chhote dialog nahi — sab ka EK hi design ka poora
    // page hai (PickerActivity). Settings ke labels onResume me taza hote hain.
    private TextView persValue;
    private TextView voiceValue;
    private TextView themeValue;
    private TextView emoValue;
    private TextView wxStatus;
    private TextView keyStatus;
    private int shownThemeIdx = -1;

    private void openPicker(String kind) {
        startActivity(new Intent(this, PickerActivity.class)
                .putExtra("kind", kind));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (persValue != null) persValue.setText(PERSONALITIES[personalityIdx(this)]);
        if (voiceValue != null) voiceValue.setText(VOICE_LABELS[voiceIdx(this)]);
        if (themeValue != null) themeValue.setText(Themes.NAMES[Themes.index(this)]);
        if (emoValue != null) emoValue.setText(EMOTIONS[emotionIdx(this)]);
        if (wxStatus != null) wxStatus.setText(
                LiveContext.weatherKey(this).isEmpty() ? "(not set)" : "(set ✓)");
        if (keyStatus != null) keyStatus.setText(keyStatusText());
        if (shownThemeIdx >= 0 && Themes.index(this) != shownThemeIdx) {
            shownThemeIdx = Themes.index(this);
            recreate(); // naye theme rangon se dobara banao (purane dialog jaisa)
        }
    }

    /** v56: Emotion chuno — agli baat se wahi mood. (v84: ab page pe.) */

    /** v53: Keys box ka status — kitni keys set hain. */
    private String keyStatusText() {
        int n = Keys.geminiKeys(this).size();
        String g = Keys.groqKey(this);
        return "(" + n + " Gemini" + (g.isEmpty() ? "" : " + Groq") + " set)";
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // v51: maujooda theme ke rang
        int[] th = Themes.get(this);
        BG = th[0]; CARD = th[1]; TXT = th[2]; HINT = th[3]; ACCENT = th[4];

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        // ===== APPEARANCE (v55: MYRA-style professional cards) =====
        root.addView(sectionHeader("APPEARANCE"));

        LinearLayout lightCard = settingCard("Neon Lighting", null);
        Switch lightSwitch = new Switch(this);
        lightSwitch.setChecked(isLightingOn(this));
        lightSwitch.setOnCheckedChangeListener((v, on) -> setLightingOn(this, on));
        lightCard.addView(lightSwitch);
        root.addView(lightCard);

        LinearLayout themeCard = settingCard("Theme", null);
        themeValue = valueLabel(Themes.NAMES[Themes.index(this)]);
        shownThemeIdx = Themes.index(this);
        themeCard.addView(themeValue);
        themeCard.setOnClickListener(v -> openPicker("theme"));
        root.addView(themeCard);

        // ===== AI & VOICE =====
        root.addView(sectionHeader("AI & VOICE"));

        LinearLayout liveCard = settingCard("Live Voice", "real-time voice chat");
        Switch liveSwitch = new Switch(this);
        liveSwitch.setChecked(isLiveVoiceOn(this));
        liveSwitch.setOnCheckedChangeListener((v, on) -> setLiveVoiceOn(this, on));
        liveCard.addView(liveSwitch);
        root.addView(liveCard);

        LinearLayout voiceCard = settingCard("Voice", null);
        voiceValue = valueLabel(VOICE_LABELS[voiceIdx(this)]);
        voiceCard.addView(voiceValue);
        voiceCard.setOnClickListener(v -> openPicker("voice"));
        root.addView(voiceCard);

        LinearLayout persCard = settingCard("Personality", null);
        persValue = valueLabel(PERSONALITIES[personalityIdx(this)]);
        persCard.addView(persValue);
        persCard.setOnClickListener(v -> openPicker("personality"));
        root.addView(persCard);

        LinearLayout emoCard = settingCard("Emotion", null);
        emoValue = valueLabel(EMOTIONS[emotionIdx(this)]);
        emoCard.addView(emoValue);
        emoCard.setOnClickListener(v -> openPicker("emotion"));
        root.addView(emoCard);

        LinearLayout orbCard = settingCard("Floating Orb", "button over other apps");
        Switch orbSwitch = new Switch(this);
        orbSwitch.setChecked(isOrbOn(this));
        orbSwitch.setOnCheckedChangeListener((v, on) -> {
            if (on) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                        && !Settings.canDrawOverlays(this)) {
                    Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                            android.net.Uri.parse("package:" + getPackageName()));
                    startActivity(i);
                    orbSwitch.setChecked(false);
                    Toast.makeText(this,
                            "First allow 'Display over other apps', then turn it ON again.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                setOrbOn(this, true);
                startService(new Intent(this, OrbService.class));
                Toast.makeText(this, "Orb ON — dabao to Ayesha khulegi.",
                        Toast.LENGTH_SHORT).show();
            } else {
                setOrbOn(this, false);
                stopService(new Intent(this, OrbService.class));
            }
        });
        orbCard.addView(orbSwitch);
        root.addView(orbCard);

        // ===== MEMORY =====
        root.addView(sectionHeader("MEMORY"));

        LinearLayout memCard = settingCard("Auto Memory", "she remembers herself");
        Switch memSwitch = new Switch(this);
        memSwitch.setChecked(isAutoMemoryOn(this));
        memSwitch.setOnCheckedChangeListener((v, on) -> setAutoMemoryOn(this, on));
        memCard.addView(memSwitch);
        root.addView(memCard);

        // ===== VOICE ID (v57 pehla qadam) =====
        root.addView(sectionHeader("VOICE ID"));

        LinearLayout vidCard = settingCard("Voice ID", "your voiceprint");
        TextView vidValue = valueLabel(
                VoicePrint.isEnrolled(this) ? "Enrolled ✓" : "Not enrolled");
        vidCard.addView(vidValue);
        vidCard.setOnClickListener(v ->
                startActivity(new Intent(this, VoiceIdActivity.class)));
        root.addView(vidCard);

        // ===== KEYS & DATA =====
        root.addView(sectionHeader("KEYS & DATA"));

        LinearLayout wxCard = settingCard("Weather API key", null);
        wxStatus = valueLabel(
                LiveContext.weatherKey(this).isEmpty() ? "(not set)" : "(set \u2713)");
        wxCard.addView(wxStatus);
        wxCard.setOnClickListener(v -> openPicker("weather"));
        root.addView(wxCard);

        LinearLayout keyCard = settingCard("Keys", "tap to open");
        keyStatus = valueLabel(keyStatusText());
        keyCard.addView(keyStatus);
        keyCard.setOnClickListener(v -> openPicker("keys"));
        root.addView(keyCard);

        Button liveBtn = darkButton("Live API Test (experimental)");
        liveBtn.setOnClickListener(v ->
                startActivity(new Intent(this, LiveTestActivity.class)));
        root.addView(liveBtn);

    }
}
