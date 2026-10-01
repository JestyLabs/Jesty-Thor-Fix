import com.thor.displaypowertest.CpuWarningModel;
import com.thor.displaypowertest.DashboardStateModel;

public final class CpuWarningModelTest {
    public static void main(String[] args) {
        check(CpuWarningModel.mayMeasure(false, false, "UNKNOWN"),
                "unknown CPU property must not hide raw clock diagnosis");
        check(!CpuWarningModel.mayMeasure(false, false, "PENDING"),
                "composer transition must pause diagnosis");
        check(!CpuWarningModel.mayMeasure(true, false, "UNKNOWN"),
                "boot hold must pause diagnosis");
        check(!CpuWarningModel.mayMeasure(false, true, "UNKNOWN"),
                "preference mismatch must pause diagnosis");
        DashboardStateModel model = new DashboardStateModel();
        DashboardStateModel.ClockStatus clocks = model.updateClocks(
                "unknown", 0, 0, 0, 0, 5, true, false, true);
        for (int i = 1; i <= 11; i++) {
            clocks = model.updateClocks("unknown", i * 10, i * 10,
                    i * 10, i * 10, 5, true, false, true);
        }
        check(clocks.pinned, "sustained high clocks must be diagnosed");
        check("CPU FIX UNKNOWN \u00B7 CLOCKS PINNED".equals(
                CpuWarningModel.text(false, true, false, "UNKNOWN", clocks)),
                "pinned clock symptom must be visible without claiming fix active");
        check("CPU FIX STATE UNKNOWN \u00B7 NO RESTART".equals(
                CpuWarningModel.text(false, true, false, "UNKNOWN", null)),
                "unknown property must not imply a restart");
        System.out.println("CpuWarningModelTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
