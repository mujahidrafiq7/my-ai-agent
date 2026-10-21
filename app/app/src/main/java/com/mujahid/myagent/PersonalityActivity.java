package com.mujahid.myagent;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * v83: Personality ka apna POORA page (pehle chhota dialog tha).
 * Sirf personality ke naam dikhte hain — Auto, Friendly, GF Mode,
 * Boss Mode, Funny, Calm. Pukarne wale naam yahan kahin nahi likhe
 * jate; wo sirf guftagu ke liye andar (prompt me) hain.
 * Tap karte hi choice save hoti hai aur page band ho jata hai;
 * Settings ka label onResume me taza ho jata hai.
 */
public class PersonalityActivity extends Activity {

    private final TextView[] radios = new TextView[SettingsActivity.PERSONALITIES.length];

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        int bg = Color.rgb(9, 12, 17);
        int card = Color.rgb(19, 26, 35);
        int accent = Color.rgb(45, 212, 191);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(20), dp(24), dp(20), dp(24));

        TextView title = new TextView(this);
        title.setText("Personality");
        title.setTextColor(Color.WHITE);
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText("Apni pasand ki personality chunein — Ayesha ka andaz isi se badlega.");
        hint.setTextColor(Color.rgb(150, 163, 180));
        hint.setTextSize(14);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(6);
        hp.bottomMargin = dp(18);
        root.addView(hint, hp);

        int selected = SettingsActivity.personalityIdx(this);
        for (int i = 0; i < SettingsActivity.PERSONALITIES.length; i++) {
            final int idx = i;
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(18), dp(18), dp(18));
            GradientDrawable g = new GradientDrawable();
            g.setColor(card);
            g.setCornerRadius(dp(16));
            row.setBackground(g);

            TextView radio = new TextView(this);
            radio.setTextSize(20);
            radio.setGravity(Gravity.CENTER);
            radios[i] = radio;
            row.addView(radio, new LinearLayout.LayoutParams(dp(40), dp(40)));

            TextView name = new TextView(this);
            name.setText(SettingsActivity.PERSONALITIES[i]);
            name.setTextColor(Color.WHITE);
            name.setTextSize(18);
            row.addView(name);

            paintRadio(i, i == selected, accent);
            row.setOnClickListener(v -> {
                getSharedPreferences("app_settings", MODE_PRIVATE)
                        .edit().putInt("personality_idx", idx).apply();
                for (int j = 0; j < radios.length; j++) {
                    paintRadio(j, j == idx, accent);
                }
                finish();
            });

            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(12);
            root.addView(row, rp);
        }

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void paintRadio(int i, boolean on, int accent) {
        TextView r = radios[i];
        if (r == null) return;
        r.setText(on ? "◉" : "○");
        r.setTextColor(on ? accent : Color.rgb(120, 133, 150));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
