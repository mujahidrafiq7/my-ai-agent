package com.mujahid.myagent;

import android.graphics.Bitmap;
import android.util.Base64;

import java.io.ByteArrayOutputStream;

/**
 * v69 (ScreenControl merge, Step 3): CONTROLLER — Ayesha ke haathon ka ek darwaza.
 * Voice (UiControl) yahin se action karwati hai; screen ki tasveer bhi yahin se
 * model ko jati hai. Har action ka boolean ASAL natija hai — gestures v67 ke
 * callback se verify hote hain (completed hi true hai).
 */
public class AyeshaController {

    /** Screen ki taaza tasveer Base64 (JPEG) me — model ko bhejne ke liye. */
    public static String getScreenImageBase64() {
        try {
            Bitmap bitmap = ProjectionService.capture(); // v68: persistent frame pehle
            if (bitmap == null) return null;

            int w = bitmap.getWidth(), h = bitmap.getHeight();
            // v90: darmiyani raasta — 900 wide, quality 65 (720 pe
            // chhote icons dhundle ho rahe thay; 1080 raftaar khata tha)
            if (w > 900) {
                float scale = 900f / w;
                bitmap = Bitmap.createScaledBitmap(bitmap,
                        900, Math.round(h * scale), true);
            }
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bitmap.compress(Bitmap.CompressFormat.JPEG, 65, baos);
            return Base64.encodeToString(baos.toByteArray(), Base64.NO_WRAP);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Ek action chalao:
     *   open_app       (target = package name)
     *   tap_coordinates(x, y)
     *   tap_text       (target = on-screen text)
     *   type_text      (target = text)
     *   scroll_down / scroll_up / back / home
     */
    public static boolean executeAction(String actionType, String target, int x, int y) {
        if (actionType == null) return false;
        switch (actionType) {
            case "open_app":
                return target != null && A11yService.openApp(target);
            case "tap_coordinates":
                return A11yService.tap(x, y); // v72: exact tap(int,int) API
            case "long_press": // v73: daba ke rakho
                return A11yService.longPressAtCoordinates(x, y);
            case "tap_text":
                if (target == null) return false;
                boolean ok = A11yService.tapText(target);
                if (!ok) ok = A11yService.tapByDesc(target);
                return ok;
            case "type_text":
                return target != null && A11yService.focusAndType(target);
            case "scroll_down":
                if (A11yService.gestureSwipe(true)) return true;
                return A11yService.scroll(false, 1);
            case "scroll_up":
                if (A11yService.gestureSwipe(false)) return true;
                return A11yService.scroll(true, 1);
            case "back":
                return A11yService.goBack();
            case "home":
                return A11yService.goHome();
            default:
                return false;
        }
    }
}
