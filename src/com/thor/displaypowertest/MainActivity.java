package com.thor.displaypowertest;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
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
import android.view.WindowManager;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class MainActivity extends Activity {
    private static final int PURPLE = Color.rgb(196, 92, 255);
    private static final int VIOLET = Color.rgb(139, 74, 226);
    private static final int AMBER = Color.rgb(255, 211, 102);
    private static final int GREEN = Color.rgb(111, 224, 163);
    private static final int RED = Color.rgb(255, 112, 126);
    private static final int MUTED = Color.rgb(168, 185, 204);
    private SharedPreferences preferences;
    private final DashboardStateModel dashboardModel = new DashboardStateModel();
    private final ExecutorService commandExecutor = Executors.newSingleThreadExecutor();
    private ScheduledExecutorService telemetryWorker;
    private ImageView backgroundImage;
    private TextureView videoTexture;
    private MediaPlayer backgroundPlayer;
    private Surface videoSurface;
    private boolean videoPrepared;
    private boolean activityResumed;
    private boolean visualBottomOn = true;
    private boolean suppressToggle;
    private boolean suppressDashboardToggle;
    private boolean suppressLidToggle;
    private boolean commandInFlight;
    private boolean dashboardCommandInFlight;
    private boolean lidCommandInFlight;
    private DashboardStateModel.Visual displayVisual = DashboardStateModel.Visual.BOTH_ON;

    private Switch fixToggle;
    private Switch dashboardFixToggle;
    private Switch lidGuardToggle;
    private TextView dashboardFixHelp;
    private TextView stateText;
    private TextView stateDetail;
    private TextView littleValue;
    private TextView bigValue;
    private TextView primeValue;
    private TextView displayValue;
    private TextView displayDetail;
    private TextView powerValue;
    private TextView warningValue;
    private TextView lidValue;

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
        suppressDashboardToggle = true;
        dashboardFixToggle.setChecked(preferences.getBoolean("dashboard_cpu_fix_enabled", false));
        updateDashboardFixHelp(dashboardFixToggle.isChecked());
        suppressDashboardToggle = false;
        dashboardFixToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressDashboardToggle) confirmDashboardCpuFix(checked);
        });
        suppressLidToggle = true;
        lidGuardToggle.setChecked(preferences.getBoolean("lid_guard_enabled", false));
        suppressLidToggle = false;
        lidGuardToggle.setOnCheckedChangeListener((button, checked) -> {
            if (!suppressLidToggle) setLidGuardEnabled(checked);
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
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(38), Gravity.BOTTOM | Gravity.END);
        badgeParams.setMargins(0, 0, dp(18), dp(16));
        root.addView(openSourceBadge, badgeParams);

        LinearLayout topActions = buildTopActions();
        FrameLayout.LayoutParams actionParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, dp(42), Gravity.TOP | Gravity.END);
        actionParams.setMargins(0, dp(16), dp(18), 0);
        root.addView(topActions, actionParams);

        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(24), dp(5), dp(24), dp(5));
        scroll.addView(content, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(buildHeader());
        TextView subtitle = text(appSubtitle(), 13f, MUTED, false);
        subtitle.setGravity(Gravity.CENTER);
        subtitle.setPadding(0, 0, 0, dp(8));
        content.addView(subtitle);

        stateDetail = text("", 12f, MUTED, false);
        stateDetail.setPadding(0, dp(2), 0, dp(5));
        stateDetail.setVisibility(View.GONE);
        content.addView(stateDetail);

        stateText = text("", 1f, Color.TRANSPARENT, false);
        stateText.setVisibility(View.GONE);
        content.addView(stateText, new LinearLayout.LayoutParams(0, 0));

        LinearLayout controls = panel();
        fixToggle = makeSwitch("", Color.WHITE, 14f);
        controls.addView(featureToggleRow("TRUE BOTTOM SCREEN OFF",
                "Actually powers off the lower screen in Top Only mode", fixToggle),
                new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(this);
        divider.setBackgroundColor(0x338B4AE2);
        controls.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));

        dashboardFixToggle = makeSwitch("", Color.WHITE, 14f);
        controls.addView(featureToggleRow("AYN DASHBOARD CPU FIX",
                "Restarts Android UI/display once to apply", dashboardFixToggle),
                new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        View guardDivider = new View(this);
        guardDivider.setBackgroundColor(0x338B4AE2);
        controls.addView(guardDivider, new LinearLayout.LayoutParams(-1, dp(1)));
        lidGuardToggle = makeSwitch("", Color.WHITE, 14f);
        controls.addView(featureToggleRow("CLOSED-LID WAKE GUARD",
                "Returns the Thor to sleep after an accidental wake while closed", lidGuardToggle),
                new LinearLayout.LayoutParams(-1, ViewGroup.LayoutParams.WRAP_CONTENT));
        content.addView(controls);

        LinearLayout cpuPanel = panel();
        TextView displayHeading = text("DISPLAY MODE", 10f, MUTED, true);
        displayHeading.setLetterSpacing(0.09f);
        cpuPanel.addView(displayHeading);
        displayValue = text("\u2014", 18f, Color.WHITE, true);
        displayValue.setGravity(Gravity.CENTER_VERTICAL);
        displayValue.setPadding(0, dp(2), 0, 0);
        displayValue.setMinHeight(dp(26));
        cpuPanel.addView(displayValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        displayDetail = text("Waiting for hardware\u2026", 10f, MUTED, false);
        displayDetail.setPadding(0, 0, 0, dp(3));
        cpuPanel.addView(displayDetail, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        powerValue = text("BATTERY DRAW  \u2014  \u00B7  UNPLUG USB", 10f, MUTED, true);
        powerValue.setPadding(0, 0, 0, dp(5));
        cpuPanel.addView(powerValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        View statusDivider = new View(this);
        statusDivider.setBackgroundColor(0x338B4AE2);
        cpuPanel.addView(statusDivider, new LinearLayout.LayoutParams(-1, dp(1)));

        TextView cpuHeading = text("CPU SPEEDS", 11f, Color.WHITE, true);
        cpuHeading.setLetterSpacing(0.09f);
        cpuHeading.setPadding(0, dp(5), 0, 0);
        cpuPanel.addView(cpuHeading);
        LinearLayout cpuRow = row();
        littleValue = addMetric(cpuRow, "LITTLE", "\u2014");
        bigValue = addMetric(cpuRow, "BIG", "\u2014");
        primeValue = addMetric(cpuRow, "PRIME", "\u2014");
        cpuPanel.addView(cpuRow, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        warningValue = text("", 10f, RED, true);
        warningValue.setPadding(0, dp(4), 0, dp(2));
        cpuPanel.addView(warningValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        lidValue = text("LID UNKNOWN  \u00B7  WAKE GUARD OFF  \u00B7  0 BLOCKED", 9f, MUTED, true);
        lidValue.setPadding(0, dp(2), 0, 0);
        cpuPanel.addView(lidValue, new LinearLayout.LayoutParams(-1,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        content.addView(cpuPanel);
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

    private void confirmDashboardCpuFix(boolean requested) {
        String message = requested
                ? "Android restarts now and once per boot. Open apps will close."
                : "Android restarts now. Open apps will close.";
        new AlertDialog.Builder(this)
                .setTitle(requested ? "Enable Dashboard CPU Fix?" : "Disable Dashboard CPU Fix?")
                .setMessage(message)
                .setNegativeButton("CANCEL", (dialog, which) -> restoreDashboardToggle())
                .setOnCancelListener(dialog -> restoreDashboardToggle())
                .setPositiveButton(requested ? "RESTART & ENABLE" : "RESTART & DISABLE",
                        (dialog, which) -> setDashboardCpuFixEnabled(requested))
                .show();
    }

    private void restoreDashboardToggle() {
        suppressDashboardToggle = true;
        dashboardFixToggle.setChecked(preferences.getBoolean("dashboard_cpu_fix_enabled", false));
        suppressDashboardToggle = false;
        updateDashboardFixHelp(dashboardFixToggle.isChecked());
    }

    private void setDashboardCpuFixEnabled(boolean requested) {
        if (dashboardCommandInFlight) return;
        final boolean previous = preferences.getBoolean("dashboard_cpu_fix_enabled", false);
        dashboardCommandInFlight = true;
        dashboardModel.resetClocks();
        dashboardFixToggle.setEnabled(false);
        warningValue.setTextColor(MUTED);
        warningValue.setText("AYN DASHBOARD CPU FIX • APPLYING…");
        commandExecutor.execute(() -> {
            try {
                if (!preferences.edit().putBoolean("dashboard_cpu_fix_enabled", requested).commit()) {
                    throw new IllegalStateException("CPU Fix preference could not be saved");
                }
                Map<String, String> result = parse(SocketClient.request(requested ? 'R' : 'L', 1800));
                if (!"1".equals(result.get("ok"))) throw new IllegalStateException("Command not accepted");
                final boolean restart = "scheduled_once".equals(result.get("composer_restart"));
                runOnUiThread(() -> {
                    dashboardCommandInFlight = false;
                    dashboardFixToggle.setEnabled(true);
                    updateDashboardFixHelp(requested);
                    Toast.makeText(this, restart ? "Android restart pending"
                            : "CPU Fix confirmed without restart",
                            Toast.LENGTH_LONG).show();
                });
            } catch (Throwable error) {
                final boolean rejected = error.getMessage() != null
                        && error.getMessage().startsWith("ok=0");
                if (rejected) preferences.edit()
                        .putBoolean("dashboard_cpu_fix_enabled", previous).commit();
                runOnUiThread(() -> {
                    dashboardCommandInFlight = false;
                    dashboardFixToggle.setEnabled(true);
                    restoreDashboardToggle();
                    Toast.makeText(this, rejected ? "CPU Fix change rejected"
                            : "CPU Fix status unknown; checking again", Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private LinearLayout buildOpenSourceBadge() {
        LinearLayout badge = new LinearLayout(this);
        badge.setOrientation(LinearLayout.VERTICAL);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(dp(16), dp(4), dp(16), dp(4));
        TextView title = text("JESTY APPS ARE FREE & OPEN SOURCE", 9f, AMBER, true);
        title.setGravity(Gravity.CENTER);
        title.setLetterSpacing(0.05f);
        badge.addView(title);
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
        header.addView(lockup, new LinearLayout.LayoutParams(dp(242), dp(68)));

        header.setLayoutParams(new LinearLayout.LayoutParams(-1, dp(70)));
        return header;
    }

    private void setFixEnabled(final boolean requested) {
        if (commandInFlight) return;
        final boolean previous = preferences.getBoolean("fix_enabled", true);
        commandInFlight = true;
        fixToggle.setEnabled(false);
        stateText.setText("APPLYING\u2026");
        stateText.setTextColor(AMBER);
        commandExecutor.execute(() -> {
            try {
                if (!preferences.edit().putBoolean("fix_enabled", requested).commit()) {
                    throw new IllegalStateException("Display Fix preference could not be saved");
                }
                SocketClient.request(requested ? 'E' : 'N', 1500);
                runOnUiThread(() -> finishToggle(requested, null, false));
            } catch (Throwable error) {
                final boolean rejected = error.getMessage() != null
                        && error.getMessage().startsWith("ok=0");
                if (rejected && !preferences.edit()
                        .putBoolean("fix_enabled", previous).commit()) {
                    android.util.Log.e("ThorDisplay", "display preference rollback failed", error);
                }
                runOnUiThread(() -> finishToggle(rejected ? previous : requested,
                        error.getMessage(), !rejected));
            }
        });
    }

    private void setLidGuardEnabled(final boolean requested) {
        if (lidCommandInFlight) return;
        final boolean previous = preferences.getBoolean("lid_guard_enabled", false);
        lidCommandInFlight = true;
        lidGuardToggle.setEnabled(false);
        commandExecutor.execute(() -> {
            try {
                if (!preferences.edit().putBoolean("lid_guard_enabled", requested).commit()) {
                    throw new IllegalStateException("Wake Guard preference could not be saved");
                }
                SocketClient.request(requested ? 'G' : 'H', 1800);
                runOnUiThread(() -> {
                    lidCommandInFlight = false;
                    lidGuardToggle.setEnabled(true);
                });
            } catch (Throwable error) {
                final boolean rejected = error.getMessage() != null
                        && error.getMessage().startsWith("ok=0");
                if (rejected && !preferences.edit()
                        .putBoolean("lid_guard_enabled", previous).commit()) {
                    android.util.Log.e("ThorDisplay", "lid preference rollback failed", error);
                }
                runOnUiThread(() -> {
                    lidCommandInFlight = false;
                    lidGuardToggle.setEnabled(true);
                    suppressLidToggle = true;
                    lidGuardToggle.setChecked(rejected ? previous : requested);
                    suppressLidToggle = false;
                    Toast.makeText(this, rejected ? "Wake Guard change rejected"
                                    : "Wake Guard status unknown; checking again",
                            Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void finishToggle(boolean desired, String error, boolean uncertain) {
        suppressToggle = true;
        fixToggle.setChecked(desired);
        suppressToggle = false;
        fixToggle.setEnabled(true);
        commandInFlight = false;
        if (error != null) {
            stateText.setText(uncertain ? "STATUS UNKNOWN" : "CHANGE REJECTED");
            stateText.setTextColor(uncertain ? AMBER : RED);
            stateDetail.setText(error);
            stateDetail.setVisibility(View.VISIBLE);
            Toast.makeText(this, uncertain ? "Checking display state again"
                    : "Change rejected; setting restored", Toast.LENGTH_LONG).show();
        } else {
            stateText.setText("CONFIRMING DISPLAY");
            stateText.setTextColor(AMBER);
            stateDetail.setVisibility(View.GONE);
        }
    }

    private void pollTelemetry() {
        try {
            final Map<String, String> values = parse(SocketClient.request('Q', 2000));
            runOnUiThread(() -> render(values));
        } catch (Throwable error) {
            runOnUiThread(() -> {
                if (!commandInFlight) {
                    stateText.setText("DAEMON UNAVAILABLE");
                    stateText.setTextColor(RED);
                    stateDetail.setText("No telemetry response");
                    stateDetail.setVisibility(View.VISIBLE);
                }
                dashboardModel.resetClocks();
                dashboardModel.resetBattery();
                warningValue.setText("");
            });
        }
    }

    private void render(Map<String, String> values) {
        if (commandInFlight) return;
        boolean enabled = "1".equals(values.get("fix"));
        boolean desiredDisplayFix = preferences.getBoolean("fix_enabled", true);
        suppressToggle = true;
        fixToggle.setChecked(desiredDisplayFix);
        suppressToggle = false;
        String displayEffective = value(values, "display_effective");
        boolean watcherReady = "RUNNING".equals(values.get("watcher_health"));
        stateText.setText(!watcherReady ? "MONITOR UNAVAILABLE"
                : enabled != desiredDisplayFix ? "SETTING NOT APPLIED"
                : "CONFIRMED".equals(displayEffective)
                ? enabled ? "ACTIVE" : "STOCK"
                : "MISMATCH".equals(displayEffective) ? "CHECK DISPLAY" : "PENDING");
        stateText.setTextColor(!watcherReady || enabled != desiredDisplayFix
                || "MISMATCH".equals(displayEffective) ? RED
                : "CONFIRMED".equals(displayEffective) && !enabled ? Color.WHITE : AMBER);
        String topCrtc = values.get("top_crtc");
        String bottomCrtc = values.get("bottom_crtc");
        stateDetail.setVisibility(View.GONE);
        boolean bootHeld = "1".equals(values.get("display_actions_held"));
        String lid = value(values, "lid");
        boolean desiredGuard = preferences.getBoolean("lid_guard_enabled", false);
        boolean effectiveGuard = "1".equals(values.get("lid_guard"));
        String guardState = bootHeld && preferences.getBoolean("lid_guard_enabled", false)
                ? "PENDING" : desiredGuard != effectiveGuard ? "ERROR"
                : effectiveGuard ? "ON" : "OFF";
        lidValue.setText("LID " + lid.toUpperCase(Locale.US) + "  \u00B7  WAKE GUARD "
                + guardState + "  \u00B7  " + value(values, "blocked_wakes") + " BLOCKED");

        long littleCur = number(values, "little_cur"), littleMax = number(values, "little_max");
        long bigCur = number(values, "big_cur"), bigMax = number(values, "big_max");
        long primeCur = number(values, "prime_cur"), primeMax = number(values, "prime_max");
        int utilization = (int) number(values, "cpu_pct");
        littleValue.setText(clock(littleCur, littleMax));
        bigValue.setText(clock(bigCur, bigMax));
        primeValue.setText(clock(primeCur, primeMax));
        String mode = values.get("mode");
        if (!lidCommandInFlight) {
            if (lidGuardToggle.isChecked() != desiredGuard) {
                suppressLidToggle = true;
                lidGuardToggle.setChecked(desiredGuard);
                suppressLidToggle = false;
            }
        }
        DashboardStateModel.DisplayStatus display = null;
        if (bootHeld) {
            String phase = value(values, "boot_phase").replace('_', ' ');
            displayValue.setText(phase);
            displayValue.setTextColor(AMBER);
            displayDetail.setText("Display controls paused until boot is safe");
            displayDetail.setTextColor(MUTED);
        } else {
            display = dashboardModel.updateDisplay(mode, topCrtc, bottomCrtc, enabled);
            displayValue.setText(display.title);
            displayValue.setTextColor(color(display.tone));
            displayDetail.setText(display.detail);
            displayDetail.setTextColor(color(display.tone));
            if (display.confirmed) setDisplayVisual(display.confirmedVisual);
        }
        fixToggle.setEnabled(!bootHeld && watcherReady && !commandInFlight);
        dashboardFixToggle.setEnabled(!bootHeld && watcherReady && !dashboardCommandInFlight);
        lidGuardToggle.setEnabled(!bootHeld && !lidCommandInFlight);

        double batteryWatts = decimalNumber(values, "battery_w");
        if ("battery".equals(values.get("power_source")) && batteryWatts >= 0d) {
            double smoothedBatteryWatts = dashboardModel.smoothBattery(batteryWatts);
            powerValue.setText(String.format(Locale.US,
                    "BATTERY DRAW  ~%.2f W", smoothedBatteryWatts));
            powerValue.setTextColor(GREEN);
        } else {
            dashboardModel.resetBattery();
            powerValue.setText("external".equals(values.get("power_source"))
                    ? "BATTERY DRAW  \u2014  \u00B7  UNPLUG USB"
                    : "BATTERY DRAW  \u2014  \u00B7  WAITING FOR BATTERY");
            powerValue.setTextColor(MUTED);
        }
        String cpuPhase = value(values, "cpu_fix_phase");
        boolean dashboardFixActive = "CONFIRMED".equals(cpuPhase)
                && "1".equals(values.get("system_load_fix"));
        boolean dashboardFixDesired = preferences.getBoolean("dashboard_cpu_fix_enabled", false);
        boolean cpuIntentMismatch = dashboardFixDesired
                != "1".equals(values.get("cpu_fix_desired"));
        String clockStateKey = value(values, "mode") + ":" + value(values, "top_crtc")
                + ":" + value(values, "bottom_crtc") + ":" + dashboardFixActive
                + ":" + dashboardFixDesired + ":" + cpuPhase
                + ":" + (display != null && display.confirmed);
        DashboardStateModel.ClockStatus clocks = !CpuWarningModel.mayMeasure(
                bootHeld, cpuIntentMismatch, cpuPhase) ? null : dashboardModel.updateClocks(clockStateKey,
                number(values, "little_high_ticks"), number(values, "little_total_ticks"),
                number(values, "big_high_ticks"), number(values, "big_total_ticks"), utilization,
                dashboardFixDesired, dashboardFixActive, "UNKNOWN".equals(cpuPhase));
        littleValue.setTextColor(clocks != null && clocks.pinned ? RED : Color.WHITE);
        bigValue.setTextColor(clocks != null && clocks.pinned ? RED : Color.WHITE);
        primeValue.setTextColor(Color.WHITE);
        warningValue.setTextColor(!watcherReady || cpuIntentMismatch || "UNKNOWN".equals(cpuPhase) || "ERROR".equals(cpuPhase)
                || "MISMATCH".equals(cpuPhase)
                ? RED : clocks == null ? AMBER : color(clocks.tone));
        warningValue.setText(CpuWarningModel.text(
                bootHeld, watcherReady, cpuIntentMismatch, cpuPhase, clocks));
    }

    private void startTelemetry() {
        if (telemetryWorker != null) return;
        dashboardModel.resetDisplay();
        dashboardModel.resetClocks();
        dashboardModel.resetBattery();
        telemetryWorker = Executors.newSingleThreadScheduledExecutor();
        telemetryWorker.scheduleAtFixedRate(this::pollTelemetry, 1L, 1L, TimeUnit.SECONDS);
    }

    private void stopTelemetry() {
        if (telemetryWorker != null) telemetryWorker.shutdownNow();
        telemetryWorker = null;
        dashboardModel.resetClocks();
        dashboardModel.resetBattery();
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

    private void setDisplayVisual(DashboardStateModel.Visual visual) {
        if (displayVisual == visual) return;
        displayVisual = visual;
        visualBottomOn = visual == DashboardStateModel.Visual.BOTH_ON;
        String resourceName;
        if (visual == DashboardStateModel.Visual.BOTH_ON) {
            resourceName = "jesty_thor_background";
        } else if (visual == DashboardStateModel.Visual.AYN_FAKE_OFF) {
            resourceName = "jesty_thor_background_fake_off";
        } else {
            resourceName = "jesty_thor_background_true_off";
        }
        backgroundImage.setImageResource(resource("drawable", resourceName));
        if (visualBottomOn) {
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
        commandExecutor.shutdown();
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
        params.setMargins(0, 0, 0, dp(6));
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
        TextView value = text(initial, 15f, Color.WHITE, true);
        value.setPadding(0, dp(2), 0, 0);
        metric.addView(value);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        row.addView(metric, params);
        return value;
    }

    private LinearLayout featureToggleRow(String title, String description, Switch toggle) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(dp(46));
        row.setPadding(0, dp(4), 0, dp(4));

        LinearLayout copy = new LinearLayout(this);
        copy.setOrientation(LinearLayout.VERTICAL);
        copy.setGravity(Gravity.CENTER_VERTICAL);
        TextView heading = text(title, 14f, Color.WHITE, true);
        heading.setLetterSpacing(0.025f);
        copy.addView(heading);
        TextView help = text(description, 10f, MUTED, false);
        help.setPadding(0, dp(1), dp(8), 0);
        if (toggle == dashboardFixToggle) dashboardFixHelp = help;
        copy.addView(help);
        row.addView(copy, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        row.addView(toggle, new LinearLayout.LayoutParams(dp(58), dp(40)));
        return row;
    }

    private void updateDashboardFixHelp(boolean enabled) {
        if (dashboardFixHelp == null) return;
        dashboardFixHelp.setText(enabled
                ? "Restarts Android UI once per boot (may look like a second boot)"
                : "Restarts Android UI/display once to apply");
        dashboardFixHelp.setTextColor(enabled ? AMBER : MUTED);
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

    private String appSubtitle() {
        String label = "Display and CPU fixes for AYN Thor";
        try {
            String version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
            if (version != null && !version.trim().isEmpty()) return label + " (v" + version.trim() + ")";
        } catch (PackageManager.NameNotFoundException ignored) {
            // Keep the descriptive label even if package metadata is unavailable.
        }
        return label;
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

    private static double decimalNumber(Map<String, String> values, String key) {
        try { return Double.parseDouble(values.get(key)); } catch (Throwable ignored) { return -1d; }
    }

    private static String clock(long current, long maximum) {
        if (current <= 0 || maximum <= 0) return "\u2014";
        return String.format(Locale.US, "%.2f / %.2f GHz", current / 1_000_000d, maximum / 1_000_000d);
    }

    private static int color(DashboardStateModel.Tone tone) {
        if (tone == DashboardStateModel.Tone.GREEN) return GREEN;
        if (tone == DashboardStateModel.Tone.AMBER) return AMBER;
        if (tone == DashboardStateModel.Tone.RED) return RED;
        return MUTED;
    }
}
