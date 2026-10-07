import com.thor.displaypowertest.CpuBootAttemptModel;
import com.thor.displaypowertest.EarlyCpuAttemptImportModel;
import com.thor.displaypowertest.EarlyCpuAttemptImportModel.Action;

public final class EarlyCpuAttemptImportModelTest {
    private static final String BOOT = "11111111-2222-3333-4444-555555555555";
    private static final String OLD = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static CpuBootAttemptModel.Attempt attempt(String boot,
            CpuBootAttemptModel.Phase phase, long at) {
        return new CpuBootAttemptModel.Attempt(
                boot, "1", "UNSET", "100", phase, at);
    }

    public static void main(String[] args) {
        CpuBootAttemptModel.Attempt early =
                attempt(BOOT, CpuBootAttemptModel.Phase.RESTART_REQUESTED, 4300L);

        eq(EarlyCpuAttemptImportModel.decide(
                        null, false, null, false, BOOT, true),
                Action.NO_EARLY_ATTEMPT,
                "absence preserves current path");

        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, null, false, BOOT, true),
                Action.IMPORT,
                "valid same-boot early attempt imports");

        eq(EarlyCpuAttemptImportModel.decide(
                        early, true, null, false, BOOT, true),
                Action.FAIL_SAFE,
                "corrupt early record fails safe");

        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, null, true, BOOT, true),
                Action.FAIL_SAFE,
                "corrupt durable marker blocks import");

        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, null, false, OLD, true),
                Action.FAIL_SAFE,
                "early record must match current kernel boot");

        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, null, false, BOOT, false),
                Action.FAIL_SAFE,
                "early enable attempt cannot authorize saved OFF");

        CpuBootAttemptModel.Attempt matching =
                attempt(BOOT, CpuBootAttemptModel.Phase.RESTART_REQUESTED, 4300L);
        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, matching, false, BOOT, true),
                Action.KEEP_MATCHING,
                "identical current-boot durable marker is accepted");

        CpuBootAttemptModel.Attempt conflict =
                attempt(BOOT, CpuBootAttemptModel.Phase.PROPERTY_VERIFIED, 4250L);
        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, conflict, false, BOOT, true),
                Action.FAIL_SAFE,
                "different current-boot provenance must never be overwritten");

        CpuBootAttemptModel.Attempt stale =
                attempt(OLD, CpuBootAttemptModel.Phase.APPLIED, 5000L);
        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, stale, false, BOOT, true),
                Action.REPLACE_STALE_DURABLE,
                "old-boot durable marker may be replaced by same-boot early proof");

        CpuBootAttemptModel.Attempt impossibleApplied =
                attempt(BOOT, CpuBootAttemptModel.Phase.APPLIED, 4300L);
        eq(EarlyCpuAttemptImportModel.decide(
                        impossibleApplied, false, null, false, BOOT, true),
                Action.FAIL_SAFE,
                "pservice hook must never manufacture APPLIED");

        CpuBootAttemptModel.Attempt impossibleFailed =
                attempt(BOOT, CpuBootAttemptModel.Phase.FAILED, 4300L);
        eq(EarlyCpuAttemptImportModel.decide(
                        impossibleFailed, false, null, false, BOOT, true),
                Action.FAIL_SAFE,
                "prototype hook must never manufacture FAILED");

        System.out.println("Early CPU attempt import model tests passed");
    }
}
