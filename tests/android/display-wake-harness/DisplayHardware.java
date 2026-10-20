package com.thor.displaypowertest;

/** Synthetic panel boundary: never invokes the vendor bridge or system properties. */
public final class DisplayHardware {
    public static String getProperty() { return TestDisplayState.property; }
    private static void set(String value) {
        TestDisplayState.property = value;
        TestDisplayState.propertyWrites++;
    }
    public static synchronized boolean apply(boolean on, String reason) {
        TestDisplayState.hardwareCalls++;
        if (TestDisplayState.callAdvanceMs > 0) {
            // Real elapsed Android time, synthetic panel boundary. Never calls SurfaceControl.
            android.os.SystemClock.sleep(TestDisplayState.callAdvanceMs);
        }
        if (TestDisplayState.powerFailure) return false;
        TestDisplayState.bottom = on ? "1" : "0";
        set(TestDisplayState.bottom);
        return true;
    }
    public static synchronized void alignPropertyWithCrtc(String value) { set(value); }
    // Enables characterization of the previous coordinator in a private control build.
    public static synchronized boolean markWakePending() { set("0"); return true; }
}
