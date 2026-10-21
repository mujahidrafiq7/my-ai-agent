package com.mujahid.myagent;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.graphics.Path;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * STEP 2 — Screen control (AccessibilityService).
 * Voice se: scroll, type, tap-by-text, home, back — KISI BHI APP ME.
 * User ko ek dafa Settings → Accessibility me "My Agent" ON karna parega.
 */
public class A11yService extends AccessibilityService {

    // v72: volatile — voice/controller threads ko hamesha taaza instance dikhe
    private static volatile A11yService instance;

    /** Service zinda aur enabled hai? */
    public static boolean isEnabled() {
        return instance != null;
    }

    /** Bandha hua service instance (controller ke liye). Null = service OFF/unbound. */
    public static A11yService getInstance() {
        return instance;
    }

    // v46: foreground app tracking (screen share context injection)
    private static volatile String foregroundPackage = "";

    /** Is waqt kaunsi app khuli hai (package name), "" = maloom nahi. */
    public static String getForegroundPackage() {
        return foregroundPackage == null ? "" : foregroundPackage;
    }

    // ---------- v48: LOCAL SCREEN READER (zero API quota) ----------

    /**
     * Screen ka muft local khulasa — Accessibility node tree se.
     * Koi API call nahi, koi quota nahi, offline bhi chalta hai.
     * Null = Accessibility off ya tree nahi mila.
     */
    public static String readScreen() {
        A11yService s = instance;
        if (s == null) return null;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return null;
            java.util.LinkedHashSet<String> texts = new java.util.LinkedHashSet<>();
            collectTexts(root, texts, 0);
            String app = appLabel(s);
            if (texts.isEmpty()) {
                return app + " khula hai, lekin parhne laiq text nahi mila.";
            }
            StringBuilder b = new StringBuilder();
            b.append(app).append(" khula hai. Screen pe ye likha hai: ");
            int n = 0;
            for (String t : texts) {
                if (n >= 20) { b.append("... (aur bhi hai)"); break; }
                if (n > 0) b.append(" | ");
                b.append(t);
                n++;
            }
            return b.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String appLabel(A11yService s) {
        try {
            String pkg = foregroundPackage;
            if (pkg == null || pkg.isEmpty()) return "Screen";
            android.content.pm.PackageManager pm = s.getPackageManager();
            android.content.pm.ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
            CharSequence l = pm.getApplicationLabel(ai);
            if (l != null && l.length() > 0) return l.toString();
            return pkg;
        } catch (Exception e) {
            return "Screen";
        }
    }

    private static void collectTexts(AccessibilityNodeInfo n,
                                     java.util.Set<String> out, int depth) {
        if (n == null || depth > 12 || out.size() >= 40) return;
        try {
            CharSequence t = n.getText();
            if (t == null) t = n.getContentDescription();
            if (t != null) {
                String s = t.toString().trim().replaceAll("\\s+", " ");
                if (s.length() >= 2 && s.length() <= 80) out.add(s);
            }
            for (int i = 0; i < n.getChildCount(); i++) {
                collectTexts(n.getChild(i), out, depth + 1);
            }
        } catch (Exception ignored) { }
    }

    // ---------- Tap by content-description (WhatsApp ke Send/Call button) ----------

    /**
     * Screen pe wo button dabao jis ke text ya content-description me
     * `part` ata ho (case-insensitive). Har match try karta hai.
     */
    public static boolean tapByDesc(String part) {
        A11yService s = instance;
        if (s == null || part == null || part.isEmpty()) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            String needle = part.toLowerCase(java.util.Locale.ROOT);
            java.util.List<AccessibilityNodeInfo> found =
                    new java.util.ArrayList<>();
            collectByDesc(root, needle, found);
            for (AccessibilityNodeInfo n : found) {
                if (performClickOnNode(n)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    private static void collectByDesc(AccessibilityNodeInfo node, String needle,
                                      java.util.List<AccessibilityNodeInfo> out) {
        if (node == null) return;
        try {
            CharSequence t = node.getText();
            CharSequence d = node.getContentDescription();
            boolean hit = (t != null && t.toString()
                    .toLowerCase(java.util.Locale.ROOT).contains(needle))
                    || (d != null && d.toString()
                    .toLowerCase(java.util.Locale.ROOT).contains(needle));
            if (hit) out.add(node);
            for (int i = 0; i < node.getChildCount(); i++)
                collectByDesc(node.getChild(i), needle, out);
        } catch (Exception ignored) { }
    }

    // ---------- Node click (targeted) ----------

    /** Kisi node (ya uske sab se qareebi clickable parent) par ACTION_CLICK chalao. */
    public static boolean performClickOnNode(AccessibilityNodeInfo node) {
        if (node == null) return false;
        try {
            AccessibilityNodeInfo c = node;
            while (c != null && !c.isClickable()) c = c.getParent();
            return c != null
                    && c.performAction(AccessibilityNodeInfo.ACTION_CLICK);
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    protected void onServiceConnected() {
        instance = this;
        Diag.event("a11y instance BOUND");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // v46: foreground app track karo — screen share ko context milega
        try {
            if (event != null
                    && event.getEventType()
                            == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
                    && event.getPackageName() != null) {
                foregroundPackage = event.getPackageName().toString();
            }
        } catch (Exception ignored) { }
    }

    @Override
    public void onInterrupt() { }

    // v72: unbind pe instance foran saaf — latka hua purana instance khatam
    @Override
    public boolean onUnbind(Intent intent) {
        if (instance == this) instance = null;
        Diag.event("a11y instance UNBOUND");
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
        Diag.event("a11y instance DESTROYED");
        super.onDestroy();
    }

    // ---------- Global actions ----------

    public static boolean goHome() {
        return globalAction(GLOBAL_ACTION_HOME);
    }

    public static boolean goBack() {
        return globalAction(GLOBAL_ACTION_BACK);
    }

    private static boolean globalAction(int action) {
        A11yService s = instance;
        if (s == null) return false;
        try {
            return s.performGlobalAction(action);
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- App kholna (ScreenControl merge) ----------

    /** Package name se app seedha launch karo. */
    public static boolean openApp(String packageName) {
        A11yService s = instance;
        if (s == null || packageName == null) return false;
        try {
            android.content.Intent intent = s.getPackageManager()
                    .getLaunchIntentForPackage(packageName);
            if (intent == null) return false;
            intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK);
            s.startActivity(intent);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Scroll ----------

    /** up=true → upar, warna neeche. times = kitni dafa. */
    public static boolean scroll(boolean up, int times) {
        A11yService s = instance;
        if (s == null) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo target = findScrollable(root);
            if (target == null) return false;
            boolean ok = false;
            for (int i = 0; i < Math.max(1, times); i++) {
                ok |= target.performAction(up
                        ? AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                        : AccessibilityNodeInfo.ACTION_SCROLL_FORWARD);
                try { Thread.sleep(350); } catch (InterruptedException ignored) { }
            }
            return ok;
        } catch (Exception e) {
            return false;
        }
    }

    private static AccessibilityNodeInfo findScrollable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        try {
            if (node.isScrollable()) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo r = findScrollable(node.getChild(i));
                if (r != null) return r;
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ---------- Type ----------

    /** Focused text field me likho (keyboard kholne ki zaroorat nahi). */
    // ---------- v71: Typing VERIFY — text asal me field me gaya ya nahi ----------

    /** Field ko dobara parr ke check karo ke likha gaya text us me maujood hai. */
    private static boolean verifyTextPresent(String expected) {
        A11yService s = instance;
        if (s == null || expected == null) return false;
        try { Thread.sleep(200); } catch (InterruptedException ignored) { }
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo input = null;
            try {
                input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            } catch (Exception ignored) { }
            if (input == null) input = findEditable(root);
            if (input == null) return false;
            if (input.isPassword()) return true; // password field ka text parhna mumkin nahi
            CharSequence cur = input.getText();
            if (cur == null) return false;
            String now = cur.toString();
            // Kuch apps text format kar dete hain — shuru ke 20 harf mila ke dekho
            String probe = expected.length() > 20
                    ? expected.substring(0, 20) : expected;
            return now.contains(probe);
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean typeText(String text) {
        A11yService s = instance;
        if (s == null || text == null || text.isEmpty()) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo input = null;
            try {
                input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            } catch (Exception ignored) { }
            if (input == null) input = findEditable(root);
            if (input == null) return false;
            Bundle b = new Bundle();
            b.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text);
            if (!input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b))
                return false;
            // v71: sirf action ka boolean nahi — field parr ke TASDEEQ
            boolean verified = verifyTextPresent(text);
            Diag.event(verified ? "type VERIFIED" : "type NOT verified");
            return verified;
        } catch (Exception e) {
            return false;
        }
    }

    private static AccessibilityNodeInfo findEditable(AccessibilityNodeInfo node) {
        if (node == null) return null;
        try {
            if (node.isEditable() || "android.widget.EditText"
                    .equals(node.getClassName())) return node;
            for (int i = 0; i < node.getChildCount(); i++) {
                AccessibilityNodeInfo r = findEditable(node.getChild(i));
                if (r != null) return r;
            }
        } catch (Exception ignored) { }
        return null;
    }

    // ---------- v64: Focus karke type ----------

    /**
     * Input field pe pehle FOCUS karo, phir likho — WhatsApp jaisi apps me
     * kabhi-kabhi bina focus ke SET_TEXT nahi lagta. "Fazil ki chat pe Hello
     * type karke send karo" isi se type hota hai.
     */
    public static boolean focusAndType(String text) {
        A11yService s = instance;
        if (s == null || text == null || text.isEmpty()) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo input = findEditable(root);
            if (input == null) return false;
            try {
                input.performAction(AccessibilityNodeInfo.ACTION_FOCUS);
            } catch (Exception ignored) { }
            try { Thread.sleep(400); } catch (InterruptedException ignored) { }
            Bundle b = new Bundle();
            b.putCharSequence(
                    AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                    text);
            if (input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b)) {
                // v71: TASDEEQ — field dobara parrha jayega
                if (verifyTextPresent(text)) {
                    Diag.event("type VERIFIED");
                    return true;
                }
                Diag.event("type NOT verified — fallback try");
            }
            return typeText(text); // fallback: purana tareeqa (wo bhi verify karta hai)
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- v67: GESTURES — asal natija check karke ----------
    // v66 ka masla: dispatchGesture(..., null, null) — boolean sirf "request gayi"
    // batata tha, "ungli chali" nahi. Gesture cancel bhi ho jaye to pata nahi chalta tha.
    // Ab: MAIN thread se dispatch + GestureResultCallback ka intezar — sach hi return hoga.

    /** Gesture MAIN thread se bhejo, aur completed/cancelled ka asal faisla lo. */
    private static boolean dispatchAndWait(final GestureDescription g) {
        final A11yService s = instance;
        if (s == null) return false;
        final java.util.concurrent.CountDownLatch latch =
                new java.util.concurrent.CountDownLatch(1);
        final boolean[] completed = {false};
        final boolean[] accepted = {false};
        Runnable r = () -> {
            try {
                accepted[0] = s.dispatchGesture(g, new GestureResultCallback() {
                    @Override public void onCompleted(GestureDescription gd) {
                        completed[0] = true;
                        Diag.event("gesture COMPLETED");
                        latch.countDown();
                    }
                    @Override public void onCancelled(GestureDescription gd) {
                        Diag.event("gesture CANCELLED");
                        latch.countDown();
                    }
                }, null);
                if (!accepted[0]) latch.countDown();
            } catch (Exception e) {
                latch.countDown();
            }
        };
        boolean onMain = Looper.myLooper() == Looper.getMainLooper();
        if (onMain) {
            r.run(); // main thread pe hain — await karna deadlock hoga
            return accepted[0];
        }
        new Handler(Looper.getMainLooper()).post(r);
        try {
            latch.await(1500, java.util.concurrent.TimeUnit.MILLISECONDS);
        } catch (InterruptedException ignored) { }
        return completed[0];
    }

    /** tap(int, int) — controller/tool calls ke liye exact API. */
    public static boolean tap(int x, int y) {
        return tapAtCoordinates(x, y);
    }

    /** v73: Kisi point pe daba ke rakho (long-press) — 700ms ka stroke. */
    public static boolean longPressAtCoordinates(float x, float y) {
        if (instance == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(p, 0, 700);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(stroke);
            return dispatchAndWait(b.build());
        } catch (Exception e) {
            return false;
        }
    }

    /** Screen ke kisi bhi point (X, Y) par tap karo. */
    public static boolean tapAtCoordinates(float x, float y) {
        if (instance == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            Path p = new Path();
            p.moveTo(x, y);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(p, 0, 80);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(stroke);
            return dispatchAndWait(b.build());
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Asli swipe. down=true → neeche ka content dekho (ungli upar ki taraf),
     * down=false → upar wapas (ungli neeche ki taraf).
     */
    public static boolean gestureSwipe(boolean down) {
        A11yService s = instance;
        if (s == null) return false;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false;
        try {
            android.graphics.Point rp = new android.graphics.Point();
            try {
                ((android.view.WindowManager) s.getSystemService(
                        android.content.Context.WINDOW_SERVICE))
                        .getDefaultDisplay().getRealSize(rp);
            } catch (Exception ignored) { }
            int sw = rp.x > 0 ? rp.x
                    : s.getResources().getDisplayMetrics().widthPixels;
            int sh = rp.y > 0 ? rp.y
                    : s.getResources().getDisplayMetrics().heightPixels;
            float cx = sw / 2f;
            float y1 = down ? sh * 0.7f : sh * 0.3f;
            float y2 = down ? sh * 0.3f : sh * 0.7f;
            Path p = new Path();
            p.moveTo(cx, y1);
            p.lineTo(cx, y2);
            GestureDescription.StrokeDescription stroke =
                    new GestureDescription.StrokeDescription(p, 0, 300);
            GestureDescription.Builder b = new GestureDescription.Builder();
            b.addStroke(stroke);
            return dispatchAndWait(b.build());
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- v78: Clipboard actions (select all / copy / cut / paste) ----------

    /** Focused likhne wali jagah pe pakka text action.
     *  Paste pe field dobara parh ke TASDEEQ (text barha ya nahi). */
    public static boolean textAction(String action) {
        A11yService s = instance;
        if (s == null || action == null) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            AccessibilityNodeInfo input = null;
            try {
                input = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT);
            } catch (Exception ignored) { }
            if (input == null) input = findEditable(root);
            if (input == null) return false;
            switch (action) {
                case "select_all": {
                    CharSequence t = input.getText();
                    int len = t == null ? 0 : t.length();
                    if (len == 0) return false;
                    Bundle b = new Bundle();
                    b.putInt(AccessibilityNodeInfo
                            .ACTION_ARGUMENT_SELECTION_START_INT, 0);
                    b.putInt(AccessibilityNodeInfo
                            .ACTION_ARGUMENT_SELECTION_END_INT, len);
                    return input.performAction(
                            AccessibilityNodeInfo.ACTION_SET_SELECTION, b);
                }
                case "copy":
                    return input.performAction(
                            AccessibilityNodeInfo.ACTION_COPY);
                case "cut":
                    return input.performAction(
                            AccessibilityNodeInfo.ACTION_CUT);
                case "paste": {
                    CharSequence before = input.getText();
                    int bl = before == null ? 0 : before.length();
                    if (!input.performAction(
                            AccessibilityNodeInfo.ACTION_PASTE))
                        return false;
                    try { Thread.sleep(200); }
                    catch (InterruptedException ignored) { }
                    AccessibilityNodeInfo fresh = null;
                    try {
                        fresh = root.findFocus(
                                AccessibilityNodeInfo.FOCUS_INPUT);
                    } catch (Exception ignored) { }
                    if (fresh == null) fresh = findEditable(root);
                    CharSequence after = fresh == null ? null : fresh.getText();
                    int al = after == null ? 0 : after.length();
                    boolean ok = al > bl;
                    Diag.event(ok ? "paste VERIFIED" : "paste NOT verified");
                    return ok;
                }
                default:
                    return false;
            }
        } catch (Exception e) {
            return false;
        }
    }

    // ---------- Tap by text ----------

    /** Screen pe "text" wale button/lafz pe tap karo. */
    public static boolean tapText(String text) {
        A11yService s = instance;
        if (s == null || text == null || text.isEmpty()) return false;
        try {
            AccessibilityNodeInfo root = s.getRootInActiveWindow();
            if (root == null) return false;
            List<AccessibilityNodeInfo> nodes =
                    root.findAccessibilityNodeInfosByText(text);
            for (AccessibilityNodeInfo n : nodes) {
                if (performClickOnNode(n)) return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
