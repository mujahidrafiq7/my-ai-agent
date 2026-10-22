package com.mujahid.myagent;

import android.content.Context;
import android.util.DisplayMetrics;

import org.json.JSONObject;

/**
 * v76 "1.0-khud": Live model ke TOOLS ka executor.
 *
 * Farq samjho: pehle model sirf BOLTI thi, peeche ek alag handler uski
 * baat sun ke andaze se kaam karta tha aur report ek nakli message ki
 * surat me ati thi — model use ignore kar ke apni "ho gaya" bana leti thi.
 * Ab model KHUD tool call karti hai, ye executor wo kaam asal me karta
 * hai, aur natija TOOL RESPONSE ki surat me seedha model ko milta hai —
 * wo usi natije pe bolti hai. Beech wala banda khatam.
 *
 * Har tool ka jawab chhota, do-tok, sach: hua to "hua", nahi to wajah.
 */
public class LiveTools {

    /**
     * v87: ASAL poori screen ka size (upar/neeche ki system pattiyan
     * samet). Pehle getDisplayMetrics() pattiyan ghata ke deta tha —
     * isi liye tap asal jagah se upar lagta tha aur screen ke neeche
     * wale hisse tak tap phonch hi nahi sakta tha.
     */
    /** v90: aaj (ya kal, agar waqt guzar gaya) ke alarm ka millis —
     *  sirf confirm bolne ke liye. */
    private static long alarmMillis(int hh, int mm) {
        java.util.Calendar c = java.util.Calendar.getInstance();
        c.set(java.util.Calendar.HOUR_OF_DAY, hh);
        c.set(java.util.Calendar.MINUTE, mm);
        c.set(java.util.Calendar.SECOND, 0);
        if (c.getTimeInMillis() <= System.currentTimeMillis())
            c.add(java.util.Calendar.DAY_OF_YEAR, 1);
        return c.getTimeInMillis();
    }

    private static int[] realSize(Context ctx) {
        try {
            android.view.WindowManager wm = (android.view.WindowManager)
                    ctx.getSystemService(Context.WINDOW_SERVICE);
            android.graphics.Point p = new android.graphics.Point();
            wm.getDefaultDisplay().getRealSize(p);
            if (p.x > 0 && p.y > 0) return new int[]{p.x, p.y};
        } catch (Exception ignored) { }
        DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    public static String execute(Context ctx, String name, JSONObject a) {
        if (a == null) a = new JSONObject();
        try {
            switch (name) {

                case "open_app":
                    return PhoneTools.openAppByName(
                            ctx, a.optString("app_name", ""));

                case "open_whatsapp_chat":
                    return WhatsAppAction.openChat(
                            ctx, a.optString("name", "")).reply;

                case "send_whatsapp_message": {
                    String n = a.optString("name", "");
                    String m = a.optString("message", "");
                    if (!a.optBoolean("confirmed", false)) {
                        // v64 wala usool: bhejne se pehle boss se tasdeeq
                        return "Abhi NAHI bheja. Pehle boss se poochho: '"
                                + n + " ko \"" + m + "\" bhejun?' — agar boss "
                                + "HAAN kahe to isi tool ko confirmed=true ke "
                                + "sath dobara call karo, tab asal me jayega.";
                    }
                    return WhatsAppAction.sendMessage(ctx, n, m).reply;
                }

                case "tap_screen": {
                    if (!A11yService.isEnabled())
                        return "Tap nahi ho saka — Accessibility me Ayesha OFF hai.";
                    int[] rs = realSize(ctx);
                    int x = (int) (rs[0]
                            * a.optDouble("x_percent", 50) / 100.0);
                    int y = (int) (rs[1]
                            * a.optDouble("y_percent", 50) / 100.0);
                    boolean ok = A11yService.tapAtCoordinates(x, y);
                    return ok
                            ? "Tap ho gaya us jagah pe. Screen feed pe natija dekho."
                            : "Tap nahi ho saka — gesture cancel ho gaya.";
                }

                case "type_text": {
                    String text = a.optString("text", "");
                    boolean ok = A11yService.focusAndType(text);
                    return ok
                            ? "Type kar diya: '" + text + "' (field me check ho gaya)."
                            : "Type nahi ho saka — screen pe koi likhne wali jagah "
                                    + "(input field) selected nahi mili.";
                }

                case "scroll_screen": {
                    boolean up = "up".equals(a.optString("direction", "down"));
                    boolean ok = AyeshaController.executeAction(
                            up ? "scroll_up" : "scroll_down", null, 0, 0);
                    return ok
                            ? "Scroll ho gaya (" + (up ? "upar" : "neeche") + ")."
                            : "Scroll nahi ho saka — accessibility check karo.";
                }

                case "press_button": {
                    boolean home = "home".equals(a.optString("button", "back"));
                    boolean ok = AyeshaController.executeAction(
                            home ? "home" : "back", null, 0, 0);
                    return ok
                            ? (home ? "Home pe aa gaye." : "Back kar diya.")
                            : "Button nahi dab saka.";
                }

                case "search_youtube":
                    return PhoneTools.youtube(ctx,
                            a.optString("query", ""),
                            a.optBoolean("play", false));

                case "remember_task":
                    return TaskStore.add(ctx, a.optString("task", ""));

                case "complete_task":
                    return TaskStore.complete(ctx, a.optString("task", ""));

                case "long_press_screen": {
                    if (!A11yService.isEnabled())
                        return "Long-press nahi ho saka — Accessibility me Ayesha OFF hai.";
                    int[] rsLp = realSize(ctx);
                    boolean okLp = A11yService.longPressAtCoordinates(
                            (float) (rsLp[0]
                                    * a.optDouble("x_percent", 50) / 100.0),
                            (float) (rsLp[1]
                                    * a.optDouble("y_percent", 50) / 100.0));
                    return okLp
                            ? "Long-press ho gaya. Agar menu khula hai to feed pe "
                                    + "dekho — phir tap_text se matlooba lafz dabao."
                            : "Long-press nahi ho saka — gesture cancel ho gaya.";
                }

                case "tap_text": {
                    String txt = a.optString("text", "");
                    boolean okT = A11yService.tapText(txt)
                            || A11yService.tapByDesc(
                                    txt.toLowerCase(java.util.Locale.ROOT));
                    return okT
                            ? "'" + txt + "' daba diya."
                            : "'" + txt + "' screen pe nahi mila — feed pe dekh "
                                    + "ke tap_screen se dabao.";
                }

                case "text_action": {
                    String act = a.optString("action", "");
                    boolean okA = A11yService.textAction(act);
                    switch (act) {
                        case "select_all":
                            return okA ? "Saara text select ho gaya."
                                    : "Select nahi ho saka — field me text nahi "
                                            + "hai ya koi field selected nahi.";
                        case "copy":
                            return okA ? "Copy ho gaya — text clipboard me hai."
                                    : "Copy nahi ho saka — pehle text select karo "
                                            + "ya field pe focus karo.";
                        case "cut":
                            return okA ? "Cut ho gaya — text wahan se hat ke "
                                            + "clipboard me chala gaya."
                                    : "Cut nahi ho saka — pehle text select karo.";
                        case "paste":
                            return okA ? "Paste ho gaya — field me parh ke "
                                            + "tasdeeq bhi kar li."
                                    : "Paste nahi hua — clipboard khaali ho sakta "
                                            + "hai ya koi field selected nahi.";
                        default:
                            return "Ye text action samajh nahi aya: " + act;
                    }
                }

                case "add_reminder": {
                    // v91: Ayesha ka APNA reminder — Clock timer/alarm
                    // se bilkul alag (strict separation)
                    String rmsg = a.optString("message", "").trim();
                    if (rmsg.isEmpty()) rmsg = "yaad dilane ka waqt ho gaya";
                    int afterMin = a.optInt("after_minutes", 0);
                    long atMs;
                    if (afterMin > 0) {
                        atMs = System.currentTimeMillis()
                                + afterMin * 60_000L;
                    } else {
                        int rH = a.optInt("hour", -1);
                        int rM = a.optInt("minute", 0);
                        if (rH < 0 || rH > 23 || rM < 0 || rM > 59)
                            return "Reminder ka waqt samajh nahi aya "
                                    + "boss — kitne minute baad, ya kis "
                                    + "waqt (ghanta:min), ye batao.";
                        atMs = alarmMillis(rH, rM);
                    }
                    // Dedupe: waisa hi reminder ±2 min me pehle se ho
                    // (pattern-rasta bhi kabhi kabhi wahi laga deta hai)
                    for (org.json.JSONObject e : Reminder.entries(ctx)) {
                        if (e.optString("msg", "").equalsIgnoreCase(rmsg)
                                && Math.abs(e.optLong("at", 0) - atMs)
                                        < 120_000L) {
                            return "Ye reminder pehle se laga hua hai "
                                    + "boss: " + Reminder.timeText(atMs)
                                    + " — " + rmsg;
                        }
                    }
                    String rErr = Reminder.schedule(ctx, atMs, rmsg);
                    return rErr != null ? rErr
                            : "Reminder lag gaya boss: "
                                    + Reminder.timeText(atMs)
                                    + " — " + rmsg;
                }

                case "list_reminders":
                    return Reminder.listText(ctx);

                case "delete_reminder": {
                    String gone = Reminder.deleteByMatch(
                            ctx, a.optString("match", ""));
                    return gone != null
                            ? "Reminder hata diya boss: " + gone
                            : "Aisa koi reminder nahi mila boss.";
                }

                case "clear_all_reminders": {
                    int nRem = Reminder.clearAll(ctx);
                    return nRem > 0
                            ? nRem + " reminders delete kar diye boss."
                            : "Koi reminder tha hi nahi boss.";
                }

                // v90: Alarm/Timer — Clock app ki UI pe tap/scroll WALA
                // rasta band; Android ke direct intent se 1 second me set
                case "set_alarm": {
                    int hh = a.optInt("hour", -1);
                    int mm = a.optInt("minute", 0);
                    if (hh < 0 || hh > 23 || mm < 0 || mm > 59)
                        return "Alarm ka waqt samajh nahi aya boss.";
                    try {
                        android.content.Intent ai = new android.content.Intent(
                                android.provider.AlarmClock.ACTION_SET_ALARM)
                                .putExtra(android.provider.AlarmClock.EXTRA_HOUR, hh)
                                .putExtra(android.provider.AlarmClock.EXTRA_MINUTES, mm)
                                .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE,
                                        a.optString("message", "Ayesha alarm"))
                                .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(ai);
                        return "Alarm set ho gaya boss: "
                                + Reminder.timeText(alarmMillis(hh, mm));
                    } catch (Exception e) {
                        return "Alarm set nahi ho saka boss — is phone ka "
                                + "Clock app direct alarm ko support nahi karta.";
                    }
                }

                case "set_timer": {
                    int secs = a.optInt("seconds", 0);
                    if (secs <= 0)
                        return "Timer ka waqt samajh nahi aya boss.";
                    try {
                        android.content.Intent ti = new android.content.Intent(
                                android.provider.AlarmClock.ACTION_SET_TIMER)
                                .putExtra(android.provider.AlarmClock.EXTRA_LENGTH, secs)
                                .putExtra(android.provider.AlarmClock.EXTRA_MESSAGE,
                                        a.optString("message", "Ayesha timer"))
                                .putExtra(android.provider.AlarmClock.EXTRA_SKIP_UI, true)
                                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(ti);
                        return "Timer shuru ho gaya boss: "
                                + (secs >= 60 ? (secs / 60) + " minute"
                                        : secs + " second") + " ka.";
                    } catch (Exception e) {
                        return "Timer set nahi ho saka boss — is phone ka "
                                + "Clock app direct timer ko support nahi karta.";
                    }
                }

                case "wait_seconds": {
                    int sec = a.optInt("seconds", 5);
                    if (sec < 1) sec = 1;
                    if (sec > 60) sec = 60;
                    Thread.sleep(sec * 1000L);
                    return sec + " second ruk gayi — ab screen feed pe dekho "
                            + "aur wahan ka jawab parho.";
                }

                default:
                    return "Ye tool mojood nahi: " + name;
            }
        } catch (Exception e) {
            return "Tool chalte hue error aya: " + e.getClass().getSimpleName();
        }
    }
}
