package com.thor.displaypowertest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Host-only side-effect boundary: the production DisplayActionCoordinator and
 * production DisplayDecisionModel / DisplayGenerationModel are compiled as-is.
 * The vendor/Android dependencies below are inert, explicit test doubles.
 * This file must NEVER be compiled into a product or release APK.
 */
final class BootSafety {
    static volatile boolean held;
    static boolean isHeld() { return held; }
    static boolean knownMode(String value) {
        return "0".equals(value) || "1".equals(value) || "2".equals(value);
    }
}

final class DaemonState {
    static volatile boolean enabled = true;
    static volatile String mode = "?";
    static int repairBegins;
    static String repairResult;
    static boolean isEnabled() { return enabled; }
    static void setEnabled(boolean value) { enabled = value; }
    static void setMode(String value) { mode = value; }
    static String getMode() { return mode; }
    static void onRepairBegin() { repairBegins++; }
    static void onRepairEnd(String value) { repairResult = value; }
}

final class DisplayHardware {
    static volatile String property = "0";
    static int markCount;
    static int actionCount;
    static int alignedCount;
    static boolean markSucceeds = true;
    static boolean scriptedApply;
    static Boolean lastRequestedOn;
    static String getProperty() { return property; }
    static synchronized boolean markWakePending() {
        markCount++;
        if (markSucceeds) property = "0";
        return markSucceeds;
    }
    static synchronized boolean apply(boolean on, String reason) {
        actionCount++;
        if (scriptedApply) {
            lastRequestedOn = Boolean.valueOf(on);
            return true;
        }
        // Host tests must not silently succeed after an unexpected power request.
        throw new AssertionError("unexpected vendor display command: " + reason);
    }
    static synchronized void alignPropertyWithCrtc(String crtc) { alignedCount++; }
}

final class LidGuard {
    static volatile boolean enabled;
    static volatile boolean defer;
    static int probes;
    static CountDownLatch entered;
    static CountDownLatch released;
    static boolean isEnabled() { return enabled; }
    static boolean onWakeEvent() {
        probes++;
        CountDownLatch start = entered;
        if (start != null) start.countDown();
        CountDownLatch finish = released;
        if (finish != null) {
            try {
                if (!finish.await(5, TimeUnit.SECONDS))
                    throw new AssertionError("timed out waiting for deterministic lid callback release");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("lid callback interrupted", e);
            }
        }
        return defer;
    }
    static boolean isSleepDecisionCurrent(long epoch) { return false; }
    static String externalDisplayCached() { return "unknown"; }
}

final class WatcherCadence {
    static volatile boolean skipRead;
    static int modeChanges;
    static int requestedReads;
    static int completedReads;
    static int repairSchedules;
    static void onModeChanged() { modeChanges++; }
    static long beginDrmRead(long previous, boolean modeChanged,
            boolean repairDue, boolean wake) {
        requestedReads++;
        return skipRead ? -1L : requestedReads;
    }
    static void completeDrmRead(long generation) { completedReads++; }
    static void onWakeRepairScheduled() { repairSchedules++; }
}

final class WakeRepairScheduler {
    static volatile boolean pending;
    static volatile boolean due;
    static int schedules;
    static int cancellations;
    static int completions;
    static int displayOnNotes;
    static boolean isDue() { return due; }
    static boolean isPending() { return pending; }
    static void scheduleFromWake() {
        pending = true;
        due = false;
        schedules++;
    }
    static void cancel() {
        pending = false;
        due = false;
        cancellations++;
    }
    static void markDone() {
        pending = false;
        due = false;
        completions++;
    }
    static void noteDisplayOn() { displayOnNotes++; }
}

final class Telemetry {
    static volatile String top = "1";
    static volatile String bottom = "1";
    static int reads;
    static String[][] scriptedPairs;
    static int pairIndex;
    static String[] scriptedBottom;
    static int bottomIndex;
    static String[] crtcActivePair() {
        reads++;
        if (scriptedPairs != null) {
            if (pairIndex >= scriptedPairs.length)
                throw new AssertionError("unexpected extra CRTC pair read");
            return scriptedPairs[pairIndex++].clone();
        }
        return new String[] { top, bottom };
    }
    static String bottomCrtcActive() {
        if (scriptedBottom != null) {
            if (bottomIndex >= scriptedBottom.length)
                throw new AssertionError("unexpected extra bottom CRTC poll");
            return scriptedBottom[bottomIndex++];
        }
        return bottom;
    }
}

final class WatcherSupervisor {
    static volatile String state = "RUNNING";
    static String health() { return state; }
}

final class ProcessWait {
    static boolean exited(Process process, long timeout) {
        // requestGuardSleep has a fail-closed precondition in this harness.
        throw new AssertionError("unexpected external process execution from host test");
    }
}
