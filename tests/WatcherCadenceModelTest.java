import com.thor.displaypowertest.WatcherCadenceModel;

public final class WatcherCadenceModelTest {
    private static int assertions;

    public static void main(String[] args) {
        stableKnownModeUsesSafetyPoll();
        unknownAndRepairStayFast();
        eventStartsAndExtendsBurst();
        drmCadenceChangesBetweenBurstAndSteady();
        forcedDrmProbeIsImmediate();
        resetClearsState();
        System.out.println("WatcherCadenceModelTest passed: " + assertions + " assertions");
    }

    private static void stableKnownModeUsesSafetyPoll() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        eq(WatcherCadenceModel.STEADY_SAFETY_POLL_MS,
                m.nextModePollDelayMs(1000L, true, false));
    }

    private static void unknownAndRepairStayFast() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        eq(WatcherCadenceModel.FAST_POLL_MS,
                m.nextModePollDelayMs(1000L, false, false));
        eq(WatcherCadenceModel.FAST_POLL_MS,
                m.nextModePollDelayMs(1000L, true, true));
    }

    private static void eventStartsAndExtendsBurst() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        m.noteEvent(1000L);
        truth(m.burstActive(2499L));
        eq(WatcherCadenceModel.FAST_POLL_MS,
                m.nextModePollDelayMs(2000L, true, false));
        m.noteEvent(2000L);
        truth(m.burstActive(3499L));
        truth(!m.burstActive(3500L));
        eq(WatcherCadenceModel.STEADY_SAFETY_POLL_MS,
                m.nextModePollDelayMs(3500L, true, false));
    }

    private static void drmCadenceChangesBetweenBurstAndSteady() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        truth(m.shouldProbeDrm(0L, false));
        truth(!m.shouldProbeDrm(999L, false));
        truth(m.shouldProbeDrm(1000L, false));

        m.noteEvent(1100L);
        truth(!m.shouldProbeDrm(1249L, false));
        truth(m.shouldProbeDrm(1250L, false));
        truth(!m.shouldProbeDrm(1499L, false));
        truth(m.shouldProbeDrm(1500L, false));
    }

    private static void forcedDrmProbeIsImmediate() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        truth(m.shouldProbeDrm(100L, false));
        truth(!m.shouldProbeDrm(200L, false));
        truth(m.shouldProbeDrm(200L, true));
    }

    private static void resetClearsState() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        m.noteEvent(1000L);
        m.shouldProbeDrm(1000L, false);
        m.reset();
        truth(!m.burstActive(1001L));
        truth(m.shouldProbeDrm(1001L, false));
    }

    private static void eq(long expected, long actual) {
        assertions++;
        if (expected != actual) throw new AssertionError(expected + " != " + actual);
    }

    private static void truth(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("expected true");
    }
}
