package com.thor.displaypowertest;
final class BootSafety {
    static boolean isHeld() { return TestDisplayState.held; }
    static boolean knownMode(String mode) { return "0".equals(mode) || "1".equals(mode) || "2".equals(mode); }
}
final class DaemonState {
    static String mode = "?";
    static boolean isEnabled() { return TestDisplayState.enabled; }
    static String getMode() { return mode; }
    static void setMode(String next) { mode = next; }
    static void setEnabled(boolean enabled) { TestDisplayState.enabled = enabled; }
    static void onRepairBegin() {}
    static void onRepairEnd(String status) {}
    static void setLastAction(String action) {}
}
final class LidGuard {
    static boolean isEnabled() { return TestDisplayState.guard; }
    static boolean onWakeEvent() { return TestDisplayState.guardDefers; }
    static boolean isSleepDecisionCurrent(long epoch) { return false; }
    static String externalDisplayCached() { return "0"; }
}
final class WatcherCadence {
    static void onDisplayEvent() {}
    static void onModeChanged() {}
    static long beginDrmRead(long last, boolean changed, boolean due, boolean wake) { return 0L; }
    static void completeDrmRead(long generation) {}
    static void onWakeRepairScheduled() {}
}
final class Telemetry {
    static String[] crtcActivePair() { return new String[]{TestDisplayState.top, TestDisplayState.bottom}; }
    static String bottomCrtcActive() { return TestDisplayState.bottom; }
}
final class WakeRepairScheduler {
    static boolean pending;
    static long deadline;
    static int schedules, notes;
    static boolean displayOnNoted;
    static boolean isDue() { return pending && android.os.SystemClock.elapsedRealtime() >= deadline; }
    static boolean isPending() { return pending; }
    static void markDone() { pending = false; }
    static void noteDisplayOn() {
        if (displayOnNoted) return;
        notes++;
        deadline = Math.max(deadline, android.os.SystemClock.elapsedRealtime() + 400L);
        pending = true; displayOnNoted = true;
    }
    static void scheduleFromWake() {
        schedules++; pending = true; displayOnNoted = false;
        deadline = android.os.SystemClock.elapsedRealtime() + 700L;
    }
    static void cancel() { pending = false; displayOnNoted = false; }
}
final class WatcherSupervisor { static String health() { return "RUNNING"; } }
final class ProcessWait { static boolean exited(Process process, long timeout) { throw new AssertionError("No process execution allowed in host harness"); } }
final class DisplayModeReader { static String current() { return TestDisplayState.mode; } }

/** Host-only inert boundary: never authorizes an idle display action. */
final class BottomIdleRuntime {
    static void onModeSample(String mode) {}
    static void onDisplayWake() {}
    static boolean isOff() { return false; }
    static boolean isOffOrPending() { return false; }
    static boolean isPending(long ticket) { return false; }
    static void completeOff(long ticket, boolean confirmed) {}
    static boolean canRestore(long lease) { return false; }
    static void completeRestore(long lease, boolean confirmed) {}
}
