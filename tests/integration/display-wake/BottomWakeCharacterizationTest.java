package com.thor.displaypowertest;

import android.os.SystemClock;

/** Production BOTH reconciliation with synthetic observations, not optical flash reproduction. */
public final class BottomWakeCharacterizationTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void reset() {
        SystemClock.now += 10000L;
        TestDisplayState.baseline();
    }
    private static void sampleBoth(String top, String bottom) {
        TestDisplayState.mode = "0";
        TestDisplayState.top = top;
        TestDisplayState.bottom = bottom;
        DisplayActionCoordinator.onWatcherSample("0");
    }
    public static void main(String[] args) {
        reset();
        // A normal, already-awake TOP -> BOTH edge starts with flag0 from the TOP OFF.
        check("0".equals(TestDisplayState.property), "TOP baseline has lower-panel OFF flag");
        sampleBoth("1", "0");
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom),
                "TOP -> BOTH must restore the lower panel even when the previous flag was0");
        check("1".equals(TestDisplayState.property) && TestDisplayState.propertyWrites == 1,
                "the actual ON publishes its flag once");
        check("CONFIRMED".equals(DisplayActionCoordinator.status()), "BOTH restore confirms");
        for (int i=0; i<30; i++) {
            SystemClock.now += 20L;
            DisplayActionCoordinator.onWatcherSample("0");
        }
        check(TestDisplayState.hardwareCalls == 1 && TestDisplayState.propertyWrites == 1,
                "healthy BOTH samples do not repeatedly power-cycle the lower panel");

        reset(); sampleBoth("0", "0");
        check(TestDisplayState.hardwareCalls == 0 && TestDisplayState.propertyWrites == 0,
                "both CRTCs inactive does not request ON");
        check("DISPLAY_ASLEEP_OR_TRANSITION".equals(DisplayActionCoordinator.status()),
                "inactive panels retain transition status");
        TestDisplayState.property = "1";
        sampleBoth("1", "1");
        check(TestDisplayState.hardwareCalls == 0 && TestDisplayState.propertyWrites == 0,
                "firmware-restored BOTH needs no additional app ON");

        reset(); sampleBoth("1", "0");
        check(TestDisplayState.hardwareCalls == 1,
                "mixed CRTC state alone is not an authoritative global-sleep signal");
        check(!DisplayActionCoordinator.isWakeRepairPending(), "BOTH does not retain TOP repair");

        for (int gate=0; gate<2; gate++) {
            reset(); TestDisplayState.held = gate==0; TestDisplayState.enabled = gate!=1;
            sampleBoth("1", "0");
            check(TestDisplayState.hardwareCalls == 0 && TestDisplayState.propertyWrites == 0,
                    "boot hold and disabled fix cannot request automatic BOTH ON");
        }
        reset(); sampleBoth("?", "0");
        check(TestDisplayState.hardwareCalls == 0, "unknown upper CRTC cannot authorize ON");
        reset(); sampleBoth("1", "?");
        check(TestDisplayState.hardwareCalls == 0, "unknown lower CRTC cannot authorize ON");
        reset(); TestDisplayState.mode = "?";
        DisplayActionCoordinator.onWatcherSample("?");
        check(TestDisplayState.hardwareCalls == 0, "unknown mode cannot authorize ON");
        reset(); TestDisplayState.mode = "2";
        DisplayActionCoordinator.onWatcherSample("2");
        check(TestDisplayState.hardwareCalls == 0, "BOTTOM ONLY is not an automatic dark-panel override");
        reset();
        // The sampled BOTH mode is obsolete; the command-boundary reader still sees TOP.
        DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 0 && TestDisplayState.propertyWrites == 0,
                "fresh mode mismatch prevents a stale BOTH ON");
        System.out.println("BottomWakeCharacterizationTest passed: " + checks
                + " assertions (production classes; synthetic panel observations)");
    }
}
