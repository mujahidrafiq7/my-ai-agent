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

    private final List<EditText> keyInputs = new ArrayList<>();
    private EditText groqInput;

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
        gd.setColor(CARD);
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
        gd.setColor(BG);
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
        gd.setColor(CARD);
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

    /** v51 "Rang": theme chuno — foran lag jayega. */
    private void showThemeDialog(TextView valueView) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Theme")
                .setSingleChoiceItems(Themes.NAMES, Themes.index(this),
                        (d, which) -> {
                            Themes.set(this, which);
                            valueView.setText(Themes.NAMES[which]);
                            d.dismiss();
                            recreate(); // naye rangon se dobara banao
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** "Keys" — sari saved keys ek dialog me dikhao. */
    /** v40: Weather API key dalo — key sirf is phone me rehti hai. */
    private void showWeatherKeyDialog(TextView statusView) {
        final android.widget.EditText et = new android.widget.EditText(this);
        et.setHint("OpenWeatherMap API key");
        et.setText(LiveContext.weatherKey(this));
        et.setTextColor(TXT);
        int p = (int) (16 * getResources().getDisplayMetrics().density);
        et.setPadding(p, p, p, p);
        new android.app.AlertDialog.Builder(this)
                .setTitle("Weather API key")
                .setView(et)
                .setPositiveButton("Save", (d, w) -> {
                    LiveContext.setWeatherKey(this, et.getText().toString());
                    statusView.setText(
                            LiveContext.weatherKey(this).isEmpty() ? "(not set)" : "(set ✓)");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** v54 "Orb": Floating Orb on/off — doosri apps ke upar chhota button. */
    public static boolean isOrbOn(Context ctx) {
        return ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getBoolean("floating_orb", false);
    }

    private static void setOrbOn(Context ctx, boolean on) {
        ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putBoolean("floating_orb", on).apply();
    }

    /** v56: Emotion picker — Ayesha kis mood me baat kare. */
    public static final String[] EMOTIONS =
            {"Normal", "Happy", "Sad", "Angry", "Romantic", "Emotional", "Excited"};

    public static int emotionIdx(Context ctx) {
        int i = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getInt("emotion_idx", 0);
        return (i >= 0 && i < EMOTIONS.length) ? i : 0;
    }

    /** System prompt me jodne wali line — Live + Chat dono me lagti hai. */
    public static String emotionPrompt(Context ctx) {
        switch (emotionIdx(ctx)) {
            case 1: return "Emotional tone: cheerful and upbeat — happy, lively, full of energy.";
            case 2: return "Emotional tone: sad and gentle — soft, a little heavy-hearted, comforting.";
            case 3: return "Emotional tone: angry — sharp, short-tempered, fiery words (never abusive).";
            case 4: return "Emotional tone: romantic — sweet, tender, loving words.";
            case 5: return "Emotional tone: deeply emotional — heartfelt, expressive, moves the heart.";
            case 6: return "Emotional tone: excited — enthusiastic, thrilled, high energy.";
            default: return "Emotional tone: natural and balanced.";
        }
    }

    /** v53: Voice + Personality — MYRA jaisa picker. */
    public static final String[] VOICE_IDS = {"Sulafat", "Aoede", "Kore", "Puck"};
    public static final String[] VOICE_LABELS =
            {"Sulafat – Warm", "Aoede – Soft", "Kore – Bright", "Puck – Deep"};

    public static int voiceIdx(Context ctx) {
        int i = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getInt("voice_idx", 2); // default Kore — pehle jaisi awaz
        return (i >= 0 && i < VOICE_IDS.length) ? i : 2;
    }

    public static String liveVoiceName(Context ctx) {
        return VOICE_IDS[voiceIdx(ctx)];
    }

    public static final String[] PERSONALITIES =
            {"Friendly", "GF Mode", "Boss Mode", "Funny", "Calm"};

    public static int personalityIdx(Context ctx) {
        int i = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getInt("personality_idx", 0);
        return (i >= 0 && i < PERSONALITIES.length) ? i : 0;
    }

    /** System prompt me jodne wali line — Live + Chat dono me lagti hai. */
    public static String personalityPrompt(Context ctx) {
        switch (personalityIdx(ctx)) {
            case 1: return "Personality style: like a sweet, caring girlfriend — "
                    + "affectionate, a little playful, warm. Make him smile.";
            case 2: return "Personality style: confident and to-the-point, respectful boss-like tone.";
            case 3: return "Personality style: light-hearted and playful — keep it fun, never rude.";
            case 4: return "Personality style: calm and soothing — gentle, unhurried.";
            default: return "Personality style: warm and friendly, like a caring friend.";
        }
    }

    /** v53: Voice chuno — agli Live call se nayi awaz. */
    private void showVoiceDialog(TextView valueView) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Voice")
                .setSingleChoiceItems(VOICE_LABELS, voiceIdx(this),
                        (d, which) -> {
                            getSharedPreferences("app_settings", MODE_PRIVATE)
                                    .edit().putInt("voice_idx", which).apply();
                            valueView.setText(VOICE_LABELS[which]);
                            d.dismiss();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** v53: Personality chuno — agli baat se naya andaz. */
    private void showPersonalityDialog(TextView valueView) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Personality")
                .setSingleChoiceItems(PERSONALITIES, personalityIdx(this),
                        (d, which) -> {
                            getSharedPreferences("app_settings", MODE_PRIVATE)
                                    .edit().putInt("personality_idx", which).apply();
                            valueView.setText(PERSONALITIES[which]);
                            d.dismiss();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

    /** v56: Emotion chuno — agli baat se wahi mood. */
    private void showEmotionDialog(TextView valueView) {
        new android.app.AlertDialog.Builder(this)
                .setTitle("Emotion")
                .setSingleChoiceItems(EMOTIONS, emotionIdx(this),
                        (d, which) -> {
                            getSharedPreferences("app_settings", MODE_PRIVATE)
                                    .edit().putInt("emotion_idx", which).apply();
                            valueView.setText(EMOTIONS[which]);
                            d.dismiss();
                        })
                .setNegativeButton("Cancel", null)
                .show();
    }

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
        TextView themeValue = valueLabel(Themes.NAMES[Themes.index(this)]);
        themeCard.addView(themeValue);
        themeCard.setOnClickListener(v -> showThemeDialog(themeValue));
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
        TextView voiceValue = valueLabel(VOICE_LABELS[voiceIdx(this)]);
        voiceCard.addView(voiceValue);
        voiceCard.setOnClickListener(v -> showVoiceDialog(voiceValue));
        root.addView(voiceCard);

        LinearLayout persCard = settingCard("Personality", null);
        TextView persValue = valueLabel(PERSONALITIES[personalityIdx(this)]);
        persCard.addView(persValue);
        persCard.setOnClickListener(v -> showPersonalityDialog(persValue));
        root.addView(persCard);

        LinearLayout emoCard = settingCard("Emotion", null);
        TextView emoValue = valueLabel(EMOTIONS[emotionIdx(this)]);
        emoCard.addView(emoValue);
        emoCard.setOnClickListener(v -> showEmotionDialog(emoValue));
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
                            "Pehle 'Display over other apps' allow karo, phir dobara ON karo.",
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

        // ===== KEYS & DATA =====
        root.addView(sectionHeader("KEYS & DATA"));

        LinearLayout wxCard = settingCard("Weather API key", null);
        TextView wxStatus = valueLabel(
                LiveContext.weatherKey(this).isEmpty() ? "(not set)" : "(set \u2713)");
        wxCard.addView(wxStatus);
        wxCard.setOnClickListener(v -> showWeatherKeyDialog(wxStatus));
        root.addView(wxCard);

        LinearLayout keyCard = settingCard("Keys", "tap to open");
        TextView keyStatus = valueLabel(keyStatusText());
        keyCard.addView(keyStatus);
        root.addView(keyCard);

        LinearLayout keysBox = new LinearLayout(this);
        keysBox.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable boxBg = new GradientDrawable();
        boxBg.setColor(CARD);
        boxBg.setCornerRadius(dp(28));
        keysBox.setBackground(boxBg);
        keysBox.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams boxLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        boxLp.bottomMargin = dp(10);
        keysBox.setLayoutParams(boxLp);
        keysBox.setVisibility(View.GONE);
        root.addView(keysBox);
        keyCard.setOnClickListener(v -> {
            boolean open = keysBox.getVisibility() != View.VISIBLE;
            keysBox.setVisibility(open ? View.VISIBLE : View.GONE);
            keyStatus.setText(open ? "(tap to close)" : keyStatusText());
        });

        TextView title = darkLabel("API Keys \u2014 stored only on your phone\n"
                + "Make each key from a DIFFERENT Gmail (2 keys from one Gmail = one shared limit)",
                14);
        title.setTextColor(HINT);
        title.setPadding(0, 0, 0, pad / 2);
        keysBox.addView(title);

        for (int i = 1; i <= Keys.MAX_KEYS; i++) {
            TextView label = darkLabel("Gemini Key " + i + " (AI Studio)", 13);
            label.setPadding(0, pad / 2, 0, dp(4));
            keysBox.addView(label);

            EditText in = darkInput("Paste key " + i + " here (can leave empty)");
            in.setInputType(InputType.TYPE_CLASS_TEXT
                    | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            in.setText(Keys.geminiKeyAt(this, i));
            keysBox.addView(in);
            keyInputs.add(in);
        }

        TextView groqLabel = darkLabel("Groq API Key (backup brain + speech recognition)", 13);
        groqLabel.setPadding(0, pad / 2, 0, dp(4));
        keysBox.addView(groqLabel);

        groqInput = darkInput("Paste Groq key here");
        groqInput.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        groqInput.setText(Keys.groqKey(this));
        keysBox.addView(groqInput);

        Button save = darkButton("Save");
        keysBox.addView(save);

        save.setOnClickListener(v -> {
            List<String> keys = new ArrayList<>();
            for (EditText in : keyInputs) keys.add(in.getText().toString());
            Keys.save(this, keys, groqInput.getText().toString());
            ChatClient.resetBreakers();
            GeminiTts.resetBreaker();
            int n = Keys.geminiKeys(this).size();
            Toast.makeText(this, n + " Gemini key(s) saved.",
                    Toast.LENGTH_SHORT).show();
            finish();
        });

        Button liveBtn = darkButton("Live API Test (experimental)");
        liveBtn.setOnClickListener(v ->
                startActivity(new Intent(this, LiveTestActivity.class)));
        root.addView(liveBtn);

        // ===== BACKUP =====
        root.addView(sectionHeader("BACKUP"));

        Button backupBtn = darkButton("\uD83D\uDCBE Backup Keys (to Downloads)");
        root.addView(backupBtn);
        backupBtn.setOnClickListener(v -> exportKeys());

        Button restoreBtn = darkButton("\uD83D\uDCE5 Restore Keys from Backup");
        root.addView(restoreBtn);
        restoreBtn.setOnClickListener(v -> importKeys());

        TextView warn = darkLabel("\u26A0\uFE0F The backup file contains your keys \u2014 never send it to anyone!", 12);
        warn.setTextColor(HINT);
        warn.setPadding(0, dp(4), 0, 0);
        root.addView(warn);
    }

    /** Saari keys ek JSON file me — Downloads folder, sirf tumhare phone me. */
    private void exportKeys() {
        try {
            JSONObject obj = new JSONObject();
            obj.put("app", "my-agent-keys");
            JSONArray arr = new JSONArray();
            for (int i = 1; i <= Keys.MAX_KEYS; i++)
                arr.put(Keys.geminiKeyAt(this, i));
            obj.put("gemini_keys", arr);
            obj.put("groq_key", Keys.groqKey(this));

            String fileName = "my-agent-keys-backup.json";
            ContentValues values = new ContentValues();
            values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
            values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
            values.put(MediaStore.Downloads.RELATIVE_PATH,
                    Environment.DIRECTORY_DOWNLOADS);
            Uri uri = getContentResolver().insert(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
            if (uri == null) throw new Exception("Could not create file");
            OutputStream os = getContentResolver().openOutputStream(uri);
            os.write(obj.toString().getBytes("UTF-8"));
            os.close();
            Toast.makeText(this, "Backup done: Downloads/" + fileName,
                    Toast.LENGTH_LONG).show();
        } catch (Exception e) {
            Toast.makeText(this, "Backup failed: " + e.getMessage(),
                    Toast.LENGTH_LONG).show();
        }
    }

    /** Backup file chuno — keys wapas aa jayengi. */
    private void importKeys() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(i, 200);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req == 200 && res == RESULT_OK && data != null
                && data.getData() != null) {
            try {
                InputStream in = getContentResolver()
                        .openInputStream(data.getData());
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[4096];
                int n;
                while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
                in.close();
                JSONObject obj = new JSONObject(
                        new String(b.toByteArray(), "UTF-8"));

                List<String> keys = new ArrayList<>();
                JSONArray arr = obj.optJSONArray("gemini_keys");
                if (arr != null) {
                    for (int i = 0; i < arr.length() && i < Keys.MAX_KEYS; i++)
                        keys.add(arr.optString(i, ""));
                }
                String groq = obj.optString("groq_key", "");
                Keys.save(this, keys, groq);
                ChatClient.resetBreakers();
                GeminiTts.resetBreaker();

                // Screen par bhi dikhao ke keys aa gayin
                for (int i = 0; i < keyInputs.size(); i++) {
                    String k = i < keys.size() ? keys.get(i) : "";
                    keyInputs.get(i).setText(k);
                }
                groqInput.setText(groq);
                Toast.makeText(this,
                        Keys.geminiKeys(this).size() + " Gemini keys restored!",
                        Toast.LENGTH_LONG).show();
            } catch (Exception e) {
                Toast.makeText(this, "Import failed: " + e.getMessage(),
                        Toast.LENGTH_LONG).show();
            }
        }
    }
}
