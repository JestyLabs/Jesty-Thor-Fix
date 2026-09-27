import com.thor.displaypowertest.DashboardStateModel;

public final class DashboardStateModelTest {
    private static int assertions;

    public static void main(String[] args) {
        displayStates();
        displayTransitionsAndMismatch();
        clockDiagnosis();
        clockResetsAndBusyState();
        batteryMathAndMedian();
        System.out.println("DashboardStateModelTest passed: " + assertions + " assertions");
    }

    private static void displayStates() {
        assertDisplay("BOTH SCREENS", stableDisplay("0", "1", "1", true));
        assertDisplay("TOP ONLY \u00B7 AYN BLACK SCREEN", stableDisplay("1", "1", "1", false));
        assertDisplay("TOP ONLY \u00B7 TRUE OFF", stableDisplay("1", "1", "0", true));
        assertDisplay("BOTTOM ONLY", stableDisplay("2", "0", "1", true));
    }

    private static DashboardStateModel.DisplayStatus stableDisplay(
            String mode, String top, String bottom, boolean fix) {
        DashboardStateModel model = new DashboardStateModel();
        DashboardStateModel.DisplayStatus status = null;
        for (int i = 0; i < 3; i++) status = model.updateDisplay(mode, top, bottom, fix);
        return status;
    }

    private static void displayTransitionsAndMismatch() {
        DashboardStateModel model = new DashboardStateModel();
        assertDisplay("DISPLAY TRANSITION", model.updateDisplay("0", "1", "1", true));
        model.resetDisplay();
        assertDisplay("DISPLAY TRANSITION", model.updateDisplay("0", "1", "1", true));
        for (int i = 0; i < 7; i++) model.updateDisplay("0", "1", "0", true);
        assertDisplay("DISPLAY STATE MISMATCH", model.updateDisplay("0", "1", "0", true));
    }

    private static void clockDiagnosis() {
        DashboardStateModel model = new DashboardStateModel();
        DashboardStateModel.ClockStatus status = feed(model, "a", false, false,
                10, 10, 10, 10, 5, 11);
        equal("LITTLE + BIG PINNED AT MAX", status.text);
        truth(status.pinned);

        model.resetClocks();
        status = feed(model, "b", false, false, 1, 10, 9, 10, 5, 11);
        equal("CLOCKS SCALING NORMALLY", status.text);
        truth(!status.pinned);

        model.resetClocks();
        status = feed(model, "c", true, true, 10, 10, 10, 10, 5, 11);
        equal("UNEXPECTED PINNING \u00B7 FIX ACTIVE", status.text);
    }

    private static void clockResetsAndBusyState() {
        DashboardStateModel model = new DashboardStateModel();
        DashboardStateModel.ClockStatus status = feed(model, "a", false, false,
                10, 10, 10, 10, 40, 11);
        equal("CPU BUSY \u00B7 MONITORING PAUSED", status.text);
        status = model.updateClocks("changed", 120, 120, 120, 120, 1, false, false);
        equal("CHECKING CPU CLOCKS\u2026", status.text);
        status = model.updateClocks("changed", 130, 130, 130, 130, 1, true, false);
        equal("CHECKING CPU CLOCKS\u2026", status.text);
    }

    private static DashboardStateModel.ClockStatus feed(DashboardStateModel model,
            String key, boolean desired, boolean active, long maxDelta, long totalDelta,
            long bigMaxDelta, long bigTotalDelta, int utilization, int samples) {
        long lm = 0, lt = 0, bm = 0, bt = 0;
        DashboardStateModel.ClockStatus status = null;
        status = model.updateClocks(key, lm, lt, bm, bt, utilization, desired, active);
        for (int i = 0; i < samples; i++) {
            lm += maxDelta;
            lt += totalDelta;
            bm += bigMaxDelta;
            bt += bigTotalDelta;
            status = model.updateClocks(key, lm, lt, bm, bt, utilization, desired, active);
        }
        return status;
    }

    private static void batteryMathAndMedian() {
        near(2.0d, DashboardStateModel.batteryWatts(-500000, 4000000, false, "Discharging"));
        near(2.0d, DashboardStateModel.batteryWatts(500000, 4000000, false, "Discharging"));
        truth(Double.isNaN(DashboardStateModel.batteryWatts(500000, 4000000, true, "Charging")));
        truth(Double.isNaN(DashboardStateModel.batteryWatts(500000, 4000000, false, "Unknown")));
        DashboardStateModel model = new DashboardStateModel();
        model.smoothBattery(1d);
        model.smoothBattery(100d);
        near(2d, model.smoothBattery(2d));
        model.smoothBattery(3d);
        near(3d, model.smoothBattery(4d));
    }

    private static void assertDisplay(String expected, DashboardStateModel.DisplayStatus status) {
        equal(expected, status.title);
    }

    private static void equal(String expected, String actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void near(double expected, double actual) {
        assertions++;
        if (Math.abs(expected - actual) > 0.0001d) throw new AssertionError(expected + " != " + actual);
    }

    private static void truth(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("expected true");
    }
}
