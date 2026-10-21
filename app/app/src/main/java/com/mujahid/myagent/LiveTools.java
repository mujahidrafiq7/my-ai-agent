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
                    DisplayMetrics dm = ctx.getResources().getDisplayMetrics();
                    int x = (int) (dm.widthPixels
                            * a.optDouble("x_percent", 50) / 100.0);
                    int y = (int) (dm.heightPixels
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
                    DisplayMetrics dmLp = ctx.getResources().getDisplayMetrics();
                    boolean okLp = A11yService.longPressAtCoordinates(
                            (float) (dmLp.widthPixels
                                    * a.optDouble("x_percent", 50) / 100.0),
                            (float) (dmLp.heightPixels
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
