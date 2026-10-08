package com.mujahid.myagent;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.media.projection.MediaProjectionManager;

/**
 * v43: Screen Share wapas ON — boss ka hukm (2026-10-07).
 * Button toggle: OFF → system permission dialog → ON; ON → seedha OFF.
 * Screen dekhna: "screen pe kya hai" → ProjectionService.capture() → Gemini vision.
 */
public class ScreenShare {

    public static final int REQ_CAPTURE = 4101;
    public static final String EXTRA_AUTO_SHARE = "auto_screen_share";

    private static volatile boolean on = false;

    // v47: share on/off ho to sunne wala (live vision auto-start ke liye)
    public interface StateListener {
        void onStateChange(boolean isOn);
    }

    private static volatile StateListener stateListener;

    public static void setStateListener(StateListener l) {
        stateListener = l;
    }

    /** v47: service ke liye — state change sab ko batana. */
    static void notifyState() {
        StateListener l = stateListener;
        if (l != null) {
            try { l.onStateChange(isOn()); }
            catch (Exception ignored) { }
        }
    }

    public static boolean isOn() {
        return on && ProjectionService.isRunning();
    }

    /** Front page button / voice se: system dialog kholo. */
    public static void requestOn(Activity activity) {
        if (isOn()) return;
        try {
            MediaProjectionManager mpm = (MediaProjectionManager)
                    activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mpm == null) return;
            activity.startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
        } catch (Exception ignored) { }
    }

    /** Permission dialog ka jawab — MainActivity.onActivityResult se. */
    public static void onPermissionResult(Context ctx, int resultCode, Intent data) {
        if (resultCode != Activity.RESULT_OK || data == null) {
            on = false;
            notifyState();
            return;
        }
        ProjectionService.start(ctx, resultCode, data);
        on = true;
        notifyState();
    }

    /** Band karo — kahin se bhi. */
    public static void turnOff(Context ctx) {
        on = false;
        try { ProjectionService.stop(ctx); } catch (Exception ignored) { }
        notifyState();
    }

    /** Service ke band hone pe state sync. */
    static void markOff() {
        on = false;
        notifyState();
    }

    /** Ek frame pakdo — bg thread pe (1-2s lag sakta hai). Null = nahi mila. */
    public static Bitmap capture() {
        if (!isOn()) return null;
        try {
            return ProjectionService.capture();
        } catch (Exception e) {
            return null;
        }
    }

    // ---------- voice triggers (v43) ----------

    private static boolean containsAny(String low, String... parts) {
        for (String p : parts) if (low.contains(p)) return true;
        return false;
    }

    /** "screen share on karo" / "screen share kholo" */
    public static boolean isOnCmd(String said) {
        if (said == null) return false;
        String low = said.toLowerCase(java.util.Locale.ROOT);
        return low.contains("screen share") && containsAny(low,
                "on karo", "on kar", "kholo", "khol do", "start karo", "chalu karo",
                "on kar do", "enable karo");
    }

    /** "screen share band karo" / "screen share off karo" */
    public static boolean isOffCmd(String said) {
        if (said == null) return false;
        String low = said.toLowerCase(java.util.Locale.ROOT);
        return low.contains("screen share") && containsAny(low,
                "band karo", "off karo", "band kar", "off kar", "stop karo",
                "band kar do", "off kar do");
    }

    /** "meri screen dekho" / "screen pe kya hai" */
    public static boolean isDekhoCmd(String said) {
        if (said == null) return false;
        String low = said.toLowerCase(java.util.Locale.ROOT);
        return containsAny(low, "screen dekho", "screen pe kya",
                "screen me kya", "screen dikhao", "screen nazar",
                "meri screen", "screen share dekho");
    }
}
