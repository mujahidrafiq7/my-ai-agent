package com.mujahid.myagent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

/**
 * v99 "1.0-nazar": Channel Analytics — YouTube Data/Analytics API aur
 * Facebook Graph API ke background clients. Zero screen-share.
 *
 * Credentials sirf Keys vault se (phone par) — Settings > Channel
 * Analytics me user khud dalta hai. Kuch set na ho to tool setup ki
 * saaf hidayat deta hai, andaza nahi lagata.
 */
public final class ChannelClients {

    private static String enc(String s) throws Exception {
        return URLEncoder.encode(s, "UTF-8");
    }

    private static JSONObject get(String urlStr, String bearer) throws Exception {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(urlStr).openConnection();
            c.setConnectTimeout(20000);
            c.setReadTimeout(20000);
            c.setRequestProperty("User-Agent", "AyeshaApp");
            if (bearer != null && !bearer.isEmpty()) {
                c.setRequestProperty("Authorization", "Bearer " + bearer);
            }
            int code = c.getResponseCode();
            InputStream in = code >= 200 && code < 300
                    ? c.getInputStream() : c.getErrorStream();
            StringBuilder sb = new StringBuilder();
            if (in != null) {
                BufferedReader r = new BufferedReader(
                        new InputStreamReader(in, StandardCharsets.UTF_8));
                String line;
                while ((line = r.readLine()) != null) sb.append(line);
                r.close();
            }
            if (code < 200 || code >= 300) {
                String body = sb.toString();
                throw new Exception("HTTP " + code + ": "
                        + body.substring(0, Math.min(220, body.length())));
            }
            return new JSONObject(sb.toString());
        } finally {
            if (c != null) c.disconnect();
        }
    }

    private static String num(String s) {
        try {
            return String.format(Locale.US, "%,d", Long.parseLong(s));
        } catch (Exception e) {
            return s;
        }
    }

    // ---------------- YouTube ----------------

    public static String youtube(Context ctx, String metricType, int days) {
        String key = Keys.youtubeApiKey(ctx);
        String chId = Keys.youtubeChannelId(ctx);
        if (key.isEmpty() || chId.isEmpty()) {
            return "YouTube analytics ka setup abhi baqi hai boss. "
                    + "Settings > Channel Analytics me YouTube API Key aur "
                    + "Channel ID dalo — phir main background me khud data "
                    + "la dungi, screen share ki zaroorat nahi.";
        }
        if (days < 1) days = 28;
        if (days > 365) days = 365;
        try {
            JSONObject ch = get("https://www.googleapis.com/youtube/v3/channels"
                    + "?part=snippet,statistics,contentDetails&id=" + enc(chId)
                    + "&key=" + enc(key), null);
            JSONArray items = ch.optJSONArray("items");
            if (items == null || items.length() == 0) {
                return "YouTube channel nahi mila boss — Channel ID dobara "
                        + "check karo (Settings > Channel Analytics).";
            }
            JSONObject it = items.getJSONObject(0);
            JSONObject sn = it.optJSONObject("snippet");
            JSONObject st = it.optJSONObject("statistics");
            StringBuilder sb = new StringBuilder();
            sb.append("YouTube channel: ").append(sn != null ? sn.optString("title") : chId).append("\n");
            if (st != null) {
                sb.append("Subscribers: ").append(num(st.optString("subscriberCount"))).append("\n");
                sb.append("Kul views: ").append(num(st.optString("viewCount"))).append("\n");
                sb.append("Kul videos: ").append(num(st.optString("videoCount"))).append("\n");
            }
            // Uploads playlist → recent videos
            String uploads = it.optJSONObject("contentDetails")
                    .optJSONObject("relatedPlaylists").optString("uploads");
            JSONObject pl = get("https://www.googleapis.com/youtube/v3/playlistItems"
                    + "?part=contentDetails&maxResults=50&playlistId=" + enc(uploads)
                    + "&key=" + enc(key), null);
            JSONArray pli = pl.optJSONArray("items");
            List<String> ids = new ArrayList<>();
            if (pli != null) {
                for (int i = 0; i < pli.length(); i++) {
                    String vid = pli.getJSONObject(i).optJSONObject("contentDetails")
                            .optString("videoId");
                    if (!vid.isEmpty()) ids.add(vid);
                }
            }
            // Video stats — top videos nikaalo
            List<String[]> vids = new ArrayList<>(); // [views, title, likes, comments, id]
            if (!ids.isEmpty()) {
                StringBuilder idParam = new StringBuilder();
                for (int i = 0; i < ids.size(); i++) {
                    if (i > 0) idParam.append(",");
                    idParam.append(ids.get(i));
                }
                JSONObject vs = get("https://www.googleapis.com/youtube/v3/videos"
                        + "?part=snippet,statistics&id=" + enc(idParam.toString())
                        + "&key=" + enc(key), null);
                JSONArray vi = vs.optJSONArray("items");
                if (vi != null) {
                    for (int i = 0; i < vi.length(); i++) {
                        JSONObject v = vi.getJSONObject(i);
                        JSONObject vst = v.optJSONObject("statistics");
                        vids.add(new String[]{
                                vst != null ? vst.optString("viewCount", "0") : "0",
                                v.optJSONObject("snippet") != null
                                        ? v.optJSONObject("snippet").optString("title") : "",
                                vst != null ? vst.optString("likeCount", "0") : "0",
                                vst != null ? vst.optString("commentCount", "0") : "0",
                                v.optString("id")});
                    }
                }
            }
            Collections.sort(vids, (a, b) -> Long.compare(
                    parseLong(b[0]), parseLong(a[0])));
            sb.append("Top videos (recent 50 me se):\n");
            for (int i = 0; i < Math.min(5, vids.size()); i++) {
                String[] v = vids.get(i);
                sb.append(i + 1).append(". ").append(v[1]).append(" — ")
                        .append(num(v[0])).append(" views, ")
                        .append(num(v[2])).append(" likes, ")
                        .append(num(v[3])).append(" comments\n");
            }
            if ("retention".equalsIgnoreCase(metricType)) {
                String tok = Keys.youtubeOauthToken(ctx);
                if (tok.isEmpty()) {
                    sb.append("Retention/watch-time ke liye YouTube OAuth "
                            + "token chahiye jo abhi Settings me nahi dala — "
                            + "wo dalte hi retention bhi la dungi. Upar "
                            + "overview aur top videos ka data hai.");
                } else {
                    sb.append(retention(tok, days, vids));
                }
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "YouTube data nahi aa saka boss: " + e.getMessage();
        }
    }

    private static long parseLong(String s) {
        try { return Long.parseLong(s); } catch (Exception e) { return 0; }
    }

    private static String retention(String token, int days, List<String[]> vids) {
        try {
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("UTC"));
            long now = System.currentTimeMillis();
            String start = f.format(new Date(now - days * 86_400_000L));
            String end = f.format(new Date(now));
            JSONObject r = get("https://youtubeanalytics.googleapis.com/v2/reports"
                    + "?ids=channel==MINE&startDate=" + start + "&endDate=" + end
                    + "&metrics=views,estimatedMinutesWatched,averageViewDuration,averageViewPercentage"
                    + "&dimensions=video&maxResults=10&sort=-views", token);
            JSONArray rows = r.optJSONArray("rows");
            if (rows == null || rows.length() == 0) {
                return "\nRetention: is period me Analytics data nahi mila.";
            }
            StringBuilder sb = new StringBuilder();
            sb.append("\nRetention / watch-time (pichle ").append(days).append(" din, top videos):\n");
            for (int i = 0; i < rows.length(); i++) {
                JSONArray row = rows.getJSONArray(i);
                String vid = row.optString(0);
                String title = vid;
                for (String[] v : vids) {
                    if (v[4].equals(vid)) { title = v[1]; break; }
                }
                sb.append("- ").append(title)
                        .append(" — views ").append(num(row.optString(1)))
                        .append(", watch ").append(row.optString(2)).append(" min")
                        .append(", avg duration ").append(row.optString(3)).append("s")
                        .append(", avg viewed ").append(row.optString(4)).append("%\n");
            }
            return sb.toString().trim();
        } catch (Exception e) {
            return "\nRetention data nahi aa saka (OAuth token check karo): "
                    + e.getMessage();
        }
    }

    // ---------------- Facebook ----------------

    public static String facebook(Context ctx, String metricType, int days) {
        String pageId = Keys.facebookPageId(ctx);
        String tok = Keys.facebookPageToken(ctx);
        if (pageId.isEmpty() || tok.isEmpty()) {
            return "Facebook insights ka setup abhi baqi hai boss. "
                    + "Settings > Channel Analytics me Page ID aur Page "
                    + "Access Token dalo — phir main background me reach "
                    + "aur post performance la dungi.";
        }
        if (days < 1) days = 7;
        if (days > 90) days = 90;
        String base = "https://graph.facebook.com/v21.0/" + pageId;
        StringBuilder sb = new StringBuilder();
        try {
            JSONObject p = get(base + "?fields=name,fan_count&access_token="
                    + enc(tok), null);
            sb.append("Facebook page: ").append(p.optString("name")).append("\n");
            sb.append("Followers: ").append(num(p.optString("fan_count"))).append("\n");
        } catch (Exception e) {
            return "Facebook page nahi mila boss (Page ID/token check karo): "
                    + e.getMessage();
        }
        // Reach / views (Graph ki marzi — metric na mile to wajah ke sath)
        try {
            long until = System.currentTimeMillis() / 1000;
            long since = until - days * 86_400L;
            JSONObject ins = get(base + "/insights?metric=page_impressions_unique,page_views_total"
                    + "&period=day&since=" + since + "&until=" + until
                    + "&access_token=" + enc(tok), null);
            JSONArray data = ins.optJSONArray("data");
            if (data != null) {
                for (int i = 0; i < data.length(); i++) {
                    JSONObject m = data.getJSONObject(i);
                    JSONArray vals = m.optJSONArray("values");
                    long sum = 0;
                    if (vals != null) {
                        for (int j = 0; j < vals.length(); j++) {
                            sum += vals.getJSONObject(j).optLong("value", 0);
                        }
                    }
                    String name = m.optString("name");
                    sb.append("page_impressions_unique".equals(name) ? "Reach (" : "Page views (")
                            .append(days).append(" din): ").append(num(String.valueOf(sum))).append("\n");
                }
            }
        } catch (Exception e) {
            sb.append("Reach metric nahi mila: ").append(shortMsg(e)).append("\n");
        }
        // Recent posts performance
        try {
            JSONObject posts = get(base + "/published_posts"
                    + "?fields=created_time,message,permalink_url,shares,"
                    + "likes.summary(true),comments.summary(true)&limit=10"
                    + "&access_token=" + enc(tok), null);
            JSONArray data = posts.optJSONArray("data");
            if (data != null && data.length() > 0) {
                sb.append("Recent posts:\n");
                for (int i = 0; i < data.length(); i++) {
                    JSONObject po = data.getJSONObject(i);
                    String msg = po.optString("message", "(no text)");
                    if (msg.length() > 60) msg = msg.substring(0, 60) + "...";
                    long likes = po.optJSONObject("likes") != null
                            ? po.optJSONObject("likes").optJSONObject("summary") != null
                            ? po.optJSONObject("likes").optJSONObject("summary").optLong("total_count", 0) : 0 : 0;
                    long comments = po.optJSONObject("comments") != null
                            ? po.optJSONObject("comments").optJSONObject("summary") != null
                            ? po.optJSONObject("comments").optJSONObject("summary").optLong("total_count", 0) : 0 : 0;
                    long shares = po.optJSONObject("shares") != null
                            ? po.optJSONObject("shares").optLong("count", 0) : 0;
                    sb.append("- ").append(po.optString("created_time").substring(0, 10))
                            .append(" \"").append(msg).append("\" — ")
                            .append(likes).append(" likes, ")
                            .append(comments).append(" comments, ")
                            .append(shares).append(" shares\n");
                }
            }
        } catch (Exception e) {
            sb.append("Recent posts nahi mile: ").append(shortMsg(e)).append("\n");
        }
        String out = sb.toString().trim();
        return out.length() > 3800 ? out.substring(0, 3800) : out;
    }

    private static String shortMsg(Exception e) {
        String m = e.getMessage();
        if (m == null) return "unknown error";
        return m.length() > 160 ? m.substring(0, 160) : m;
    }
}
