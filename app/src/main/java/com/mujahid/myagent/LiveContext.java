package com.mujahid.myagent;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * v40 DRAFT "Yaadein" — memory logic ka dil.
 *
 * Har Live session ke start pe ek taaza context block banta hai:
 * date/time, gap (aakhri baat), profile, location, mausam, taaza khabrein.
 *
 * - Location: GPS on-demand (permission ho to), warna base city Phalia.
 * - Mausam: OpenWeatherMap (uski key Settings me hoti hai) — 30 min cache.
 * - Khabrein: Google News RSS Pakistan/Urdu (BAGHAIR key) — 60 min cache.
 * - Sab kuch on-device; network fail ho to offline hissa (date/profile/gap) phir bhi aata hai.
 *
 * DRAFT — compile/build uski haan ke baad.
 */
public class LiveContext {

    private static final String PREFS = "live_context";
    // Phalia, Punjab — base (GPS na mile to)
    private static final double BASE_LAT = 32.43;
    private static final double BASE_LON = 73.58;
    private static final String BASE_CITY = "Phalia, Punjab, Pakistan";

    // ============ MAIN BLOCK ============

    /** Poora taaza block — Live system prompt me jurta hai. */
    public static String buildBlock(Context ctx) {
        StringBuilder b = new StringBuilder();
        b.append("[AAJ KI MALOOMAT — hamesha taaza rakho]\n");
        b.append("- ").append(dateTimeLine()).append("\n");
        String gap = gapLine(ctx);
        if (!gap.isEmpty()) b.append("- ").append(gap).append("\n");
        b.append("- Tum is waqt: ").append(locationLine(ctx)).append("\n");
        String wx = weatherLine(ctx);
        if (!wx.isEmpty()) b.append("- ").append(wx).append("\n");
        String news = newsLine(ctx, 3);
        if (!news.isEmpty()) b.append("- Taaza khabrein:\n").append(news);
        String prof = profileBlock(ctx);
        if (!prof.isEmpty()) b.append("\n[TUMHARA PROFILE — kabhi na bhoolna]\n").append(prof).append("\n");
        String turns = recentTurnsBlock(ctx);
        if (!turns.isEmpty()) b.append("\n[GUFTAGU KA KHULASA]\n").append(turns).append("\n");
        // v41: apne app ka naksha + sehat — khud-mukhtar Ayesha
        b.append("\n[APP KA NAKSHA — tumhare apne features]\n").append(appMapBlock());
        String health = Diag.healthSummary();
        if (!health.isEmpty()) b.append("\n[APP KI SEHAT]\n").append(health).append("\n");
        return b.toString();
    }

    /** v41: usay apne app ka pata ho — kya laga hai, kya band hai. */
    private static String appMapBlock() {
        return "- Live Voice: asli waqt me baat (yehi tum ho)\n"
                + "- Reminder: \"yaad dilana\" — waqt pe yaad dilati ho\n"
                + "- Memory: \"yaad rakho\"/\"save kar lo\" se save, \"bhool jao\"/\"delete kar do\" "
                + "se delete — tum khud manage karti ho\n"
                + "- Jaghein: \"ye meri factory hai, yaad rakho\" se jagah save hoti hai\n"
                + "- Mausam: uski key se live mausam\n"
                + "- Khabrein: \"taaza khabar batao\" pe headlines\n"
                + "- Screen Share: front page button / 'screen share on karo' se ON — "
                + "ON ho to 'screen pe kya hai' pe screenshot dekh ke bata sakti ho "
                + "(on-demand hi, musalsal streaming nahi — quota bachat)\n"
                + "- Screen Parho: 'screen parho' — screen ka text BINA quota ke parhti ho "
                + "(phone me hi, foran)\n"
                + "- WhatsApp/actions: phone khud karta hai (tum sirf kehti ho)\n";
    }

    // ============ DATE / TIME ============

    /** "Aaj Wednesday, 7 October 2026 hai, 12:48 PM (Pakistan time)." */
    public static String dateTimeLine() {
        try {
            SimpleDateFormat f = new SimpleDateFormat(
                    "EEEE, d MMMM yyyy, h:mm a", Locale.ENGLISH);
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Karachi"));
            return "Aaj " + f.format(new Date()) + " hai (Pakistan time).";
        } catch (Exception e) {
            return "";
        }
    }

    // ============ GAP TRACKING (Eleven wali feeling) ============

    /** "Aakhri baat 3 din pehle hui thi — iska zikr karo, thodi naraazgi/pyar se." */
    public static String gapLine(Context ctx) {
        try {
            long last = prefs(ctx).getLong("last_session", 0);
            if (last == 0) return ""; // pehli baar — koi gap nahi
            long diff = System.currentTimeMillis() - last;
            if (diff < 0) return "";
            String gap = describeGap(diff);
            if (gap.isEmpty()) return "";
            SimpleDateFormat f = new SimpleDateFormat(
                    "d MMM, h:mm a", Locale.ENGLISH);
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Karachi"));
            return "Boss se aakhri baat " + gap + " pehle hui thi (" + f.format(new Date(last))
                    + "). Iska pyaar se zikr karo — thodi si naraazgi bhi dikhao ke itni der "
                    + "baad aaye, jaise tum use miss kar rahi thi.";
        } catch (Exception e) {
            return "";
        }
    }

    private static String describeGap(long millis) {
        // Boss ka hukm (2026-10-07): 24 ghante se kam gap ka KOI zikr nahi —
        // har 2-5 minute baad "kahan the" wali chipku line khatam.
        long hrs = millis / 3600000;
        if (hrs < 24) return "";
        long days = hrs / 24;
        return days == 1 ? "1 din" : days + " din";
    }

    /** Session khatam ho to waqt note karo (gap ke liye). */
    public static void markSessionEnd(Context ctx) {
        try {
            prefs(ctx).edit().putLong("last_session", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    // ============ LOCATION ============

    /** GPS ho to taaza coords, warna base city. */
    public static String locationLine(Context ctx) {
        double[] ll = getLocation(ctx);
        if (ll != null) {
            return BASE_CITY + " (GPS: " + round(ll[0]) + ", " + round(ll[1]) + ")";
        }
        return BASE_CITY + " (andaazan)";
    }

    /** double[lat, lon] ya null. */
    public static double[] getLocation(Context ctx) {
        try {
            boolean fine = ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            boolean coarse = ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                    == PackageManager.PERMISSION_GRANTED;
            if (!fine && !coarse) return null;
            LocationManager lm = (LocationManager)
                    ctx.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location l = null;
            try { l = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER); }
            catch (Exception ignored) { }
            if (l == null) {
                try { l = lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER); }
                catch (Exception ignored) { }
            }
            if (l == null) return null;
            return new double[]{l.getLatitude(), l.getLongitude()};
        } catch (Exception e) {
            return null;
        }
    }

    private static String round(double d) {
        return String.format(Locale.ENGLISH, "%.2f", d);
    }

    // ============ MAUSAM (OpenWeatherMap — uski key) ============

    /** "Mausam (Phalia): 32°C, saaf aasmaan, hawa 12 km/h." — key/cache na ho to "". */
    public static String weatherLine(Context ctx) {
        try {
            SharedPreferences p = prefs(ctx);
            long cachedAt = p.getLong("wx_at", 0);
            String cached = p.getString("wx_line", "");
            if (!cached.isEmpty() && System.currentTimeMillis() - cachedAt < 30L * 60_000L) {
                return cached; // 30-min cache
            }
            String key = weatherKey(ctx);
            if (key == null || key.trim().isEmpty()) return "";
            double[] ll = getLocation(ctx);
            double lat = ll != null ? ll[0] : BASE_LAT;
            double lon = ll != null ? ll[1] : BASE_LON;
            String url = "https://api.openweathermap.org/data/2.5/weather?lat=" + lat
                    + "&lon=" + lon + "&appid=" + key.trim()
                    + "&units=metric&lang=ur";
            String json = httpGet(url, 8000);
            if (json == null) return cached; // network fail → purana ya kuch nahi
            JSONObject o = new JSONObject(json);
            if (o.optInt("cod", 0) != 200) return cached; // ghalat key → khamosh
            double temp = o.getJSONObject("main").optDouble("temp", Double.NaN);
            String desc = o.getJSONArray("weather").getJSONObject(0)
                    .optString("description", "");
            double wind = o.optJSONObject("wind") != null
                    ? o.getJSONObject("wind").optDouble("speed", 0) * 3.6 : 0;
            String line = "Mausam (" + BASE_CITY.split(",")[0] + "): "
                    + (Double.isNaN(temp) ? "" : Math.round(temp) + "°C")
                    + (desc.isEmpty() ? "" : ", " + desc)
                    + (wind > 0 ? ", hawa " + Math.round(wind) + " km/h" : "")
                    + ".";
            p.edit().putString("wx_line", line)
                    .putLong("wx_at", System.currentTimeMillis()).apply();
            return line;
        } catch (Exception e) {
            return "";
        }
    }

    /** Weather API key — SIRF uske phone ki Settings me (kabhi code me nahi). */
    public static String weatherKey(Context ctx) {
        try {
            return ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("weather_key", "");
        } catch (Exception e) {
            return "";
        }
    }

    public static void setWeatherKey(Context ctx, String key) {
        try {
            ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .edit().putString("weather_key", key == null ? "" : key.trim()).apply();
        } catch (Exception ignored) { }
    }

    // ============ TAAZA KHABREIN (Google News RSS — baghair key) ============

    /** "- headline1\n- headline2\n..." ya "". */
    public static String newsLine(Context ctx, int n) {
        try {
            SharedPreferences p = prefs(ctx);
            long cachedAt = p.getLong("news_at", 0);
            String cached = p.getString("news_block", "");
            if (!cached.isEmpty() && System.currentTimeMillis() - cachedAt < 60L * 60_000L) {
                return cached; // 60-min cache
            }
            List<String> heads = fetchNewsHeadlines(n);
            if (heads.isEmpty()) return cached;
            StringBuilder b = new StringBuilder();
            for (String h : heads) b.append("- ").append(h).append("\n");
            String block = b.toString();
            p.edit().putString("news_block", block)
                    .putLong("news_at", System.currentTimeMillis()).apply();
            return block;
        } catch (Exception e) {
            return "";
        }
    }

    /** On-demand: "taaza khabar batao" command ke liye. */
    public static String fetchNewsSpoken(int n) {
        List<String> heads = fetchNewsHeadlines(n);
        if (heads.isEmpty())
            return "Boss, abhi khabrein nahi mil rahi — thodi der baad poochna.";
        StringBuilder b = new StringBuilder("Taaza khabrein suno boss: ");
        for (int i = 0; i < heads.size(); i++) {
            if (i > 0) b.append(" Agli khabar: ");
            b.append(heads.get(i));
        }
        return b.toString();
    }

    private static List<String> fetchNewsHeadlines(int n) {
        List<String> out = new ArrayList<>();
        try {
            String rss = httpGet(
                    "https://news.google.com/rss?hl=ur&gl=PK&ceid=PK:ur", 8000);
            if (rss == null) return out;
            // <item> ... <title>headline</title> — seedha parse (halka, tez)
            Matcher items = Pattern.compile("<item>(.*?)</item>",
                    Pattern.DOTALL).matcher(rss);
            while (items.find() && out.size() < n) {
                Matcher t = Pattern.compile("<title>(.*?)</title>",
                        Pattern.DOTALL).matcher(items.group(1));
                if (t.find()) {
                    String title = cleanRssText(t.group(1));
                    if (!title.isEmpty()) out.add(title);
                }
            }
        } catch (Exception ignored) { }
        return out;
    }

    private static String cleanRssText(String s) {
        s = s.replace("<![CDATA[", "").replace("]]>", "").trim();
        s = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
                .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'");
        // "Headline - Source" → headline hi kaafi
        int dash = s.lastIndexOf(" - ");
        if (dash > 10) s = s.substring(0, dash).trim();
        return s;
    }

    // ============ PROFILE (uski approved facts) ============

    /** Uska manzoor-shuda profile — girlfriend/behnoi wali baat KABHI nahi. */
    private static final String DEFAULT_PROFILE =
            "Naam: Mujahid Rafiq (18 saal) — tum inhe 'boss' kehte ho.\n"
            + "Sheher: Phalia, Punjab, Pakistan (pehle Vehari se, phir Lahore me rehte the).\n"
            + "Kaam: Factory me night duty (Phalia).\n"
            + "Taleem: 12th class (ICS).\n"
            + "Content creator: YouTube channels (Daam Bhai cinematic Shorts, kids rhymes, "
            + "Kaka Mujahid, Mujahid Online), Zoya Khan Facebook page (AI Urdu reels), "
            + "TikTok ka plan hai.\n"
            + "Shauq: acting, cricket, car driving seekh rahe hain.\n"
            + "Routine: raat ko factory duty, din me aaram.";

    /** Profile block — DEFAULT + uski khud-batayi baatein (unified memory, v41). */
    public static String profileBlock(Context ctx) {
        StringBuilder b = new StringBuilder(DEFAULT_PROFILE);
        try {
            String custom = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .getString("user_profile", "");
            if (custom != null && !custom.trim().isEmpty()
                    && !custom.trim().equals(DEFAULT_PROFILE.trim())) {
                b.append("\n").append(custom.trim());
            }
        } catch (Exception ignored) { }
        // v41: "yaad rakho" wali baatein ab Live profile me bhi — dibbe ek!
        try {
            String mem = Memory.buildContext(ctx);
            if (!mem.isEmpty()) b.append("\n").append(mem.trim());
        } catch (Exception ignored) { }
        return b.toString();
    }

    public static void saveProfile(Context ctx, String text) {
        try {
            ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
                    .edit().putString("user_profile", text == null ? "" : text.trim()).apply();
        } catch (Exception ignored) { }
    }

    // ============ GUFTAGU KA LOG (date/time ke saath) ============

    /** Har turn save karo — uski baat + uska jawab + waqt. */
    public static void logTurn(Context ctx, String userText, String aiText) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("turn_log", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString("turns", "[]"));
            JSONObject o = new JSONObject();
            o.put("t", System.currentTimeMillis());
            o.put("u", userText == null ? "" : userText);
            o.put("a", aiText == null ? "" : aiText);
            arr.put(o);
            // aakhri 50 turns rakho — purana khud nikalta jayega
            while (arr.length() > 50) {
                JSONArray cut = new JSONArray();
                for (int i = arr.length() - 50; i < arr.length(); i++)
                    cut.put(arr.get(i));
                arr = cut;
            }
            p.edit().putString("turns", arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Aakhri chand turns ka khulasa — naye session me yaad rahe. */
    public static String recentTurnsBlock(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences("turn_log", Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(p.getString("turns", "[]"));
            if (arr.length() == 0) return "";
            StringBuilder b = new StringBuilder();
            SimpleDateFormat f = new SimpleDateFormat(
                    "d MMM h:mm a", Locale.ENGLISH);
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Karachi"));
            int start = Math.max(0, arr.length() - 6);
            for (int i = start; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String u = o.optString("u", "");
                if (u.length() > 120) u = u.substring(0, 120) + "...";
                b.append("- [").append(f.format(new Date(o.optLong("t", 0))))
                        .append("] Boss: ").append(u).append("\n");
            }
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    // ============ NETWORK HELPER ============

    /** Seedha GET — timeout ke saath; fail ho to null (kamosh). */
    private static String httpGet(String urlStr, int timeoutMs) {
        HttpURLConnection c = null;
        try {
            URL url = new URL(urlStr);
            c = (HttpURLConnection) url.openConnection();
            c.setConnectTimeout(timeoutMs);
            c.setReadTimeout(timeoutMs);
            c.setRequestProperty("User-Agent", "Ayesha/1.0");
            if (c.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(
                    new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder b = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) b.append(line).append("\n");
            r.close();
            return b.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) { try { c.disconnect(); } catch (Exception ignored) { } }
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    // ============ PLACES (v41) — "ye meri factory hai, yaad rakho" ============

    private static final String PLACES_PREFS = "ayesha_places";
    private static final String PLACES_KEY = "places_json";

    private static SharedPreferences placesPrefs(Context ctx) {
        return ctx.getSharedPreferences(PLACES_PREFS, Context.MODE_PRIVATE);
    }

    private static List<JSONObject> getPlaces(Context ctx) {
        List<JSONObject> out = new ArrayList<>();
        try {
            String json = placesPrefs(ctx).getString(PLACES_KEY, "");
            if (json == null || json.isEmpty()) return out;
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) out.add(arr.getJSONObject(i));
        } catch (Exception ignored) { }
        return out;
    }

    /** Jagah save karo — GPS ke saath. Naam pehle se ho to update. */
    public static String savePlace(Context ctx, String name) {
        if (name == null || name.trim().isEmpty()) return null;
        name = name.trim();
        double[] ll = getLocation(ctx);
        if (ll == null) return null; // GPS nahi mila
        try {
            List<JSONObject> all = getPlaces(ctx);
            boolean found = false;
            for (JSONObject p : all) {
                if (name.equalsIgnoreCase(p.optString("name"))) {
                    p.put("lat", ll[0]);
                    p.put("lon", ll[1]);
                    found = true;
                }
            }
            if (!found) {
                JSONObject p = new JSONObject();
                p.put("name", name);
                p.put("lat", ll[0]);
                p.put("lon", ll[1]);
                all.add(p);
            }
            JSONArray arr = new JSONArray();
            for (JSONObject p : all) arr.put(p);
            placesPrefs(ctx).edit().putString(PLACES_KEY, arr.toString()).apply();
            return name;
        } catch (Exception e) {
            return null;
        }
    }

    /** "main kahan hun?" — pehle saved jaghein (300m), phir Geocoder, phir sheher. */
    public static String whereAmI(Context ctx) {
        double[] ll = getLocation(ctx);
        if (ll == null) {
            return "Boss, is waqt GPS nahi mil raha — location on hai na?";
        }
        // 1) Saved jagah qareeb hai?
        try {
            for (JSONObject p : getPlaces(ctx)) {
                double plat = p.optDouble("lat", 0), plon = p.optDouble("lon", 0);
                float[] r = new float[1];
                Location.distanceBetween(ll[0], ll[1], plat, plon, r);
                if (r[0] <= 300) {
                    return "Boss, tum " + p.optString("name") + " me ho!";
                }
            }
        } catch (Exception ignored) { }
        // 2) Geocoder — ilaqe ka naam (net chahiye)
        try {
            android.location.Geocoder g = new android.location.Geocoder(
                    ctx, java.util.Locale.getDefault());
            List<android.location.Address> a = g.getFromLocation(ll[0], ll[1], 1);
            if (a != null && !a.isEmpty()) {
                android.location.Address ad = a.get(0);
                String area = ad.getSubLocality();
                if (area == null || area.isEmpty()) area = ad.getLocality();
                if (area == null || area.isEmpty()) area = ad.getSubAdminArea();
                if (area != null && !area.isEmpty()) {
                    return "Boss, tum is waqt " + area + " ke qareeb ho (andaazan).";
                }
            }
        } catch (Exception ignored) { }
        // 3) Aakhri sahara
        return "Boss, GPS to mil gaya lekin jagah ka naam nahi nikal saka — "
                + "tum Phalia ke ird-gird ho.";
    }
}
