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
 * "failure", "bothwake", "powercycle", "unknown", "degrade", "nodegrade", "gate", "restore".
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
        // CRTC 0/0 then 1/1 without an observed non-interactive state (vendor loop phases).
        staleOff(29600L);
        TestDisplayState.top = "0"; TestDisplayState.bottom = "0";
        for (int i = 0; i < 3; i++) { SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0"); }
        TestDisplayState.top = "1"; TestDisplayState.bottom = "1";      // loop restores the lower panel
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 2 && "0".equals(TestDisplayState.bottom),
                "BW-01 without an interactive transition 0/0 -> 1/1 is treated as the loop: degrade OFF");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared") == 0, "BW-02 CRTC 0/0 alone does not clear");
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 2, "BW-03 degraded lower panel is not re-powered by the app");
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
    /** Pure BOTH loop: platform keeps the lower ON; the app's ON probe stalls. */
    private static void loopProbe(long callMs) {
        reset();
        TestDisplayState.mode = "0"; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        TestDisplayState.property = "1";
        TestDisplayState.callAdvanceMs = callMs;
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");   // WATCH_BOTH ON
        TestDisplayState.callAdvanceMs = 0L;
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom), "DG-00 ON probe executed");
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");   // loop phase: lower active
    }
    private static void degrade() {
        loopProbe(21000L);
        check(TestDisplayState.hardwareCalls == 2 && "0".equals(TestDisplayState.bottom),
                "DG-01 after a stalled call the lower panel is turned OFF in BOTH: calls=" + TestDisplayState.hardwareCalls);
        check("BOTH_DEGRADED".equals(DisplayActionCoordinator.status()) && count("ThorDisplayDaemon: BOTH_DEGRADE OFF") == 1,
                "DG-02 degrade recorded: " + DisplayActionCoordinator.status() + " " + Log.LINES);
        for (int i = 0; i < 5; i++) { SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0"); }
        check(TestDisplayState.hardwareCalls == 2 && "BOTH_DEGRADED".equals(DisplayActionCoordinator.status()),
                "DG-03 degraded state is stable: no ON, no further OFF");
    }
    private static void nodegrade() {
        loopProbe(0L);
        for (int i = 0; i < 3; i++) { SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0"); }
        check(TestDisplayState.hardwareCalls == 1 && "1".equals(TestDisplayState.bottom),
                "ND-01 without a stall BOTH keeps the lower panel ON (contract kept)");
    }
    private static void gate() {
        reset();
        TestDisplayState.mode = "0"; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        TestDisplayState.callAdvanceMs = 21000L;
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        TestDisplayState.callAdvanceMs = 0L;
        TestDisplayState.vendorGate = true; TestDisplayState.platformPropertyDuringCall = "1";
        SystemClock.autoAdvanceMs = 700L;
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        SystemClock.autoAdvanceMs = 0L;
        TestDisplayState.vendorGate = false; TestDisplayState.platformPropertyDuringCall = null;
        check(TestDisplayState.gateRefusals == 1 && "1".equals(TestDisplayState.bottom), "GT-01 first degrade OFF skipped by the compositor gate");
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 3 && "0".equals(TestDisplayState.bottom), "GT-02 degrade OFF retried after the existing 1.5 s admission");
    }
    private static void restore() {
        loopProbe(21000L);
        check("0".equals(TestDisplayState.bottom), "RS-00 degraded");
        TestDisplayState.interactive = false; TestDisplayState.top = "0";
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        TestDisplayState.interactive = true; TestDisplayState.top = "1";
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared cause=POWER_CYCLE") == 1 && "1".equals(TestDisplayState.bottom),
                "RS-01 a real sleep/wake ends the degrade and BOTH powers the lower panel again: " + Log.LINES);
        staleOff(29600L);
        check("BOTH_ON_BACKOFF".equals(DisplayActionCoordinator.status()),
                "RS-02 a new back-off episode does not inherit the degraded label: " + DisplayActionCoordinator.status());
    }
    /** App-specific gates must still hold after the long call has returned. */
    private static void gates() {
        for (String boundary : new String[]{"boot", "disabled", "stale-bottom", "unknown-top", "unknown-bottom"}) {
            staleOff(29600L);
            TestDisplayState.bottom = "1";
            if ("boot".equals(boundary)) TestDisplayState.held = true;
            if ("disabled".equals(boundary)) TestDisplayState.enabled = false;
            if ("stale-bottom".equals(boundary)) TestDisplayState.mode = "2";
            if ("unknown-top".equals(boundary)) TestDisplayState.top = "?";
            if ("unknown-bottom".equals(boundary)) TestDisplayState.bottom = "?";
            SystemClock.now += 2000L;
            DisplayActionCoordinator.onWatcherSample("0");
            check(TestDisplayState.hardwareCalls == 1, "SAFE-01 no degrade across " + boundary);
        }
    }
    /** Refusal is not confirmation and cannot cause an immediate command flood. */
    private static void retry() {
        loopProbe(21000L); // lower OFF already confirmed once
        TestDisplayState.bottom = "1";
        TestDisplayState.powerFailure = true;
        SystemClock.now += 2000L;
        DisplayActionCoordinator.onWatcherSample("0");
        int before = TestDisplayState.hardwareCalls;
        for (int i = 0; i < 14; i++) {
            SystemClock.now += 100L;
            DisplayActionCoordinator.onWatcherSample("0");
        }
        check(TestDisplayState.hardwareCalls == before, "RETRY-01 no OFF within retry interval");
        SystemClock.now += 100L;
        DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == before + 1, "RETRY-02 admitted only after interval");
        check("1".equals(TestDisplayState.bottom), "RETRY-03 failed OFF did not change observed hardware");
    }
    /** Real JVM blocked call; platform changes mode while the watcher is inside it. */
    private static void replay() {
        reset();
        TestDisplayState.wakeTop();
        SystemClock.now = WakeRepairScheduler.deadline;
        TestDisplayState.callEntered = new java.util.concurrent.CountDownLatch(1);
        TestDisplayState.callRelease = new java.util.concurrent.CountDownLatch(1);
        final Throwable[] failure = {null};
        Thread watcher = new Thread(() -> {
            try { DisplayActionCoordinator.onWatcherSample("1"); }
            catch (Throwable error) { failure[0] = error; }
        });
        watcher.setDaemon(true);
        watcher.start();
        try {
            check(TestDisplayState.callEntered.await(3, java.util.concurrent.TimeUnit.SECONDS),
                    "REPLAY-01 watcher reached the synthetic blocked call");
            // Platform mode change, not a fabricated second watcher/coordinator caller.
            TestDisplayState.mode = "0";
            SystemClock.now += 29600L;
            TestDisplayState.callRelease.countDown();
            watcher.join(3000L);
        } catch (InterruptedException error) { throw new AssertionError(error); }
        check(!watcher.isAlive() && failure[0] == null, "REPLAY-02 caller returned normally");
        TestDisplayState.callEntered = null; TestDisplayState.callRelease = null;
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom),
                "REPLAY-03 late admitted TOP OFF completed under platform BOTH");
        SystemClock.now += 2000L;
        DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 1 && "BOTH_ON_BACKOFF".equals(DisplayActionCoordinator.status()),
                "REPLAY-04 late OFF cannot be followed by automatic re-ON");
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
        if ("all".equals(only) || "degrade".equals(only)) degrade();
        if ("all".equals(only) || "nodegrade".equals(only)) nodegrade();
        if ("all".equals(only) || "gate".equals(only)) gate();
        if ("all".equals(only) || "restore".equals(only)) restore();
        if ("all".equals(only) || "gates".equals(only)) gates();
        if ("all".equals(only) || "retry".equals(only)) retry();
        if ("all".equals(only) || "replay".equals(only)) replay();
        System.out.println("BothBackoffTest passed (" + only + "): " + checks + " assertions (host contract + simulated stall)");
    }
}
