package com.mujahid.myagent;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

/**
 * STEP 2 — Screen control commands (AccessibilityService ke zariye).
 *   "scroll karo" / "upar scroll karo" / "tez scroll karo" / "thoda scroll karo"
 *   "type karo assalamualaikum"      → focused field me type
 *   "'Search' dabao" / "X pe tap karo" → us lafz pe tap
 *   "home pe jao" / "wapas jao"      → home / back
 *   "accessibility on karo"          → settings page kholo (wahan ON karna parega)
 */
public class UiControl {

    public static class Result {
        public final boolean handled;
        public final String reply;
        Result(boolean h, String r) { handled = h; reply = r; }
    }

    public static Result tryHandle(Context ctx, String normText) {
        String t = normText == null ? "" : normText;

        // Accessibility settings page (wahan user khud ON karega)
        if (t.contains("accessibility")
                && (t.contains(" on ") || t.endsWith(" on") || t.contains("karo")
                || t.contains("kholo") || t.contains("kholen"))) {
            openA11ySettings(ctx);
            return new Result(true,
                    "Accessibility ka page khol diya — wahan 'My Agent' ko ON kar do boss.");
        }

        // Service off ho to sirf UI commandon pe yaad dilao
        if (!A11yService.isEnabled()) {
            if (looksLikeUiCommand(t)) {
                return new Result(true,
                        "Pehle accessibility on karo boss — bolo 'accessibility on karo'.");
            }
            return new Result(false, "");
        }

        // Home / Back ("aao" aur "page" bhi — "home page pe aao" pakde)
        if (t.contains("home") && (t.contains("jao") || t.contains("jau")
                || t.contains("aao") || t.contains("chalo")
                || t.contains("screen") || t.contains("page"))) {
            return new Result(true,
                    A11yService.goHome() ? "Home pe aa gaya." : "Home na khul saka.");
        }
        if ((t.contains("wapas") || t.equals("back") || t.contains(" back "))
                && (t.contains("jao") || t.contains("jau")
                || t.contains("aao") || t.contains("chalo"))) {
            return new Result(true,
                    A11yService.goBack() ? "Wapas aa gaya." : "Wapas na ja saka.");
        }

        // Scroll
        if (t.contains("scroll")) {
            boolean up = t.contains("upar") || t.contains("uper") || t.contains("oopar");
            int times = 1;
            if (t.contains("tez") || t.contains("fast") || t.contains("zyada")
                    || t.contains("ziyada")) times = 4;
            else if (t.contains("thoda") || t.contains("thora") || t.contains("halka")) times = 1;
            boolean ok = A11yService.scroll(up, times);
            return new Result(true, ok ? "Scroll kar diya."
                    : "Scroll na ho saka — screen pe koi scroll wali jagah nahi mili.");
        }

        // Type karo X  (ya: X type karo)
        if (t.contains("type") && t.contains("karo")) {
            String q = after(t, "type karo");
            if (q.isEmpty()) q = before(t, "type karo");
            if (q.endsWith(" ko")) q = q.substring(0, q.length() - 3).trim();
            if (q.isEmpty())
                return new Result(true, "Kya type karun boss?");
            boolean ok = A11yService.typeText(q);
            return new Result(true, ok ? "Type kar diya."
                    : "Type na ho saka — pehle likhne ki jagah pe ek tap karo, phir bolo.");
        }

        // X dabao / X pe tap karo
        String target = extractTap(t);
        if (target != null && !target.isEmpty()) {
            boolean ok = A11yService.tapText(target);
            return new Result(true, ok ? "Daba diya."
                    : "'" + target + "' screen pe nahi mila.");
        }

        return new Result(false, "");
    }

    private static boolean looksLikeUiCommand(String t) {
        return t.contains("scroll")
                || (t.contains("type") && t.contains("karo"))
                || t.contains("dabao") || t.contains("tap karo")
                || (t.contains("home") && t.contains("jao"))
                || (t.contains("wapas") && t.contains("jao"));
    }

    /** "X dabao" / "X ko dabao" / "X pe tap karo" / "X tap karo" me se X nikalo. */
    private static String extractTap(String t) {
        String[] tails = {" pe tap karo", " par tap karo", " tap karo",
                " ko dabao", " dabao", " press karo", " ko press karo"};
        for (String tail : tails) {
            int i = t.indexOf(tail);
            if (i > 0) {
                String x = t.substring(0, i).trim();
                // "is ko" / "us ko" jaise aakhri lafz hatao
                for (String w : new String[]{" ko", " pe", " par", " mein", " me"}) {
                    if (x.endsWith(w)) x = x.substring(0, x.length() - w.length()).trim();
                }
                return x;
            }
        }
        return null;
    }

    private static String after(String t, String key) {
        int i = t.indexOf(key);
        if (i < 0) return "";
        return t.substring(i + key.length()).trim();
    }

    private static String before(String t, String key) {
        int i = t.indexOf(key);
        if (i <= 0) return "";
        return t.substring(0, i).trim();
    }

    private static void openA11ySettings(Context ctx) {
        try {
            Intent i = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            new Handler(Looper.getMainLooper()).post(() -> {
                try { ctx.startActivity(i); } catch (Exception ignored) { }
            });
        } catch (Exception ignored) { }
    }
}
