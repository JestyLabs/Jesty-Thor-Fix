package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Current production policy characterization; all panel observations are synthetic. */
public final class ContainmentBoundaryHostTest {
    private static int checks;
    private static void check(boolean value, String message) {
        checks++;
        if (!value) throw new AssertionError(message);
    }
    private static void sample(long advance) {
        SystemClock.now += advance;
        DisplayActionCoordinator.onWatcherSample(TestDisplayState.mode);
    }
    private static long lines(String prefix) {
        synchronized (Log.LINES) {
            return Log.LINES.stream().filter(line -> line.startsWith(prefix)).count();
        }
    }
    private static void reset() {
        SystemClock.autoAdvanceMs = 0;
        SystemClock.now += 100000L;
        TestDisplayState.baseline();
        Log.LINES.clear();
    }
    private static void arm() {
        reset();
        TestDisplayState.mode = "0";
        TestDisplayState.bottom = "0";
        TestDisplayState.property = "1";
        TestDisplayState.callAdvanceMs = 5001L;
        sample(2000L);
        TestDisplayState.callAdvanceMs = 0;
        check(TestDisplayState.hardwareCalls == 1, "ARM-01 one slow synthetic ON");
        check(DisplayActionCoordinator.isLowerOnBackoff(), "ARM-02 protection armed at 5001 ms");
    }
    private static void noTotalCap() {
        arm();
        TestDisplayState.powerFailure = true;
        for (int attempt = 1; attempt <= 24; attempt++) {
            sample(1500L);
            check(TestDisplayState.hardwareCalls == attempt + 1,
                    "CAP-01 current policy admits another OFF after the interval");
            check("APPLY_FAILED".equals(DisplayActionCoordinator.status()),
                    "CAP-02 failed OFF is not confirmed");
        }
        int calls = TestDisplayState.hardwareCalls;
        sample(1499L);
        check(TestDisplayState.hardwareCalls == calls, "CAP-03 1499 ms still refused");
        sample(1L);
        check(TestDisplayState.hardwareCalls == calls + 1, "CAP-04 1500 ms admitted");
        check(DisplayActionCoordinator.isLowerOnBackoff(), "CAP-05 errors retain protection");
        System.out.println("OBSERVATION: 25 failed OFF retries admitted; interval exists, total cap absent");
    }
    private static void fastErrors() {
        reset();
        TestDisplayState.mode = "0";
        TestDisplayState.bottom = "0";
        TestDisplayState.powerFailure = true;
        for (int attempt = 1; attempt <= 16; attempt++) {
            sample(1500L);
            check(TestDisplayState.hardwareCalls == attempt, "FAST-01 repeated fast ON failure admitted");
            check(!DisplayActionCoordinator.isLowerOnBackoff(),
                    "FAST-02 fast errors alone do not arm elapsed-call protection");
        }
        check("APPLY_FAILED".equals(DisplayActionCoordinator.status()), "FAST-03 no false success");
        System.out.println("OBSERVATION: repeated fast failures have no separate containment trigger");
    }
    private static void modeReset(String mode) {
        arm();
        TestDisplayState.mode = mode;
        TestDisplayState.bottom = "1".equals(mode) ? "0" : "1";
        sample(2000L);
        check(!DisplayActionCoordinator.isLowerOnBackoff(), "RESET-01 leaving BOTH for selected mode clears");
        check(DisplayActionCoordinator.isLowerFailureLatched(), "RESET-03 the boot-scoped latch survives the mode change");
        TestDisplayState.mode = "0";
        TestDisplayState.bottom = "0";
        sample(2000L);
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom),
                "RESET-02 return to BOTH keeps automatic ON suppressed by the boot latch");
        System.out.println("OBSERVATION: mode " + mode + " clears the back-off; the boot latch keeps BOTH ON suppressed");
    }
    private static void unknownMode() {
        arm();
        TestDisplayState.bottom = "0";
        TestDisplayState.mode = "?";
        sample(2000L);
        check(DisplayActionCoordinator.isLowerOnBackoff(), "UNKNOWN-01 unknown mode retains protection");
        TestDisplayState.mode = "0";
        sample(2000L);
        check(TestDisplayState.hardwareCalls == 1, "UNKNOWN-02 no lower ON on return to BOTH");
    }
    private static void unknownPower() {
        arm();
        TestDisplayState.bottom = "0";
        TestDisplayState.powerServiceMissing = true;
        TestDisplayState.interactive = false;
        sample(2000L);
        TestDisplayState.interactive = true;
        sample(2000L);
        check(DisplayActionCoordinator.isLowerOnBackoff(), "POWER-01 unavailable power state cannot clear");
        check(TestDisplayState.hardwareCalls == 1, "POWER-02 no speculative ON after unknown power cycle");
    }
    private static void missedPowerCycle() {
        arm();
        TestDisplayState.bottom = "0";
        sample(2000L); // sampled interactive=true
        int queries = TestDisplayState.powerQueries;
        TestDisplayState.interactive = false;
        TestDisplayState.top = "0";
        sample(100L); // rate-limited: false is not sampled
        TestDisplayState.interactive = true;
        TestDisplayState.top = "1";
        sample(100L);
        check(TestDisplayState.powerQueries == queries, "MISS-01 short cycle has no power query");
        sample(1000L);
        check(DisplayActionCoordinator.isLowerOnBackoff(), "MISS-02 unsampled false cannot clear");
        check(TestDisplayState.hardwareCalls == 1, "MISS-03 protection remains conservative");
        System.out.println("OBSERVATION: rate-limited power sampling can miss a short sleep/wake");
    }
    private static void sampledPowerCycle() {
        arm();
        TestDisplayState.interactive = false;
        TestDisplayState.top = "0";
        TestDisplayState.bottom = "0";
        sample(2000L);
        check(DisplayActionCoordinator.isLowerOnBackoff(), "CYCLE-01 sleep alone retains protection");
        TestDisplayState.interactive = true;
        TestDisplayState.top = "1";
        sample(1000L);
        check(!DisplayActionCoordinator.isLowerOnBackoff(), "CYCLE-02 observed wake clears");
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom)
                        && DisplayActionCoordinator.isLowerFailureLatched(),
                "CYCLE-03 the boot latch keeps BOTH ON suppressed after the clearing sample");
        System.out.println("OBSERVATION: sampled sleep/wake clears the back-off; the boot latch keeps ON suppressed until reboot or manual reconcile");
    }
    private static void transientCrtc() {
        arm();
        TestDisplayState.top = "0";
        TestDisplayState.bottom = "0";
        sample(2000L);
        TestDisplayState.top = "1";
        TestDisplayState.bottom = "1";
        sample(2000L);
        check(DisplayActionCoordinator.isLowerOnBackoff(), "CRTC-01 0/0 alone cannot clear");
        check("0".equals(TestDisplayState.bottom), "CRTC-02 admitted isolation still applies");
        check(TestDisplayState.hardwareCalls == 2, "CRTC-03 no automatic ON");
    }
    private static void noAppCallTrigger() {
        reset();
        TestDisplayState.mode = "0";
        for (String pair : new String[]{"00", "01", "11", "00", "11"}) {
            TestDisplayState.top = pair.substring(0, 1);
            TestDisplayState.bottom = pair.substring(1);
            sample(2000L);
        }
        check(TestDisplayState.hardwareCalls == 0, "TRIGGER-01 these observations need no app action");
        check(!DisplayActionCoordinator.isLowerOnBackoff(), "TRIGGER-02 no slow app call means no trigger");
        TestDisplayState.top = "1";
        TestDisplayState.bottom = "0";
        sample(2000L);
        check(TestDisplayState.hardwareCalls == 1, "TRIGGER-03 ordinary BOTH ON remains eligible");
        System.out.println("OBSERVATION: CRTC patterns alone are not a vendor-failure detector");
    }
    private static boolean awaitBlocked(Thread thread) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (thread.isAlive() && thread.getState() != Thread.State.BLOCKED
                && System.nanoTime() < end) Thread.sleep(2L);
        return thread.getState() == Thread.State.BLOCKED;
    }
    private static void concurrentPropertyRollback() throws Exception {
        reset();
        TestDisplayState.property = "0";
        TestDisplayState.powerFailure = true;
        TestDisplayState.callEntered = new CountDownLatch(1);
        TestDisplayState.callRelease = new CountDownLatch(1);
        final boolean[] result = {true};
        final Throwable[] failure = {null};
        Thread caller = new Thread(() -> {
            try { result[0] = DisplayHardware.apply(false, "SYNTHETIC_ROLLBACK"); }
            catch (Throwable error) { failure[0] = error; }
        }, "synthetic-rollback-caller");
        caller.setDaemon(true);
        caller.start();
        try {
            check(TestDisplayState.callEntered.await(2L, TimeUnit.SECONDS), "ROLLBACK-01 entered power boundary");
            TestDisplayState.property = "1"; // an independent writer during the blocked call
        } finally {
            TestDisplayState.callRelease.countDown();
            caller.join(2500L);
        }
        check(!caller.isAlive() && failure[0] == null, "ROLLBACK-02 caller ended cleanly");
        check(!result[0], "ROLLBACK-03 platform error is reported");
        check("0".equals(TestDisplayState.property) && TestDisplayState.propertyWrites == 2,
                "ROLLBACK-04 known previous value overwrites the later synthetic writer");
        System.out.println("OBSERVATION: error rollback is not atomic with an independent property writer");
    }
    private static void blocked(boolean throwing) throws Exception {
        reset();
        TestDisplayState.wakeTop();
        SystemClock.now = WakeRepairScheduler.deadline;
        TestDisplayState.powerFailure = throwing;
        TestDisplayState.callEntered = new CountDownLatch(1);
        TestDisplayState.callRelease = new CountDownLatch(1);
        WatcherSupervisor.noteSample(); // real class, not the existing fixture's health constant
        final Throwable[] failures = new Throwable[3];
        Thread caller = new Thread(() -> {
            try { DisplayActionCoordinator.onWatcherSample("1"); }
            catch (Throwable error) { failures[0] = error; }
        }, "synthetic-platform-caller");
        Thread cancel = new Thread(() -> {
            try { DisplayActionCoordinator.cancelWakeRepair(); }
            catch (Throwable error) { failures[1] = error; }
        }, "synthetic-cancel-callback");
        Thread pending = new Thread(() -> {
            try { DisplayActionCoordinator.isWakeRepairPending(); }
            catch (Throwable error) { failures[2] = error; }
        }, "synthetic-pending-query");
        caller.setDaemon(true);
        cancel.setDaemon(true);
        pending.setDaemon(true);
        caller.start();
        try {
            check(TestDisplayState.callEntered.await(2L, TimeUnit.SECONDS), "BLOCK-01 entered platform boundary");
            long generation = DisplayActionCoordinator.generation();
            TestDisplayState.mode = "0"; // independent platform observation, never a second watcher
            SystemClock.now += 6000L;
            check("STALLED".equals(WatcherSupervisor.health()), "BLOCK-02 real supervisor exposes stale sampling");
            cancel.start();
            pending.start();
            check(awaitBlocked(cancel), "BLOCK-03 cancel waits for the hardware monitor");
            check(awaitBlocked(pending), "BLOCK-04 even pending query waits for that monitor");
            check(lines("ThorDisplayDaemon: HW_CALL_OBSERVED ") == 0,
                    "BLOCK-05 no completed-call observation before return");
            check(lines("ThorDisplayDaemon: LOWER_ON_BACKOFF set") == 0,
                    "BLOCK-06 post-return protection is not armed while blocked");
            check("CONFIRMED".equals(DisplayActionCoordinator.status()),
                    "BLOCK-07 action status may retain prior confirmation during in-flight work");
            check(DisplayActionCoordinator.generation() == generation,
                    "BLOCK-08 blocked cancellation has not invalidated the generation");
        } finally {
            TestDisplayState.callRelease.countDown();
            caller.join(2500L);
            if (cancel.getState() != Thread.State.NEW) cancel.join(2500L);
            if (pending.getState() != Thread.State.NEW) pending.join(2500L);
        }
        check(!caller.isAlive() && !cancel.isAlive() && !pending.isAlive(), "BLOCK-09 all started workers terminated");
        for (Throwable failure : failures) check(failure == null, "BLOCK-10 worker failure: " + failure);
        check(DisplayActionCoordinator.isLowerOnBackoff(), "BLOCK-11 slow completion arms protection, including error");
        check((throwing ? "APPLY_FAILED" : "UNCONFIRMED").equals(DisplayActionCoordinator.status()),
                "BLOCK-12 late result cannot confirm stale TOP");
        check(TestDisplayState.hardwareCalls == 1, "BLOCK-13 no competing physical operation");
        System.out.println("OBSERVATION: blocked monitor, accessible stale action status, real STALLED health; release then "
                + DisplayActionCoordinator.status() + "; synthetic error=" + throwing);
    }
    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("one case required");
        switch (args[0]) {
            case "no-total-cap": noTotalCap(); break;
            case "fast-errors": fastErrors(); break;
            case "threshold-one-over": arm(); break;
            case "reset-top": modeReset("1"); break;
            case "reset-bottom": modeReset("2"); break;
            case "unknown-mode": unknownMode(); break;
            case "unknown-power": unknownPower(); break;
            case "missed-power-cycle": missedPowerCycle(); break;
            case "sampled-power-cycle": sampledPowerCycle(); break;
            case "transient-crtc": transientCrtc(); break;
            case "no-app-call-trigger": noAppCallTrigger(); break;
            case "blocked-return": blocked(false); break;
            case "blocked-error": blocked(true); break;
            case "concurrent-property-rollback": concurrentPropertyRollback(); break;
            default: throw new IllegalArgumentException(args[0]);
        }
        System.out.println("PASS " + args[0] + ": " + checks + " assertions; HOST synthetic platform only");
    }
}
