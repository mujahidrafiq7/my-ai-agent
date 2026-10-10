package com.mujahid.myagent;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

/**
 * v51 "Rang": 6 themes (MYRA wale naam). Simple design:
 * - Har theme = 5 rang: background, card, text, hint, accent.
 * - Pasand "app_settings" me save hoti hai.
 * - SettingsActivity (code se bani UI) seedha Themes.get() parhti hai.
 * - XML layouts (Main/Chat) ke liye tint(): purane palette rangon ko
 *   view-tree walk karke naye rangon se badalta hai. Sirf pehchane hue
 *   5 palette rang badalte hain — button ke brand rang (tint) untouched.
 */
public class Themes {

    public static final String[] NAMES = {
            "Teal Pulse", "Midnight Blue", "Crimson Dark",
            "Violet Haze", "Rose Dark", "Pure Black"
    };

    // har theme: {bg, card, text, hint, accent}
    private static final int[][] COLORS = {
            {0xFF060A12, 0xFF141B28, 0xFFE8EEF4, 0xFF788C9B, 0xFF00E5FF}, // Teal Pulse
            {0xFF050914, 0xFF101A30, 0xFFE8EEF4, 0xFF7A8CA0, 0xFF2E9BFF}, // Midnight Blue
            {0xFF0F0608, 0xFF1F1013, 0xFFF4E8E8, 0xFF9B7880, 0xFFFF3B5C}, // Crimson Dark
            {0xFF0A0612, 0xFF171028, 0xFFEEE8F4, 0xFF8C789B, 0xFFA855F7}, // Violet Haze
            {0xFF0D0608, 0xFF221016, 0xFFF4E8EC, 0xFF9B7886, 0xFFFB7185}, // Rose Dark
            {0xFF000000, 0xFF111111, 0xFFFFFFFF, 0xFF888888, 0xFF00E5FF}, // Pure Black
    };

    public static int count() { return NAMES.length; }

    public static int index(Context ctx) {
        int i = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .getInt("theme_idx", 0);
        return (i >= 0 && i < NAMES.length) ? i : 0;
    }

    public static void set(Context ctx, int i) {
        ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                .edit().putInt("theme_idx", i).apply();
    }

    /** {bg, card, text, hint, accent} — maujooda theme ke rang. */
    public static int[] get(Context ctx) {
        return COLORS[index(ctx)];
    }

    /** XML layouts ke liye: purane palette rang → naye theme rang. */
    public static void tint(Activity a) {
        int[] t = get(a);
        View root = a.findViewById(android.R.id.content);
        if (root != null) walk(root, t[0], t[1], t[2], t[3], t[4]);
    }

    private static void walk(View v, int bg, int card, int txt, int hint, int accent) {
        Drawable d = v.getBackground();
        if (d instanceof ColorDrawable) {
            int c = ((ColorDrawable) d).getColor();
            int n = mapBg(c, bg, card);
            if (n != c) v.setBackgroundColor(n);
        }
        if (v instanceof TextView) {
            TextView tv = (TextView) v;
            int c = tv.getCurrentTextColor();
            int n = mapText(c, txt, hint, accent);
            if (n != c) tv.setTextColor(n);
            if (v instanceof EditText) ((EditText) v).setHintTextColor(hint);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++)
                walk(g.getChildAt(i), bg, card, txt, hint, accent);
        }
    }

    private static int mapBg(int c, int bg, int card) {
        if (c == 0xFF060A12) return bg;
        if (c == 0xFF141B28 || c == 0xFF1E2A3A || c == 0xFF23374B) return card;
        return c;
    }

    private static int mapText(int c, int txt, int hint, int accent) {
        if (c == 0xFFE8EEF4 || c == 0xFFFFFFFF || c == 0xFFF0F5FA) return txt;
        if (c == 0xFF788C9B) return hint;
        if (c == 0xFF00E5FF) return accent;
        return c;
    }
}
