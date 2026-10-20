package com.thor.displaypowertest;

import java.lang.reflect.Field;

/** S1-09: real confirmation logic, scripted OS reads and inert vendor outcomes. */
public final class CoordinatorReadbackHostTest {
    private static int checks;
    private static int cases;

    private static void equal(Object expected, Object actual, String why) {
        checks++;
        if (expected == null ? actual != null : !expected.equals(actual))
            throw new AssertionError(why + ": expected=" + expected + ", actual=" + actual);
    }

    private static void field(String name, Object value) throws Exception {
        Field field = DisplayActionCoordinator.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static long prepare(String mode, String[] modes, String[][] pairs,
            String... bottomPolls) throws Exception {
        DisplayActionCoordinator.cancelWakeRepair();
        field("status", "STARTING");
        field("lastMode", "?");
        field("lastAttemptKey", "");
        field("lastAttemptAt", Long.valueOf(0L));
        android.os.SystemClock.setElapsedRealtime(100000L);
        BootSafety.held = false;
        DaemonState.mode = mode;
        DaemonState.enabled = false;
        WatcherSupervisor.state = "RUNNING";
        DisplayHardware.actionCount = 0;
        DisplayHardware.alignedCount = 0;
        DisplayHardware.scriptedApply = true;
        DisplayHardware.lastRequestedOn = null;
        Telemetry.reads = 0;
        Telemetry.scriptedPairs = pairs;
        Telemetry.pairIndex = 0;
        Telemetry.scriptedBottom = bottomPolls;
        Telemetry.bottomIndex = 0;
        ReadbackMode.values = modes;
        ReadbackMode.index = 0;
        WakeRepairScheduler.pending = true;
        WakeRepairScheduler.due = false;
        WakeRepairScheduler.cancellations = 0;
        return DisplayActionCoordinator.generation();
    }

    private static String[] pair(String top, String bottom) {
        return new String[] {top, bottom};
    }

    private static void consumed(String id) {
        equal(ReadbackMode.values.length, ReadbackMode.index, id + " mode reads");
        equal(Telemetry.scriptedPairs.length, Telemetry.pairIndex, id + " pair reads");
        equal(Telemetry.scriptedBottom.length, Telemetry.bottomIndex, id + " poll reads");
        cases++;
        System.out.println("PASS " + id + ";HOST scripted observations, no vendor execution");
    }

    private static void success(String id, String mode, String before, String expected,
            boolean writes) throws Exception {
        long generation = prepare(mode, new String[] {mode, mode, mode},
                new String[][] {pair("1", before), pair("1", before), pair("1", expected)}, expected);
        equal("ok=1;fix=1;display=confirmed", DisplayActionCoordinator.requestFix(true), id);
        equal("CONFIRMED", DisplayActionCoordinator.status(), id + " confirmation");
        equal(Boolean.TRUE, Boolean.valueOf(DaemonState.enabled), id + " requested state committed");
        equal(Boolean.TRUE, Boolean.valueOf(DisplayActionCoordinator.generation() > generation), id + " invalidation");
        equal(Boolean.FALSE, Boolean.valueOf(WakeRepairScheduler.pending), id + " cancel prior repair");
        equal(writes ? 1 : 0, DisplayHardware.actionCount, id + " inert power requests");
        equal(writes ? Boolean.valueOf("1".equals(expected)) : null,
                DisplayHardware.lastRequestedOn, id + " requested direction");
        equal(0, DisplayHardware.alignedCount, id + " no failure alignment");
        consumed(id);
    }

    private static void finalChanged(String id, String mode, String before,
            String expected, String finalMode, String finalTop, String finalBottom,
            boolean writes) throws Exception {
        long generation = prepare(mode, new String[] {mode, mode, finalMode},
                new String[][] {pair("1", before), pair("1", before), pair(finalTop, finalBottom)}, expected);
        equal("ok=0;error=DISPLAY_UNCONFIRMED", DisplayActionCoordinator.requestFix(true), id + " reject false receipt");
        equal("UNCONFIRMED", DisplayActionCoordinator.status(), id + " status");
        equal(Boolean.FALSE, Boolean.valueOf(DaemonState.enabled), id + " no requested-state commit");
        equal(generation, DisplayActionCoordinator.generation(), id + " no success generation");
        equal(Boolean.TRUE, Boolean.valueOf(WakeRepairScheduler.pending), id + " prior repair preserved");
        equal(writes ? 1 : 0, DisplayHardware.actionCount, id + " bounded inert request");
        equal(1, DisplayHardware.alignedCount, id + " failure alignment requested");
        consumed(id);
    }

    private static void admissionChanged() throws Exception {
        String id = "S1-RB04-mode-changes-before-apply";
        long generation = prepare("0", new String[] {"0", "1"}, new String[][] {pair("1", "0")});
        equal("ok=0;error=DISPLAY_UNCONFIRMED", DisplayActionCoordinator.requestFix(true), id);
        equal("MODE_CHANGED", DisplayActionCoordinator.status(), id + " status");
        equal(0, DisplayHardware.actionCount, id + " no command after changed admission");
        equal(0, DisplayHardware.alignedCount, id + " no alignment before command");
        equal(generation, DisplayActionCoordinator.generation(), id + " no commit");
        equal(Boolean.FALSE, Boolean.valueOf(DaemonState.enabled), id + " preference unchanged");
        consumed(id);
    }

    private static void beforeChanged(String id, String top, String bottom) throws Exception {
        long generation = prepare("0", new String[] {"0", "0"},
                new String[][] {pair("1", "0"), pair(top, bottom)});
        equal("ok=0;error=DISPLAY_UNCONFIRMED", DisplayActionCoordinator.requestFix(true), id);
        equal("DISPLAY_ASLEEP_OR_TRANSITION", DisplayActionCoordinator.status(), id + " status");
        equal(0, DisplayHardware.actionCount, id + " no speculative ON");
        equal(0, DisplayHardware.alignedCount, id + " no alignment");
        equal(generation, DisplayActionCoordinator.generation(), id + " no commit");
        equal(Boolean.FALSE, Boolean.valueOf(DaemonState.enabled), id + " preference unchanged");
        consumed(id);
    }

    private static void initialUnknown() throws Exception {
        String id = "S1-RB11-initial-unknown";
        prepare("0", new String[] {"0"}, new String[][] {pair("?", "0")});
        equal("ok=0;error=DISPLAY_TRANSITION", DisplayActionCoordinator.requestFix(true), id);
        equal(0, DisplayHardware.actionCount, id + " no power request");
        equal(0, DisplayHardware.alignedCount, id + " no alignment");
        consumed(id);
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1 && "final-mode".equals(args[0])) {
            finalChanged("S1-RB05-final-mode", "0", "0", "1", "1", "1", "1", true);
            return;
        }
        success("S1-RB01-already-on", "0", "1", "1", false);
        success("S1-RB02-confirmed-on", "0", "0", "1", true);
        success("S1-RB03-confirmed-off", "1", "1", "0", true);
        admissionChanged();
        finalChanged("S1-RB05-final-mode", "0", "0", "1", "1", "1", "1", true);
        finalChanged("S1-RB06-final-bottom", "0", "0", "1", "0", "1", "0", true);
        finalChanged("S1-RB07-final-top", "0", "0", "1", "0", "0", "1", true);
        finalChanged("S1-RB08-final-unknown", "0", "0", "1", "0", "1", "?", true);
        beforeChanged("S1-RB09-before-unknown", "?", "0");
        beforeChanged("S1-RB10-before-asleep", "0", "0");
        initialUnknown();
        finalChanged("S1-RB12-final-top-mode-bottom", "1", "1", "0", "1", "1", "1", true);
        finalChanged("S1-RB13-no-command-but-readback-drifts", "0", "1", "1", "0", "1", "0", false);
        System.out.println("CoordinatorReadbackHostTest passed: " + cases + " cases, " + checks
                + " checks; Settings scripted, vendor inert; Android/Thor NOT RUN");
    }
}

/** Replaces only the private Settings process-read method in the temporary copy. */
final class ReadbackMode {
    static String[] values;
    static int index;
    static String next() {
        if (values == null || index >= values.length)
            throw new AssertionError("unexpected Settings mode read");
        return values[index++];
    }
}
