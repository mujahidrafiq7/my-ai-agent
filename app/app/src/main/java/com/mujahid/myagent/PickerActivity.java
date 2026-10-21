package com.mujahid.myagent;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * v84: Settings ke saare picker EK hi design ke POORE page pe —
 * Personality, Voice, Theme, Emotion (list wale) aur Weather API key
 * (likhne wala). Upar Back box, bara title, chhoti English hint,
 * gol radio wale professional card rows — har page bilkul ek jaisa.
 * Rang maujooda Theme se ate hain (Themes.get).
 * List pe tap = save + wapas. Weather pe Save box = save + wapas.
 */
public class PickerActivity extends Activity {

    private String kind = "personality";
    private int bg, card, txt, hintC, accent;
    private View[] dots;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String k = getIntent().getStringExtra("kind");
        if (k != null) kind = k;

        int[] pal = Themes.get(this); // {bg, card, text, hint, accent}
        bg = pal[0]; card = pal[1]; txt = pal[2]; hintC = pal[3]; accent = pal[4];

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));

        // ---- Back box (v85: VIP — gola pill, sunehri kinari, teer nahi) ----
        TextView back = new TextView(this);
        back.setText("Back");
        back.setTextColor(txt);
        back.setTextSize(15);
        back.setTypeface(null, android.graphics.Typeface.BOLD);
        back.setLetterSpacing(0.08f);
        back.setPadding(dp(26), dp(11), dp(26), dp(11));
        GradientDrawable bb = new GradientDrawable();
        bb.setColor(card);
        bb.setCornerRadius(dp(30));
        bb.setStroke(dp(2), accent);
        back.setBackground(bb);
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.bottomMargin = dp(18);
        root.addView(back, bp);

        // ---- Title + short English hint ----
        TextView title = new TextView(this);
        title.setText(title());
        title.setTextColor(txt);
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(hint());
        hint.setTextColor(hintC);
        hint.setTextSize(14);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(6);
        hp.bottomMargin = dp(18);
        root.addView(hint, hp);

        if (kind.equals("weather")) {
            buildWeather(root);
        } else if (kind.equals("keys")) {
            buildKeys(root);
        } else {
            buildList(root);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.addView(root);
        setContentView(scroll);
    }

    // ---------- list wale pages ----------

    private void buildList(LinearLayout root) {
        String[] items = items();
        int selected = current();
        dots = new View[items.length];
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(18), dp(18), dp(18));
            row.setBackground(box(card, 16));

            // gol radio: bahar ring, andar nukta (select pe)
            FrameLayout radio = new FrameLayout(this);
            View ring = new View(this);
            GradientDrawable rg = new GradientDrawable();
            rg.setShape(GradientDrawable.OVAL);
            rg.setColor(Color.TRANSPARENT);
            rg.setStroke(dp(2), i == selected ? accent : Color.rgb(110, 122, 138));
            ring.setBackground(rg);
            radio.addView(ring, new FrameLayout.LayoutParams(dp(22), dp(22),
                    Gravity.CENTER));
            View dot = new View(this);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(accent);
            dot.setBackground(dg);
            dot.setVisibility(i == selected ? View.VISIBLE : View.INVISIBLE);
            radio.addView(dot, new FrameLayout.LayoutParams(dp(11), dp(11),
                    Gravity.CENTER));
            dots[i] = dot;
            row.addView(radio, new LinearLayout.LayoutParams(dp(34), dp(34)));

            TextView name = new TextView(this);
            name.setText(items[i]);
            name.setTextColor(txt);
            name.setTextSize(18);
            row.addView(name);

            row.setOnClickListener(v -> {
                save(idx);
                finish();
            });

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(12);
            root.addView(row, rp);
        }
    }

    // ---------- weather wala page ----------

    private void buildWeather(LinearLayout root) {
        final EditText et = new EditText(this);
        et.setHint("OpenWeatherMap API key");
        et.setText(LiveContext.weatherKey(this));
        et.setTextColor(txt);
        et.setHintTextColor(hintC);
        et.setTextSize(16);
        et.setPadding(dp(16), dp(16), dp(16), dp(16));
        et.setBackground(box(card, 14));
        LinearLayout.LayoutParams ep = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        ep.bottomMargin = dp(16);
        root.addView(et, ep);

        TextView save = new TextView(this);
        save.setText("Save");
        save.setTextColor(bg);
        save.setTextSize(16);
        save.setTypeface(null, android.graphics.Typeface.BOLD);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(14), dp(16), dp(14));
        save.setBackground(box(accent, 14));
        save.setOnClickListener(v -> {
            LiveContext.setWeatherKey(this, et.getText().toString());
            finish();
        });
        root.addView(save);
    }

    // ---------- keys wala page ----------

    private void buildKeys(LinearLayout root) {
        final java.util.List<EditText> inputs = new java.util.ArrayList<>();
        for (int i = 1; i <= Keys.MAX_KEYS; i++) {
            TextView label = new TextView(this);
            label.setText("Gemini Key " + i + " (AI Studio)");
            label.setTextColor(hintC);
            label.setTextSize(13);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = dp(10);
            lp.bottomMargin = dp(5);
            root.addView(label, lp);

            EditText in = keyField("Paste key " + i + " here (can leave empty)");
            in.setText(Keys.geminiKeyAt(this, i));
            root.addView(in);
            inputs.add(in);
        }

        TextView groqLabel = new TextView(this);
        groqLabel.setText("Groq API Key (backup brain + speech recognition)");
        groqLabel.setTextColor(hintC);
        groqLabel.setTextSize(13);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        glp.topMargin = dp(14);
        glp.bottomMargin = dp(5);
        root.addView(groqLabel, glp);

        final EditText groqIn = keyField("Paste Groq key here");
        groqIn.setText(Keys.groqKey(this));
        root.addView(groqIn);

        TextView save = new TextView(this);
        save.setText("Save");
        save.setTextColor(bg);
        save.setTextSize(16);
        save.setTypeface(null, android.graphics.Typeface.BOLD);
        save.setGravity(Gravity.CENTER);
        save.setPadding(dp(16), dp(14), dp(16), dp(14));
        save.setBackground(box(accent, 14));
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = dp(18);
        root.addView(save, sp);
        save.setOnClickListener(v -> {
            java.util.List<String> keys = new java.util.ArrayList<>();
            for (EditText in : inputs) keys.add(in.getText().toString());
            Keys.save(this, keys, groqIn.getText().toString());
            ChatClient.resetBreakers();
            GeminiTts.resetBreaker();
            android.widget.Toast.makeText(this,
                    Keys.geminiKeys(this).size() + " Gemini key(s) saved.",
                    android.widget.Toast.LENGTH_SHORT).show();
            finish();
        });
    }

    private EditText keyField(String hintText) {
        EditText in = new EditText(this);
        in.setHint(hintText);
        in.setTextColor(txt);
        in.setHintTextColor(hintC);
        in.setTextSize(15);
        in.setInputType(android.text.InputType.TYPE_CLASS_TEXT
                | android.text.InputType.TYPE_TEXT_VARIATION_PASSWORD);
        in.setPadding(dp(16), dp(14), dp(16), dp(14));
        in.setBackground(box(card, 14));
        return in;
    }

    // ---------- kind ke hisaab se data ----------

    private String title() {
        switch (kind) {
            case "voice": return "Voice";
            case "theme": return "Theme";
            case "emotion": return "Emotion";
            case "weather": return "Weather API Key";
            case "keys": return "Keys";
            default: return "Personality";
        }
    }

    private String hint() {
        switch (kind) {
            case "voice": return "Choose Ayesha's voice.";
            case "theme": return "Choose the app theme.";
            case "emotion": return "Choose Ayesha's mood.";
            case "weather": return "For live weather. The key stays on this phone.";
            case "keys": return "Stored only on this phone. Make each Gemini key from a different Gmail.";
            default: return "Choose a style — Ayesha will speak in that tone.";
        }
    }

    private String[] items() {
        switch (kind) {
            case "voice": return SettingsActivity.VOICE_LABELS;
            case "theme": return Themes.NAMES;
            case "emotion": return SettingsActivity.EMOTIONS;
            default: return SettingsActivity.PERSONALITIES;
        }
    }

    private int current() {
        switch (kind) {
            case "voice": return SettingsActivity.voiceIdx(this);
            case "theme": return Themes.index(this);
            case "emotion": return SettingsActivity.emotionIdx(this);
            default: return SettingsActivity.personalityIdx(this);
        }
    }

    private void save(int idx) {
        SharedPreferences p = getSharedPreferences("app_settings", MODE_PRIVATE);
        switch (kind) {
            case "voice": p.edit().putInt("voice_idx", idx).apply(); break;
            case "theme": Themes.set(this, idx); break;
            case "emotion": p.edit().putInt("emotion_idx", idx).apply(); break;
            default: p.edit().putInt("personality_idx", idx).apply(); break;
        }
    }

    private GradientDrawable box(int color, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
