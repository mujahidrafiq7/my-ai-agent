package com.mujahid.myagent;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

/**
 * Text chat — voice se ALAG section.
 * Sirf likhna aur parhna. Koi mic nahi, koi awaz nahi, koi TTS quota nahi.
 * Iski history voice wali history se alag hai.
 */
public class ChatActivity extends Activity {

    private TextView statusText;
    private TextView chatLog;
    private ScrollView scrollView;
    private EditText inputText;
    private Button sendBtn;
    private boolean busy = false;

    // Voice wali history se ALAG — chat ka apna session
    private final List<ChatClient.Message> history = ChatClient.newHistory();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_chat);
        Themes.tint(this); // v51 "Rang": user ki pasand ka theme

        statusText = findViewById(R.id.chatStatus);
        chatLog = findViewById(R.id.chatLog);
        scrollView = (ScrollView) chatLog.getParent();
        inputText = findViewById(R.id.inputText);
        sendBtn = findViewById(R.id.sendBtn);

        sendBtn.setOnClickListener(v -> {
            if (busy) {
                toast("Let this reply finish first.");
                return;
            }
            String t = inputText.getText().toString().trim();
            if (t.isEmpty()) return;
            inputText.setText("");
            runChat(t);
        });
    }

    private void runChat(String userText) {
        List<String> geminiKeys = Keys.geminiKeys(this);
        String groqKey = Keys.groqKey(this);
        if (geminiKeys.isEmpty() && groqKey.isEmpty()) {
            setStatus("Add API keys in Settings first.");
            toast("Add keys in Settings.");
            return;
        }
        busy = true;
        // "Yaad rakho ke ..." → save karo, chat ko tang nahi karna
        String memFact = Memory.extractMemoryCommand(userText);
        if (memFact != null) {
            Memory.save(this, memFact);
            history.add(new ChatClient.Message("user", userText));
            history.add(new ChatClient.Message("model", "Remembered: " + memFact));
            final String fact = memFact;
            ui(() -> {
                addLog("🧠 Remembered: " + fact);
                setStatus("💬 Text chat — type and read only.");
            });
            busy = false;
            return;
        }
        history.add(new ChatClient.Message("user", userText));
        ui(() -> addLog("You: " + userText));

        // Phone command? (bina quota, phone me hi) — voice ki tarah yahan bhi
        PhoneTools.Result cmd = PhoneTools.tryHandle(ChatActivity.this, userText);
        if (cmd.handled) {
            history.add(new ChatClient.Message("model", cmd.reply));
            ui(() -> {
                addLog("Agent: " + cmd.reply);
                setStatus("💬 Text chat — type and read only.");
            });
            busy = false;
            return;
        }

        setStatus("Thinking...");

        new Thread(() -> {
            try {
                String memCtx = Memory.buildContext(ChatActivity.this);
                if (SettingsActivity.isAutoMemoryOn(ChatActivity.this))
                    memCtx += ChatClient.AUTO_MEMORY_INSTRUCTION;
                // SELF-DIAGNOSIS (v32): apne baare me puche to EXACT data do, tukka nahi
                if (Diag.isSelfQuestion(userText.toLowerCase(java.util.Locale.ROOT)))
                    memCtx += "\n[APNI JAANCH KA DATA — isi ko dekh ke EXACT wajah batao, "
                            + "andaza/tukka NA lagao. Is data me koi API key/password nahi hai, "
                            + "sirf ginti aur waqt hai — 'nahi dekh sakti' mat kaho, seedha jawab do. "
                            + "Ye lines user ko nazar nahi aatin:]\n"
                            + Diag.snapshot(ChatActivity.this) + "\n";
                String reply = ChatClient.chat(history, geminiKeys, groqKey,
                        s -> setStatus(s), false,
                        memCtx); // chat mode + yaadein
                reply = ChatClient.applyAutoMemory(ChatActivity.this, reply);
                final String cleanReply = reply;
                history.add(new ChatClient.Message("model", cleanReply));
                ui(() -> {
                    addLog("Agent: " + cleanReply);
                    setStatus("💬 Text chat — type and read only.");
                });
            } catch (Exception e) {
                final String msg = e.getMessage();
                ui(() -> {
                    addLog("❌ " + msg);
                    setStatus("Stopped: " + msg);
                });
            }
            busy = false;
        }).start();
    }

    // ---------- chhoti madadgar ----------

    private void setStatus(String s) {
        runOnUiThread(() -> statusText.setText(s));
    }

    private void ui(Runnable r) {
        runOnUiThread(r);
    }

    private void addLog(String line) {
        String cur = chatLog.getText().toString();
        chatLog.setText(cur.isEmpty() ? line : cur + "\n\n" + line);
        scrollView.post(() -> scrollView.fullScroll(ScrollView.FOCUS_DOWN));
    }

    private void toast(String s) {
        runOnUiThread(() ->
                Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}
