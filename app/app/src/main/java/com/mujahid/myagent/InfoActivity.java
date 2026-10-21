package com.mujahid.myagent;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.util.List;

/**
 * v86: SOUL, My Memories aur Aaj Ka Brief — ab chhote dialog NAHI,
 * PickerActivity jaisi hi design ke POORE pages (glass boxes, Back pill).
 * kind: "soul" | "memories" | "brief".
 */
public class InfoActivity extends Activity {

    private String kind = "soul";
    private int bg, card, txt, hintC, accent;
    private LinearLayout content;
    private LinearLayout reminderBox; // v89

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        String k = getIntent().getStringExtra("kind");
        if (k != null) kind = k;

        int[] pal = Themes.get(this); // {bg, card, text, hint, accent}
        bg = pal[0]; card = pal[1]; txt = pal[2]; hintC = pal[3]; accent = pal[4];

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(bg);
        root.setPadding(dp(20), dp(20), dp(20), dp(28));

        // ---- Back pill (glass) ----
        TextView back = new TextView(this);
        back.setText("Back");
        back.setTextColor(txt);
        back.setTextSize(15);
        back.setTypeface(null, android.graphics.Typeface.BOLD);
        back.setLetterSpacing(0.08f);
        back.setPadding(dp(26), dp(11), dp(26), dp(11));
        back.setBackground(glassBox(30));
        back.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.bottomMargin = dp(18);
        root.addView(back, bp);

        TextView title = new TextView(this);
        title.setText(title());
        title.setTextColor(txt);
        title.setTextSize(26);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        TextView hint = new TextView(this);
        hint.setText(hint());
        hint.setTextColor(hintC);
        hint.setTextSize(14);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hp.topMargin = dp(6);
        hp.bottomMargin = dp(18);
        root.addView(hint, hp);

        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        root.addView(content);

        if (kind.equals("memories")) buildMemories();
        else if (kind.equals("brief")) buildBrief();
        else buildSoul();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(bg);
        scroll.addView(root);
        setContentView(scroll);
    }

    // ---------- SOUL ----------

    private void buildSoul() {
        addCard("Stubborn, caring, loyal.");
        addCard("Listens to Boss, answers from the heart — but calls out "
                + "wrong things too: \"Mujahid, you are wrong here.\"");
        addCard("Understands every mood, every language, every feeling.");
    }

    // ---------- MEMORIES ----------

    private void buildMemories() {
        content.removeAllViews();
        List<String> all = Memory.getAll(this);
        if (all.isEmpty()) {
            addCard("Nothing remembered yet — say \"remember that ...\"");
            return;
        }
        for (int i = 0; i < all.size(); i++) {
            addCard((i + 1) + ".  " + all.get(i));
        }
        // Forget all — do-tap confirm (dialog nahi)
        final TextView forget = new TextView(this);
        forget.setText("Forget All");
        forget.setTextColor(Color.rgb(255, 120, 120));
        forget.setTextSize(15);
        forget.setTypeface(null, android.graphics.Typeface.BOLD);
        forget.setGravity(Gravity.CENTER);
        forget.setPadding(dp(16), dp(14), dp(16), dp(14));
        forget.setBackground(glassBox(16));
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        fp.topMargin = dp(6);
        content.addView(forget, fp);
        final boolean[] armed = {false};
        forget.setOnClickListener(v -> {
            if (!armed[0]) {
                armed[0] = true;
                forget.setText("Sure? Tap again to forget everything");
            } else {
                Memory.clear(this);
                Toast.makeText(this, "Forgot everything.",
                        Toast.LENGTH_SHORT).show();
                buildMemories();
            }
        });
    }

    // ---------- BRIEF ----------

    private void buildBrief() {
        List<String> tasks = TaskStore.list(this);
        StringBuilder sb = new StringBuilder();
        sb.append("Pending tasks:\n");
        if (tasks.isEmpty()) {
            sb.append("• No pending tasks.");
        } else {
            for (String t : tasks) sb.append("• ").append(t).append("\n");
        }
        addCard(sb.toString().trim());

        // v89: Reminders — har reminder ke sath Delete button
        reminderBox = new LinearLayout(this);
        reminderBox.setOrientation(LinearLayout.VERTICAL);
        content.addView(reminderBox);
        fillReminders();

        String turns = LiveContext.recentTurnsBlock(this);
        if (turns != null && !turns.isEmpty()) {
            int nl = turns.indexOf('\n');
            if (nl > 0) turns = turns.substring(nl + 1);
            if (turns.length() > 500)
                turns = turns.substring(turns.length() - 500);
            addCard("Recent conversation:\n" + turns.trim());
        }
    }

    /** v89: pending reminders ki list — Delete tap = alarm cancel + remove. */
    private void fillReminders() {
        if (reminderBox == null) return;
        reminderBox.removeAllViews();
        List<JSONObject> es = Reminder.entries(this);
        if (es.isEmpty()) return;
        TextView head = new TextView(this);
        head.setText("Reminders");
        head.setTextColor(txt);
        head.setTextSize(18);
        head.setTypeface(null, android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams hh = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hh.topMargin = dp(4);
        hh.bottomMargin = dp(10);
        reminderBox.addView(head, hh);
        for (final JSONObject o : es) {
            final long id = o.optLong("id", -1);
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(18), dp(12), dp(10), dp(12));
            row.setBackground(glassBox(18));
            TextView t = new TextView(this);
            t.setText(Reminder.timeText(o.optLong("at", 0)) + " — "
                    + o.optString("msg", ""));
            t.setTextColor(txt);
            t.setTextSize(15);
            row.addView(t, new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            TextView del = new TextView(this);
            del.setText("Delete");
            del.setTextColor(Color.rgb(255, 120, 120));
            del.setTextSize(15);
            del.setTypeface(null, android.graphics.Typeface.BOLD);
            del.setPadding(dp(14), dp(8), dp(14), dp(8));
            del.setOnClickListener(v -> {
                Reminder.deleteById(this, id);
                Toast.makeText(this, "Reminder deleted",
                        Toast.LENGTH_SHORT).show();
                fillReminders();
            });
            row.addView(del);
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rp.bottomMargin = dp(12);
            reminderBox.addView(row, rp);
        }
    }

    // ---------- helpers ----------

    private void addCard(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(txt);
        t.setTextSize(16);
        t.setLineSpacing(0, 1.25f);
        t.setPadding(dp(18), dp(16), dp(18), dp(16));
        t.setBackground(glassBox(18));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        content.addView(t, lp);
    }

    private GradientDrawable glassBox(int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(Color.argb(115,
                Color.red(card), Color.green(card), Color.blue(card)));
        g.setStroke(dp(1), Color.argb(70, 255, 255, 255));
        g.setCornerRadius(dp(radiusDp));
        return g;
    }

    private String title() {
        switch (kind) {
            case "memories": return "My Memories";
            case "brief": return "Today's Brief";
            default: return "SOUL";
        }
    }

    private String hint() {
        switch (kind) {
            case "memories": return "What Ayesha remembers about you.";
            case "brief": return LiveContext.dateTimeLine();
            default: return "Who is Ayesha.";
        }
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
