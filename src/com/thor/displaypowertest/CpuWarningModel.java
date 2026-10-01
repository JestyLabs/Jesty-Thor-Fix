package com.thor.displaypowertest;

/** Keeps a confirmed clock symptom distinct from the CPU Fix's effective state. */
public final class CpuWarningModel {
    private CpuWarningModel() {}

    public static boolean mayMeasure(boolean bootHeld, boolean intentMismatch,
            String phase) {
        return !bootHeld && !intentMismatch
                && ("CONFIRMED".equals(phase) || "UNKNOWN".equals(phase));
    }

    public static String text(boolean bootHeld, boolean watcherReady,
            boolean intentMismatch, String phase, DashboardStateModel.ClockStatus clocks) {
        if (bootHeld) return "BOOT SAFETY ACTIVE";
        if (!watcherReady) return "DISPLAY MONITOR NOT RUNNING";
        if (intentMismatch) return "CPU FIX SETTING NOT APPLIED";
        if ("UNKNOWN".equals(phase)) {
            return clocks != null && clocks.pinned
                    ? "CPU FIX UNKNOWN \u00B7 CLOCKS PINNED"
                    : "CPU FIX STATE UNKNOWN \u00B7 NO RESTART";
        }
        if ("ERROR".equals(phase) || "MISMATCH".equals(phase))
            return "CPU FIX NOT CONFIRMED";
        return clocks == null ? "CPU FIX CHECKING\u2026" : clocks.text;
    }
}
