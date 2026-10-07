import com.thor.displaypowertest.WatcherCadenceModel;

public final class WatcherCadenceModelTest {
    private static int assertions;

    public static void main(String[] args) {
        idleUsesSlowSafetyCadence();
        displayEventStartsShortBurst();
        modeChangeAlsoStartsBurst();
        drmReadsAreEventOrSafetyDriven();
        repairAndStableTopForceDrm();
        eventDuringDrmReadIsNotLost();
        burstExtensionIsMonotonic();
        System.out.println("WatcherCadenceModelTest passed: " + assertions + " assertions");
    }

    private static void idleUsesSlowSafetyCadence() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        eq(750L, m.nextDelayMs(5000L));
        truth(!m.inBurst(5000L));
    }

    private static void displayEventStartsShortBurst() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        m.onDisplayEvent(1000L);
        truth(m.inBurst(1000L));
        eq(20L, m.nextDelayMs(1200L));
        truth(!m.inBurst(2600L));
        eq(750L, m.nextDelayMs(2600L));
    }

    private static void modeChangeAlsoStartsBurst() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        m.onModeChanged(2000L);
        eq(20L, m.nextDelayMs(2500L));
        truth(m.eventGeneration() == 1L);
    }

    private static void drmReadsAreEventOrSafetyDriven() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        long generation = m.beginDrmRead(1000L, -1L, false, false, false);
        eq(0L, generation);
        m.completeDrmRead(generation);
        eq(-1L, m.beginDrmRead(1500L, 1000L, false, false, false));
        eq(0L, m.beginDrmRead(2000L, 1000L, false, false, false));

        m.onDisplayEvent(2100L);
        generation = m.beginDrmRead(2100L, 2000L, false, false, false);
        eq(1L, generation);
        m.completeDrmRead(generation);
        eq(-1L, m.beginDrmRead(2200L, 2100L, false, false, false));
    }

    private static void repairAndStableTopForceDrm() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        eq(0L, m.beginDrmRead(100L, 90L, false, true, false));
        eq(0L, m.beginDrmRead(100L, 90L, false, false, true));
        eq(0L, m.beginDrmRead(100L, 90L, true, false, false));
    }

    private static void eventDuringDrmReadIsNotLost() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        m.onDisplayEvent(1000L);
        long generation = m.beginDrmRead(1000L, 900L, false, false, false);
        eq(1L, generation);
        m.onDisplayEvent(1001L);
        m.completeDrmRead(generation);
        truth(m.eventGeneration() == 2L);
        truth(m.consumedGeneration() == 1L);
        eq(2L, m.beginDrmRead(1002L, 1000L, false, false, false));
    }

    private static void burstExtensionIsMonotonic() {
        WatcherCadenceModel m = new WatcherCadenceModel();
        m.onDisplayEvent(1000L);
        m.onDisplayEvent(2000L);
        eq(20L, m.nextDelayMs(3000L));
        eq(750L, m.nextDelayMs(3600L));
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
