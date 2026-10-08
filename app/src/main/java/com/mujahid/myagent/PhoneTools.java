package com.mujahid.myagent;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;

import java.util.List;
import java.util.Locale;

/**
 * STEP 1 — Phone tools. Bina quota kharch kiye, phone me hi:
 *   "WhatsApp kholo" / "واٹس ایپ کھولیں" → app khul jayegi (koi bhi installed app)
 *   "Chrome pe X search karo"            → Google search
 *   "YouTube pe X search karo"           → YouTube search
 * Pattern na mile → handled=false → normal chat (quota wala rasta).
 *
 * NOTE: Whisper Urdu bolne pe URDU SCRIPT likhta hai — is liye pehle
 * Urdu lafzon ko Roman me badlo, phir pattern matching (nahi to "khol"
 * nazar hi nahi ata aur model jhoot bolta hai "khol diya").
 */
public class PhoneTools {

    public static class Result {
        public final boolean handled;
        public final String reply; // Ayesha kya bole
        public final Intent intent; // jo intent chalaya (notification fallback ke liye)
        Result(boolean h, String r, Intent i) { handled = h; reply = r; intent = i; }
    }

    /** Urdu script → Roman (sirf command wale lafz + mashhoor apps). */
    private static final String[][] URDU_MAP = {
            {"کھولیں", "kholo"}, {"کھولو", "kholo"}, {"کھول دو", "kholo"}, {"کھول", "kholo"},
            {"سرچ کرو", "search karo"}, {"تلاش کرو", "search karo"},
            {"سرچ", "search"}, {"تلاش", "search"},
            {"اسکرین", "screen"}, {"سکرین", "screen"},
            {"شیئر", "share"}, {"آن", "on"}, {"آف", "off"},
            {"دیکھو", "dekho"}, {"دکھاؤ", "dikhao"}, {"نظر", "nazar"},
            {"واٹس ایپ", "whatsapp"}, {"واٹسایپ", "whatsapp"},
            {"واٹس اپ", "whatsapp"}, {"واٹساپ", "whatsapp"},
            {"اوپن", "open"}, {"کرو", "karo"},
            {"یوٹیوب", "youtube"}, {"یو ٹیوب", "youtube"},
            {"کروم", "chrome"}, {"گوگل", "google"},
            {"فیس بک", "facebook"}, {"انسٹاگرام", "instagram"}, {"انسٹا", "instagram"},
            {"ٹک ٹاک", "tiktok"}, {"ٹکٹاک", "tiktok"},
            {"جی میل", "gmail"}, {"میسنجر", "messenger"},
            {"ٹیلی گرام", "telegram"}, {"سنیپ چیٹ", "snapchat"},
            {"کیمرہ", "camera"}, {"گیلری", "gallery"}, {"کیلنڈر", "calendar"},
            {"گھڑی", "clock"}, {"رابطے", "contacts"}, {"فون", "phone"},
            {"میسجز", "messages"}, {"پلے سٹور", "playstore"},
            {"سیٹنگز", "settings"}, {"فائلز", "files"}, {"نقشہ", "maps"},
    };

    private static String normalize(String text) {
        String t = text;
        for (String[] p : URDU_MAP) t = t.replace(p[0], p[1]);
        return t.toLowerCase(Locale.ROOT).trim();
    }

    /** Dosri jagah (VoiceService) bhi wahi normalization use kare. */
    public static String norm(String text) {
        String t = normalize(text == null ? "" : text);
        // Mixed Urdu-English: English verbs → Roman patterns
        // ("whatsapp open karo" → "whatsapp kholo" — ab samjhegi)
        t = t.replace(" open karo", " kholo")
             .replace(" open kar do", " kholo")
             .replace(" open karein", " kholo")
             .replace(" open it", " kholo")
             .replace(" close karo", " band karo")
             .replace(" close kar do", " band karo");
        // Ghalat sune hue app naam → seedha naam
        t = t.replace("watsapp", "whatsapp")
             .replace("vatsap", "whatsapp")
             .replace("watsap", "whatsapp")
             .replace("whatsap", "whatsapp")
             // Whisper aksar "WhatsApp" ko "oats app" sunta hai
             .replace("oats app", "whatsapp")
             .replace("oots app", "whatsapp")
             .replace("oatsap", "whatsapp")
             .replace("ootsap", "whatsapp")
             .replace("oats ap", "whatsapp")
             .replace("oots ap", "whatsapp")
             .replace("utube", "youtube")
             .replace("yutub", "youtube");
        return t;
    }

    public static Result tryHandle(Context ctx, String text) {
        String t = norm(text); // norm = Urdu script + English verbs + fuzzy names (sab)

        // 1) YouTube search
        if (t.contains("youtube") && t.contains("search")) {
            String q = cleanQuery(t, new String[]{"youtube", "pe", "par", "me", "mein",
                    "search", "karo", "kar", "do", "please", "ko", "ka", "ki"});
            if (q.isEmpty()) return new Result(false, "", null);
            Intent i = youtubeSearchIntent(q);
            fire(ctx, i);
            return new Result(true, "YouTube pe dhoond liya: " + q, i);
        }

        // 2) Web search (Chrome/Google)
        if ((t.contains("chrome") || t.contains("google")) && t.contains("search")) {
            String q = cleanQuery(t, new String[]{"chrome", "google", "pe", "par", "me", "mein",
                    "search", "karo", "kar", "do", "please", "ko", "ka", "ki"});
            if (q.isEmpty()) return new Result(false, "", null);
            Intent i = webSearchIntent(q);
            fire(ctx, i);
            return new Result(true, "Search kar diya: " + q, i);
        }

        // 2b) Camera — Ayesha ka apna camera (system camera app nahi)
        if (t.contains("camera")) {
            boolean off = t.contains("band") || t.contains("off") || t.contains("close");
            boolean on = t.contains("kholo") || t.contains("kholen") || t.contains("khol")
                    || t.contains(" on ") || t.endsWith(" on") || t.contains("open")
                    || t.contains("start") || t.contains("shuru");
            if (off && !on) {
                CameraActivity.closeIfOpen();
                return new Result(true, "Camera band kar diya boss.", null);
            }
            if (on) {
                Intent ci = new Intent(ctx, CameraActivity.class);
                ci.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                fire(ctx, ci);
                return new Result(true, "Camera on kar diya boss.", ci);
            }
            // sirf "camera" bola → neeche generic app-open sambhalega
        }

        // 3) App kholo (order): "... kholo / khol do / kholen / open ..."
        String appName = extractAppName(t);
        if (appName != null && !appName.isEmpty()) {
            String pkg = findApp(ctx, appName);
            if (pkg != null) {
                Intent i = launchIntent(ctx, pkg);
                if (i != null) {
                    fire(ctx, i);
                    return new Result(true, "Khol diya boss.", i);
                }
            }
            return new Result(true, "Ye app nahi mili: " + appName, null);
        }

        // 4) Sawal ho ("kya tum whatsapp khol sakti ho?") → seedha khol do
        if (t.contains("khol")) {
            String q = cleanQuery(t, new String[]{"kya", "tum", "aap", "main", "me", "mein",
                    "sakti", "sakta", "sakte", "saktey", "ho", "hai", "hain", "gi", "ge", "ga",
                    "ne", "ko", "ka", "ki", "ke", "zara", "please", "to",
                    "khol", "kholo", "kholen", "kholein", "kholna", "kholti", "kholta",
                    "khologe", "khologi", "kholenge", "khol do", "khol dein"});
            if (!q.isEmpty()) {
                String pkg = findApp(ctx, q);
                if (pkg != null) {
                    Intent i = launchIntent(ctx, pkg);
                    if (i != null) {
                        fire(ctx, i);
                        return new Result(true, "Haan boss, khol diya.", i);
                    }
                }
                // Na mile to model khud narmi se jawab de (usko capability ka pata hai)
            }
        }

        return new Result(false, "", null);
    }

    /** "... kholo / khol do / kholen / open ..." me se app ka naam nikalo. */
    private static String extractAppName(String t) {
        if (t.startsWith("open ")) return t.substring(5).trim();
        String[] tails = {"khol do", "khol dein", "kholein", "kholen", "kholo", " khol"};
        for (String tail : tails) {
            if (t.endsWith(tail)) {
                return t.substring(0, t.length() - tail.length()).trim();
            }
        }
        // "X open" (bare, e.g. "whatsapp open")
        if (t.endsWith(" open")) {
            String app = t.substring(0, t.length() - 5).trim();
            if (!app.isEmpty() && !app.equals("home")) return app;
        }
        // "X pe jao / X me jao" → app kholo (jaise "whatsapp pe jao")
        String[] goTails = {" pe jao", " pe jau", " pe aao", " pe chalo",
                " me jao", " me jau", " me aao", " me chalo",
                " par jao", " par jau", " par aao", " par chalo"};
        for (String tail : goTails) {
            int i = t.indexOf(tail);
            if (i > 0) {
                String app = t.substring(0, i).trim();
                if (app.equals("home")) return null; // "home pe jao" UiControl ka hai
                return app;
            }
        }
        return null;
    }

    /** Stop-words hata ke asal query bachao. */
    private static String cleanQuery(String t, String[] stop) {
        StringBuilder sb = new StringBuilder();
        for (String w : t.split(" ")) {
            boolean drop = w.isEmpty();
            for (String s : stop) {
                if (w.equals(s)) { drop = true; break; }
            }
            if (!drop) {
                if (sb.length() > 0) sb.append(' ');
                sb.append(w);
            }
        }
        return sb.toString().trim();
    }

    /** Installed apps me naam se package dhoondo (exact pehle, phir contains). */
    private static String findApp(Context ctx, String name) {
        String q = name.toLowerCase(Locale.ROOT).replace(" ", "");
        if (q.isEmpty()) return null;
        PackageManager pm = ctx.getPackageManager();
        List<ApplicationInfo> apps;
        try {
            apps = pm.getInstalledApplications(0);
        } catch (Exception e) {
            return null;
        }
        String fuzzy = null;
        for (ApplicationInfo app : apps) {
            String label;
            try {
                label = pm.getApplicationLabel(app).toString()
                        .toLowerCase(Locale.ROOT).replace(" ", "");
            } catch (Exception e) {
                continue;
            }
            if (label.equals(q)) return app.packageName;
            if (fuzzy == null && label.length() >= 3
                    && (label.contains(q) || q.contains(label))) {
                fuzzy = app.packageName;
            }
        }
        if (fuzzy != null) return fuzzy;
        if (q.length() >= 3) {
            for (ApplicationInfo app : apps) {
                if (app.packageName.toLowerCase(Locale.ROOT).contains(q)) {
                    return app.packageName;
                }
            }
        }
        return null;
    }

    private static Intent launchIntent(Context ctx, String pkg) {
        try {
            Intent i = ctx.getPackageManager().getLaunchIntentForPackage(pkg);
            if (i != null) i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            return i;
        } catch (Exception e) {
            return null;
        }
    }

    private static Intent webSearchIntent(String q) {
        Intent i = new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://www.google.com/search?q=" + Uri.encode(q)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        return i;
    }

    private static Intent youtubeSearchIntent(String q) {
        Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(
                "https://www.youtube.com/results?search_query=" + Uri.encode(q)));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        // Pehle YouTube app, na ho to browser (fire me fallback)
        return i;
    }

    // v49: SINGLE launch path — seedha startActivity background me BLOCK hota hai
    // (Android 10+), is liye sirf ForegroundOpen (full-screen intent wala rasta).
    private static void fire(Context ctx, Intent i) {
        // YouTube app prefer karo
        if (i.getData() != null && i.getData().toString().contains("youtube.com")) {
            Intent yt = new Intent(i);
            yt.setPackage("com.google.android.youtube");
            ForegroundOpen.open(ctx, yt, "YouTube");
            return;
        }
        ForegroundOpen.open(ctx, i, "Ayesha");
    }
}
