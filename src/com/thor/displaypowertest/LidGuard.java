package com.thor.displaypowertest;

/**
 * Stable daemon and smali-facing API for the optional Lid Protection feature.
 * Keep method names/signatures and IPC/telemetry unchanged while the feature
 * implementation remains isolated and in maintenance mode.
 */
public final class LidGuard {
    private LidGuard() {}

    public static boolean setEnabled(boolean value) {
        return LidProtectionRuntime.setEnabled(value);
    }

    public static boolean isEnabled() { return LidProtectionRuntime.isEnabled(); }
    public static String lid() { return LidProtectionRuntime.lid(); }
    public static String state() { return LidProtectionRuntime.state(); }
    public static int blockedWakes() { return LidProtectionRuntime.blockedWakes(); }
    public static String lastAction() { return LidProtectionRuntime.lastAction(); }
    public static String externalDisplay() { return LidProtectionRuntime.externalDisplay(); }
    static String externalDisplayCached() { return LidProtectionRuntime.externalDisplayCached(); }
    public static boolean onWakeEvent() { return LidProtectionRuntime.onWakeEvent(); }
}
