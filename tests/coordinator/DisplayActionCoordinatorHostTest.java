package com.thor.displaypowertest;

import java.lang.reflect.Field;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Tests the real production coordinator against inert vendor/OS boundaries.
 * The fixture does not represent physical screen luminance or Android sleep.
 */
public final class DisplayActionCoordinatorHostTest {
    private static int checks;
    private static void yes(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void equal(Object expected, Object actual, String message) {
        yes(expected == null ? actual == null : expected.equals(actual),
                message + ": expected " + expected + ", got " + actual);
    }
    private static void resetField(String name, Object value) throws Exception {
        Field field = DisplayActionCoordinator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
    private static void reset() throws Exception {
        synchronized (DisplayHardware.class) {
            DisplayActionCoordinator.cancelWakeRepair();
            resetField("lastMode", "?");
            resetField("status", "STARTING");
            resetField("lastHardwareReadAt", Long.valueOf(-1L));
            resetField("lastAttemptAt", Long.valueOf(0L));
            resetField("lastAttemptKey", "");
            android.os.SystemClock.setElapsedRealtime(100000L);
            BootSafety.held = false;
            DaemonState.enabled = true;
            DaemonState.mode = "?";
            DaemonState.repairBegins = 0;
            DaemonState.repairResult = null;
            DisplayHardware.property = "0";
            DisplayHardware.markCount = 0;
            DisplayHardware.actionCount = 0;
            DisplayHardware.alignedCount = 0;
            DisplayHardware.markSucceeds = true;
            LidGuard.enabled = false;
            LidGuard.defer = false;
            LidGuard.probes = 0;
            LidGuard.entered = null;
            LidGuard.released = null;
            WatcherCadence.skipRead = false;
            WatcherCadence.modeChanges = 0;
            WatcherCadence.requestedReads = 0;
            WatcherCadence.completedReads = 0;
            WatcherCadence.repairSchedules = 0;
            WakeRepairScheduler.pending = false;
            WakeRepairScheduler.due = false;
            WakeRepairScheduler.schedules = 0;
            WakeRepairScheduler.cancellations = 0;
            WakeRepairScheduler.completions = 0;
            WakeRepairScheduler.displayOnNotes = 0;
            Telemetry.top = "1";
            Telemetry.bottom = "1";
            Telemetry.reads = 0;
            WatcherSupervisor.state = "RUNNING";
        }
    }
    private static void noPowerActions(String reason) {
        equal(0, DisplayHardware.actionCount, reason + " must issue no panel power commands");
        equal(0, DisplayHardware.alignedCount, reason + " must not realign properties");
    }

    private static void unknownModeFailsClosed() throws Exception {
        reset();
        DisplayActionCoordinator.onWatcherSample(null);
        equal("MODE_UNKNOWN", DisplayActionCoordinator.status(), "unknown mode is rejected");
        equal("?", DaemonState.mode, "unknown mode is not guessed");
        equal(0, Telemetry.reads, "unknown mode prevents DRM read");
        noPowerActions("unknown mode");
    }

    private static void bootHoldStopsSampleAndUserFix() throws Exception {
        reset();
        BootSafety.held = true;
        DisplayActionCoordinator.onWatcherSample("1");
        equal("BOOT_HOLD", DisplayActionCoordinator.status(), "boot hold remains visible");
        equal(0, Telemetry.reads, "boot hold prevents DRM read");
        equal("ok=0;error=BOOT_HOLD", DisplayActionCoordinator.requestFix(true),
                "boot hold rejects user reconcile before external process read");
        DisplayActionCoordinator.scheduleWakeRepair();
        equal(0, WakeRepairScheduler.schedules, "boot hold rejects scheduled repair");
        noPowerActions("boot hold");
    }

    private static void fixDisabledCancelsAndStops() throws Exception {
        reset();
        DaemonState.enabled = false;
        DisplayActionCoordinator.onWatcherSample("1");
        equal("STOCK", DisplayActionCoordinator.status(), "fix disabled stays stock");
        equal(0, Telemetry.reads, "stock mode does not probe DRM here");
        DisplayActionCoordinator.scheduleWakeRepair();
        equal(0, WakeRepairScheduler.schedules, "stock mode schedules no repair");
        noPowerActions("stock mode");
    }

    private static void repairAdmissionAndCancellation() throws Exception {
        reset();
        DisplayActionCoordinator.scheduleWakeRepair();
        equal(0, WakeRepairScheduler.schedules, "not-TOP mode cannot schedule");
        DaemonState.mode = "1";
        DisplayActionCoordinator.scheduleWakeRepair();
        equal(1, WakeRepairScheduler.schedules, "known TOP enables repair");
        yes(WakeRepairScheduler.pending, "repair is pending");
        DisplayActionCoordinator.noteDisplayOn();
        equal(1, WakeRepairScheduler.displayOnNotes, "current repair observes display ON");
        DisplayActionCoordinator.cancelWakeRepair();
        yes(!WakeRepairScheduler.pending, "cancel clears pending repair");
        DisplayActionCoordinator.noteDisplayOn();
        equal(1, WakeRepairScheduler.displayOnNotes, "stale display ON ignored");
        noPowerActions("repair control API");
    }

    private static void stableTopWakeSchedulesAndCompletesSafely() throws Exception {
        reset();
        // Initial TOP sample has already observed a physically inactive BOTTOM.
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        equal("1", DaemonState.mode, "TOP mode recorded");
        // Normal wake reveals BOTTOM active; repaired OFF is deferred.
        DisplayHardware.property = "1";
        Telemetry.bottom = "1";
        DisplayActionCoordinator.onWatcherSample("1");
        equal(1, DisplayHardware.markCount, "stable TOP wake marks pending");
        equal(1, WakeRepairScheduler.schedules, "stable wake schedules only one repair");
        yes(WakeRepairScheduler.pending, "repair pending after wake");
        equal(1, WatcherCadence.repairSchedules, "cadence informed of wake repair");
        noPowerActions("stable TOP wake scheduling");
        // A scheduled due repair sees CRTC already OFF; no vendor command needed.
        WakeRepairScheduler.due = true;
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        equal(1, WakeRepairScheduler.completions, "already-OFF due repair completes");
        equal("OFF_OK", DaemonState.repairResult, "repair completion reported");
        yes(!WakeRepairScheduler.pending, "completed repair is no longer pending");
        noPowerActions("already-OFF repair completion");
    }

    private static void modeEdgeInvalidatesScheduledTopRepair() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayActionCoordinator.scheduleWakeRepair();
        long oldGeneration = DisplayActionCoordinator.generation();
        yes(WakeRepairScheduler.pending, "TOP repair pending before BOTH mode edge");
        Telemetry.bottom = "1";
        DisplayActionCoordinator.onWatcherSample("0");
        equal("0", DaemonState.mode, "new BOTH mode stored");
        yes(DisplayActionCoordinator.generation() > oldGeneration,
                "BOTH mode invalidates old generation");
        yes(!WakeRepairScheduler.pending, "old TOP repair cancelled on BOTH");
        equal(0, WakeRepairScheduler.completions, "cancelled TOP not marked complete");
        noPowerActions("TOP->BOTH invalidation");
    }

    private static void unknownCrtsAndReadDeferralNeverWrite() throws Exception {
        reset();
        Telemetry.top = "?";
        Telemetry.bottom = "?";
        DisplayActionCoordinator.onWatcherSample("0");
        equal("PENDING", DisplayActionCoordinator.status(), "unknown CRTCs stay pending");
        noPowerActions("unknown CRTCs");
        reset();
        WatcherCadence.skipRead = true;
        DisplayActionCoordinator.onWatcherSample("1");
        equal(0, Telemetry.reads, "deferred DRM read never calls telemetry");
        equal(0, WatcherCadence.completedReads, "deferred read not completed");
        equal("1", DaemonState.mode, "mode still recorded on deferred read");
        noPowerActions("skipped DRM read");
    }

    private static void lidDeferralPreventsWakeRepairScheduling() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayHardware.property = "1";
        Telemetry.bottom = "1";
        LidGuard.enabled = true;
        LidGuard.defer = true;
        DisplayActionCoordinator.onWatcherSample("1");
        equal(1, LidGuard.probes, "lid callback evaluated for stable TOP wake");
        equal(0, DisplayHardware.markCount, "guard defer does not mark wake repair");
        equal(0, WakeRepairScheduler.schedules, "guard defer does not schedule repair");
        noPowerActions("guard defer");
    }

    private static void callbackBeforeHardwareLockDoesNotBlockCancel() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayHardware.property = "1";
        Telemetry.bottom = "1";
        LidGuard.enabled = true;
        LidGuard.defer = true;
        LidGuard.entered = new CountDownLatch(1);
        LidGuard.released = new CountDownLatch(1);
        final AtomicReference<Throwable> error = new AtomicReference<Throwable>();
        Thread watcher = new Thread(new Runnable() {
            @Override public void run() {
                try { DisplayActionCoordinator.onWatcherSample("1"); }
                catch (Throwable failure) { error.set(failure); }
            }
        }, "fixture-guard-probe");
        watcher.setDaemon(true);
        watcher.start();
        try {
            yes(LidGuard.entered.await(3, TimeUnit.SECONDS),
                    "watcher reached guard callback before display lock");
            // Must not wait for the guard callback: the implementation deliberately
            // evaluates Guard outside the shared DisplayHardware monitor.
            long before = DisplayActionCoordinator.generation();
            DisplayActionCoordinator.cancelWakeRepair();
            yes(DisplayActionCoordinator.generation() > before,
                    "cancel can invalidate while guard callback is waiting");
        } finally {
            LidGuard.released.countDown();
            watcher.join(3000L);
        }
        yes(!watcher.isAlive(), "guard callback completes without deadlock");
        yes(error.get() == null, "guard callback no exception: " + error.get());
        equal(0, DisplayHardware.markCount, "deferred callback does not mark repair");
        equal(0, WakeRepairScheduler.schedules, "deferred callback does not reschedule");
        noPowerActions("guard / cancel interleaving");
    }

    private static void guardCannotSleepFromUnknownDecision() throws Exception {
        reset();
        LidGuard.enabled = true;
        yes(!DisplayActionCoordinator.requestGuardSleep(123L),
                "invalid lid epoch blocks external sleep command");
        noPowerActions("invalid guard epoch");
    }

    public static void main(String[] args) throws Exception {
        unknownModeFailsClosed();
        bootHoldStopsSampleAndUserFix();
        fixDisabledCancelsAndStops();
        repairAdmissionAndCancellation();
        stableTopWakeSchedulesAndCompletesSafely();
        modeEdgeInvalidatesScheduledTopRepair();
        unknownCrtsAndReadDeferralNeverWrite();
        lidDeferralPreventsWakeRepairScheduling();
        callbackBeforeHardwareLockDoesNotBlockCancel();
        guardCannotSleepFromUnknownDecision();
        System.out.println("DisplayActionCoordinatorHostTest passed: " + checks
                + " checks against unchanged production coordinator; HOST ONLY");
    }
}
