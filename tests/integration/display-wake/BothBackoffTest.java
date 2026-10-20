package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * Real coordinator/hardware with synthetic platform boundaries (host contract +
 * simulated compositor stall). Shape of recurrence B, 2026-10-20: an OFF blocked
 * ~29.6 s behind the vendor power-reset loop, the mode became BOTH meanwhile, and
 * BOTH reconciliation re-powered the failing lower panel. Not a physical reproduction.
 * Uses only APIs present in B so that running it on B is a meaningful negative control.
 * Arguments: none = all, or one of "trace", "control", "threshold", "clear", "topoff",
 * "failure", "bothwake", "powercycle", "unknown".
 */
public final class BothBackoffTest {
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
    private static void reset() { SystemClock.now += 100000L; TestDisplayState.baseline(); Log.LINES.clear(); }
    /** TOP wake repair OFF blocked for callMs while the vendor mode switches to BOTH. */
    private static void staleOff(long callMs) {
        reset();
        TestDisplayState.wakeTop();
        SystemClock.now = WakeRepairScheduler.deadline;
        TestDisplayState.modeDuringCall = "0";
        TestDisplayState.callAdvanceMs = callMs;
        DisplayActionCoordinator.onWatcherSample("1");
        TestDisplayState.modeDuringCall = null;
        TestDisplayState.callAdvanceMs = 0L;
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom), "BO-00 stale OFF executed");
        SystemClock.now += 500L;
        DisplayActionCoordinator.onWatcherSample("0");
    }
    private static void trace() {
        staleOff(29600L);
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom),
                "BO-02 failing lower panel must not be re-powered automatically after a stalled call: calls="
                + TestDisplayState.hardwareCalls);
        check("BOTH_ON_BACKOFF".equals(DisplayActionCoordinator.status()), "BO-03 status " + DisplayActionCoordinator.status());
        for (int i = 0; i < 5; i++) { SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0"); }
        check(TestDisplayState.hardwareCalls == 1 && count("ThorDisplayDaemon: WATCH_BOTH_SUPPRESSED") == 1
                && count("ThorDisplayDaemon: LOWER_ON_BACKOFF set reason=WAKE_REPAIR;call_ms=29600") == 1,
                "BO-04 suppression is stable and logged once: " + Log.LINES);
    }
    private static void control() {
        staleOff(0L);
        check(TestDisplayState.hardwareCalls == 2 && "1".equals(TestDisplayState.bottom),
                "CT-01 without a stall BOTH reconciliation restores the lower panel (contract kept)");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF") == 0, "CT-02 no back-off without stall");
        check(TestDisplayState.powerQueries == 0, "CT-03 no power query without back-off");
    }
    private static void threshold() {
        staleOff(1710L);   // slowest completed transition observed
        check(TestDisplayState.hardwareCalls == 2, "TH-01 1710 ms call does not trigger back-off");
        staleOff(5000L);   // strict threshold
        check(TestDisplayState.hardwareCalls == 2, "TH-02 5000 ms call does not trigger back-off");
        staleOff(7200L);   // shortest stall observed
        check(TestDisplayState.hardwareCalls == 1, "TH-03 7200 ms call triggers back-off");
    }
    private static void clear() {
        staleOff(29600L);
        SystemClock.now += 2000L;
        TestDisplayState.mode = "1";
        DisplayActionCoordinator.onWatcherSample("1");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared cause=MODE_1") == 1, "CL-01 leaving BOTH clears: " + Log.LINES);
        SystemClock.now += 2000L;
        TestDisplayState.mode = "0"; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 2 && "1".equals(TestDisplayState.bottom), "CL-02 re-entering BOTH powers the lower panel");
        staleOff(29600L);
        check(TestDisplayState.hardwareCalls == 1, "CL-03 back-off set again");
        check(DisplayActionCoordinator.requestFix(true).startsWith("ok=1"), "CL-04 explicit user reconcile still acts");
        check(TestDisplayState.hardwareCalls == 2 && "1".equals(TestDisplayState.bottom)
                && count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared cause=USER_RECONCILE") == 1, "CL-05 user reconcile clears: " + Log.LINES);
    }
    private static void topoff() {
        reset();
        TestDisplayState.mode = "1"; TestDisplayState.top = "1"; TestDisplayState.bottom = "1";
        TestDisplayState.property = "0";
        TestDisplayState.vendorGate = true; TestDisplayState.platformPropertyDuringCall = "1";
        TestDisplayState.callAdvanceMs = 16200L;
        SystemClock.autoAdvanceMs = 700L;
        DisplayActionCoordinator.onWatcherSample("1");
        SystemClock.autoAdvanceMs = 0L;
        TestDisplayState.vendorGate = false; TestDisplayState.platformPropertyDuringCall = null;
        TestDisplayState.callAdvanceMs = 0L;
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom), "TO-01 TOP OFF skipped by gate (incident-1 shape)");
        TestDisplayState.property = "0";
        SystemClock.now += 3000L;
        DisplayActionCoordinator.onWatcherSample("1");
        check(TestDisplayState.hardwareCalls == 2 && "0".equals(TestDisplayState.bottom),
                "TO-02 back-off never blocks the lower OFF that ends the loop: " + Log.LINES);
    }
    /** Review gap 1: a stalled call that ends in an error must also start the back-off. */
    private static void failure() {
        reset();
        TestDisplayState.wakeTop();
        SystemClock.now = WakeRepairScheduler.deadline;
        TestDisplayState.callAdvanceMs = 29600L;
        TestDisplayState.powerFailure = true;
        DisplayActionCoordinator.onWatcherSample("1");
        TestDisplayState.callAdvanceMs = 0L;
        TestDisplayState.powerFailure = false;
        check("APPLY_FAILED".equals(DisplayActionCoordinator.status()) || TestDisplayState.hardwareCalls == 1,
                "FA-01 stalled call failed");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF set reason=WAKE_REPAIR;call_ms=29600") == 1,
                "FA-02 stalled failing call starts back-off: " + Log.LINES);
        SystemClock.now += 2000L;
        TestDisplayState.mode = "0"; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 1, "FA-03 no automatic lower ON after the failed stalled call");
    }
    /**
     * Review gap 2 (characterization, documented behaviour): a sleep/wake while staying
     * in BOTH does not clear the flag; CRTC 0/0 is not used as a clear signal because the
     * vendor loop passes through 0/0 every cycle. At wake the platform powers both panels.
     */
    private static void bothwake() {
        // CRTC 0/0 without an observed non-interactive state (vendor loop phases).
        staleOff(29600L);
        TestDisplayState.top = "0"; TestDisplayState.bottom = "0";
        for (int i = 0; i < 3; i++) { SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0"); }
        TestDisplayState.top = "1"; TestDisplayState.bottom = "1";      // platform wake in BOTH
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 1, "BW-01 platform wake needs no app action");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared") == 0, "BW-02 BOTH sleep/wake (CRTC 0/0) does not clear");
        TestDisplayState.bottom = "0";
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 1, "BW-03 later lower drop in BOTH still not re-powered by the app");
    }
    /** Real sleep/wake while staying in BOTH: interactive false then true clears. */
    private static void powercycle() {
        staleOff(29600L);
        TestDisplayState.interactive = false; TestDisplayState.top = "0"; TestDisplayState.bottom = "0";
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared") == 0, "PC-01 sleep alone does not clear");
        TestDisplayState.interactive = true; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared cause=POWER_CYCLE") == 1, "PC-02 wake after sleep clears: " + Log.LINES);
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 2 && "1".equals(TestDisplayState.bottom), "PC-03 BOTH reconciliation resumes after a real power cycle");
        int queries = TestDisplayState.powerQueries;
        for (int i = 0; i < 5; i++) { SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0"); }
        check(TestDisplayState.powerQueries == queries, "PC-04 no power query while back-off is clear");
    }
    /** Query unavailable or rate-limited: never clears. */
    private static void unknown() {
        staleOff(29600L);
        TestDisplayState.powerServiceMissing = true;
        TestDisplayState.interactive = false; SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        TestDisplayState.interactive = true;  SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared") == 0 && TestDisplayState.hardwareCalls == 1,
                "UN-01 unavailable power service never clears");
        TestDisplayState.powerServiceMissing = false;
        int before = TestDisplayState.powerQueries;
        for (int i = 0; i < 10; i++) { SystemClock.now += 100L; DisplayActionCoordinator.onWatcherSample("0"); }
        check(TestDisplayState.powerQueries - before <= 1, "UN-02 at most one query per second: " + (TestDisplayState.powerQueries - before));
    }
    public static void main(String[] args) {
        String only = args.length > 0 ? args[0] : "all";
        if ("all".equals(only) || "trace".equals(only)) trace();
        if ("all".equals(only) || "control".equals(only)) control();
        if ("all".equals(only) || "threshold".equals(only)) threshold();
        if ("all".equals(only) || "clear".equals(only)) clear();
        if ("all".equals(only) || "topoff".equals(only)) topoff();
        if ("all".equals(only) || "failure".equals(only)) failure();
        if ("all".equals(only) || "bothwake".equals(only)) bothwake();
        if ("all".equals(only) || "powercycle".equals(only)) powercycle();
        if ("all".equals(only) || "unknown".equals(only)) unknown();
        System.out.println("BothBackoffTest passed (" + only + "): " + checks + " assertions (host contract + simulated stall)");
    }
}
