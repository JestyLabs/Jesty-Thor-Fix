package com.thor.displaypowertest;

import java.lang.reflect.Field;

/** S1-10: real coordinator, scripted clock/CRTC and inert vendor result. */
public final class CoordinatorFailureHostTest {
    private static int checks;
    private static int cases;
    private static void equal(Object expected, Object actual, String why) {
        checks++;
        if (!expected.equals(actual)) throw new AssertionError(why + ": " + actual);
    }
    private static void field(String name, Object value) throws Exception {
        Field f = DisplayActionCoordinator.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(null, value);
    }
    private static long reset() throws Exception {
        DisplayActionCoordinator.cancelWakeRepair();
        field("lastMode", "0");
        field("lastHardwareReadAt", -1L);
        field("lastAttemptAt", 0L);
        field("lastAttemptKey", "");
        field("status", "STARTING");
        // S1 sleep-order race: no pending stable-BOTH candidate and no recent mode edge.
        field("bothOnCandidateAt", -1L);
        field("candidateInteractive", "?");
        field("candidateQueried", false);
        field("candidateConfirmed", false);
        field("lastModeEdgeAt", Long.MIN_VALUE / 2);
        // Process-scoped lower-failure state (stall latch, poisoned boot): each case is a fresh daemon.
        field("lowerFailureLatched", false);
        field("lowerPoisonedReason", null);
        field("kernelSignalsAvailable", false);
        android.os.SystemClock.setElapsedRealtime(100000L);
        BootSafety.held = false;
        DaemonState.mode = "0";
        DaemonState.enabled = false;
        WatcherSupervisor.state = "RUNNING";
        WatcherCadence.skipRead = false;
        DisplayHardware.property = "0";
        DisplayHardware.scriptedApply = true;
        DisplayHardware.applySucceeds = true;
        DisplayHardware.applyElapsed = 0L;
        DisplayHardware.actionCount = 0;
        DisplayHardware.alignedCount = 0;
        Telemetry.top = "1";
        Telemetry.bottom = "0";
        Telemetry.scriptedPairs = null;
        Telemetry.scriptedBottom = null;
        Telemetry.bottomIndex = 0;
        Telemetry.pollElapsed = 0L;
        ReadbackMode.values = new String[] {"0", "0", "0"};
        ReadbackMode.index = 0;
        return DisplayActionCoordinator.generation();
    }
    private static void rejected(long generation, String status, String id) {
        equal("ok=0;error=DISPLAY_UNCONFIRMED", DisplayActionCoordinator.requestFix(true), id + " receipt");
        equal(status, DisplayActionCoordinator.status(), id + " status");
        equal(false, DaemonState.enabled, id + " no preference commit");
        equal(generation, DisplayActionCoordinator.generation(), id + " no generation commit");
        equal(1, DisplayHardware.actionCount, id + " single inert request");
    }
    private static void done(String id) { cases++; System.out.println("PASS " + id); }
    private static void applyFailure() throws Exception {
        long generation = reset();
        DisplayHardware.applySucceeds = false;
        Telemetry.pollElapsed = 1200L;
        rejected(generation, "APPLY_FAILED", "CF-apply-failure");
        equal(2, ReadbackMode.index, "CF-apply-failure no final mode read");
        equal(0, DisplayHardware.alignedCount, "CF-apply-failure rollback belongs to hardware");
        done("CF-apply-failure");
    }
    private static void retry() throws Exception {
        reset();
        DaemonState.enabled = true;
        DisplayHardware.applySucceeds = false;
        // Stable BOTH: the first observation is only a candidate; the ON follows the
        // confirming sample (300 ms, interactive unavailable on the host keeps the contract).
        DisplayActionCoordinator.onWatcherSample("0");
        equal(0, DisplayHardware.actionCount, "CF-retry stable-BOTH candidate only");
        android.os.SystemClock.setElapsedRealtime(100300L);
        DisplayActionCoordinator.onWatcherSample("0");
        equal(1, DisplayHardware.actionCount, "CF-retry first attempt");
        equal("APPLY_FAILED", DisplayActionCoordinator.status(), "CF-retry first fails");
        android.os.SystemClock.setElapsedRealtime(101799L);
        DisplayActionCoordinator.onWatcherSample("0");
        equal(1, DisplayHardware.actionCount, "CF-retry suppressed at 1499ms");
        android.os.SystemClock.setElapsedRealtime(101800L);
        DisplayActionCoordinator.onWatcherSample("0");
        equal(2, DisplayHardware.actionCount, "CF-retry admitted at 1500ms");
        equal("APPLY_FAILED", DisplayActionCoordinator.status(), "CF-retry still no success");
        done("CF-retry");
    }
    private static void pendingRepair() throws Exception {
        reset();
        field("lastMode", "1");
        DaemonState.mode = "1";
        DaemonState.enabled = true;
        DaemonState.repairResult = "initial";
        ReadbackMode.values = new String[] {"1", "1", "1"};
        Telemetry.bottom = "1";
        DisplayActionCoordinator.scheduleWakeRepair();
        WakeRepairScheduler.due = true;
        WakeRepairScheduler.completions = 0;
        DisplayHardware.applySucceeds = false;
        DisplayActionCoordinator.onWatcherSample("1");
        equal("RETRY_PENDING", DaemonState.repairResult, "CF-repair failed result");
        equal(true, WakeRepairScheduler.pending, "CF-repair remains pending");
        equal(0, WakeRepairScheduler.completions, "CF-repair not falsely completed");
        android.os.SystemClock.setElapsedRealtime(101499L);
        DisplayActionCoordinator.onWatcherSample("1");
        equal(1, DisplayHardware.actionCount, "CF-repair retry throttled");
        equal("RETRY_PENDING", DaemonState.repairResult, "CF-repair throttled result");
        android.os.SystemClock.setElapsedRealtime(101500L);
        DisplayHardware.applySucceeds = true;
        Telemetry.scriptedBottom = new String[] {"0"};
        Telemetry.scriptedPairs = new String[][] {{"1", "1"}, {"1", "1"}, {"1", "0"}};
        Telemetry.pairIndex = 0;
        DisplayActionCoordinator.onWatcherSample("1");
        equal(2, DisplayHardware.actionCount, "CF-repair next admitted request");
        equal("OFF_OK", DaemonState.repairResult, "CF-repair confirmed result");
        equal(false, WakeRepairScheduler.pending, "CF-repair pending cleared on confirmation");
        equal(1, WakeRepairScheduler.completions, "CF-repair complete once");
        DisplayActionCoordinator.noteDisplayOn();
        equal(0, WakeRepairScheduler.displayOnNotes, "CF-repair completed generation cannot extend repair");
        done("CF-pending-repair-failure-then-confirmation");
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if ("apply".equals(args[0])) applyFailure(); else retry();
            return;
        }
        applyFailure();
        long generation = reset();
        // A frozen fake clock would never expire. Advance 1200ms at the first
        // CRTC poll, exercising the real deadline without a 1.2s host sleep.
        Telemetry.pollElapsed = 1200L;
        rejected(generation, "UNCONFIRMED", "CF-readback-deadline");
        equal(101200L, android.os.SystemClock.elapsedRealtime(), "CF-readback-deadline exact");
        equal(1, DisplayHardware.alignedCount, "CF-readback-deadline realignment requested");
        done("CF-readback-deadline");
        generation = reset();
        Thread.currentThread().interrupt();
        try {
            rejected(generation, "INTERRUPTED", "CF-interrupted");
            equal(true, Thread.currentThread().isInterrupted(), "CF-interrupted flag retained");
            equal(0, DisplayHardware.alignedCount, "CF-interrupted no invented final readback");
        } finally { Thread.interrupted(); }
        done("CF-interrupted");
        reset();
        DisplayHardware.applyElapsed = 5000L;
        Telemetry.scriptedBottom = new String[] {"1"};
        Telemetry.scriptedPairs = new String[][] {{"1", "0"}, {"1", "0"}, {"1", "1"}};
        Telemetry.pairIndex = 0;
        equal("ok=1;fix=1;display=confirmed", DisplayActionCoordinator.requestFix(true), "CF-vendor-duration receipt");
        equal(105000L, android.os.SystemClock.elapsedRealtime(), "CF-vendor-duration outside readback deadline");
        equal(1, DisplayHardware.actionCount, "CF-vendor-duration one request");
        // Without kernel log access a lower ON of >= 1 s is the poisoned-boot fallback signal.
        equal("POISONED_SLOW_ON", DisplayActionCoordinator.lowerFailureState(),
                "CF-vendor-duration slow lower ON without kernel signals latches");
        done("CF-vendor-duration-is-not-readback-timeout");
        retry();
        pendingRepair();
        System.out.println("CoordinatorFailureHostTest passed: " + cases + " cases, " + checks
                + " checks; retry interval characterized, vendor blocking UNKNOWN");
    }
}
