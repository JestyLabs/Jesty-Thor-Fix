package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * Real coordinator/hardware with synthetic platform boundaries. Shape of the S1 sleep-order
 * race (physical session 2026-10-28, 2/2 BOTH lid sleeps on 1.7.6, and 7 of 20 BOTH sleeps on
 * 1.7.5 in the long-sleep collector): going to sleep in BOTH, the platform powered the lower
 * panel off first; while the top CRTC was still active the watcher saw mode=0;top=1;bottom=0,
 * WATCH_BOTH re-powered the lower panel, and the platform then turned only the top off. The
 * lower panel stayed powered for the whole lid-closed sleep. Not a physical reproduction.
 * Arguments: none = all, or one of "race", "lag", "loss", "switch", "nopower", "wakeloss", "toggle".
 */
public final class SleepOrderRaceTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static int count(String prefix) {
        int n = 0;
        for (String line : Log.LINES) if (line.startsWith(prefix)) n++;
        return n;
    }
    /** Stable BOTH with both panels active and an interactive device. */
    private static void stableBoth() {
        SystemClock.now += 100000L;
        TestDisplayState.baseline();
        TestDisplayState.both();
        SystemClock.now += 2000L;
        DisplayActionCoordinator.onWatcherSample("0");
        Log.LINES.clear();
        TestDisplayState.hardwareCalls = 0;
        TestDisplayState.powerQueries = 0;
    }
    private static void sample(long advanceMs) {
        SystemClock.now += advanceMs;
        DisplayActionCoordinator.onWatcherSample(TestDisplayState.mode);
    }
    /**
     * Observed order: interactive already false, lower OFF first, top OFF ~0.41 s later
     * (SurfaceFlinger power-mode requests 0.41 s apart on 2026-10-28), beyond the window.
     */
    private static void race() {
        stableBoth();
        TestDisplayState.interactive = false;
        TestDisplayState.bottom = "0";
        sample(0L);
        sample(20L);
        sample(200L);
        sample(200L);
        check(TestDisplayState.hardwareCalls == 0 && "0".equals(TestDisplayState.bottom),
                "SR-01 going to sleep, the lower panel must not be re-powered while the top is still "
                + "turning off: calls=" + TestDisplayState.hardwareCalls + " " + Log.LINES);
        TestDisplayState.top = "0";
        sample(20L);
        for (int i = 0; i < 5; i++) sample(2000L);
        check(TestDisplayState.hardwareCalls == 0 && "0".equals(TestDisplayState.bottom),
                "SR-02 the lower panel stays off for the whole sleep: calls=" + TestDisplayState.hardwareCalls);
        check(count("ThorDisplayDaemon: WATCH_BOTH_SKIPPED reason=NON_INTERACTIVE") == 1,
                "SR-03 the skipped repair is logged once: " + Log.LINES);
        TestDisplayState.interactive = true;
        TestDisplayState.top = "1";
        TestDisplayState.bottom = "1";
        sample(2000L);
        sample(2000L);
        check(TestDisplayState.hardwareCalls == 0, "SR-04 a normal BOTH wake needs no app action");
    }
    /** isInteractive() may still report true at the first lower OFF; the top follows quickly. */
    private static void lag() {
        stableBoth();
        TestDisplayState.bottom = "0";
        sample(0L);
        sample(20L);
        TestDisplayState.top = "0";
        sample(180L);
        for (int i = 0; i < 3; i++) sample(2000L);
        check(TestDisplayState.hardwareCalls == 0 && "0".equals(TestDisplayState.bottom),
                "SR-05 a lower OFF followed by the top OFF within the confirmation window is a sleep, "
                + "not a lost lower panel: calls=" + TestDisplayState.hardwareCalls + " " + Log.LINES);
    }
    /** Contract kept: a lower panel lost while awake in stable BOTH is restored. */
    private static void loss() {
        stableBoth();
        TestDisplayState.bottom = "0";
        sample(0L);
        sample(100L);
        sample(150L);
        sample(150L);
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom),
                "SR-06 an awake lower-panel loss in BOTH is restored after confirmation: calls="
                + TestDisplayState.hardwareCalls + " " + Log.LINES);
        check(count("ThorDisplayDaemon: HW_CALL_OBSERVED reason=WATCH_BOTH") == 1, "SR-07 one WATCH_BOTH call: " + Log.LINES);
        check(TestDisplayState.powerQueries >= 1, "SR-08 the decision used a fresh interactive query");
    }
    /** A user TOP -> BOTH switch keeps its immediate lower ON, without a power query. */
    private static void modeSwitch() {
        SystemClock.now += 100000L;
        TestDisplayState.baseline();
        Log.LINES.clear();
        TestDisplayState.hardwareCalls = 0;
        TestDisplayState.powerQueries = 0;
        TestDisplayState.mode = "0";
        sample(500L);
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom),
                "SR-09 TOP -> BOTH turns the lower panel on in the same sample: calls=" + TestDisplayState.hardwareCalls);
        check(TestDisplayState.powerQueries == 0, "SR-10 no power query on a user mode switch");
    }
    /** Without a power service the previous behaviour is kept, only confirmed first. */
    private static void noPower() {
        stableBoth();
        TestDisplayState.powerServiceMissing = true;
        TestDisplayState.bottom = "0";
        sample(0L);
        sample(150L);
        sample(200L);
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom),
                "SR-11 unknown interactive state falls back to the restore contract: calls=" + TestDisplayState.hardwareCalls);
    }
    /** A BOTH wake where the lower panel does not come back is still repaired. */
    private static void wakeLoss() {
        stableBoth();
        TestDisplayState.interactive = false;
        TestDisplayState.top = "0";
        TestDisplayState.bottom = "0";
        sample(500L);
        sample(2000L);
        TestDisplayState.interactive = true;
        TestDisplayState.top = "1";
        sample(2000L);
        sample(150L);
        sample(200L);
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom),
                "SR-12 a lower panel missing after a BOTH wake is restored: calls=" + TestDisplayState.hardwareCalls
                + " " + Log.LINES);
    }
    /** The manual fix toggle leaves one observation line per request (D2 had no trace). */
    private static void toggle() {
        stableBoth();
        String off = DisplayActionCoordinator.requestFix(false);
        String on = DisplayActionCoordinator.requestFix(true);
        check(off.startsWith("ok=1;") && on.startsWith("ok=1;"), "SR-13 toggle accepted: " + off + " / " + on);
        check(count("ThorDisplayDaemon: USER_RECONCILE fix=0;result=CONFIRMED") == 1
                && count("ThorDisplayDaemon: USER_RECONCILE fix=1;result=CONFIRMED") == 1,
                "SR-14 each toggle request is logged once with its result: " + Log.LINES);
    }
    public static void main(String[] args) {
        String only = args.length > 0 ? args[0] : "all";
        if ("all".equals(only) || "race".equals(only)) race();
        if ("all".equals(only) || "lag".equals(only)) lag();
        if ("all".equals(only) || "loss".equals(only)) loss();
        if ("all".equals(only) || "switch".equals(only)) modeSwitch();
        if ("all".equals(only) || "nopower".equals(only)) noPower();
        if ("all".equals(only) || "wakeloss".equals(only)) wakeLoss();
        if ("all".equals(only) || "toggle".equals(only)) toggle();
        System.out.println("SleepOrderRaceTest passed (" + only + "): " + checks + " assertions (host contract)");
    }
}
