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
        equal("1", DisplayHardware.property, "stable TOP wake preserves firmware property");
        equal(0, DisplayHardware.markCount, "stable TOP wake uses internal pending state");
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

    private static void lateDueRepairCannotCommitAfterBothModeEdge() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayActionCoordinator.scheduleWakeRepair();
        yes(WakeRepairScheduler.pending, "S1-L01 TOP repair scheduled before mode change");
        WakeRepairScheduler.due = true;
        Telemetry.bottom = "1"; // fully active BOTH, no power action needed
        long before = DisplayActionCoordinator.generation();
        DisplayActionCoordinator.onWatcherSample("0");
        yes(DisplayActionCoordinator.generation() > before,
                "S1-L01 BOTH edge invalidated old TOP generation");
        equal("0", DaemonState.mode, "S1-L01 switched to BOTH");
        yes(!WakeRepairScheduler.pending && !WakeRepairScheduler.due,
                "S1-L01 stale repair deadline cancelled");
        equal(0, DaemonState.repairBegins, "S1-L01 stale due work not applied");
        equal(0, WakeRepairScheduler.completions,
                "S1-L01 stale due work never records a completed repair");
        DisplayActionCoordinator.noteDisplayOn(); // late confirmation
        equal(0, WakeRepairScheduler.displayOnNotes,
                "S1-L01 old display ON ignored after BOTH transition");
        DisplayActionCoordinator.onWatcherSample("0"); // duplicate event
        equal("0", DaemonState.mode, "S1-L01 duplicate BOTH remains BOTH");
        equal(2, WatcherCadence.modeChanges, "S1-L01 first TOP and BOTH are two mode edges");
        noPowerActions("S1-L01 late due repair");
    }

    private static void rapidTopBothTopRejectsOrphanedDeadline() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayActionCoordinator.scheduleWakeRepair();
        long firstRepair = DisplayActionCoordinator.generation();
        Telemetry.bottom = "1";
        DisplayActionCoordinator.onWatcherSample("0");
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        yes(DisplayActionCoordinator.generation() > firstRepair,
                "S1-L02 a fresh mode epoch supersedes the old TOP repair");
        yes(!WakeRepairScheduler.pending,
                "S1-L02 no repair should be pending after TOP/BOTH/TOP");
        // Adversarial stub: a delayed timer delivered after it was cancelled.
        WakeRepairScheduler.due = true;
        DisplayActionCoordinator.onWatcherSample("1");
        equal(0, DaemonState.repairBegins,
                "S1-L02 orphaned deadline must not begin repair");
        equal(0, WakeRepairScheduler.completions,
                "S1-L02 orphaned deadline cannot complete repair");
        yes(!WakeRepairScheduler.pending && !WakeRepairScheduler.due,
                "S1-L02 orphaned due flag is cleared");
        noPowerActions("S1-L02 stale due timer");
    }

    private static void rapidDuplicateModeNotificationsStayBounded() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        for (int i = 0; i < 20; i++) {
            DisplayActionCoordinator.onWatcherSample("1");
        }
        equal(1, WatcherCadence.modeChanges,
                "S1-L03 duplicate TOP samples do not create mode edges");
        equal(0, WakeRepairScheduler.schedules,
                "S1-L03 repeated stable TOP without wake signal schedules nothing");
        equal(0, DisplayHardware.markCount,
                "S1-L03 repeated samples do not forge a wake pending mark");
        noPowerActions("S1-L03 duplicate TOP samples");
    }

    private static void overlappingWatcherSamplesAreCharacterizedNotCertified() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayHardware.property = "1";
        Telemetry.bottom = "1";
        LidGuard.enabled = true;
        LidGuard.defer = true; // forbid any write while observing the ordering
        LidGuard.entered = new CountDownLatch(1);
        LidGuard.released = new CountDownLatch(1);
        final AtomicReference<Throwable> failure = new AtomicReference<Throwable>();
        Thread delayedTop = new Thread(new Runnable() {
            @Override public void run() {
                try { DisplayActionCoordinator.onWatcherSample("1"); }
                catch (Throwable error) { failure.set(error); }
            }
        }, "synthetic-parallel-watcher");
        delayedTop.setDaemon(true);
        delayedTop.start();
        try {
            yes(LidGuard.entered.await(3, TimeUnit.SECONDS),
                    "S1-L04 delayed TOP sample stopped before display lock");
            DisplayActionCoordinator.onWatcherSample("0");
            equal("0", DaemonState.mode, "S1-L04 BOTH wins while TOP callback blocked");
        } finally {
            LidGuard.released.countDown();
            delayedTop.join(3000L);
        }
        yes(!delayedTop.isAlive() && failure.get() == null,
                "S1-L04 no deadlock or uncaught exception on artificial overlap");
        // This is deliberately an OBSERVATION of a parallel caller assumption.
        // It is not a proof that DaemonWatchThread invokes concurrently in vivo.
        if ("1".equals(DaemonState.mode)) {
            System.out.println("S1-L04 OBSERVATION: artificial concurrent older TOP "
                    + "callback replaced newer BOTH; concurrency contract UNVERIFIED");
        } else {
            System.out.println("S1-L04 OBSERVATION: artificial older callback did not "
                    + "replace newer BOTH in this run");
        }
        noPowerActions("S1-L04 artificial overlap");
        // A real later BOTH sample recovers the logical model, without I/O.
        DisplayActionCoordinator.onWatcherSample("0");
        equal("0", DaemonState.mode, "S1-L04 latest BOTH sample restores mode");
        noPowerActions("S1-L04 mode resync");
    }

    /**
     * S1-S01: a delayed (or stale) TOP value accompanied by UNKNOWN panel
     * observation must not be promoted into an unverified vendor OFF action.
     * The later authoritative BOTH sample reconciles the logical mode.
     */
    private static void staleTopSampleWithUnknownBottomRemainsPassive() throws Exception {
        reset();
        DisplayActionCoordinator.onWatcherSample("0");
        equal("CONFIRMED", DisplayActionCoordinator.status(),
                "S1-S01 healthy BOTH baseline");
        long before = DisplayActionCoordinator.generation();

        Telemetry.bottom = "?";
        DisplayActionCoordinator.onWatcherSample("1");
        equal("1", DaemonState.mode, "S1-S01 stale candidate TOP is recorded as a sample");
        equal("PENDING", DisplayActionCoordinator.status(),
                "S1-S01 unverified panel observation stays pending");
        yes(DisplayActionCoordinator.generation() > before,
                "S1-S01 candidate mode edge invalidates earlier generation");

        Telemetry.bottom = "1";
        DisplayActionCoordinator.onWatcherSample("0");
        equal("0", DaemonState.mode, "S1-S01 subsequent BOTH sample restores mode");
        equal("CONFIRMED", DisplayActionCoordinator.status(),
                "S1-S01 matching BOTH panel observations confirm state");
        noPowerActions("S1-S01 stale TOP candidate / unknown CRTC");
    }

    /** S1-S02: a delayed BOTH sample with unknown upper panel is passive. */
    private static void staleBothSampleWithUnknownTopRemainsPassive() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        equal("CONFIRMED", DisplayActionCoordinator.status(),
                "S1-S02 healthy TOP baseline");
        long before = DisplayActionCoordinator.generation();

        Telemetry.top = "?";
        DisplayActionCoordinator.onWatcherSample("0");
        equal("0", DaemonState.mode, "S1-S02 candidate BOTH recorded");
        equal("PENDING", DisplayActionCoordinator.status(),
                "S1-S02 unknown top panel blocks reconciliation");
        yes(DisplayActionCoordinator.generation() > before,
                "S1-S02 candidate BOTH invalidates TOP generation");

        Telemetry.top = "1";
        DisplayActionCoordinator.onWatcherSample("1");
        equal("1", DaemonState.mode, "S1-S02 later TOP sample restores mode");
        equal("CONFIRMED", DisplayActionCoordinator.status(),
                "S1-S02 matching TOP panel observations confirm state");
        noPowerActions("S1-S02 stale BOTH candidate / unknown CRTC");
    }

    /** S1-S03: unknown sampled mode invalidates a due repair before DRM. */
    private static void unknownModeCancelsDueTopRepairBeforeHardwareRead() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayActionCoordinator.scheduleWakeRepair();
        yes(WakeRepairScheduler.pending, "S1-S03 TOP repair scheduled");
        WakeRepairScheduler.due = true;
        long before = DisplayActionCoordinator.generation();
        int drmBefore = Telemetry.reads;

        DisplayActionCoordinator.onWatcherSample("garbled");
        equal("?", DaemonState.mode, "S1-S03 unknown Settings mode retained as unknown");
        equal("MODE_UNKNOWN", DisplayActionCoordinator.status(),
                "S1-S03 unknown mode refuses hardware reconcile");
        yes(DisplayActionCoordinator.generation() > before,
                "S1-S03 unknown mode invalidates old repair generation");
        yes(!WakeRepairScheduler.pending && !WakeRepairScheduler.due,
                "S1-S03 due repair cancelled");
        equal(drmBefore, Telemetry.reads, "S1-S03 no unnecessary DRM read on invalid mode");
        equal(0, DaemonState.repairBegins, "S1-S03 no repair started");
        DisplayActionCoordinator.noteDisplayOn();
        equal(0, WakeRepairScheduler.displayOnNotes,
                "S1-S03 late display-on callback cannot resurrect invalidated repair");
        noPowerActions("S1-S03 unknown mode with due TOP repair");
    }

    /** S1-S04: mode-edge repair cancellation precedes a throttled DRM read. */
    private static void modeChangeCancelsRepairWithDeferredDrmSnapshot() throws Exception {
        reset();
        Telemetry.bottom = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        DisplayActionCoordinator.scheduleWakeRepair();
        yes(WakeRepairScheduler.pending, "S1-S04 initial TOP repair pending");
        int readsBefore = Telemetry.reads;
        long before = DisplayActionCoordinator.generation();
        WatcherCadence.skipRead = true;
        Telemetry.bottom = "1";

        DisplayActionCoordinator.onWatcherSample("0");
        equal("0", DaemonState.mode, "S1-S04 BOTH edge stored despite throttled DRM");
        yes(DisplayActionCoordinator.generation() > before,
                "S1-S04 BOTH edge invalidates old TOP repair");
        yes(!WakeRepairScheduler.pending && !WakeRepairScheduler.due,
                "S1-S04 pending TOP repair cancelled before DRM read");
        equal(readsBefore, Telemetry.reads, "S1-S04 throttled read remains skipped");
        equal(0, DaemonState.repairBegins, "S1-S04 no obsolete repair started");
        DisplayActionCoordinator.onWatcherSample("0");
        equal("0", DaemonState.mode, "S1-S04 duplicate BOTH stays BOTH");
        noPowerActions("S1-S04 cancelled repair with throttled DRM");
    }

    /**
     * S1-S05: a syntactically valid but temporally stale Settings value may
     * have plausible CRTCs. The pure production decision model has no access
     * to the later authoritative Settings sample: classify, do NOT command.
     */
    private static void plausibleStaleModeCannotBeCertifiedFromCrtcsAlone() throws Exception {
        reset();
        equal(DisplayDecisionModel.Action.OFF,
                DisplayDecisionModel.reconcile("1", "1", "1", true, false, false),
                "S1-S05 TOP 1/1 normally authorizes OFF without knowing sample age");
        equal(DisplayDecisionModel.Action.ON,
                DisplayDecisionModel.reconcile("0", "1", "0", true, false, false),
                "S1-S05 BOTH 1/0 normally authorizes ON without knowing sample age");
        equal(DisplayDecisionModel.Action.NONE,
                DisplayDecisionModel.reconcile("1", "?", "1", true, false, false),
                "S1-S05 unknown upper CRTC denies TOP OFF");
        equal(DisplayDecisionModel.Action.NONE,
                DisplayDecisionModel.reconcile("0", "?", "0", true, false, false),
                "S1-S05 unknown upper CRTC denies BOTH ON");
        System.out.println("S1-S05 OBSERVATION: known CRTCs alone cannot establish "
                + "whether a sequential Settings mode observation is stale; "
                + "no hardware action attempted");
        noPowerActions("S1-S05 conditional ambiguous observation");
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
        lateDueRepairCannotCommitAfterBothModeEdge();
        rapidTopBothTopRejectsOrphanedDeadline();
        rapidDuplicateModeNotificationsStayBounded();
        overlappingWatcherSamplesAreCharacterizedNotCertified();
        staleTopSampleWithUnknownBottomRemainsPassive();
        staleBothSampleWithUnknownTopRemainsPassive();
        unknownModeCancelsDueTopRepairBeforeHardwareRead();
        modeChangeCancelsRepairWithDeferredDrmSnapshot();
        plausibleStaleModeCannotBeCertifiedFromCrtcsAlone();
        System.out.println("DisplayActionCoordinatorHostTest passed: " + checks
                + " checks against unchanged production coordinator; HOST ONLY");
    }
}
