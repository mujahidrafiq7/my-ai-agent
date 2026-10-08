package com.mujahid.myagent;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;

import java.util.Locale;

/**
 * WhatsApp actions — PHONE KE SYSTEM se (AccessibilityService),
 * WhatsApp ke API ko chhue baghair. Jaise tum manually karte ho:
 * chat kholo → type karo → send dabao / call ka button dabao.
 *
 * Commands (voice):
 *   "Fazil ko hello bhejo" / "Fazil ko hello send karo" → type + send tap
 *   "Fazil ki chat kholo"                              → seedha chat
 *   "Fazil ko call karo" / "Fazil ko video call karo"   → call button tap
 *   "call kaat do" / "call band karo"                   → end button tap
 */
public class WhatsAppAction {

    public static class Result {
        public final boolean handled;
        public final String reply;
        Result(boolean h, String r) { handled = h; reply = r; }
    }

    public static Result tryHandle(Context ctx, String normText) {
        String t = normText == null ? "" : normText.trim();
        if (t.isEmpty()) return new Result(false, "");

        // 1) Call kaat do (sab se pehle — isme naam nahi hota)
        if (t.contains("call") && (t.contains("kaat") || t.contains("cut")
                || t.contains("khatam")
                || (t.contains("band") && t.contains("kar")))) {
            return endCall();
        }

        // 2) Call lagao: "Fazil ko call karo" / "Fazil ko video call karo"
        if (t.contains("call") && t.contains(" ko ")) {
            String name = before(t, " ko ").trim();
            if (!name.isEmpty()) {
                boolean video = t.contains("video");
                return call(ctx, name, video);
            }
        }

        // 3) Message bhejo: "Fazil ko hello bhejo"
        if (t.contains(" ko ")) {
            int ki = t.indexOf(" ko ");
            String name = t.substring(0, ki).trim();
            String rest = t.substring(ki + 4).trim();
            String[] tails = {"bhejo", "bhej do", "send karo", "send kar do"};
            for (String tail : tails) {
                if (rest.endsWith(tail)) {
                    String msg = rest.substring(0,
                            rest.length() - tail.length()).trim();
                    if (msg.isEmpty() || msg.equals("message"))
                        return openChat(ctx, name); // "Fazil ko message karo" = chat kholo
                    return sendMessage(ctx, name, msg);
                }
            }
        }

        // 4) Chat kholo: "Fazil ki chat kholo"
        if (t.contains("chat") && (t.contains("kholo") || t.contains("kholen")
                || t.contains("open"))) {
            String name = t.contains(" ki chat")
                    ? before(t, " ki chat").trim()
                    : before(t, "chat").trim();
            // "whatsapp kholo" = APP kholo, kisi ki chat nahi → PhoneTools sambhalega
            if (name.isEmpty() || name.contains("whatsapp")
                    || name.equals("whats") || name.equals("app"))
                return new Result(false, "");
            return openChat(ctx, name);
        }

        return new Result(false, "");
    }

    // ---------- Actions ----------

    private static Result needA11y() {
        return new Result(true,
                "Pehle accessibility on karo boss — bolo 'accessibility on karo'.");
    }

    private static Result needContacts() {
        return new Result(true,
                "Contacts ki ijazat nahi hai boss — app kholo aur ijazat allow karo.");
    }

    private static boolean contactsOk(Context ctx) {
        try {
            return ctx.checkSelfPermission(Manifest.permission.READ_CONTACTS)
                    == PackageManager.PERMISSION_GRANTED;
        } catch (Exception e) {
            return false;
        }
    }

    public static Result openChat(Context ctx, String name) {
        if (!contactsOk(ctx)) return needContacts();
        String num = findNumber(ctx, name);
        if (num == null)
            return new Result(true, cap(name)
                    + " contacts me nahi mila boss — naam ya number check karo.");
        ForegroundOpen.open(ctx, waChat(num), cap(name) + " ki chat");
        return new Result(true, cap(name) + " ki chat khol di.");
    }

    public static Result sendMessage(Context ctx, String name, String msg) {
        if (!A11yService.isEnabled()) return needA11y();
        if (!contactsOk(ctx)) return needContacts();
        String num = findNumber(ctx, name);
        if (num == null)
            return new Result(true, cap(name)
                    + " contacts me nahi mila boss — naam ya number check karo.");
        ForegroundOpen.open(ctx, waChat(num), cap(name) + " ki chat");
        sleep(2800);
        boolean typed = A11yService.typeText(msg);
        sleep(1200);
        boolean sent = false;
        if (typed) {
            for (int i = 0; i < 3 && !sent; i++) {
                sent = A11yService.tapByDesc("send");
                if (!sent) sleep(900);
            }
        }
        if (sent) return new Result(true, "Bhej diya boss.");
        if (typed) return new Result(true,
                "Message likh diya hai — send ka button nahi mila, tum daba do.");
        return new Result(true,
                "Chat khul gayi — likhne ki jagah nahi mili, khud likh do.");
    }

    public static Result call(Context ctx, String name, boolean video) {
        if (!A11yService.isEnabled()) return needA11y();
        if (!contactsOk(ctx)) return needContacts();
        String num = findNumber(ctx, name);
        if (num == null)
            return new Result(true, cap(name)
                    + " contacts me nahi mila boss — naam ya number check karo.");
        ForegroundOpen.open(ctx, waChat(num), cap(name) + " ki chat");
        sleep(2800);
        boolean ok = A11yService.tapByDesc(video ? "video call" : "voice call");
        if (ok) return new Result(true,
                video ? "Video call laga di." : "Call laga di.");
        return new Result(true,
                "Chat khul gayi — call ka button nahi mila, khud laga lo.");
    }

    public static Result endCall() {
        if (!A11yService.isEnabled()) return needA11y();
        boolean ok = A11yService.tapByDesc("end call")
                || A11yService.tapByDesc("end");
        return new Result(true,
                ok ? "Call kaat di." : "Call ka button nahi mila.");
    }

    // ---------- Helpers ----------

    private static Intent waChat(String num) {
        return new Intent(Intent.ACTION_VIEW,
                Uri.parse("https://wa.me/" + num));
    }

    /** Contacts me naam → number (pehla best match). */
    private static String findNumber(Context ctx, String name) {
        String needle = name.toLowerCase(Locale.ROOT).trim();
        if (needle.isEmpty()) return null;
        Cursor c = null;
        try {
            c = ctx.getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER},
                    null, null, null);
            if (c == null) return null;
            String fallback = null;
            while (c.moveToNext()) {
                String dn = c.getString(0);
                String num = c.getString(1);
                if (dn == null || num == null) continue;
                String low = dn.toLowerCase(Locale.ROOT).trim();
                if (low.equals(needle) || low.startsWith(needle + " "))
                    return normNum(num);
                if (fallback == null && low.contains(needle))
                    fallback = normNum(num);
            }
            return fallback;
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) {
                try { c.close(); } catch (Exception ignored) { }
            }
        }
    }

    /** "0300..." → "92300..." (wa.me ko poora number chahiye). */
    private static String normNum(String num) {
        String d = num.replaceAll("[^0-9]", "");
        if (d.startsWith("0")) d = "92" + d.substring(1);
        return d;
    }

    private static String before(String t, String key) {
        int i = t.indexOf(key);
        return i <= 0 ? "" : t.substring(0, i);
    }

    private static String cap(String s) {
        if (s == null || s.isEmpty()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) { }
    }
}
