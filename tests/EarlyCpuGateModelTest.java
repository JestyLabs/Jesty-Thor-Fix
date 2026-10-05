import com.thor.displaypowertest.EarlyCpuGateModel;

public final class EarlyCpuGateModelTest {
    private static final String BOOT = "11111111-2222-3333-4444-555555555555";

    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static EarlyCpuGateModel.Result observe(EarlyCpuGateModel gate, long now,
            boolean booted, boolean composer, String watcher, String bootId,
            boolean desired, String property, String pid) {
        return gate.observe(now, booted, composer, watcher, bootId, desired, property, pid);
    }

    public static void main(String[] args) {
        EarlyCpuGateModel gate = new EarlyCpuGateModel(1000L);

        eq(observe(gate, 1000L, false, true, "RUNNING", BOOT, true, "0", "100"),
                EarlyCpuGateModel.Result.WAIT,
                "sys.boot_completed is required");
        eq(observe(gate, 1500L, true, false, "RUNNING", BOOT, true, "0", "100"),
                EarlyCpuGateModel.Result.WAIT,
                "composer service must be running");
        eq(observe(gate, 2000L, true, true, "STARTING", BOOT, true, "0", "100"),
                EarlyCpuGateModel.Result.WAIT,
                "watcher must have produced a real sample");
        eq(observe(gate, 2500L, true, true, "STALLED", BOOT, true, "0", "100"),
                EarlyCpuGateModel.Result.WAIT,
                "stalled watcher never authorizes an early restart");

        eq(observe(gate, 3000L, true, true, "RUNNING", "", true, "0", "100"),
                EarlyCpuGateModel.Result.FAIL_SAFE,
                "missing boot id cannot anchor restart provenance");
        eq(observe(gate, 3500L, true, true, "RUNNING", BOOT, true, "?", "100"),
                EarlyCpuGateModel.Result.WAIT,
                "transient property read failure waits");
        eq(observe(gate, 4000L, true, true, "RUNNING", BOOT, true, "0", "?"),
                EarlyCpuGateModel.Result.WAIT,
                "transient composer pid failure waits");
        eq(observe(gate, 4500L, true, true, "RUNNING", BOOT, true, "bogus", "100"),
                EarlyCpuGateModel.Result.FAIL_SAFE,
                "unexpected property value fails safe");
        eq(observe(gate, 5000L, true, true, "RUNNING", BOOT, true, "0", "0"),
                EarlyCpuGateModel.Result.FAIL_SAFE,
                "invalid composer pid fails safe");

        eq(observe(gate, 5500L, true, true, "RUNNING", BOOT, true, "UNSET", "100"),
                EarlyCpuGateModel.Result.READY,
                "enable may start from an explicitly unset property");
        eq(observe(gate, 6000L, true, true, "RUNNING", BOOT, false, "UNSET", "100"),
                EarlyCpuGateModel.Result.FAIL_SAFE,
                "unset property is never proof of disabled");
        eq(observe(gate, 6500L, true, true, "RUNNING", BOOT, true, "1", "100"),
                EarlyCpuGateModel.Result.READY,
                "matching binary property is a valid observation");
        eq(observe(gate, 7000L, true, true, "RUNNING", BOOT, false, "1", "100"),
                EarlyCpuGateModel.Result.READY,
                "disable mismatch is valid input for the attempt model");

        eq(observe(gate, 999L, true, true, "RUNNING", BOOT, true, "0", "100"),
                EarlyCpuGateModel.Result.FAIL_SAFE,
                "time before the gate start is invalid");
        eq(observe(gate, 1000L + EarlyCpuGateModel.TIMEOUT_MS,
                        true, true, "RUNNING", BOOT, true, "0", "100"),
                EarlyCpuGateModel.Result.TIMEOUT,
                "early CPU gate is bounded by the boot safety deadline");

        System.out.println("Early CPU gate model tests passed");
    }
}
