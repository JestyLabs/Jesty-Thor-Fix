package com.thor.displaypowertest;

import android.os.SystemClock;

/** Shared gate for every daemon path that can change display or sleep state. */
public final class BootSafety {
    private static volatile String phase = "READY";
    private static volatile boolean held;
    private static volatile long phaseSince = SystemClock.elapsedRealtime();

    private BootSafety() {}

    public static void begin(boolean bootHold) {
        held = bootHold;
        setPhase(bootHold ? "BOOT HOLD" : "READY");
    }

    public static boolean isHeld() { return held; }
    public static String phase() { return phase; }

    /** Milliseconds since the current phase was entered; lets AutoService spot a stalled phase. */
    public static long phaseAgeMs() {
        return Math.max(0L, SystemClock.elapsedRealtime() - phaseSince);
    }

    public static void phase(String value) { setPhase(value); }

    public static void ready() {
        setPhase("READY");
        held = false;
    }

    public static void timeout() { setPhase("BOOT SAFETY TIMEOUT"); }

    /**
     * A failed compositor handover may leave this daemon with stale framework
     * binders. Hold display actions, including at runtime, until AutoService
     * replaces the daemon.
     */
    public static void handoffFailed() {
        held = true;
        setPhase(DaemonLaunchModel.HANDOFF_FAILED_PHASE.replace('_', ' '));
    }

    private static void setPhase(String value) {
        phaseSince = SystemClock.elapsedRealtime();
        phase = value;
    }

    /** Avoid repeating a transition when the boot coordinator already reached its target. */
    public static boolean shouldApplyMode(String mode) {
        if (held) return false;
        String bottom = Telemetry.bottomCrtcActive();
        return BootGateModel.displayActionRequired(mode, DaemonState.isEnabled(), bottom);
    }

    public static boolean shouldRepairStableTop(String mode, boolean wakeRepairPending) {
        return !held && BootGateModel.shouldRepairStableTop(mode,
                Telemetry.topCrtcActive(), Telemetry.bottomCrtcActive(),
                DaemonState.isEnabled(), wakeRepairPending);
    }

    public static boolean knownMode(String mode) {
        return "0".equals(mode) || "1".equals(mode) || "2".equals(mode);
    }
}
