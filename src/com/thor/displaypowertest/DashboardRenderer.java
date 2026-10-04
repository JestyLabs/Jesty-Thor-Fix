package com.thor.displaypowertest;

import android.graphics.Color;
import android.view.View;
import android.widget.TextView;

import java.util.Locale;
import java.util.Map;

import static com.thor.displaypowertest.DashboardStyle.AMBER;
import static com.thor.displaypowertest.DashboardStyle.GREEN;
import static com.thor.displaypowertest.DashboardStyle.MUTED;
import static com.thor.displaypowertest.DashboardStyle.RED;
import static com.thor.displaypowertest.DashboardStyle.color;
import static com.thor.displaypowertest.TelemetryValues.decimalNumber;
import static com.thor.displaypowertest.TelemetryValues.number;
import static com.thor.displaypowertest.TelemetryValues.value;

/**
 * Turns one daemon 'Q' sample into dashboard text and colors. Decisions stay
 * in DashboardStateModel and CpuWarningModel; this class only applies them.
 * Every method runs on the UI thread.
 */
final class DashboardRenderer implements DashboardSettingsController.Feedback {
    /** Actions that leave the dashboard's own widgets. */
    interface Host {
        void onDisplayVisual(DashboardStateModel.Visual visual);
        void requestDaemonReplacement();
    }

    private final DashboardStateModel dashboardModel = new DashboardStateModel();
    private final Host host;
    private final TextView stateText;
    private final TextView stateDetail;
    private final TextView littleValue;
    private final TextView bigValue;
    private final TextView primeValue;
    private final TextView displayValue;
    private final TextView displayDetail;
    private final TextView powerValue;
    private final TextView warningValue;
    private final TextView lidValue;

    DashboardRenderer(DashboardLayout layout, Host host) {
        this.host = host;
        stateText = layout.stateText;
        stateDetail = layout.stateDetail;
        littleValue = layout.littleValue;
        bigValue = layout.bigValue;
        primeValue = layout.primeValue;
        displayValue = layout.displayValue;
        displayDetail = layout.displayDetail;
        powerValue = layout.powerValue;
        warningValue = layout.warningValue;
        lidValue = layout.lidValue;
    }

    /** A new telemetry generation starts without smoothing or clock history. */
    void resetForStart() {
        dashboardModel.resetDisplay();
        dashboardModel.resetClocks();
        dashboardModel.resetBattery();
    }

    void resetOnStop() {
        dashboardModel.resetClocks();
        dashboardModel.resetBattery();
    }

    @Override public void displayCommandStarted() {
        stateText.setText("APPLYING\u2026");
        stateText.setTextColor(AMBER);
    }

    @Override public void displayCommandFinished(String error, boolean uncertain) {
        if (error != null) {
            stateText.setText(uncertain ? "STATUS UNKNOWN" : "CHANGE REJECTED");
            stateText.setTextColor(uncertain ? AMBER : RED);
            stateDetail.setText(error);
            stateDetail.setVisibility(View.VISIBLE);
        } else {
            stateText.setText("CONFIRMING DISPLAY");
            stateText.setTextColor(AMBER);
            stateDetail.setVisibility(View.GONE);
        }
    }

    @Override public void cpuFixCommandStarted() {
        dashboardModel.resetClocks();
        warningValue.setTextColor(MUTED);
        warningValue.setText("AYN DASHBOARD CPU FIX \u2022 APPLYING\u2026");
    }

    void renderUnavailable(boolean displayCommandInFlight) {
        if (!displayCommandInFlight) {
            stateText.setText("DAEMON UNAVAILABLE");
            stateText.setTextColor(RED);
            stateDetail.setText("No telemetry response");
            stateDetail.setVisibility(View.VISIBLE);
        }
        dashboardModel.resetClocks();
        dashboardModel.resetBattery();
        warningValue.setText("");
    }

    void render(Map<String, String> values, DashboardSettingsController settings) {
        if (settings.displayCommandInFlight()) return;
        boolean enabled = "1".equals(values.get("fix"));
        boolean desiredDisplayFix = settings.desiredDisplayFix();
        String displayEffective = value(values, "display_effective");
        boolean watcherReady = "RUNNING".equals(values.get("watcher_health"));
        boolean bootHeld = "1".equals(values.get("display_actions_held"));
        settings.syncFromTelemetry(bootHeld, watcherReady);
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
        String lid = value(values, "lid");
        boolean desiredGuard = settings.desiredLidGuard();
        boolean effectiveGuard = "1".equals(values.get("lid_guard"));
        String guardState = bootHeld && desiredGuard
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
        DashboardStateModel.DisplayStatus display = null;
        if (bootHeld) {
            String rawPhase = value(values, "boot_phase");
            String phase = rawPhase.replace('_', ' ');
            // AutoService makes the authenticated decision; this only asks it to
            // look again while a failed handover or stalled phase is visible.
            boolean replaceable = DaemonLaunchModel.HANDOFF_FAILED_PHASE.equals(rawPhase)
                    || (!"BOOT_SAFETY_TIMEOUT".equals(rawPhase)
                    && number(values, "boot_phase_ms") >= DaemonLaunchModel.STUCK_PHASE_MS);
            if (replaceable) host.requestDaemonReplacement();
            displayValue.setText(phase);
            displayValue.setTextColor(AMBER);
            displayDetail.setText(replaceable ? "Restarting the background service\u2026"
                    : "Display controls paused until boot is safe");
            displayDetail.setTextColor(MUTED);
        } else {
            display = dashboardModel.updateDisplay(mode, topCrtc, bottomCrtc, enabled);
            displayValue.setText(display.title);
            displayValue.setTextColor(color(display.tone));
            displayDetail.setText(display.detail);
            displayDetail.setTextColor(color(display.tone));
            if (display.confirmed) host.onDisplayVisual(display.confirmedVisual);
        }

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
        boolean dashboardFixDesired = settings.desiredCpuFix();
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

    private static String clock(long current, long maximum) {
        if (current <= 0 || maximum <= 0) return "\u2014";
        return String.format(Locale.US, "%.2f / %.2f GHz", current / 1_000_000d, maximum / 1_000_000d);
    }
}
