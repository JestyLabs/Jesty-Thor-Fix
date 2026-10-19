package com.thor.displaypowertest;
import android.os.SystemClock;

/** Actual coordinator and hardware implementation; platform observations are synthetic. */
public final class DisplayWakeCoordinatorTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void reset() { SystemClock.now += 10000L; TestDisplayState.baseline(); }
    public static void main(String[] args) {
        reset();
        TestDisplayState.wakeTop();
        check(DisplayActionCoordinator.isWakeRepairPending(), "TOP wake must schedule repair");
        check("1".equals(TestDisplayState.property) && TestDisplayState.propertyWrites == 0,
                "Pending repair must preserve firmware wake property");
        TestDisplayState.both();
        DisplayActionCoordinator.noteDisplayOn();
        check(!DisplayActionCoordinator.isWakeRepairPending(), "BOTH cancels repair");
        check(WakeRepairScheduler.notes == 0, "stale display callback cannot revive cancelled repair");
        check("1".equals(TestDisplayState.property) && TestDisplayState.hardwareCalls == 0,
                "cancelled repair leaves BOTH and firmware flag unchanged");

        reset();
        TestDisplayState.wakeTop();
        long originalDeadline = WakeRepairScheduler.deadline;
        for (int i=0; i<30; i++) {
            SystemClock.now += 20;
            DisplayActionCoordinator.onWatcherSample("1");
        }
        check(WakeRepairScheduler.schedules == 1 && WakeRepairScheduler.deadline == originalDeadline,
                "Repeated awake samples must not postpone repair indefinitely");
        check(TestDisplayState.hardwareCalls == 0, "repair cannot run before existing deadline");
        SystemClock.now = originalDeadline;
        DisplayActionCoordinator.onWatcherSample("1");
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom),
                "real hardware method performs lower OFF at unchanged deadline");
        check("0".equals(TestDisplayState.property) && TestDisplayState.propertyWrites == 1,
                "only the actual OFF action publishes zero");

        reset(); TestDisplayState.wakeTop();
        TestDisplayState.sleep();
        check(!DisplayActionCoordinator.isWakeRepairPending(), "observed sleep cancels pending work");
        check(TestDisplayState.hardwareCalls == 0 && TestDisplayState.propertyWrites == 0,
                "sleep cancellation must not write power or overwrite firmware zero");
        long newWakeAt = SystemClock.now;
        TestDisplayState.wakeTop();
        check(WakeRepairScheduler.schedules == 2 && WakeRepairScheduler.deadline == newWakeAt+700,
                "new wake after sleep starts a fresh repair interval");
        SystemClock.now += 500; DisplayActionCoordinator.noteDisplayOn();
        check(WakeRepairScheduler.deadline == newWakeAt+900,
                "display ON confirmation retains the existing 400ms minimum");

        reset(); TestDisplayState.wakeTop();
        SystemClock.now = WakeRepairScheduler.deadline;
        TestDisplayState.mode = "0";
        DisplayActionCoordinator.onWatcherSample("1");
        check(TestDisplayState.hardwareCalls == 0 && TestDisplayState.propertyWrites == 0,
                "fresh mode mismatch at command boundary prevents stale TOP OFF");

        for (int callbackOrder=0; callbackOrder<2; callbackOrder++) {
            reset();
            // Exercise display confirmation before and after the watcher sees wake.
            if (callbackOrder==0) DisplayActionCoordinator.noteDisplayOn();
            TestDisplayState.wakeTop();
            if (callbackOrder==1) DisplayActionCoordinator.noteDisplayOn();
            TestDisplayState.both();
            check("1".equals(TestDisplayState.property) && TestDisplayState.hardwareCalls==0,
                    "display callback ordering cannot leave an early OFF flag after BOTH");
        }
        for (int gate=0; gate<3; gate++) {
            reset();
            TestDisplayState.enabled = gate!=0;
            TestDisplayState.held = gate==1;
            TestDisplayState.wakeTop();
            if (gate==2) DisplayActionCoordinator.onWatcherSample("?");
            check(!DisplayActionCoordinator.isWakeRepairPending(), "disabled/held/unknown gate cancels or prevents repair");
            check(TestDisplayState.hardwareCalls==0 && TestDisplayState.propertyWrites==0,
                    "unavailable authority cannot change power");
        }
        reset(); TestDisplayState.wakeTop();
        check(DisplayActionCoordinator.requestFix(false).startsWith("ok=1"),
                "disabling fix while panels already awake confirms without a power call");
        check(!DisplayActionCoordinator.isWakeRepairPending() && !TestDisplayState.enabled,
                "confirmed disable cancels pending repair");
        check("1".equals(TestDisplayState.property) && TestDisplayState.propertyWrites==0,
                "disable cancellation does not leave an early OFF flag");

        reset(); TestDisplayState.guard = true; TestDisplayState.guardDefers = true;
        TestDisplayState.wakeTop();
        check(!DisplayActionCoordinator.isWakeRepairPending() && TestDisplayState.propertyWrites==0,
                "lid deferral prevents scheduling and power writes");

        reset(); TestDisplayState.property="1"; TestDisplayState.noToken=true;
        check(!DisplayHardware.apply(false, "TEST_NO_TOKEN") && TestDisplayState.propertyWrites==0,
                "missing physical token cannot publish OFF");
        reset(); TestDisplayState.property="1"; TestDisplayState.powerFailure=true;
        check(!DisplayHardware.apply(false, "TEST_PLATFORM_FAILURE"), "platform exception is reported");
        check("1".equals(TestDisplayState.property) && TestDisplayState.propertyWrites==2,
                "failed hardware operation rolls back the actual-action property write");
        System.out.println("DisplayWakeCoordinatorTest passed: "+checks+" assertions (host, synthetic boundaries)");
    }
}
