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
 * "failure", "bothwake", "powercycle", "unknown", "degrade", "nodegrade", "gate", "restore",
 * "gates", "retry", "replay", "latchloop", "latchtopwake", "latchuser".
 *
 * S1 boot latch (2026-10-26): on 2026-10-20 the lower-panel failure persisted until reboot
 * and automatic BOTH ON re-entered the vendor reset loop twice, about 1 s after the app's own
 * OFF. A stalled call therefore also latches suppression of automatic lower ON for the rest
 * of the daemon process; mode change, TOP wake and sleep/wake clear only the back-off.
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
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared") == 0,
                "BO-05 staying in BOTH does not clear the back-off: " + Log.LINES);
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
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom),
                "CL-02 re-entering BOTH after a stall keeps the failed lower panel OFF (boot latch): calls="
                + TestDisplayState.hardwareCalls);
        staleOff(29600L);
        check(TestDisplayState.hardwareCalls == 1, "CL-03 back-off set again");
        check(DisplayActionCoordinator.requestFix(true).startsWith("ok=1"), "CL-04 explicit user reconcile still acts");
        check(TestDisplayState.hardwareCalls == 2 && "1".equals(TestDisplayState.bottom)
                && count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared cause=USER_RECONCILE") == 1, "CL-05 user reconcile clears: " + Log.LINES);
        check(count("ThorDisplayDaemon: LOWER_FAILURE_LATCH cleared cause=USER_RECONCILE") == 1,
                "CL-06 user reconcile is the only in-process latch clear: " + Log.LINES);
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
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom),
                "PC-03 a real power cycle clears the back-off but not the boot latch: no automatic lower ON");
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
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared cause=POWER_CYCLE") == 1 && "0".equals(TestDisplayState.bottom)
                && TestDisplayState.hardwareCalls == 2,
                "RS-01 a real sleep/wake clears the back-off; the boot latch keeps the lower panel OFF: " + Log.LINES);
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
    /**
     * Incident run shape (2026-10-20): after the degrade, a real sleep/wake clears the
     * back-off and the platform powers the lower panel again on wake. The latch must keep
     * the app from adding its own ON and must still allow the OFF that ended every run.
     */
    private static void latchloop() {
        loopProbe(21000L);
        check(TestDisplayState.hardwareCalls == 2 && "0".equals(TestDisplayState.bottom), "LA-00 degraded after the stalled probe");
        check(count("ThorDisplayDaemon: LOWER_FAILURE_LATCH set reason=WATCH_BOTH;call_ms=21000") == 1,
                "LA-00b stalled call latches once with its reason: " + Log.LINES);
        TestDisplayState.interactive = false; TestDisplayState.top = "0"; TestDisplayState.bottom = "0";
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        TestDisplayState.interactive = true; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(count("ThorDisplayDaemon: LOWER_ON_BACKOFF cleared cause=POWER_CYCLE") == 1, "LA-00c wake clears the back-off");
        TestDisplayState.bottom = "1";                       // platform wake powers the lower panel
        SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == 3 && "0".equals(TestDisplayState.bottom),
                "LA-01 after wake the latch keeps automatic ON out and turns the platform-powered lower panel OFF: calls="
                + TestDisplayState.hardwareCalls + " bottom=" + TestDisplayState.bottom);
        for (int i = 0; i < 5; i++) { SystemClock.now += 2000L; DisplayActionCoordinator.onWatcherSample("0"); }
        check(TestDisplayState.hardwareCalls == 3 && "BOTH_DEGRADED".equals(DisplayActionCoordinator.status()),
                "LA-02 latched degrade is stable: no re-ON, no further OFF: " + DisplayActionCoordinator.status());
    }
    /** Leaving BOTH through a TOP wake clears the back-off; returning to BOTH must not re-power. */
    private static void latchtopwake() {
        staleOff(29600L);
        SystemClock.now += 2000L;
        TestDisplayState.wakeTop();
        SystemClock.now += 100L;
        DisplayActionCoordinator.onWatcherSample("1");
        SystemClock.now = Math.max(SystemClock.now, WakeRepairScheduler.deadline);
        DisplayActionCoordinator.onWatcherSample("1");
        check("0".equals(TestDisplayState.bottom), "LA-03a TOP keeps the lower panel OFF");
        int before = TestDisplayState.hardwareCalls;
        SystemClock.now += 2000L;
        TestDisplayState.mode = "0"; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == before && "0".equals(TestDisplayState.bottom),
                "LA-03 returning to BOTH after a TOP wake does not re-power the latched lower panel: calls="
                + TestDisplayState.hardwareCalls + " before=" + before);
    }
    /** Explicit user reconcile remains the manual escape hatch and restores the normal contract. */
    private static void latchuser() {
        staleOff(29600L);
        check(DisplayActionCoordinator.requestFix(true).startsWith("ok=1") && "1".equals(TestDisplayState.bottom),
                "LA-04 confirmed manual reconcile still powers the lower panel in BOTH");
        check(count("ThorDisplayDaemon: LOWER_FAILURE_LATCH cleared cause=USER_RECONCILE") == 1,
                "LA-04b manual reconcile clears the latch: " + Log.LINES);
        SystemClock.now += 2000L;
        TestDisplayState.mode = "1"; TestDisplayState.top = "1"; TestDisplayState.bottom = "1";
        DisplayActionCoordinator.onWatcherSample("1");
        SystemClock.now += 2000L;
        DisplayActionCoordinator.onWatcherSample("1");
        int before = TestDisplayState.hardwareCalls;
        SystemClock.now += 2000L;
        TestDisplayState.mode = "0"; TestDisplayState.top = "1"; TestDisplayState.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("0");
        check(TestDisplayState.hardwareCalls == before + 1 && "1".equals(TestDisplayState.bottom),
                "LA-05 after manual reconcile BOTH reconciliation is normal again: calls="
                + TestDisplayState.hardwareCalls + " before=" + before);
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
        if ("all".equals(only) || "latchloop".equals(only)) latchloop();
        if ("all".equals(only) || "latchtopwake".equals(only)) latchtopwake();
        if ("all".equals(only) || "latchuser".equals(only)) latchuser();
        System.out.println("BothBackoffTest passed (" + only + "): " + checks + " assertions (host contract + simulated stall)");
    }
}
