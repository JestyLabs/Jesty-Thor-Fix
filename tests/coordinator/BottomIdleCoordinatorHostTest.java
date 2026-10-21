package com.thor.displaypowertest;

import java.lang.reflect.Field;

/** Production v1.7.5 coordinator with scripted Settings/DRM and inert panel. */
public final class BottomIdleCoordinatorHostTest {
    private static int checks;
    private static void check(boolean condition, String id) {
        checks++;
        if (!condition) throw new AssertionError("IDLE-" + id);
    }
    private static void field(String name, Object value) throws Exception {
        Field field = DisplayActionCoordinator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }
    private static void reset() throws Exception {
        field("status", "STARTING");
        field("lowerOnBackoff", false);
        field("bothDegraded", false);
        field("lastMode", "0");
        field("lastAttemptKey", "");
        field("lastAttemptAt", 0L);
        android.os.SystemClock.setElapsedRealtime(100000L);
        DaemonState.mode = "0";
        DaemonState.enabled = true;
        BootSafety.held = false;
        WatcherSupervisor.state = "RUNNING";
        BottomIdleRuntime.pending = -1L;
        BottomIdleRuntime.restoreLease = -1L;
        BottomIdleRuntime.off = false;
        BottomIdleRuntime.completed = 0;
        BottomIdleRuntime.lastConfirmed = false;
        DisplayHardware.actionCount = 0;
        DisplayHardware.alignedCount = 0;
        DisplayHardware.scriptedApply = true;
        DisplayHardware.applySucceeds = true;
        DisplayHardware.applyElapsed = 0L;
        DisplayHardware.lastRequestedOn = null;
        Telemetry.top = "1";
        Telemetry.bottom = "1";
        Telemetry.scriptedPairs = null;
        Telemetry.scriptedBottom = null;
        Telemetry.pairIndex = 0;
        Telemetry.bottomIndex = 0;
        ReadbackMode.values = new String[0];
        ReadbackMode.index = 0;
    }
    private static void denied() {
        check(DisplayHardware.actionCount == 0, "denied-no-hardware");
        check(BottomIdleRuntime.completed == 1 && !BottomIdleRuntime.lastConfirmed,
                "denied-not-confirmed");
        check(ReadbackMode.index == 0, "denied-no-mode-read");
    }
    public static void main(String[] args) throws Exception {
        reset();
        DisplayActionCoordinator.requestIdleBottomOff(7L);
        denied();
        reset();
        BottomIdleRuntime.pending = 9L;
        field("lowerOnBackoff", true);
        DisplayActionCoordinator.requestIdleBottomOff(9L);
        denied();
        check(DisplayActionCoordinator.isLowerOnBackoff(), "backoff-kept");
        reset();
        BottomIdleRuntime.pending = 9L;
        BootSafety.held = true;
        DisplayActionCoordinator.requestIdleBottomOff(9L);
        denied();
        reset();
        BottomIdleRuntime.restoreLease = 4L;
        BottomIdleRuntime.pending = 5L;
        DisplayActionCoordinator.restoreIdleBottom(4L);
        check(DisplayHardware.actionCount == 0, "stale-restore-pending-off");
        reset();
        BottomIdleRuntime.restoreLease = 5L;
        DisplayActionCoordinator.restoreIdleBottom(4L);
        check(DisplayHardware.actionCount == 0, "stale-restore-generation");
        reset();
        BottomIdleRuntime.restoreLease = 5L;
        field("lowerOnBackoff", true);
        DisplayActionCoordinator.restoreIdleBottom(5L);
        check(DisplayHardware.actionCount == 0, "stale-restore-during-backoff");
        reset();
        BottomIdleRuntime.pending = 10L;
        ReadbackMode.values = new String[] {"0", "0"};
        Telemetry.scriptedPairs = new String[][] {{"1", "1"}, {"1", "0"}};
        Telemetry.scriptedBottom = new String[] {"0"};
        DisplayActionCoordinator.requestIdleBottomOff(10L);
        check(BottomIdleRuntime.completed == 1 && BottomIdleRuntime.lastConfirmed,
                "confirmed-off-lease");
        check(DisplayHardware.actionCount == 1
                        && Boolean.FALSE.equals(DisplayHardware.lastRequestedOn),
                "confirmed-off-single-command");
        reset();
        BottomIdleRuntime.pending = 11L;
        ReadbackMode.values = new String[] {"0", "0"};
        Telemetry.scriptedPairs = new String[][] {{"1", "1"}, {"1", "0"}};
        Telemetry.scriptedBottom = new String[] {"0"};
        DisplayHardware.applyElapsed = 6000L;
        DisplayActionCoordinator.requestIdleBottomOff(11L);
        check(DisplayActionCoordinator.isLowerOnBackoff(), "slow-call-backoff");
        check(BottomIdleRuntime.lastConfirmed, "slow-confirmed-off-owned");
        reset();
        BottomIdleRuntime.restoreLease = 16L;
        Telemetry.bottom = "0";
        ReadbackMode.values = new String[] {"0", "0"};
        Telemetry.scriptedPairs = new String[][] {{"1", "0"}, {"1", "1"}};
        Telemetry.scriptedBottom = new String[] {"1"};
        DisplayActionCoordinator.restoreIdleBottom(16L);
        check(DisplayHardware.actionCount == 1
                        && Boolean.TRUE.equals(DisplayHardware.lastRequestedOn),
                "current-generation-restore");
        System.out.println("BottomIdleCoordinatorHostTest passed " + checks + " checks");
    }
}
