package com.mujahid.myagent;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/**
 * Stonic-style HOME screen — sirf UI.
 * Asal conversation VoiceService (foreground service) me chalti hai,
 * taake app background me ho ya screen lock ho, tab bhi baat jari rahe.
 * - Reactor tap = conversation ON/OFF (service start/stop)
 * - Status service se ata hai (screen khuli ho to)
 */
public class MainActivity extends Activity {

    private TextView statusText;
    private TextView sysVoiceState;
    private ReactorView reactorView;
    private BeamsView beamsView;
    private View middleZone;
    private Button screenShareBtn;

    private final VoiceService.StatusListener listener = this::onServiceStatus;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setContentView(R.layout.activity_main);
        Themes.tint(this); // v51 "Rang": user ki pasand ka theme

        statusText = findViewById(R.id.statusText);
        sysVoiceState = findViewById(R.id.sysVoiceState);
        reactorView = findViewById(R.id.reactorView);
        beamsView = findViewById(R.id.beamsView);
        middleZone = findViewById(R.id.middleZone);

        Memory.seedDefaults(this);

        askAllPermissions();

        // v43: Screen Share wapas ON — boss ka hukm (2026-10-07)
        screenShareBtn = findViewById(R.id.screenShareBtn);
        screenShareBtn.setOnClickListener(v -> toggleScreenShare());

        // Reactor tap = conversation ON/OFF
        reactorView.setOnClickListener(v -> toggleConversation());

        // Left pills
        findViewById(R.id.pillMemory).setOnClickListener(v -> showMemories());
        findViewById(R.id.pillChat).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
        findViewById(R.id.pillSoul).setOnClickListener(v -> showSoul());
        findViewById(R.id.pillSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        // Bottom nav
        findViewById(R.id.navHome).setOnClickListener(v -> { /* already home */ });
        findViewById(R.id.navChat).setOnClickListener(v ->
                startActivity(new Intent(this, ChatActivity.class)));
        findViewById(R.id.navVoice).setOnClickListener(v -> toggleConversation());
        findViewById(R.id.navMemory).setOnClickListener(v -> showMemories());
        findViewById(R.id.navSettings).setOnClickListener(v ->
                startActivity(new Intent(this, SettingsActivity.class)));

        setupBeams();
    }

    @Override
    protected void onResume() {
        super.onResume();
        VoiceService.setStatusListener(listener);
        // Lighting setting ke hisaab se neon border dikhao/chhupao
        View neon = findViewById(R.id.neonBorder);
        if (neon != null) neon.setVisibility(
                SettingsActivity.isLightingOn(this) ? View.VISIBLE : View.GONE);
        syncUi();
        syncShareBtn();
        // Voice se "screen share on karo" → auto permission dialog
        if (getIntent() != null && getIntent().getBooleanExtra(
                ScreenShare.EXTRA_AUTO_SHARE, false)) {
            getIntent().removeExtra(ScreenShare.EXTRA_AUTO_SHARE);
            if (!ScreenShare.isOn()) ScreenShare.requestOn(this);
        }
    }

    /** v43: Screen share toggle — ON hai to seedha OFF, warna permission dialog. */
    private void toggleScreenShare() {
        if (ScreenShare.isOn()) {
            ScreenShare.turnOff(this);
            toast("Screen share OFF kar diya.");
        } else {
            ScreenShare.requestOn(this);
        }
        syncShareBtn();
    }

    private void syncShareBtn() {
        if (screenShareBtn == null) return;
        boolean on = ScreenShare.isOn();
        screenShareBtn.setText(on ? "🖥 Screen Share: ON" : "🖥 Screen Share: OFF");
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == ScreenShare.REQ_CAPTURE) {
            // v43: system permission dialog ka jawab
            ScreenShare.onPermissionResult(this, resultCode, data);
            if (ScreenShare.isOn()) {
                toast("Screen share ON — ab 'screen pe kya hai' pucho!");
            } else {
                toast("Screen share ke liye permission chahiye thi.");
            }
            syncShareBtn();
        }
    }

    @Override
    protected void onPause() {
        VoiceService.setStatusListener(null);
        super.onPause();
    }

    /** App khulte hi SAB permissions ek saath — mic, camera, files, notifications. */
    private void askAllPermissions() {
        List<String> need = new ArrayList<>();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.RECORD_AUDIO);
        if (checkSelfPermission(Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.CAMERA);
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS)
                != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.READ_CONTACTS);
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.POST_NOTIFICATIONS);
        if (Build.VERSION.SDK_INT <= 32
                && checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.READ_EXTERNAL_STORAGE);
        // v40 Yaadein: location (mausam + "main kahan hun") — na mile to Phalia default
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED)
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        if (!need.isEmpty())
            requestPermissions(need.toArray(new String[0]), 100);
    }

    /** Service start/stop — wahi conversation toggle hai. */
    private void toggleConversation() {
        if (VoiceService.isRunning()) {
            startService(new Intent(this, VoiceService.class)
                    .setAction(VoiceService.ACTION_STOP));
        } else {
            Intent i = new Intent(this, VoiceService.class)
                    .setAction(VoiceService.ACTION_START);
            startForegroundService(i);
            toast("Conversation ON — keeps running even with screen off.");
        }
    }

    private void onServiceStatus(String s) {
        runOnUiThread(() -> {
            statusText.setText(s);
            syncUi();
        });
    }

    private void syncUi() {
        boolean active = VoiceService.isRunning();
        reactorView.setActive(active);
        sysVoiceState.setText(active ? "● Voice active" : "● Voice ready");
        if (!active && statusText != null
                && statusText.getText().toString().equals("listening")) {
            statusText.setText("ready");
        }
    }

    /** Beams: har pill ke right edge se reactor ke center tak. */
    private void setupBeams() {
        final int[] pillIds = {R.id.pillMemory, R.id.pillChat, R.id.pillSoul, R.id.pillSettings};
        final int[] beamColors = {0xFF3C8CFF, 0xFFFF9632, 0xFFAAB4BE, 0xFF50DC82};
        middleZone.getViewTreeObserver().addOnGlobalLayoutListener(
                new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override public void onGlobalLayout() {
                        middleZone.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        int[] locMid = new int[2];
                        middleZone.getLocationInWindow(locMid);
                        float[] sx = new float[4];
                        float[] sy = new float[4];
                        for (int i = 0; i < 4; i++) {
                            View p = findViewById(pillIds[i]);
                            int[] loc = new int[2];
                            p.getLocationInWindow(loc);
                            sx[i] = loc[0] - locMid[0] + p.getWidth();
                            sy[i] = loc[1] - locMid[1] + p.getHeight() / 2f;
                        }
                        int[] locR = new int[2];
                        reactorView.getLocationInWindow(locR);
                        float ex = locR[0] - locMid[0] + reactorView.getWidth() / 2f;
                        float ey = locR[1] - locMid[1] + reactorView.getHeight() / 2f;
                        beamsView.setBeams(sx, sy, beamColors, ex, ey);
                    }
                });
    }

    // ---------- 🧠 Yaadein ----------

    private void showMemories() {
        List<String> all = Memory.getAll(this);
        StringBuilder sb = new StringBuilder();
        if (all.isEmpty()) {
            sb.append("Nothing remembered yet — say \"remember that ...\"");
        } else {
            for (int i = 0; i < all.size(); i++) {
                sb.append(i + 1).append(". ").append(all.get(i)).append("\n\n");
            }
        }
        new AlertDialog.Builder(this)
                .setTitle("🧠 My memories (" + all.size() + ")")
                .setMessage(sb.toString().trim())
                .setPositiveButton("OK", null)
                .setNegativeButton("Forget all", (d, w) ->
                        new AlertDialog.Builder(this)
                                .setTitle("Sure?")
                                .setMessage("All memories will be erased.")
                                .setPositiveButton("Yes, forget", (d2, w2) -> {
                                    Memory.clear(this);
                                    toast("Forgot everything.");
                                })
                                .setNegativeButton("Cancel", null)
                                .show())
                .show();
    }

    // ---------- SOUL ----------

    private void showSoul() {
        new AlertDialog.Builder(this)
                .setTitle("SOUL — Who is Ayesha")
                .setMessage("Stubborn, caring, loyal.\n\n"
                        + "Listens to Boss, answers from the heart — "
                        + "but calls out wrong things too: "
                        + "\"Mujahid, you are wrong here.\"\n\n"
                        + "Understands every mood, every language, every feeling.")
                .setPositiveButton("OK", null)
                .show();
    }

    private void toast(String s) {
        runOnUiThread(() ->
                Toast.makeText(this, s, Toast.LENGTH_SHORT).show());
    }
}
