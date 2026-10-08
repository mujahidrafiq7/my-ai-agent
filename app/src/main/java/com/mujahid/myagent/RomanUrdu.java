package com.mujahid.myagent;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Urdu script → Roman Urdu (sirf SCREEN PAR DIKHANE ke liye — "Suna:" toast).
 * Asal text (model/commands ke liye) waisa hi rehta hai.
 * Aam lafz dictionary se, baqi harf-ba-harf.
 */
public class RomanUrdu {

    private static final Map<String, String> WORDS = new HashMap<>();
    private static final Pattern URDU_WORD = Pattern.compile("[\\u0600-\\u06FF]+");

    static {
        String[][] w = {
            // Salam / dua
            {"اسلام", "assalam"}, {"علیکم", "alaikum"}, {"وعلیکم", "walaikum"},
            {"سلام", "salam"}, {"اللہ", "Allah"}, {"حافظ", "hafiz"},
            {"ماشاءاللہ", "mashallah"}, {"انشاءاللہ", "inshallah"},
            {"شکریہ", "shukriya"}, {"مہربانی", "meherbani"},
            // Pronouns
            {"میں", "mein"}, {"میرا", "mera"}, {"میری", "meri"}, {"میرے", "mere"},
            {"مجھے", "mujhe"}, {"مجھ", "mujh"}, {"ہم", "hum"}, {"ہمارا", "hamara"},
            {"آپ", "ap"}, {"آپکا", "apka"}, {"آپکی", "apki"}, {"آپکے", "apke"},
            {"تم", "tum"}, {"تمہارا", "tumhara"}, {"تمہیں", "tumhen"},
            {"تو", "to"}, {"تجھے", "tujhe"},
            {"یہ", "ye"}, {"یہی", "yehi"}, {"وہ", "wo"}, {"وہی", "wohi"},
            {"اس", "is"}, {"اسکا", "iska"}, {"اسکی", "iski"}, {"اسے", "ise"},
            {"ان", "in"}, {"انکا", "inka"},
            // Helping verbs
            {"ہے", "hai"}, {"ہیں", "hain"}, {"تھا", "tha"}, {"تھی", "thi"},
            {"تھے", "the"}, {"ہو", "ho"}, {"ہوں", "hun"}, {"ہوگا", "hoga"},
            {"ہوگی", "hogi"}, {"ہونگے", "honge"},
            // Postpositions
            {"کا", "ka"}, {"کی", "ki"}, {"کے", "ke"}, {"کو", "ko"},
            {"سے", "se"}, {"نے", "ne"}, {"پر", "par"}, {"پہ", "pe"},
            {"میں", "mein"}, {"تک", "tak"}, {"لیے", "liye"}, {"لئے", "liye"},
            {"والا", "wala"}, {"والی", "wali"}, {"والے", "wale"},
            // Question words
            {"کیا", "kya"}, {"کیسے", "kese"}, {"کیسا", "kesa"}, {"کیسی", "kesi"},
            {"کیوں", "kyun"}, {"کب", "kab"}, {"کہاں", "kahan"}, {"کون", "kon"},
            {"کتنا", "kitna"}, {"کتنے", "kitne"}, {"کونسا", "konsa"},
            // Common
            {"نہیں", "nahi"}, {"نہ", "na"}, {"بہت", "bohat"}, {"بھی", "bhi"},
            {"ہی", "hi"}, {"اور", "aur"}, {"لیکن", "lekin"}, {"مگر", "magar"},
            {"پھر", "phir"}, {"اب", "ab"}, {"ابھی", "abhi"}, {"آج", "aaj"},
            {"کل", "kal"}, {"یہاں", "yahan"}, {"وہاں", "wahan"},
            {"اچھا", "acha"}, {"اچھی", "achi"}, {"اچھے", "ache"},
            {"ٹھیک", "theek"}, {"خراب", "kharab"}, {"نیا", "naya"},
            {"بڑا", "bara"}, {"چھوٹا", "chota"},
            {"وقت", "waqt"}, {"دن", "din"}, {"رات", "raat"},
            {"صبح", "subah"}, {"شام", "shaam"}, {"گھر", "ghar"},
            {"کام", "kaam"}, {"بات", "baat"}, {"چیز", "cheez"}, {"جگہ", "jagah"},
            // Commands (uski zubaan)
            {"کھولو", "kholo"}, {"کھول", "khol"}, {"کھولیں", "kholen"},
            {"بند", "band"}, {"کرو", "karo"}, {"کریں", "karen"}, {"کر", "kar"},
            {"کرنا", "karna"}, {"دیکھو", "dekho"}, {"دکھاؤ", "dikhao"},
            {"دکھا", "dikha"}, {"سنو", "suno"}, {"بولو", "bolo"}, {"بتاؤ", "batao"},
            {"بتا", "bata"}, {"لگاؤ", "lagao"}, {"چلاؤ", "chalao"},
            {"بھیجو", "bhejo"}, {"بھیج", "bhej"}, {"اوپن", "open"},
            {"جاؤ", "jao"}, {"آؤ", "aao"}, {"چلو", "chalo"}, {"واپس", "wapas"},
            // App / tech words
            {"واٹس", "whats"}, {"ایپ", "app"}, {"یوٹیوب", "youtube"},
            {"سکرین", "screen"}, {"اسکرین", "screen"}, {"شیئر", "share"},
            {"موبائل", "mobile"}, {"فون", "phone"}, {"میسج", "message"},
            {"کیمرہ", "camera"}, {"فوٹو", "photo"}, {"ویڈیو", "video"},
            {"کال", "call"}, {"چیٹ", "chat"}, {"سکرول", "scroll"},
            {"ٹائپ", "type"}, {"سرچ", "search"}, {"سیٹنگ", "setting"},
        };
        for (String[] p : w) WORDS.put(p[0], p[1]);
    }

    /** Urdu script wala hissa Roman me badlo; baqi waisa hi. */
    public static String toRoman(String s) {
        if (s == null || s.isEmpty()) return s;
        Matcher m = URDU_WORD.matcher(s);
        if (!m.find()) return s;
        m.reset();
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String word = m.group();
            String r = WORDS.get(word);
            if (r == null) r = chars(word);
            m.appendReplacement(sb, Matcher.quoteReplacement(r));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** Harf-ba-harf (unknown lafzon ke liye). */
    private static String chars(String w) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < w.length(); i++) {
            char c = w.charAt(i);
            String r = charMap(c);
            if (r != null) b.append(r);
        }
        return b.length() == 0 ? w : b.toString();
    }

    private static String charMap(char c) {
        switch (c) {
            case 'ا': case 'ع': case 'ء': return "a";
            case 'آ': return "aa";
            case 'ب': return "b";
            case 'پ': return "p";
            case 'ت': case 'ٹ': case 'ط': return "t";
            case 'ث': case 'س': case 'ص': return "s";
            case 'ج': return "j";
            case 'چ': return "ch";
            case 'ح': case 'ہ': case 'ھ': return "h";
            case 'خ': return "kh";
            case 'د': case 'ڈ': return "d";
            case 'ذ': case 'ز': case 'ض': case 'ظ': return "z";
            case 'ر': case 'ڑ': return "r";
            case 'ژ': return "zh";
            case 'ش': return "sh";
            case 'غ': return "gh";
            case 'ف': return "f";
            case 'ق': return "q";
            case 'ک': return "k";
            case 'گ': return "g";
            case 'ل': return "l";
            case 'م': return "m";
            case 'ن': case 'ں': return "n";
            case 'و': return "o";
            case 'ی': case 'ئ': return "i";
            case 'ے': return "e";
            default:
                // Airaab (zabar/zer/pesh/shadd) aur ghair-haroof → chhoro
                if (c >= 0x064B && c <= 0x065F) return "";
                if (c == 0x0670) return "";
                return null;
        }
    }
}
