import com.thor.displaypowertest.DisplayGenerationModel;

public final class DisplayGenerationModelTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        DisplayGenerationModel model = new DisplayGenerationModel();
        check(!model.isRepairCurrent(), "no repair at startup");
        long first = model.scheduleRepair();
        check(model.isRepairCurrent(), "scheduled repair is current");
        model.invalidate(); // TOP to BOTH, toggle, sleep, or boot transition
        check(model.current() > first && !model.isRepairCurrent(),
                "old repair invalidated by newer action");
        model.scheduleRepair();
        check(model.isRepairCurrent(), "new repair accepted");
        model.completeRepair();
        check(!model.isRepairCurrent(), "completed repair cannot run again");
        System.out.println("DisplayGenerationModelTest passed");
    }
}
