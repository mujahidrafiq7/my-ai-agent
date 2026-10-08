package com.mujahid.myagent;

import android.content.Context;

import java.util.List;
import java.util.Locale;

/**
 * v41 "Khud-mukhtar Ayesha" — memory ke self-service commands.
 * Save / delete / edit / jagah — sab sync, tez. Reply Roman Urdu me.
 * Live path: sayText(reply). Old path: speakAndContinue(reply).
 */
public class MemCommands {

    private static final String[] DELETE_TRIGGERS = {
            "bhool jao", "bhul jao", "bhool jaoo",
            "delete kar do", "delete kardo", "delete karo",
            "hata do", "mita do", "remove kar do", "bhool ja",
    };

    private static final String[] EDIT_TRIGGERS = {
            "theek kar do", "sahi kar do", "change kar do",
            "update kar do", "badal do", "edit kar do",
    };

    private static final String[] STOPWORDS = {
            "mera", "meri", "mere", "hai", "hain", "ka", "ki", "ke", "ko",
            "ye", "yeh", "wo", "woh", "aur", "mein", "me", "ne", "se",
            "kya", "wala", "wali", "wale", "kaun", "kahan", "koi", "naya",
            "nayi", "naye", "purana", "purani", "the", "a", "an",
    };

    /**
     * Fast commands: save / delete / edit / jagah-save.
     * Handle ho gaya to reply, warna null.
     */
    public static String tryFast(Context ctx, String said) {
        if (said == null) return null;
        String t = said.trim();
        if (t.isEmpty()) return null;
        String low = t.toLowerCase(Locale.ROOT);

        // 1) Jagah save — "ye meri factory hai, yaad rakho" (pehle!)
        String place = extractPlaceSave(t, low);
        if (place != null) {
            String saved = LiveContext.savePlace(ctx, place);
            if (saved != null) {
                return "Ho gaya boss! '" + saved + "' jagah yaad kar li — "
                        + "ab puchoge 'main kahan hun' to bata dungi!";
            }
            return "Boss, jagah ka naam to samajh gaya, lekin GPS nahi mil raha — "
                    + "location on karke dobara kehna.";
        }

        // 2) Delete — "is ko delete kar do" / "bhool jao"
        String delKey = stripTrigger(t, low, DELETE_TRIGGERS);
        if (delKey != null) {
            int before = Memory.getAll(ctx).size();
            Memory.delete(ctx, delKey);
            int after = Memory.getAll(ctx).size();
            int gone = before - after;
            if (gone > 0) {
                return "Ho gaya boss! " + gone + " baat bhool gayi — "
                        + "ab yaad nahi rahegi.";
            }
            return "Boss, aisi koi baat meri yaad me thi hi nahi — "
                    + "kuch aur kehna tha?";
        }

        // 3) Edit — "theek kar do" (purani hatao + nayi save)
        String editText = stripTrigger(t, low, EDIT_TRIGGERS);
        if (editText != null && !editText.isEmpty()) {
            String keyword = bestKeyword(editText);
            if (keyword != null) Memory.delete(ctx, keyword);
            Memory.save(ctx, editText);
            return "Ho gaya boss! Purani wali hata ke nayi baat save kar li: "
                    + editText;
        }

        // 4) Save — "yaad rakho" / "save kar lo"
        String fact = Memory.extractMemoryCommand(t);
        if (fact != null) {
            // Jagah wali baat yahan nahi (upar handle ho chuki)
            Memory.save(ctx, fact);
            return "Yaad kar liya boss! '" + fact + "' — ab nahi bhoolungi.";
        }

        return null;
    }

    /** "ye meri factory hai, yaad rakho" → "factory". */
    private static String extractPlaceSave(String t, String low) {
        boolean wantsSave = low.contains("yaad rakho") || low.contains("yaad rakhna")
                || low.contains("save kar lo") || low.contains("save karlo");
        if (!wantsSave) return null;
        String[] starts = {"ye meri ", "ye mera ", "yeh meri ", "yeh mera "};
        for (String s : starts) {
            int i = low.indexOf(s);
            if (i >= 0) {
                String rest = t.substring(i + s.length()).trim();
                int hai = rest.toLowerCase(Locale.ROOT).indexOf(" hai");
                String name = hai > 0 ? rest.substring(0, hai).trim() : rest;
                // trailing trigger saaf karo
                for (String tr : new String[]{"yaad rakho", "yaad rakhna",
                        "save kar lo", "save karlo"}) {
                    int j = name.toLowerCase(Locale.ROOT).indexOf("," + tr);
                    if (j < 0) j = name.toLowerCase(Locale.ROOT).indexOf(" " + tr);
                    if (j > 0) name = name.substring(0, j).trim();
                }
                name = name.replaceAll("[,.]+$", "").trim();
                if (!name.isEmpty() && name.length() <= 40) return name;
            }
        }
        return null;
    }

    /** Trigger hata ke baqi text do; trigger na ho to null. */
    private static String stripTrigger(String t, String low, String[] triggers) {
        for (String tr : triggers) {
            int i = low.indexOf(tr);
            if (i >= 0) {
                String rest = (t.substring(0, i) + " " + t.substring(i + tr.length()))
                        .replaceAll("\\s+", " ").trim()
                        .replaceAll("^[,.\\s]+|[,.\\s]+$", "");
                // "is ko" / "ye" / "baat" jaisa filler hatado
                String rl = rest.toLowerCase(Locale.ROOT);
                for (String f : new String[]{"is ko ", "isko ", "ye baat ", "yeh baat ",
                        "ye ", "yeh ", "wo ", "woh ", "isay ", "usy ", "baat "}) {
                    if (rl.startsWith(f)) {
                        rest = rest.substring(f.length()).trim();
                        rl = rest.toLowerCase(Locale.ROOT);
                    }
                }
                return rest;
            }
        }
        return null;
    }

    /** Nayi baat me se sab se wazni lafz — purani dhoondne ke liye. */
    private static String bestKeyword(String text) {
        String best = null;
        for (String w : text.toLowerCase(Locale.ROOT).split("[^a-zA-Z\\u0600-\\u06FF0-9]+")) {
            if (w.length() < 4) continue;
            boolean stop = false;
            for (String s : STOPWORDS) {
                if (s.equals(w)) { stop = true; break; }
            }
            if (stop) continue;
            if (best == null || w.length() > best.length()) best = w;
        }
        return best;
    }

    /** Health sawal? "koi error hai?" / "app theek hai?" */
    public static boolean isHealthQuestion(String said) {
        if (said == null) return false;
        String low = said.toLowerCase(Locale.ROOT);
        return (low.contains("error") && (low.contains("koi") || low.contains("kya")
                || low.contains("hai") || low.contains("masla")))
                || low.contains("koi masla") || low.contains("koi problem")
                || low.contains("app theek") || low.contains("app ki sehat")
                || low.contains("sehat kaisi");
    }

    /** Jagah sawal? "main kahan hun?" */
    public static boolean isWhereQuestion(String said) {
        if (said == null) return false;
        String low = said.toLowerCase(Locale.ROOT);
        return low.contains("main kahan hun") || low.contains("mein kahan hun")
                || low.contains("meri location") || low.contains("meri jagah")
                || low.contains("kaunsi jagah") || low.contains("konsi jagah")
                || low.contains("kahan hun main");
    }
}
