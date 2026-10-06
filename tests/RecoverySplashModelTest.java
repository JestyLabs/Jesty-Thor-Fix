import com.thor.displaypowertest.CpuBootAttemptModel;
import com.thor.displaypowertest.RecoverySplashModel;
import com.thor.displaypowertest.RecoverySplashModel.Failure;
import com.thor.displaypowertest.RecoverySplashModel.Policy;
import com.thor.displaypowertest.RecoverySplashModel.State;
import com.thor.displaypowertest.RecoverySplashModel.Trace;
import com.thor.displaypowertest.RecoverySplashModel.Transition;

public final class RecoverySplashModelTest {
    private static final String BOOT = "11111111-2222-3333-4444-555555555555";
    private static final Policy POLICY = new Policy(1000L, 200L, 4000L, 300L);

    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static void traces(Transition value, Trace... expected) {
        eq(value.traces.length, expected.length, "trace count");
        for (int i = 0; i < expected.length; i++) {
            eq(value.traces[i], expected[i], "trace at " + i);
        }
    }

    private static Transition armed(long at) {
        Transition t = RecoverySplashModel.arm(true, true, true, at);
        eq(t.session.state, State.ARMED, "prototype arms only on restart path");
        traces(t, Trace.SPLASH_ARMED);
        return t;
    }

    private static void successPath() {
        Transition t = armed(100L);
        t = RecoverySplashModel.waitForSurfaceFlinger(t.session, 110L);
        eq(t.session.state, State.WAITING_FOR_SF, "waits for successor SF");
        traces(t, Trace.SPLASH_WAIT_SF);
        t = RecoverySplashModel.successorSurfaceFlinger(t.session, 600L);
        eq(t.session.state, State.SHOW_REQUESTED, "successor SF permits show request");
        traces(t, Trace.SPLASH_SHOW_REQUESTED);
        t = RecoverySplashModel.shown(t.session, true, 650L);
        eq(t.session.state, State.SHOWN, "confirmed show enters visible state");
        traces(t, Trace.SPLASH_SHOWN);
        t = RecoverySplashModel.frameworkRecovered(t.session, 2400L);
        eq(t.session.state, State.REMOVE_REQUESTED, "framework recovery requests removal");
        traces(t, Trace.SPLASH_REMOVE_REQUESTED);
        t = RecoverySplashModel.removed(t.session, true, 2440L);
        eq(t.session.state, State.REMOVED, "removal completes lifecycle");
        traces(t, Trace.SPLASH_REMOVED);
    }

    private static void armingGate() {
        eq(RecoverySplashModel.arm(false, true, true, 1L).session.state, State.DISARMED,
                "feature flag off is inert");
        eq(RecoverySplashModel.arm(true, false, true, 1L).session.state, State.DISARMED,
                "runtime/non-boot path is inert");
        eq(RecoverySplashModel.arm(true, true, false, 1L).session.state, State.DISARMED,
                "splash cannot arm before restart is requested");
    }

    private static void timeoutsFailOpen() {
        Transition t = armed(0L);
        t = RecoverySplashModel.waitForSurfaceFlinger(t.session, 10L);
        t = RecoverySplashModel.tick(t.session, POLICY, 1010L);
        eq(t.session.state, State.FAIL_OPEN, "SF timeout fails open");
        eq(t.session.failure, Failure.SF_TIMEOUT, "SF timeout reason");
        traces(t, Trace.SPLASH_TIMEOUT, Trace.SPLASH_FAIL_OPEN);

        t = armed(0L);
        t = RecoverySplashModel.waitForSurfaceFlinger(t.session, 10L);
        t = RecoverySplashModel.successorSurfaceFlinger(t.session, 20L);
        t = RecoverySplashModel.tick(t.session, POLICY, 220L);
        eq(t.session.state, State.FAIL_OPEN, "show timeout fails open");
        eq(t.session.failure, Failure.SHOW_TIMEOUT, "show timeout reason");
        traces(t, Trace.SPLASH_TIMEOUT, Trace.SPLASH_FAIL_OPEN);

        t = armed(0L);
        t = RecoverySplashModel.waitForSurfaceFlinger(t.session, 10L);
        t = RecoverySplashModel.successorSurfaceFlinger(t.session, 20L);
        t = RecoverySplashModel.shown(t.session, true, 30L);
        t = RecoverySplashModel.tick(t.session, POLICY, 4030L);
        eq(t.session.state, State.REMOVE_REQUESTED,
                "visible timeout requests cleanup before fail-open");
        eq(t.session.failure, Failure.VISIBLE_TIMEOUT, "visible timeout reason");
        traces(t, Trace.SPLASH_TIMEOUT, Trace.SPLASH_REMOVE_REQUESTED);
        t = RecoverySplashModel.tick(t.session, POLICY, 4330L);
        eq(t.session.state, State.FAIL_OPEN, "remove timeout no longer gates boot");
        eq(t.session.failure, Failure.REMOVE_TIMEOUT, "remove timeout reason");
        traces(t, Trace.SPLASH_TIMEOUT, Trace.SPLASH_FAIL_OPEN);
    }

    private static void explicitFailuresFailOpen() {
        Transition t = armed(0L);
        t = RecoverySplashModel.waitForSurfaceFlinger(t.session, 10L);
        t = RecoverySplashModel.successorSurfaceFlinger(t.session, 20L);
        t = RecoverySplashModel.shown(t.session, false, 30L);
        eq(t.session.state, State.FAIL_OPEN, "show failure fails open");
        eq(t.session.failure, Failure.SHOW_FAILED, "show failure reason");
        traces(t, Trace.SPLASH_FAIL_OPEN);

        t = armed(0L);
        t = RecoverySplashModel.waitForSurfaceFlinger(t.session, 10L);
        t = RecoverySplashModel.successorSurfaceFlinger(t.session, 20L);
        t = RecoverySplashModel.shown(t.session, true, 30L);
        t = RecoverySplashModel.frameworkRecovered(t.session, 40L);
        t = RecoverySplashModel.removed(t.session, false, 50L);
        eq(t.session.state, State.FAIL_OPEN, "remove failure fails open");
        eq(t.session.failure, Failure.REMOVE_FAILED, "remove failure reason");
        traces(t, Trace.SPLASH_FAIL_OPEN);
    }

    private static void cpuRestartInvariantIsIndependent() {
        CpuBootAttemptModel.Attempt attempt = new CpuBootAttemptModel.Attempt(
                BOOT, "1", "UNSET", "100",
                CpuBootAttemptModel.Phase.RESTART_REQUESTED, 1000L);
        eq(CpuBootAttemptModel.decide(attempt, BOOT, "1", "1", "100", 1100L),
                CpuBootAttemptModel.Action.WAIT_FOR_RESTART,
                "CPU model starts in one-restart pending state");

        Transition splash = armed(1100L);
        splash = RecoverySplashModel.waitForSurfaceFlinger(splash.session, 1110L);
        splash = RecoverySplashModel.successorSurfaceFlinger(splash.session, 1200L);
        splash = RecoverySplashModel.shown(splash.session, true, 1210L);
        splash = RecoverySplashModel.frameworkRecovered(splash.session, 1400L);
        splash = RecoverySplashModel.removed(splash.session, true, 1410L);
        eq(splash.session.state, State.REMOVED, "splash lifecycle completes independently");

        eq(CpuBootAttemptModel.decide(attempt, BOOT, "1", "1", "100", 1410L),
                CpuBootAttemptModel.Action.WAIT_FOR_RESTART,
                "splash transitions cannot authorize a second restart");
        eq(CpuBootAttemptModel.decide(attempt, BOOT, "1", "1", "200", 1410L),
                CpuBootAttemptModel.Action.MARK_APPLIED,
                "only successor composer provenance advances CPU attempt");
    }

    public static void main(String[] args) {
        armingGate();
        successPath();
        timeoutsFailOpen();
        explicitFailuresFailOpen();
        cpuRestartInvariantIsIndependent();
        System.out.println("RecoverySplashModel tests passed");
    }
}
