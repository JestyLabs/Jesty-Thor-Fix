import com.thor.displaypowertest.EarlyCpuGateModel;
import com.thor.displaypowertest.EarlyCpuGateModel.Result;

/**
 * Exhaustively checks early CPU readiness combinations and timeout boundaries.
 * Pure model only: no setprop, reboot, SurfaceFlinger restart or physical Thor.
 */
public final class EarlyCpuGateAdversarialTest {
    private static final String VALID_BOOT = "11111111-2222-3333-4444-555555555555";
    private static final String[] BOOT_IDS = {VALID_BOOT, "corrupt", null};
    private static final String[] WATCHERS = {"RUNNING", "STARTING", "STALLED", null};
    private static final String[] PROPERTIES = {"0", "1", "UNSET", "?", "invalid", null};
    private static final String[] PIDS = {"100", "?", "0", "99999999999", null};
    private static final long START = 1000L;
    private static int cases;

    private static Result expected(long now, boolean boot, boolean composer,
            String watcher, String bootId, boolean desired, String property, String pid) {
        if (now < START) return Result.FAIL_SAFE;
        if (now >= START + EarlyCpuGateModel.TIMEOUT_MS) return Result.TIMEOUT;
        if (!boot || !composer || !"RUNNING".equals(watcher)) return Result.WAIT;
        if (!VALID_BOOT.equals(bootId)) return Result.FAIL_SAFE;
        if ("?".equals(property) || "?".equals(pid)) return Result.WAIT;
        boolean pidKnown = "100".equals(pid);
        boolean propertyKnown = "0".equals(property) || "1".equals(property)
                || "UNSET".equals(property);
        if (!pidKnown || !propertyKnown) return Result.FAIL_SAFE;
        return !desired && "UNSET".equals(property) ? Result.FAIL_SAFE : Result.READY;
    }
    private static void check(boolean success, String scenario) {
        cases++;
        if (!success) throw new AssertionError(scenario);
    }
    public static void main(String[] args) {
        for (long now : new long[] {
                START - 1L, START, START + 123L,
                START + EarlyCpuGateModel.TIMEOUT_MS - 1L,
                START + EarlyCpuGateModel.TIMEOUT_MS,
                START + EarlyCpuGateModel.TIMEOUT_MS + 1L }) {
            for (boolean boot : new boolean[] {false, true}) {
                for (boolean composer : new boolean[] {false, true}) {
                    for (String watcher : WATCHERS) {
                        for (String id : BOOT_IDS) {
                            for (boolean desired : new boolean[] {false, true}) {
                                for (String property : PROPERTIES) {
                                    for (String pid : PIDS) {
                                        Result actual = new EarlyCpuGateModel(START).observe(
                                                now, boot, composer, watcher, id, desired,
                                                property, pid);
                                        Result want = expected(now, boot, composer, watcher,
                                                id, desired, property, pid);
                                        check(actual == want, "early boot gate now=" + now
                                                + " boot=" + boot + " composer=" + composer
                                                + " watcher=" + watcher + " id=" + id
                                                + " desired=" + desired + " property=" + property
                                                + " pid=" + pid + " expected=" + want
                                                + " actual=" + actual);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        check(EarlyCpuGateModel.POLL_MS == 200L,
                "keep CPU prerequisite poll cadence");
        check(EarlyCpuGateModel.TIMEOUT_MS == 60000L,
                "keep independent 60s CPU gate");
        System.out.println("EarlyCpuGateAdversarialTest passed (" + cases
                + " source-policy cases)");
    }
}
