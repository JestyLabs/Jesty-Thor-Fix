package com.thor.displaypowertest;

import android.os.SystemClock;

public final class DaemonState {
    private static volatile boolean enabled = true;
    private static volatile String mode = "?";
    private static volatile String lastAction = "STARTING";
    private static volatile String lastRepairResult = "NONE";
    private static volatile long wakeId;
    private static volatile long wakeAt = -1L;
    private static volatile long displayOnAt = -1L;
    private static volatile long repairBeginAt = -1L;
    private static volatile long repairEndAt = -1L;
    private static final long startedAt = SystemClock.elapsedRealtime();

    private DaemonState() {}

    public static boolean isEnabled() { return enabled; }
    public static void setEnabled(boolean value) {
        enabled = value;
        lastAction = value ? "FIX_ENABLED" : "NATIVE_MODE";
    }
    public static void setMode(String value) { mode = value == null ? "?" : value; }
    public static String getMode() { return mode; }
    public static void setLastAction(String value) { lastAction = value; }

    public static synchronized void onWake(boolean rescheduled) {
        wakeId++;
        wakeAt = SystemClock.elapsedRealtime();
        displayOnAt = repairBeginAt = repairEndAt = -1L;
        lastRepairResult = rescheduled ? "RESCHEDULED" : "SCHEDULED";
    }
    public static synchronized void onDisplayOn() {
        displayOnAt = SystemClock.elapsedRealtime();
    }
    public static synchronized void onRepairBegin() {
        repairBeginAt = SystemClock.elapsedRealtime();
        lastRepairResult = "RUNNING";
    }
    public static synchronized void onRepairEnd(String result) {
        repairEndAt = SystemClock.elapsedRealtime();
        lastRepairResult = result;
    }
    public static synchronized void onRepairCancelled() {
        if ("SCHEDULED".equals(lastRepairResult) || "RESCHEDULED".equals(lastRepairResult)) {
            lastRepairResult = "CANCELLED";
        }
    }

    // Snapshot does slow telemetry and LidGuard probes: never hold DaemonState's
    // monitor while the display coordinator may need to record a transition.
    public static String snapshot() {
        long now = SystemClock.elapsedRealtime();
        Telemetry.CpuSnapshot cpu = Telemetry.readCpu();
        Telemetry.PowerSnapshot power = Telemetry.readPower();
        String topCrtc = Telemetry.topCrtcActive();
        String bottomCrtc = Telemetry.bottomCrtcActive();
        return "ok=1"
                + ";boot_phase=" + clean(BootSafety.phase()).replace(' ', '_')
                + ";boot_phase_ms=" + BootSafety.phaseAgeMs()
                + ";display_actions_held=" + (BootSafety.isHeld() ? "1" : "0")
                + ";lid=" + LidGuard.lid()
                + ";lid_guard=" + (LidGuard.isEnabled() ? "1" : "0")
                + ";lid_guard_state=" + LidGuard.state()
                + ";blocked_wakes=" + LidGuard.blockedWakes()
                + ";last_lid_action=" + LidGuard.lastAction()
                + ";external_display=" + LidGuard.externalDisplay()
                + ";fix=" + (enabled ? "1" : "0")
                + ";display_desired=" + (enabled ? "1" : "0")
                + ";display_effective=" + DisplayDecisionModel.effective(
                        mode, topCrtc, bottomCrtc, enabled, BootSafety.isHeld())
                + ";display_action_status=" + clean(DisplayActionCoordinator.status())
                + ";display_generation=" + DisplayActionCoordinator.generation()
                + ";watcher_health=" + WatcherSupervisor.health()
                + ";watcher_last_sample_ms=" + WatcherSupervisor.lastSampleAt()
                + ";mode=" + clean(mode)
                + ";power=" + clean(DisplayHardware.getProperty())
                + ";top_crtc=" + clean(topCrtc)
                + ";bottom_crtc=" + clean(bottomCrtc)
                + ";uptime_ms=" + (now - startedAt)
                + ";little_cur=" + cpu.littleCurrent
                + ";little_max=" + cpu.littleMax
                + ";big_cur=" + cpu.bigCurrent
                + ";big_max=" + cpu.bigMax
                + ";prime_cur=" + cpu.primeCurrent
                + ";prime_max=" + cpu.primeMax
                + ";little_max_ticks=" + cpu.littleMaxTicks
                + ";little_high_ticks=" + cpu.littleHighTicks
                + ";little_total_ticks=" + cpu.littleTotalTicks
                + ";big_max_ticks=" + cpu.bigMaxTicks
                + ";big_high_ticks=" + cpu.bigHighTicks
                + ";big_total_ticks=" + cpu.bigTotalTicks
                + ";cpu_pct=" + cpu.utilization
                + ";usb_w=" + decimal(power.usbWatts)
                + ";battery_charge_w=" + decimal(power.batteryChargeWatts)
                + ";system_proxy_w=" + decimal(power.systemProxyWatts)
                + ";power_source=" + clean(power.source)
                + ";battery_w=" + decimal(power.batteryWatts)
                + ";system_load_fix=" + Telemetry.systemLoadFixState()
                + ";wake_id=" + wakeId
                + ";wake_at=" + wakeAt
                + ";display_on_at=" + displayOnAt
                + ";repair_begin=" + repairBeginAt
                + ";repair_end=" + repairEndAt
                + ";repair_result=" + clean(lastRepairResult)
                + ";action=" + clean(lastAction)
                + WatcherCostMetrics.fieldsAndLog();
    }

    private static String clean(String value) {
        return value == null ? "?" : value.replace(';', '_').replace('\n', '_');
    }

    private static String decimal(double value) {
        return Double.isNaN(value) ? "?" : String.format(java.util.Locale.US, "%.3f", value);
    }
}
