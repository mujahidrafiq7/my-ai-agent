package com.mujahid.myagent;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * v52 "History": purani baatein — din ke hisaab se. Sirf parhna, Clear ka button.
 * (Nayi baat Chat tab me hoti hai.)
 */
public class HistoryActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        int[] th = Themes.get(this);
        int BG = th[0], CARD = th[1], TXT = th[2], HINT = th[3], ACCENT = th[4];
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
        title.setText("History");
        title.setTextSize(20);
        title.setTextColor(TXT);
        title.setPadding(0, 0, 0, pad / 2);
        root.addView(title);

        Button clear = new Button(this);
        clear.setText("Clear History");
        clear.setTextColor(TXT);
        clear.setBackgroundColor(CARD);
        root.addView(clear);

        TextView log = new TextView(this);
        log.setTextColor(TXT);
        log.setTextSize(14);
        log.setPadding(0, pad / 2, 0, 0);
        root.addView(log);

        List<ChatHistory.Entry> entries = ChatHistory.load(this);
        if (entries.isEmpty()) {
            log.setText("Abhi koi baat save nahi hui.\nChat me baat karo — yahan yaad rahegi.");
            log.setTextColor(HINT);
        } else {
            StringBuilder sb = new StringBuilder();
            SimpleDateFormat dayFmt = new SimpleDateFormat("d MMM yyyy", Locale.US);
            String lastDay = "";
            for (ChatHistory.Entry e : entries) {
                String day = e.time > 0
                        ? dayFmt.format(new Date(e.time)) : "";
                if (!day.isEmpty() && !day.equals(lastDay)) {
                    sb.append("\n— ").append(day).append(" —\n\n");
                    lastDay = day;
                }
                sb.append(e.role.equals("user") ? "You: " : "Agent: ")
                  .append(e.text).append("\n\n");
            }
            log.setText(sb.toString().trim());
        }

        clear.setOnClickListener(v -> {
            ChatHistory.clear(this);
            log.setText("History saaf kar di.");
            log.setTextColor(HINT);
            Toast.makeText(this, "History cleared.", Toast.LENGTH_SHORT).show();
        });
    }
}
