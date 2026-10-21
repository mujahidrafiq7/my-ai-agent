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

        // ---- Back box (professional) ----
        TextView back = new TextView(this);
        back.setText("←  Back");
        back.setTextColor(txt);
        back.setTextSize(15);
        back.setTypeface(null, android.graphics.Typeface.BOLD);
        back.setPadding(dp(16), dp(10), dp(16), dp(10));
        back.setBackground(box(card, 12));
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

    // ---------- kind ke hisaab se data ----------

    private String title() {
        switch (kind) {
            case "voice": return "Voice";
            case "theme": return "Theme";
            case "emotion": return "Emotion";
            case "weather": return "Weather API Key";
            default: return "Personality";
        }
    }

    private String hint() {
        switch (kind) {
            case "voice": return "Choose Ayesha's voice.";
            case "theme": return "Choose the app theme.";
            case "emotion": return "Choose Ayesha's mood.";
            case "weather": return "For live weather. The key stays on this phone.";
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
