package com.thor.displaypowertest;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(196, 92, 255);
    private static final int VIOLET = Color.rgb(139, 74, 226);
    private static final int LAVENDER = Color.rgb(225, 195, 255);
    private static final int GREEN = Color.rgb(112, 255, 190);
    private static final int AMBER = Color.rgb(255, 211, 102);
    private static final int RED = Color.rgb(255, 112, 126);
    private static final int MUTED = Color.rgb(168, 185, 204);

    private SharedPreferences preferences;
    private ScheduledExecutorService telemetryWorker;
    private ImageView backgroundImage;
    private TextureView videoTexture;
    private MediaPlayer backgroundPlayer;
    private Surface videoSurface;
    private boolean videoPrepared;
    private boolean activityResumed;
    private boolean visualBottomOn = true;
    private boolean suppressToggle;
    private boolean commandInFlight;
    private int possibleLockSamples;

    private Switch fixToggle;
    private TextView stateText;
    private TextView stateDetail;
    private TextView littleValue;
    private TextView bigValue;
    private TextView primeValue;
    private TextView displayValue;
    private TextView repairValue;
    private TextView daemonValue;
    private TextView warningValue;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("state", MODE_PRIVATE);
        configureWindow();
        setContentView(buildUi());

        suppressToggle = true;
        fixToggle.setChecked(preferences.getBoolean("fix_enabled", true));
        suppressToggle = false;

        fixToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressToggle) setFixEnabled(checked);
        });
        startService(new Intent(this, AutoService.class));
    }

    private void configureWindow() {
        Window window = getWindow();
        window.setStatusBarColor(Color.TRANSPARENT);
        window.setNavigationBarColor(Color.BLACK);
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        // Deliberately no FLAG_KEEP_SCREEN_ON: normal Android timeout is respected.
    }

    private View buildUi() {
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.BLACK);
        backgroundImage = new ImageView(this);
        backgroundImage.setImageResource(resource("drawable", "jesty_thor_background"));
        backgroundImage.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(backgroundImage, match());

        videoTexture = new TextureView(this);
        videoTexture.setOpaque(false);
        videoTexture.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override public void onSurfaceTextureAvailable(SurfaceTexture texture, int width, int height) {
                startBackgroundVideo(texture);
            }
            @Override public void onSurfaceTextureSizeChanged(SurfaceTexture texture, int width, int height) {}
            @Override public boolean onSurfaceTextureDestroyed(SurfaceTexture texture) {
                releaseBackgroundVideo();
                return true;
            }
            @Override public void onSurfaceTextureUpdated(SurfaceTexture texture) {}
        });
        root.addView(videoTexture, match());

        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xFC080510, 0xF012091B, 0xB3211031, 0x22311242, 0x00000000}));
        root.addView(shade, match());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        int panelWidth = Math.round(getResources().getDisplayMetrics().widthPixels * 0.56f);
        root.addView(scroll, new FrameLayout.LayoutParams(panelWidth,
                ViewGroup.LayoutParams.MATCH_PARENT, Gravity.START));

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(14), dp(24), dp(14));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeader());
        TextView subtitle = text("True bottom-display control for AYN Thor", 12f, MUTED, false);
        subtitle.setPadding(dp(2), 0, 0, dp(8));
        content.addView(subtitle);

        stateText = text("CONNECTING\u2026", 20f, AMBER, true);
        stateText.setLetterSpacing(0.06f);
        content.addView(stateText);
        stateDetail = text("Starting local daemon", 12f, MUTED, false);
        stateDetail.setPadding(0, dp(2), 0, dp(9));
        content.addView(stateDetail);

        LinearLayout controls = panel();
        fixToggle = makeSwitch("True Bottom Display Fix", Color.WHITE, 17f);
        controls.addView(fixToggle, new LinearLayout.LayoutParams(-1, dp(46)));
        content.addView(controls);

        LinearLayout row1 = row();
        littleValue = addCard(row1, "LITTLE", "\u2014");
        bigValue = addCard(row1, "BIG", "\u2014");
        content.addView(row1);
        LinearLayout row2 = row();
        primeValue = addCard(row2, "PRIME", "\u2014");
        displayValue = addCard(row2, "DISPLAY", "\u2014");
        content.addView(row2);

        LinearLayout detailPanel = panel();
        repairValue = text("LAST WAKE REPAIR  \u2022  \u2014", 11f, Color.WHITE, true);
        detailPanel.addView(repairValue);
        daemonValue = text("DAEMON  \u2022  \u2014", 11f, MUTED, false);
        daemonValue.setPadding(0, dp(5), 0, 0);
        detailPanel.addView(daemonValue);
        warningValue = text("", 11f, RED, true);
        warningValue.setPadding(0, dp(5), 0, 0);
        detailPanel.addView(warningValue);
        content.addView(detailPanel);

        Button verify = new Button(this);
        verify.setText("VERIFY BOTTOM SCANOUT");
        verify.setTextColor(Color.WHITE);
        verify.setTextSize(12f);
        GradientDrawable buttonBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xDD32164A, 0xDD67235D});
        buttonBackground.setCornerRadius(dp(12));
        buttonBackground.setStroke(dp(1), PURPLE);
        verify.setBackground(buttonBackground);
        verify.setOnClickListener(v -> verifyDrm());
        LinearLayout.LayoutParams verifyParams = new LinearLayout.LayoutParams(-1, dp(43));
        verifyParams.setMargins(0, 0, 0, dp(8));
        content.addView(verify, verifyParams);
        return root;
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        ImageView wordmark = new ImageView(this);
        wordmark.setImageResource(resource("drawable", "jesty_thor_wordmark"));
        wordmark.setScaleType(ImageView.ScaleType.FIT_START);
        wordmark.setAdjustViewBounds(true);
        wordmark.setContentDescription("Jesty");
        LinearLayout.LayoutParams logoParams = new LinearLayout.LayoutParams(dp(126), dp(62));
        logoParams.setMargins(0, 0, dp(12), 0);
        header.addView(wordmark, logoParams);

        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        TextView thor = text("THOR", 18f, AMBER, true);
        thor.setLetterSpacing(0.11f);
        name.addView(thor);
        TextView fix = text("FIX", 18f, PURPLE, true);
        fix.setLetterSpacing(0.16f);
        name.addView(fix);
        header.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));

        header.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(64)));
        return header;
    }

    private void setFixEnabled(final boolean requested) {
        if (commandInFlight) return;
        final boolean previous = preferences.getBoolean("fix_enabled", true);
        commandInFlight = true;
        fixToggle.setEnabled(false);
        stateText.setText("APPLYING\u2026");
        stateText.setTextColor(AMBER);
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                SocketClient.request(requested ? 'E' : 'N', 1500);
                preferences.edit().putBoolean("fix_enabled", requested).commit();
                runOnUiThread(() -> finishToggle(requested, null));
            } catch (Throwable error) {
                runOnUiThread(() -> finishToggle(previous, error.getMessage()));
            }
        });
    }

    private void finishToggle(boolean actual, String error) {
        suppressToggle = true;
        fixToggle.setChecked(actual);
        suppressToggle = false;
        fixToggle.setEnabled(true);
        commandInFlight = false;
        if (error != null) {
            stateText.setText("DAEMON UNAVAILABLE");
            stateText.setTextColor(RED);
            stateDetail.setText(error);
            Toast.makeText(this, "Command not confirmed; setting restored", Toast.LENGTH_LONG).show();
        } else {
            stateText.setText(actual ? "FIX ACTIVE" : "NATIVE THOR MODE");
            stateText.setTextColor(actual ? GREEN : LAVENDER);
        }
    }

    private void verifyDrm() {
        Toast.makeText(this, "Reading DRM state once\u2026", Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                final Map<String, String> values = parse(SocketClient.request('V', 1800));
                final String message = "CRTC 181: " + value(values, "crtc181")
                        + "   \u2022   CRTC 243: " + value(values, "crtc243");
                runOnUiThread(() -> Toast.makeText(this, message, Toast.LENGTH_LONG).show());
            } catch (Throwable error) {
                runOnUiThread(() -> Toast.makeText(this,
                        "DRM verification failed: " + error.getMessage(), Toast.LENGTH_LONG).show());
            }
        }, "drm-verify").start();
    }

    private void pollTelemetry() {
        try {
            final Map<String, String> values = parse(SocketClient.request('Q', 900));
            runOnUiThread(() -> render(values));
        } catch (Throwable error) {
            runOnUiThread(() -> {
                if (!commandInFlight) {
                    stateText.setText("DAEMON UNAVAILABLE");
                    stateText.setTextColor(RED);
                    stateDetail.setText("No telemetry response");
                }
                possibleLockSamples = 0;
                warningValue.setText("");
            });
        }
    }

    private void render(Map<String, String> values) {
        if (commandInFlight) return;
        boolean enabled = "1".equals(values.get("fix"));
        suppressToggle = true;
        fixToggle.setChecked(enabled);
        suppressToggle = false;
        stateText.setText(enabled ? "FIX ACTIVE" : "NATIVE THOR MODE");
        stateText.setTextColor(enabled ? GREEN : LAVENDER);
        String mode = modeName(values.get("mode"));
        stateDetail.setText(mode + "  \u2022  power=" + value(values, "power"));

        long littleCur = number(values, "little_cur"), littleMax = number(values, "little_max");
        long bigCur = number(values, "big_cur"), bigMax = number(values, "big_max");
        long primeCur = number(values, "prime_cur"), primeMax = number(values, "prime_max");
        int utilization = (int) number(values, "cpu_pct");
        littleValue.setText(clock(littleCur, littleMax));
        bigValue.setText(clock(bigCur, bigMax));
        primeValue.setText(clock(primeCur, primeMax));
        displayValue.setText(mode + "\npower " + value(values, "power"));
        setBottomVisual("1".equals(values.get("power")));

        repairValue.setText("LAST WAKE REPAIR  \u2022  #" + value(values, "wake_id")
                + "  " + value(values, "repair_result"));
        daemonValue.setText("DAEMON  \u2022  up " + duration(number(values, "uptime_ms"))
                + "  \u2022  CPU " + utilization + "%  \u2022  " + value(values, "action"));

        boolean suspicious = utilization >= 0 && utilization < 40
                && ratio(littleCur, littleMax) >= 0.95
                && ratio(bigCur, bigMax) >= 0.95;
        possibleLockSamples = suspicious ? possibleLockSamples + 1 : 0;
        warningValue.setText(possibleLockSamples >= 10 ? "\u26A0 POSSIBLE CLOCK LOCK" : "");
    }

    private void startTelemetry() {
        if (telemetryWorker != null) return;
        telemetryWorker = Executors.newSingleThreadScheduledExecutor();
        telemetryWorker.scheduleAtFixedRate(this::pollTelemetry, 1L, 1L, TimeUnit.SECONDS);
    }

    private void stopTelemetry() {
        if (telemetryWorker != null) telemetryWorker.shutdownNow();
        telemetryWorker = null;
        possibleLockSamples = 0;
    }

    private void startBackgroundVideo(SurfaceTexture texture) {
        releaseBackgroundVideo();
        try {
            AssetFileDescriptor source = getResources().openRawResourceFd(
                    resource("raw", "jesty_thor_background_loop"));
            MediaPlayer player = new MediaPlayer();
            player.setDataSource(source.getFileDescriptor(), source.getStartOffset(), source.getLength());
            source.close();
            videoSurface = new Surface(texture);
            player.setSurface(videoSurface);
            player.setLooping(true);
            player.setVolume(0f, 0f);
            player.setOnPreparedListener(prepared -> {
                videoPrepared = true;
                if (activityResumed && visualBottomOn) prepared.start();
            });
            player.setOnErrorListener((failed, what, extra) -> {
                videoTexture.setVisibility(View.GONE);
                releaseBackgroundVideo();
                return true;
            });
            backgroundPlayer = player;
            player.prepareAsync();
        } catch (Throwable ignored) {
            videoTexture.setVisibility(View.GONE);
            releaseBackgroundVideo();
        }
    }

    private void resumeBackgroundVideo() {
        if (!visualBottomOn) {
            videoTexture.setVisibility(View.GONE);
            return;
        }
        videoTexture.setVisibility(View.VISIBLE);
        if (backgroundPlayer == null && videoTexture.isAvailable()) {
            startBackgroundVideo(videoTexture.getSurfaceTexture());
        } else if (activityResumed && videoPrepared && !backgroundPlayer.isPlaying()) {
            backgroundPlayer.start();
        }
    }

    private void setBottomVisual(boolean bottomOn) {
        if (visualBottomOn == bottomOn) return;
        visualBottomOn = bottomOn;
        backgroundImage.setImageResource(resource("drawable",
                bottomOn ? "jesty_thor_background" : "jesty_thor_background_off"));
        if (bottomOn) {
            resumeBackgroundVideo();
        } else {
            videoTexture.setVisibility(View.GONE);
            if (backgroundPlayer != null && backgroundPlayer.isPlaying()) backgroundPlayer.pause();
        }
    }

    private void releaseBackgroundVideo() {
        videoPrepared = false;
        if (backgroundPlayer != null) {
            try { backgroundPlayer.stop(); } catch (Throwable ignored) {}
            backgroundPlayer.release();
            backgroundPlayer = null;
        }
        if (videoSurface != null) { videoSurface.release(); videoSurface = null; }
    }

    @Override protected void onResume() {
        super.onResume();
        activityResumed = true;
        startTelemetry();
        resumeBackgroundVideo();
    }

    @Override protected void onPause() {
        activityResumed = false;
        stopTelemetry();
        if (backgroundPlayer != null && backgroundPlayer.isPlaying()) backgroundPlayer.pause();
        super.onPause();
    }

    @Override protected void onDestroy() {
        stopTelemetry();
        releaseBackgroundVideo();
        super.onDestroy();
    }

    private LinearLayout panel() {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(14), dp(6), dp(14), dp(6));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xC7231431);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0x66C45CFF);
        panel.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(2f);
        return row;
    }

    private TextView addCard(LinearLayout row, String label, String initial) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(13), dp(8), dp(13), dp(8));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xC31B1028);
        background.setCornerRadius(dp(13));
        background.setStroke(dp(1), 0x668B4AE2);
        card.setBackground(background);
        TextView heading = text(label, 10f, MUTED, true);
        heading.setLetterSpacing(0.09f);
        card.addView(heading);
        TextView value = text(initial, 17f, Color.WHITE, true);
        value.setPadding(0, dp(3), 0, 0);
        card.addView(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(78), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        row.addView(card, params);
        return value;
    }

    private Switch makeSwitch(String label, int color, float size) {
        Switch control = new Switch(this);
        control.setText(label);
        control.setTextColor(color);
        control.setTextSize(size);
        control.setTypeface(Typeface.DEFAULT_BOLD);
        int[][] states = new int[][] { new int[] { android.R.attr.state_checked }, new int[] {} };
        control.setThumbTintList(new ColorStateList(states,
                new int[] { AMBER, Color.rgb(128, 105, 146) }));
        control.setTrackTintList(new ColorStateList(states,
                new int[] { PURPLE, Color.rgb(67, 50, 78) }));
        return control;
    }

    private TextView text(String value, float size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private FrameLayout.LayoutParams match() { return new FrameLayout.LayoutParams(-1, -1); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private int resource(String type, String name) {
        return getResources().getIdentifier(name, type, getPackageName());
    }

    private static Map<String, String> parse(String response) {
        Map<String, String> values = new HashMap<>();
        for (String part : response.split(";")) {
            int split = part.indexOf('=');
            if (split > 0) values.put(part.substring(0, split), part.substring(split + 1));
        }
        return values;
    }

    private static String value(Map<String, String> values, String key) {
        String value = values.get(key);
        return value == null || value.length() == 0 ? "\u2014" : value;
    }

    private static long number(Map<String, String> values, String key) {
        try { return Long.parseLong(values.get(key)); } catch (Throwable ignored) { return -1L; }
    }

    private static String clock(long current, long maximum) {
        if (current <= 0 || maximum <= 0) return "\u2014";
        return String.format(Locale.US, "%.2f / %.2f GHz", current / 1_000_000d, maximum / 1_000_000d);
    }

    private static double ratio(long current, long maximum) {
        return current > 0 && maximum > 0 ? (double) current / maximum : 0d;
    }

    private static String duration(long milliseconds) {
        if (milliseconds < 0) return "\u2014";
        long seconds = milliseconds / 1000L;
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private static String modeName(String mode) {
        if ("1".equals(mode)) return "TOP";
        if ("0".equals(mode)) return "BOTH";
        if ("2".equals(mode)) return "BOTTOM";
        return "MODE ?";
    }
}
