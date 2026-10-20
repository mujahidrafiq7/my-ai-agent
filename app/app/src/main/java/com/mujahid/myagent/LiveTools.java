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

                default:
                    return "Ye tool mojood nahi: " + name;
            }
        } catch (Exception e) {
            return "Tool chalte hue error aya: " + e.getClass().getSimpleName();
        }
    }
}
