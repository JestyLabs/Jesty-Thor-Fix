package com.thor.displaypowertest;

/**
 * Android-retirement fixture ONLY. Deliberately refuses any timer configuration.
 * It cannot create an input monitor, request display power, or alter a vendor panel.
 * The real BottomIdleRuntime is compiled and tested in the product APK/other suites.
 */
final class BottomIdleRuntime {
    static String configure(int minutes) {
        return "ok=0;error=IDLE_UNAVAILABLE_IN_RETIREMENT_FIXTURE";
    }
    static boolean isOffOrPending() { return false; }
    static String snapshotFields() {
        return ";idle_enabled=0;idle_minutes=0;idle_input=OFF;idle_off=0";
    }
}
