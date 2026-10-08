package com.mujahid.myagent;

import android.accessibilityservice.AccessibilityService;
import android.os.Bundle;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.List;

/**
 * STEP 2 — Screen control (AccessibilityService).
 * Voice se: scroll, type, tap-by-text, home, back — KISI BHI APP ME.
 * User ko ek dafa Settings → Accessibility me "My Agent" ON karna parega.
 */
public class A11yService extends AccessibilityService {

    private static A11yService instance;

    /** Service zinda aur enabled hai? */
    public static boolean isEnabled() {
        return instance != null;
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
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null) {
                    try {
                        if (c.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                            return true;
                    } catch (Exception ignored) { }
                }
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

    @Override
    protected void onServiceConnected() {
        instance = this;
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

    @Override
    public void onDestroy() {
        if (instance == this) instance = null;
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
            return input.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b);
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
                AccessibilityNodeInfo c = n;
                while (c != null && !c.isClickable()) c = c.getParent();
                if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK))
                    return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
