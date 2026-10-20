package com.thor.displaypowertest;

import android.os.SystemClock;

/** Production coordinator regression; synthetic panel and platform boundaries. */
public final class ContainmentStatusRegression {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        String testCase = args.length == 0 ? "failure" : args[0];
        if (!"failure".equals(testCase) && !"unconfirmed".equals(testCase)
                && !"mode-changed".equals(testCase)) throw new IllegalArgumentException(testCase);
        SystemClock.now += 100000L;
        TestDisplayState.baseline();
        TestDisplayState.mode = "0";
        TestDisplayState.top = "1";
        TestDisplayState.bottom = "0";
        TestDisplayState.property = "1";
        TestDisplayState.callAdvanceMs = 21000L;
        SystemClock.now += 2000L;
        DisplayActionCoordinator.onWatcherSample("0");
        TestDisplayState.callAdvanceMs = 0L;
        SystemClock.now += 2000L;
        DisplayActionCoordinator.onWatcherSample("0");
        check("0".equals(TestDisplayState.bottom), "SETUP lower panel isolated");
        check("BOTH_DEGRADED".equals(DisplayActionCoordinator.status()), "SETUP isolation confirmed");

        // Platform reset reasserts lower ON after a prior successful isolation.
        TestDisplayState.bottom = "1";
        if ("mode-changed".equals(testCase)) {
            TestDisplayState.mode = "1";
        } else if ("unconfirmed".equals(testCase)) {
            TestDisplayState.vendorGate = true;
            TestDisplayState.platformPropertyDuringCall = "1";
            SystemClock.autoAdvanceMs = 700L;
        } else {
            TestDisplayState.powerFailure = true;
        }
        SystemClock.now += 2000L;
        DisplayActionCoordinator.onWatcherSample("0");
        SystemClock.autoAdvanceMs = 0L;
        String expected = "mode-changed".equals(testCase) ? "MODE_CHANGED"
                : "unconfirmed".equals(testCase) ? "UNCONFIRMED" : "APPLY_FAILED";
        System.out.println("case=" + testCase + ";bottom=" + TestDisplayState.bottom
                + ";status=" + DisplayActionCoordinator.status()
                + ";hardware_calls=" + TestDisplayState.hardwareCalls
                + ";property_writes=" + TestDisplayState.propertyWrites);
        check("1".equals(TestDisplayState.bottom), "REG-01 failed OFF leaves lower observed active");
        check(TestDisplayState.hardwareCalls == ("mode-changed".equals(testCase) ? 2 : 3), "REG-02 exactly one OFF retry");
        check(DisplayActionCoordinator.isLowerOnBackoff(), "REG-03 ON back-off remains active");
        check(expected.equals(DisplayActionCoordinator.status()),
                "REG-04 preserve " + expected + " instead of historical BOTH_DEGRADED");

        if ("failure".equals(testCase)) {
            int calls = TestDisplayState.hardwareCalls;
            int writes = TestDisplayState.propertyWrites;
            for (int i = 0; i < 14; i++) {
                SystemClock.now += 100L;
                DisplayActionCoordinator.onWatcherSample("0");
            }
            check(TestDisplayState.hardwareCalls == calls, "REG-05 unchanged retry admission");
            check(TestDisplayState.propertyWrites == writes, "REG-06 no extra property write");
            check("BOTH_ON_BACKOFF".equals(DisplayActionCoordinator.status()),
                    "REG-07 waiting for retry does not claim isolation confirmed");
            TestDisplayState.powerFailure = false;
            SystemClock.now += 100L;
            DisplayActionCoordinator.onWatcherSample("0");
            check(TestDisplayState.hardwareCalls == calls + 1, "REG-08 admitted retry unchanged");
            check("0".equals(TestDisplayState.bottom)
                    && "BOTH_DEGRADED".equals(DisplayActionCoordinator.status()),
                    "REG-09 successful isolation becomes confirmed again");
        }
        if (!"failure".equals(testCase)) {
            int calls = TestDisplayState.hardwareCalls;
            TestDisplayState.mode = "0";
            TestDisplayState.vendorGate = false;
            TestDisplayState.platformPropertyDuringCall = null;
            SystemClock.now += 2000L;
            DisplayActionCoordinator.onWatcherSample("0");
            check(TestDisplayState.hardwareCalls == calls + 1,
                    "REG-10 recovery submits exactly one admitted OFF");
            check("0".equals(TestDisplayState.bottom)
                    && "BOTH_DEGRADED".equals(DisplayActionCoordinator.status()),
                    "REG-11 current confirmed isolation restores degraded status");
        }
        System.out.println("ContainmentStatusRegression passed: " + checks
                + " assertions (" + testCase + "; host only)");
    }
}
