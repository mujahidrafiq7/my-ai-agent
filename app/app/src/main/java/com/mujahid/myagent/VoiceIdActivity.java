package com.mujahid.myagent;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

/**
 * v57 "Voice ID" pehla qadam: apni awaz ka naqsha banao + test karo.
 * - Enroll: 3 baar "Main Mujahid hun" bolo → voiceprint save.
 * - Test: bolo kuch → match score dekho (90+ = Mujahid).
 * Score khula dikhta hai taake mil ke tune karein — koi jhooth nahi.
 */
public class VoiceIdActivity extends Activity {

    private TextView statusText;
    private TextView enrollState;
    private Button enrollBtn;
    private Button testBtn;
    private boolean busy = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int[] th = Themes.get(this);
        int BG = th[0], CARD = th[1], TXT = th[2], HINT = th[3];
        float d = getResources().getDisplayMetrics().density;
        int pad = (int) (16 * d);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        scroll.addView(root);
        setContentView(scroll);

        TextView title = new TextView(this);
        title.setText("Voice ID");
        title.setTextSize(20);
        title.setTextColor(TXT);
        title.setPadding(0, 0, 0, pad / 4);
        root.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Tumhari awaz ka naqsha — sirf is phone me rehta hai.");
        sub.setTextSize(13);
        sub.setTextColor(HINT);
        sub.setPadding(0, 0, 0, pad / 2);
        root.addView(sub);

        enrollState = new TextView(this);
        enrollState.setTextSize(15);
        enrollState.setPadding(0, 0, 0, pad / 2);
        root.addView(enrollState);
        refreshState();

        enrollBtn = mkButton("🎙️ Enroll — 3 baar bolo 'Main Mujahid hun'", CARD, TXT, pad);
        root.addView(enrollBtn);
        enrollBtn.setOnClickListener(v -> {
            if (busy) return;
            busy = true;
            setButtons(false);
            VoicePrint.enroll(this, listener());
        });

        testBtn = mkButton("🔍 Test — bolo kuch, score dekho", CARD, TXT, pad);
        root.addView(testBtn);
        testBtn.setOnClickListener(v -> {
            if (busy) return;
            busy = true;
            setButtons(false);
            VoicePrint.verify(this, listener());
        });

        Button resetBtn = mkButton("🗑️ Reset voiceprint", CARD, TXT, pad);
        root.addView(resetBtn);
        resetBtn.setOnClickListener(v -> {
            VoicePrint.clear(this);
            refreshState();
            statusText.setText("Voiceprint delete ho gaya.");
            Toast.makeText(this, "Voiceprint cleared.", Toast.LENGTH_SHORT).show();
        });

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setTextColor(TXT);
        statusText.setPadding(0, pad / 2, 0, 0);
        root.addView(statusText);

        TextView note = new TextView(this);
        note.setText("\nPehla qadam: score khula dikhega.\n90+ = Mujahid ✓ — neeche = unknown.\n"
                + "Shor/zukam se score hil sakta hai — mil ke tune karenge.");
        note.setTextSize(12);
        note.setTextColor(HINT);
        root.addView(note);
    }

    private VoicePrint.Listener listener() {
        return new VoicePrint.Listener() {
            @Override
            public void onStatus(String s) {
                statusText.setText(s);
            }

            @Override
            public void onDone(boolean ok, String msg) {
                statusText.setText(msg);
                busy = false;
                setButtons(true);
                refreshState();
            }
        };
    }

    private void refreshState() {
        boolean e = VoicePrint.isEnrolled(this);
        enrollState.setText(e ? "Status: Enrolled ✓" : "Status: Not enrolled");
        enrollState.setTextColor(e ? 0xFF50DC82 : 0xFFFF9632);
    }

    private void setButtons(boolean on) {
        enrollBtn.setEnabled(on);
        testBtn.setEnabled(on);
    }

    private Button mkButton(String s, int bg, int fg, int pad) {
        Button btn = new Button(this);
        btn.setText(s);
        btn.setTextColor(fg);
        btn.setBackgroundColor(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = pad / 2;
        btn.setLayoutParams(lp);
        return btn;
    }
}
