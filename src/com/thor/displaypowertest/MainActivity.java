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
import android.net.Uri;
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
    private TextView verifyResult;

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

        LinearLayout openSourceBadge = buildOpenSourceBadge();
        FrameLayout.LayoutParams badgeParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(50), Gravity.TOP | Gravity.END);
        badgeParams.setMargins(0, dp(16), dp(18), 0);
        root.addView(openSourceBadge, badgeParams);

        LinearLayout topActions = buildTopActions();
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42), Gravity.BOTTOM | Gravity.END);
        actionParams.setMargins(0, 0, dp(18), dp(16));
        root.addView(topActions, actionParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(10), dp(24), dp(10));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeader());
        TextView subtitle = text("True bottom-display control for AYN Thor", 13f, MUTED, false);
        subtitle.setPadding(dp(2), 0, 0, dp(8));
        content.addView(subtitle);

        stateText = text("CONNECTING\u2026", 19f, AMBER, true);
        stateText.setLetterSpacing(0.06f);
        content.addView(stateText);
        stateDetail = text("", 12f, MUTED, false);
        stateDetail.setPadding(0, dp(2), 0, dp(5));
        stateDetail.setVisibility(View.GONE);
        content.addView(stateDetail);

        LinearLayout controls = panel();
        fixToggle = makeSwitch("True Bottom Display Fix", Color.WHITE, 17f);
        controls.addView(fixToggle, new LinearLayout.LayoutParams(-1, dp(46)));

        View divider = new View(this);
        divider.setBackgroundColor(0x338B4AE2);
        controls.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        LinearLayout verifyRow = new LinearLayout(this);
        verifyRow.setOrientation(LinearLayout.HORIZONTAL);
        verifyRow.setGravity(Gravity.CENTER_VERTICAL);
        verifyRow.setPadding(0, dp(6), 0, dp(3));

        LinearLayout verifyCopy = new LinearLayout(this);
        verifyCopy.setOrientation(LinearLayout.VERTICAL);
        TextView verifyTitle = text("Bottom screen & CPU check", 12f, Color.WHITE, true);
        verifyCopy.addView(verifyTitle);
        TextView verifyHelp = text("Checks true hardware-off and LITTLE/BIG clock pinning", 10f, MUTED, false);
        verifyHelp.setPadding(0, dp(1), dp(8), 0);
        verifyCopy.addView(verifyHelp);
        verifyRow.addView(verifyCopy, new LinearLayout.LayoutParams(0, -2, 1f));

        Button verify = new Button(this);
        verify.setText("CHECK NOW");
        verify.setTextColor(Color.WHITE);
        verify.setTextSize(11f);
        verify.setTypeface(Typeface.DEFAULT_BOLD);
        verify.setMinHeight(0);
        verify.setMinWidth(0);
        GradientDrawable buttonBackground = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xDD32164A, 0xDD67235D});
        buttonBackground.setCornerRadius(dp(10));
        buttonBackground.setStroke(dp(1), PURPLE);
        verify.setBackground(buttonBackground);
        verify.setOnClickListener(v -> verifyDrm());
        verifyRow.addView(verify, new LinearLayout.LayoutParams(dp(104), dp(36)));
        controls.addView(verifyRow);

        verifyResult = text("", 10f, MUTED, true);
        controls.addView(verifyResult);
        content.addView(controls);

        LinearLayout cpuPanel = panel();
        TextView cpuHeading = text("CPU SPEEDS", 11f, Color.WHITE, true);
        cpuHeading.setLetterSpacing(0.09f);
        cpuPanel.addView(cpuHeading);
        LinearLayout cpuRow = row();
        littleValue = addMetric(cpuRow, "LITTLE", "\u2014");
        bigValue = addMetric(cpuRow, "BIG", "\u2014");
        primeValue = addMetric(cpuRow, "PRIME", "\u2014");
        cpuPanel.addView(cpuRow, new LinearLayout.LayoutParams(-1, dp(46)));
        warningValue = text("", 10f, RED, true);
        warningValue.setPadding(0, dp(2), 0, 0);
        cpuPanel.addView(warningValue);
        content.addView(cpuPanel);

        LinearLayout detailPanel = panel();
        TextView displayHeading = text("DISPLAY & SERVICE", 10f, MUTED, true);
        displayHeading.setLetterSpacing(0.09f);
        detailPanel.addView(displayHeading);
        LinearLayout displayRow = row();
        displayValue = text("\u2014", 13f, Color.WHITE, true);
        displayValue.setGravity(Gravity.CENTER_VERTICAL);
        displayRow.addView(displayValue, new LinearLayout.LayoutParams(0, dp(27), 1f));
        repairValue = text("LAST WAKE  \u2022  \u2014", 10f, Color.WHITE, true);
        repairValue.setGravity(Gravity.CENTER_VERTICAL | Gravity.END);
        displayRow.addView(repairValue, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(27)));
        detailPanel.addView(displayRow);
        daemonValue = text("DAEMON  \u2022  \u2014", 10f, MUTED, false);
        daemonValue.setPadding(0, dp(1), 0, 0);
        detailPanel.addView(daemonValue);
        content.addView(detailPanel);
        return root;
    }

    private LinearLayout buildTopActions() {
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);

        TextView support = topAction("\u2615  SUPPORT");
        support.setOnClickListener(v -> openExternal("https://buymeacoffee.com/jesty"));
        actions.addView(support);

        TextView github = topAction("\u2605  GITHUB");
        github.setOnClickListener(v -> openExternal("https://github.com/JestyLabs/Jesty-Thor-Fix"));
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38));
        githubParams.setMargins(dp(8), 0, 0, 0);
        actions.addView(github, githubParams);
        return actions;
    }

    private TextView topAction(String label) {
        TextView action = text(label, 10f, AMBER, true);
        action.setGravity(Gravity.CENTER);
        action.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable bubble = new GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xE0241338, 0xE0441B55});
        bubble.setCornerRadius(dp(21));
        bubble.setStroke(dp(1), 0xCCC45CFF);
        action.setBackground(bubble);
        action.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38)));
        return action;
    }

    private LinearLayout buildOpenSourceBadge() {
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(16), dp(5), dp(16), dp(5));
        TextView title = text("JESTY APPS ARE FREE & OPEN SOURCE", 9f, AMBER, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.05f);
        badge.addView(title);
        TextView copy = text("Support device testing or star the project.", 8f,
                Color.rgb(205, 196, 218), false);
        copy.setGravity(Gravity.CENTER);
        badge.addView(copy);
        GradientDrawable bubble = new GradientDrawable();
        bubble.setColor(0xB5100B19);
        bubble.setCornerRadius(dp(25));
        bubble.setStroke(dp(1), 0x668B4AE2);
        badge.setBackground(bubble);
        return badge;
    }

    private void openExternal(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Throwable error) {
            Toast.makeText(this, "No browser is available", Toast.LENGTH_SHORT).show();
        }
    }

    private View buildHeader() {
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER);

        ImageView lockup = new ImageView(this);
        lockup.setImageResource(resource("drawable", "jesty_thor_header_lockup"));
        lockup.setScaleType(ImageView.ScaleType.FIT_CENTER);
        lockup.setAdjustViewBounds(true);
        lockup.setContentDescription("Jesty Thor Fix");
        header.addView(lockup, new LinearLayout.LayoutParams(dp(242), dp(78)));

        header.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(80)));
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
            stateDetail.setVisibility(View.VISIBLE);
            Toast.makeText(this, "Command not confirmed; setting restored", Toast.LENGTH_LONG).show();
        } else {
            stateText.setText(actual ? "FIX ACTIVE" : "NATIVE THOR MODE");
            stateText.setTextColor(actual ? AMBER : Color.WHITE);
            stateDetail.setVisibility(View.GONE);
        }
    }

    private void verifyDrm() {
        verifyResult.setTextColor(MUTED);
        verifyResult.setText("Checking lower-screen hardware and CPU clocks\u2026");
        new Thread(() -> {
            try {
                final Map<String, String> values = parse(SocketClient.request('V', 1800));
                final Map<String, String> telemetry = parse(SocketClient.request('Q', 1800));
                final boolean topOn = "1".equals(values.get("crtc181"));
                final boolean bottomOff = "0".equals(values.get("crtc243"));
                final long littleCur = number(telemetry, "little_cur");
                final long littleMax = number(telemetry, "little_max");
                final long bigCur = number(telemetry, "big_cur");
                final long bigMax = number(telemetry, "big_max");
                final boolean coresPinned = ratio(littleCur, littleMax) >= 0.95
                        && ratio(bigCur, bigMax) >= 0.95;
                final String friendly;
                if (topOn && bottomOff) {
                    friendly = "Bottom hardware fully off \u2713"
                            + (coresPinned ? " \u2022 CPU clocks currently high" : " \u2022 CPU clocks released");
                } else if (coresPinned) {
                    friendly = "STOCK BUG CONFIRMED \u2022 LITTLE/BIG PINNED AT MAX";
                } else {
                    friendly = "Bottom hardware active \u2022 LITTLE/BIG can stay pinned";
                }
                final String detail = "Top hardware " + (topOn ? "on" : "off")
                        + "  \u2022  Bottom hardware " + (bottomOff ? "off" : "on")
                        + "  \u2022  LITTLE " + clock(littleCur, littleMax)
                        + "  \u2022  BIG " + clock(bigCur, bigMax);
                runOnUiThread(() -> {
                    verifyResult.setTextColor(topOn && bottomOff ? AMBER : RED);
                    verifyResult.setText(friendly);
                    Toast.makeText(this, detail, Toast.LENGTH_LONG).show();
                });
            } catch (Throwable error) {
                runOnUiThread(() -> {
                    verifyResult.setTextColor(RED);
                    verifyResult.setText("Could not check the bottom screen");
                    Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
                });
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
                    stateDetail.setVisibility(View.VISIBLE);
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
        stateText.setTextColor(enabled ? AMBER : Color.WHITE);
        boolean bottomPowered = "1".equals(values.get("power"));
        stateDetail.setVisibility(View.GONE);

        long littleCur = number(values, "little_cur"), littleMax = number(values, "little_max");
        long bigCur = number(values, "big_cur"), bigMax = number(values, "big_max");
        long primeCur = number(values, "prime_cur"), primeMax = number(values, "prime_max");
        int utilization = (int) number(values, "cpu_pct");
        littleValue.setText(clock(littleCur, littleMax));
        bigValue.setText(clock(bigCur, bigMax));
        primeValue.setText(clock(primeCur, primeMax));
        displayValue.setText(displayDescription(values.get("mode"), bottomPowered));
        setBottomVisual(bottomPowered);

        repairValue.setText("LAST WAKE  \u2022  #" + value(values, "wake_id")
                + "  " + value(values, "repair_result"));
        daemonValue.setText("DAEMON  \u2022  up " + duration(number(values, "uptime_ms"))
                + "  \u2022  CPU " + utilization + "%  \u2022  " + value(values, "action"));

        boolean suspicious = utilization >= 0 && utilization < 40
                && ratio(littleCur, littleMax) >= 0.95
                && ratio(bigCur, bigMax) >= 0.95;
        possibleLockSamples = suspicious ? possibleLockSamples + 1 : 0;
        warningValue.setText(possibleLockSamples >= 10
                ? "STOCK BUG CONFIRMED \u2022 LITTLE/BIG PINNED AT MAX"
                : "");
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
        panel.setPadding(dp(14), dp(5), dp(14), dp(5));
        GradientDrawable background = new GradientDrawable();
        background.setColor(0xCF151021);
        background.setCornerRadius(dp(14));
        background.setStroke(dp(1), 0x88C45CFF);
        panel.setBackground(background);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, 0, 0, dp(8));
        panel.setLayoutParams(params);
        return panel;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setWeightSum(3f);
        return row;
    }

    private TextView addMetric(LinearLayout row, String label, String initial) {
        LinearLayout metric = new LinearLayout(this);
        metric.setOrientation(LinearLayout.VERTICAL);
        metric.setGravity(Gravity.CENTER_VERTICAL);
        metric.setPadding(dp(2), 0, dp(5), 0);
        TextView heading = text(label, 10f, MUTED, true);
        heading.setLetterSpacing(0.09f);
        metric.addView(heading);
        TextView value = text(initial, 13f, Color.WHITE, true);
        value.setPadding(0, dp(2), 0, 0);
        metric.addView(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -1, 1f);
        row.addView(metric, params);
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

    private static String modeDescription(String mode) {
        if ("1".equals(mode)) return "Top screen only";
        if ("0".equals(mode)) return "Both screens";
        if ("2".equals(mode)) return "Bottom screen only";
        return "Display mode unknown";
    }

    private static String displayDescription(String mode, boolean bottomPowered) {
        if ("1".equals(mode) && !bottomPowered) return "Top only  \u2022  Bottom fully off";
        if ("0".equals(mode)) return "Both screens available";
        if ("2".equals(mode)) return "Bottom screen only";
        if ("1".equals(mode)) return "Top only  \u2022  Bottom still active";
        return "Display state unavailable";
    }
}
