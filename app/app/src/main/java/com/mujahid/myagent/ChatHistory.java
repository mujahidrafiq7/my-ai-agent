package com.mujahid.myagent;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * v52 "History": chat file me save hoti hai — app band ho,
 * phone restart ho, naya version aaye — baatein yaad rehti hain.
 * Aakhri 60 entries rakhta hai (quota bhi bachta hai).
 */
public class ChatHistory {

    private static final String FILE = "chat_history.json";
    private static final int MAX = 60;

    public static class Entry {
        public final String role; // "user" ya "model"
        public final String text;
        public final long time;
        public Entry(String role, String text, long time) {
            this.role = role; this.text = text; this.time = time;
        }
    }

    /** Poori saved history — purani se nayi. */
    public static List<Entry> load(Context ctx) {
        List<Entry> out = new ArrayList<>();
        try {
            FileInputStream in = ctx.openFileInput(FILE);
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) != -1) b.write(buf, 0, n);
            in.close();
            JSONArray arr = new JSONArray(new String(b.toByteArray(), "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                out.add(new Entry(o.optString("role", "user"),
                        o.optString("text", ""), o.optLong("time", 0)));
            }
        } catch (Exception ignored) { /* pehli baar file nahi hoti */ }
        return out;
    }

    /** Ek nayi baat joro (purani 60 se zyada hon to sab se purani hatao). */
    public static void append(Context ctx, String role, String text) {
        try {
            List<Entry> all = load(ctx);
            all.add(new Entry(role, text, System.currentTimeMillis()));
            while (all.size() > MAX) all.remove(0);
            JSONArray arr = new JSONArray();
            for (Entry e : all) {
                JSONObject o = new JSONObject();
                o.put("role", e.role);
                o.put("text", e.text);
                o.put("time", e.time);
                arr.put(o);
            }
            FileOutputStream out = ctx.openFileOutput(FILE, Context.MODE_PRIVATE);
            out.write(arr.toString().getBytes("UTF-8"));
            out.close();
        } catch (Exception ignored) { }
    }

    /** Saari history saaf karo. */
    public static void clear(Context ctx) {
        try { ctx.deleteFile(FILE); } catch (Exception ignored) { }
    }
}
