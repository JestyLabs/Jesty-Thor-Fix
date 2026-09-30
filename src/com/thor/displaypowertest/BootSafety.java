package com.thor.displaypowertest;

/** Shared gate for every daemon path that can change display or sleep state. */
public final class BootSafety {
    private static volatile String phase = "READY";
    private static volatile boolean held;

    private BootSafety() {}

    public static void begin(boolean bootHold) {
        held = bootHold;
        phase = bootHold ? "BOOT HOLD" : "READY";
    }

    public static boolean isHeld() { return held; }
    public static String phase() { return phase; }

    public static void phase(String value) { phase = value; }

    public static void ready() {
        phase = "READY";
        held = false;
    }

    public static void timeout() { phase = "BOOT SAFETY TIMEOUT"; }

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
