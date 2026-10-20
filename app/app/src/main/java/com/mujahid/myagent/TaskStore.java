package com.mujahid.myagent;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v77 "1.0-yaad": PENDING TASKS — session band ho jaye, kaam na mare.
 *
 * Boss jo kaam de ("ye karna hai", "kal ye check karna"), wo model
 * remember_task tool se yahan save karti hai. Har naye session me ye
 * list us ke context me jati hai ([BAQI KAAM]) — jab tak complete_task
 * se kat na jaye. SharedPreferences me rehta hai: app band, phone
 * restart — task mehfooz.
 */
public class TaskStore {

    private static final String PREFS = "ayesha_tasks";
    private static final String KEY = "pending";

    private static JSONArray load(Context ctx) {
        try {
            SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
            return new JSONArray(p.getString(KEY, "[]"));
        } catch (Exception e) {
            return new JSONArray();
        }
    }

    private static void save(Context ctx, JSONArray arr) {
        try {
            ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                    .edit().putString(KEY, arr.toString()).apply();
        } catch (Exception ignored) { }
    }

    /** Naya task joro (dobra entry nahi). */
    public static synchronized String add(Context ctx, String text) {
        String t = text == null ? "" : text.trim();
        if (t.isEmpty()) return "Task khaali tha — kuch save nahi hua.";
        try {
            JSONArray arr = load(ctx);
            for (int i = 0; i < arr.length(); i++) {
                if (arr.getJSONObject(i).optString("task", "")
                        .equalsIgnoreCase(t))
                    return "Ye kaam pehle se baqi list me hai boss: " + t;
            }
            JSONObject o = new JSONObject();
            o.put("task", t);
            o.put("at", System.currentTimeMillis());
            arr.put(o);
            save(ctx, arr);
            return "Task save kar liya boss: '" + t + "' — ye mere baqi kaam ki "
                    + "list me rahega, session band ho jaye tab bhi nahi bhoolungi.";
        } catch (Exception e) {
            return "Task save nahi ho saka — error aya.";
        }
    }

    /** Mukammal task kato (naam milao — hissa milna kaafi hai). */
    public static synchronized String complete(Context ctx, String text) {
        String t = text == null ? "" : text.trim().toLowerCase(Locale.ROOT);
        if (t.isEmpty()) return "Kaunsa kaam mukammal hua, samajh nahi aya.";
        try {
            JSONArray arr = load(ctx);
            JSONArray keep = new JSONArray();
            String removed = null;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                String task = o.optString("task", "");
                if (removed == null && (task.toLowerCase(Locale.ROOT).contains(t)
                        || t.contains(task.toLowerCase(Locale.ROOT)))) {
                    removed = task;
                } else {
                    keep.put(o);
                }
            }
            if (removed == null)
                return "Ye kaam baqi list me nahi mila: " + text;
            save(ctx, keep);
            return "Kaam mukammal — list se kaat diya boss: '" + removed + "'";
        } catch (Exception e) {
            return "Task kaatne me error aya.";
        }
    }

    public static synchronized List<String> list(Context ctx) {
        List<String> out = new ArrayList<>();
        try {
            JSONArray arr = load(ctx);
            for (int i = 0; i < arr.length(); i++)
                out.add(arr.getJSONObject(i).optString("task", ""));
        } catch (Exception ignored) { }
        return out;
    }

    /** Session-start context ke liye block (khaali ho to ""). */
    public static String block(Context ctx) {
        try {
            JSONArray arr = load(ctx);
            if (arr.length() == 0) return "";
            SimpleDateFormat f = new SimpleDateFormat(
                    "d MMM h:mm a", Locale.ENGLISH);
            f.setTimeZone(java.util.TimeZone.getTimeZone("Asia/Karachi"));
            StringBuilder b = new StringBuilder();
            b.append("(Ye kaam boss ne tumhe diye thay aur abhi BAQI hain. "
                    + "Session khatam hone se ye NAHI mitte. Jab koi ho jaye to "
                    + "complete_task se kaato. Mauqa mile to boss ko yaad dilao.)\n");
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                b.append("- ").append(o.optString("task", ""))
                        .append(" (diya tha: ")
                        .append(f.format(new Date(o.optLong("at", 0))))
                        .append(")\n");
            }
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }
}
