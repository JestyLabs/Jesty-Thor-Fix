package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/** The only author of lower-panel transitions and guard-requested sleep. */
public final class DisplayActionCoordinator {
    private static final String TAG = "ThorDisplayDaemon";
    private static volatile String lastMode = "?";
    private static volatile String status = "STARTING";
    private static final DisplayGenerationModel generations = new DisplayGenerationModel();
    private static long lastHardwareReadAt;
    private static long lastAttemptAt;
    private static String lastAttemptKey = "";

    private DisplayActionCoordinator() {}
    public static String status() { return status; }
    public static long generation() { return generations.current(); }

    /** Called by the existing 20 ms settings watcher; DRM reads are throttled. */
    public static void onWatcherSample(String sampledMode) {
        String mode = BootSafety.knownMode(sampledMode) ? sampledMode : "?";
        boolean stableTopWake = "1".equals(mode) && mode.equals(lastMode)
                && !BootSafety.isHeld() && DaemonState.isEnabled()
                && "1".equals(DisplayHardware.getProperty());
        // Avoid an inverted lock order with LidGuard's synchronized methods.
        boolean deferredByLid = stableTopWake && LidGuard.onWakeEvent();
        synchronized (DisplayHardware.class) {
            boolean changed = !mode.equals(lastMode);
            if (changed) {
                generations.invalidate();
                cancelRepairLocked();
                lastMode = mode;
                status = "MODE_CHANGED";
            }
            DaemonState.setMode(mode);
            if (!BootSafety.knownMode(mode) || BootSafety.isHeld()) {
                status = BootSafety.isHeld() ? "BOOT_HOLD" : "MODE_UNKNOWN";
                return;
            }
            if (!DaemonState.isEnabled()) {
                cancelRepairLocked();
                status = "STOCK";
                return;
            }
            long now = SystemClock.elapsedRealtime();
            boolean due = WakeRepairScheduler.isDue();
            if (!changed && !due && !stableTopWake && now - lastHardwareReadAt < 250L) return;
            lastHardwareReadAt = now;
            String top = Telemetry.topCrtcActive();
            String bottom = Telemetry.bottomCrtcActive();
            if (stableTopWake && "1".equals(top) && "1".equals(bottom)) {
                if (deferredByLid) return;
                if (DisplayHardware.markWakePending()) scheduleRepairLocked();
                return;
            }
            if (due) {
                if (!generations.isRepairCurrent() || !"1".equals(mode)
                        || !"1".equals(top)) {
                    cancelRepairLocked();
                } else {
                    DaemonState.onRepairBegin();
                    if ("0".equals(bottom) || attempt(false, "WAKE_REPAIR", mode)) {
                        WakeRepairScheduler.markDone();
                        generations.completeRepair();
                        DaemonState.onRepairEnd("OFF_OK");
                    } else {
                        DaemonState.onRepairEnd("RETRY_PENDING");
                    }
                    return;
                }
            }
            DisplayDecisionModel.Action action = DisplayDecisionModel.reconcile(
                    mode, top, bottom, true, false, WakeRepairScheduler.isPending());
            if (action == DisplayDecisionModel.Action.OFF) attempt(false, "WATCH_TOP", mode);
            else if (action == DisplayDecisionModel.Action.ON) attempt(true, "WATCH_BOTH", mode);
            else if (!WakeRepairScheduler.isPending()) status =
                    DisplayDecisionModel.effective(mode, top, bottom, true, false);
        }
    }

    public static String requestFix(boolean requested) {
        synchronized (DisplayHardware.class) {
            if (BootSafety.isHeld()) return "ok=0;error=BOOT_HOLD";
            if (!"RUNNING".equals(WatcherSupervisor.health()))
                return "ok=0;error=WATCHER_NOT_READY";
            String mode = readCurrentMode();
            if (!BootSafety.knownMode(mode) || !mode.equals(DaemonState.getMode()))
                return "ok=0;error=DISPLAY_MODE_UNKNOWN";
            String top = Telemetry.topCrtcActive();
            String bottom = Telemetry.bottomCrtcActive();
            if (!DisplayDecisionModel.readyForReconcile(mode, top, bottom))
                return "ok=0;error=DISPLAY_TRANSITION";
            boolean targetOn = !requested || !"1".equals(mode);
            if (!applyConfirmed(targetOn, "USER_RECONCILE", mode))
                return "ok=0;error=DISPLAY_UNCONFIRMED";
            generations.invalidate();
            cancelRepairLocked();
            DaemonState.setEnabled(requested);
            status = "CONFIRMED";
            return "ok=1;fix=" + (requested ? "1" : "0") + ";display=confirmed";
        }
    }

    /** Called only after the boot gate has observed a stable mode and CRTCs. */
    public static boolean reconcileBoot(String expectedMode, boolean targetOn) {
        synchronized (DisplayHardware.class) {
            if (!BootSafety.knownMode(expectedMode)
                    || !expectedMode.equals(readCurrentMode())) return false;
            if (!DisplayDecisionModel.readyForReconcile(expectedMode,
                    Telemetry.topCrtcActive(), Telemetry.bottomCrtcActive())) return false;
            if (!applyConfirmed(targetOn, "BOOT_RECONCILE", expectedMode)) return false;
            generations.invalidate();
            cancelRepairLocked();
            status = "CONFIRMED";
            return true;
        }
    }

    public static void cancelWakeRepair() {
        synchronized (DisplayHardware.class) { generations.invalidate(); cancelRepairLocked(); }
    }
    public static void scheduleWakeRepair() {
        synchronized (DisplayHardware.class) {
            if (BootSafety.isHeld() || !DaemonState.isEnabled()
                    || !"1".equals(DaemonState.getMode())) return;
            scheduleRepairLocked();
        }
    }
    public static void noteDisplayOn() {
        synchronized (DisplayHardware.class) {
            if (generations.isRepairCurrent() && WakeRepairScheduler.isPending())
                WakeRepairScheduler.noteDisplayOn();
        }
    }

    /** LidGuard decides policy; this method serializes and rechecks the action. */
    public static boolean requestGuardSleep() {
        synchronized (DisplayHardware.class) {
            if (BootSafety.isHeld() || !"closed".equals(LidGuard.lid())
                    || !"0".equals(LidGuard.externalDisplayCached())) return false;
            generations.invalidate();
            cancelRepairLocked();
            Process process = null;
            try {
                process = new ProcessBuilder("input", "keyevent", "223").start();
                boolean slept = ProcessWait.exited(process, 2000L)
                        && process.exitValue() == 0;
                status = slept ? "SLEEP_REQUESTED" : "SLEEP_FAILED";
                return slept;
            } catch (Throwable error) {
                status = "SLEEP_FAILED";
                Log.e(TAG, "guard sleep command failed", error);
                return false;
            } finally {
                if (process != null) process.destroy();
            }
        }
    }

    private static void scheduleRepairLocked() {
        generations.scheduleRepair();
        WakeRepairScheduler.scheduleFromWake();
    }
    private static void cancelRepairLocked() {
        generations.completeRepair();
        WakeRepairScheduler.cancel();
    }
    private static boolean attempt(boolean on, String reason, String mode) {
        String key = reason + ':' + mode + ':' + (on ? '1' : '0');
        long now = SystemClock.elapsedRealtime();
        if (key.equals(lastAttemptKey) && now - lastAttemptAt < 1500L) return false;
        lastAttemptKey = key;
        lastAttemptAt = now;
        return applyConfirmed(on, reason, mode);
    }
    private static boolean applyConfirmed(boolean on, String reason, String expectedMode) {
        if (!expectedMode.equals(readCurrentMode())) {
            status = "MODE_CHANGED";
            return false;
        }
        String expected = on ? "1" : "0";
        String top = Telemetry.topCrtcActive();
        String before = Telemetry.bottomCrtcActive();
        if (!DisplayDecisionModel.readyForReconcile(expectedMode, top, before)
                || (on && "0".equals(top) && "0".equals(before))) {
            status = "DISPLAY_ASLEEP_OR_TRANSITION";
            return false;
        }
        if (!("0".equals(before) || "1".equals(before))) {
            status = "HARDWARE_UNKNOWN";
            return false;
        }
        if (!expected.equals(before) && !DisplayHardware.apply(on, reason)) {
            status = "APPLY_FAILED";
            return false;
        }
        long deadline = SystemClock.elapsedRealtime() + 1200L;
        while (!expected.equals(Telemetry.bottomCrtcActive())
                && SystemClock.elapsedRealtime() < deadline) {
            try { Thread.sleep(80L); } catch (InterruptedException error) {
                Thread.currentThread().interrupt();
                status = "INTERRUPTED";
                return false;
            }
        }
        String finalBottom = Telemetry.bottomCrtcActive();
        boolean confirmed = expectedMode.equals(readCurrentMode())
                && expected.equals(finalBottom)
                && DisplayDecisionModel.topCompatible(expectedMode,
                        Telemetry.topCrtcActive());
        if (!confirmed) DisplayHardware.alignPropertyWithCrtc(finalBottom);
        status = confirmed ? "CONFIRMED" : "UNCONFIRMED";
        return confirmed;
    }
    private static String readCurrentMode() {
        Process process = null;
        try {
            process = new ProcessBuilder("settings", "get", "system",
                    "dual_screen_display_mode").start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0) return "?";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return BootSafety.knownMode(value) ? value : "?";
            }
        } catch (Throwable error) {
            return "?";
        } finally {
            if (process != null) process.destroy();
        }
    }
}
