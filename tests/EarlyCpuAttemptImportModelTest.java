import com.thor.displaypowertest.CpuBootAttemptModel;
import com.thor.displaypowertest.EarlyCpuAttemptImportModel;
import com.thor.displaypowertest.EarlyCpuAttemptImportModel.Action;

public final class EarlyCpuAttemptImportModelTest {
    private static final String BOOT = "11111111-2222-3333-4444-555555555555";
    private static final String OLD = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
    private static int assertions;

    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
        assertions++;
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

        CpuBootAttemptModel.Attempt applied = early.withPhase(CpuBootAttemptModel.Phase.APPLIED, 5000L);
        eq(EarlyCpuAttemptImportModel.decide(
                        early, false, applied, false, BOOT, true),
                Action.KEEP_PROGRESS,
                "daemon replacement must retain the completed form of the same early attempt");

        eq(EarlyCpuAttemptImportModel.decide(early, false,
                        early.withPhase(CpuBootAttemptModel.Phase.APPLIED, 4200L), false, BOOT, true),
                Action.FAIL_SAFE, "completion before the early request is inconsistent");
        eq(EarlyCpuAttemptImportModel.decide(early, false,
                        new CpuBootAttemptModel.Attempt(BOOT, "1", "0", "100",
                                CpuBootAttemptModel.Phase.APPLIED, 5000L), false, BOOT, true),
                Action.FAIL_SAFE, "different previous property is different provenance");
        eq(EarlyCpuAttemptImportModel.decide(early, false,
                        new CpuBootAttemptModel.Attempt(BOOT, "1", "UNSET", "101",
                                CpuBootAttemptModel.Phase.APPLIED, 5000L), false, BOOT, true),
                Action.FAIL_SAFE, "different baseline composer cannot inherit early proof");
        eq(EarlyCpuAttemptImportModel.decide(early, false,
                        early.withPhase(CpuBootAttemptModel.Phase.FAILED, 5000L), false, BOOT, true),
                Action.FAIL_SAFE, "failed attempt must never be upgraded to success");
        eq(EarlyCpuAttemptImportModel.decide(early, false, applied, true, BOOT, true),
                Action.FAIL_SAFE, "corrupt completed proof must remain rejected");
        eq(EarlyCpuAttemptImportModel.decide(early, false, applied, false, BOOT, false),
                Action.FAIL_SAFE, "progress cannot override saved OFF");
        eq(CpuBootAttemptModel.decide(applied, BOOT, "1", "1", "100", 6000L),
                CpuBootAttemptModel.Action.FAIL_SAFE, "current composer still needs successor proof");
        eq(CpuBootAttemptModel.decide(applied, BOOT, "1", "1", "200", 6000L),
                CpuBootAttemptModel.Action.PROCEED, "matching successor permits no-restart recovery");

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

        laterCompletedTransitionTests(early);
        System.out.println("Early CPU attempt import model tests passed: " + assertions + " assertions");
    }

    private static Action observed(CpuBootAttemptModel.Attempt early, boolean earlyCorrupt,
            CpuBootAttemptModel.Attempt durable, boolean durableCorrupt, String boot,
            boolean enabled, String property, String composer, long now) {
        return EarlyCpuAttemptImportModel.decide(early, earlyCorrupt, durable, durableCorrupt,
                boot, enabled, property, composer, now);
    }

    private static void laterCompletedTransitionTests(CpuBootAttemptModel.Attempt early) {
        CpuBootAttemptModel.Attempt later = new CpuBootAttemptModel.Attempt(
                BOOT, "1", "0", "300", CpuBootAttemptModel.Phase.APPLIED, 10000L);
        eq(observed(early, false, later, false, BOOT, true, "1", "400", 20000L),
                Action.KEEP_LATER_APPLIED,
                "later completed OFF to ON survives replacement using its own successor proof");
        eq(CpuBootAttemptModel.decide(later, BOOT, "1", "1", "400", 20000L),
                CpuBootAttemptModel.Action.PROCEED,
                "preserved later proof requires no additional compositor restart");
        eq(EarlyCpuAttemptImportModel.decide(early, false, later, false, BOOT, true),
                Action.FAIL_SAFE, "without current evidence different attempts remain rejected");

        CpuBootAttemptModel.Attempt off = new CpuBootAttemptModel.Attempt(
                BOOT, "0", "1", "300", CpuBootAttemptModel.Phase.APPLIED, 10000L);
        eq(observed(early, false, off, false, BOOT, false, "0", "400", 20000L),
                Action.KEEP_LATER_APPLIED,
                "later completed OFF respects saved OFF despite the original early ON record");
        eq(observed(early, false, later, false, BOOT, false, "1", "400", 20000L),
                Action.FAIL_SAFE, "saved OFF cannot inherit a completed ON state");
        eq(observed(early, false, off, false, BOOT, true, "0", "400", 20000L),
                Action.FAIL_SAFE, "saved ON cannot inherit a completed OFF state");
        eq(observed(early, true, later, false, BOOT, true, "1", "400", 20000L),
                Action.FAIL_SAFE, "corrupt early record stays rejected with later evidence");
        eq(observed(early, false, later, true, BOOT, true, "1", "400", 20000L),
                Action.FAIL_SAFE, "corrupt durable record stays rejected with later evidence");
        eq(observed(early, false, later, false, OLD, true, "1", "400", 20000L),
                Action.FAIL_SAFE, "current boot must match both proofs");
        eq(observed(attempt(OLD, CpuBootAttemptModel.Phase.RESTART_REQUESTED, 4300L),
                false, later, false, BOOT, true, "1", "400", 20000L),
                Action.FAIL_SAFE, "early record from another boot cannot authorize later progress");
        eq(observed(early, false, later, false, BOOT, true, "0", "400", 20000L),
                Action.FAIL_SAFE, "observed property must match latest completed desired state");
        eq(observed(early, false, later, false, BOOT, true, "UNKNOWN", "400", 20000L),
                Action.FAIL_SAFE, "unknown property cannot authorize later progress");
        eq(observed(early, false, later, false, BOOT, true, "UNSET", "400", 20000L),
                Action.FAIL_SAFE, "unset property cannot authorize completed ON progress");
        eq(observed(early, false, later, false, BOOT, true, "1", "300", 20000L),
                Action.FAIL_SAFE, "latest baseline still alive is not a proven successor");
        eq(observed(early, false, later, false, BOOT, true, "1", "", 20000L),
                Action.FAIL_SAFE, "missing composer is not successor proof");
        eq(observed(early, false, later, false, BOOT, true, "1", "0", 20000L),
                Action.FAIL_SAFE, "invalid composer is not successor proof");
        eq(observed(early, false, later, false, BOOT, true, "1", "400", 9999L),
                Action.FAIL_SAFE, "a completion timestamp in the future is not evidence");
        eq(observed(early, false, later, false, BOOT, true, "1", "400", -1L),
                Action.FAIL_SAFE, "unknown current time cannot authorize later progress");
        for (long completedAt : new long[]{4200L, 4300L}) {
            eq(observed(early, false, later.withPhase(CpuBootAttemptModel.Phase.APPLIED,
                    completedAt), false, BOOT, true, "1", "400", 20000L),
                    Action.FAIL_SAFE, "different attempt must complete strictly after the early phase");
        }
        for (CpuBootAttemptModel.Phase phase : CpuBootAttemptModel.Phase.values()) {
            if (phase != CpuBootAttemptModel.Phase.APPLIED) {
                eq(observed(early, false, later.withPhase(phase, 10000L), false,
                        BOOT, true, "1", "400", 20000L),
                        Action.FAIL_SAFE, "pending or failed later record cannot be promoted: " + phase);
            }
        }
        for (CpuBootAttemptModel.Phase phase : new CpuBootAttemptModel.Phase[]{
                CpuBootAttemptModel.Phase.APPLIED, CpuBootAttemptModel.Phase.FAILED}) {
            eq(observed(early.withPhase(phase, 4300L), false, later, false,
                    BOOT, true, "1", "400", 20000L),
                    Action.FAIL_SAFE, "early hook cannot manufacture a terminal phase: " + phase);
        }
        eq(observed(null, false, later, false, BOOT, true, "1", "400", 20000L),
                Action.NO_EARLY_ATTEMPT, "absence preserves the existing durable recovery path");
        eq(observed(early, false, early.withPhase(CpuBootAttemptModel.Phase.APPLIED, 5000L),
                false, BOOT, true, "1", "200", 20000L),
                Action.KEEP_PROGRESS, "same-attempt completion retains its existing policy");
        eq(observed(early, false, attempt(OLD, CpuBootAttemptModel.Phase.APPLIED, 5000L),
                false, BOOT, true, "1", "400", 20000L),
                Action.REPLACE_STALE_DURABLE, "old-boot durable handling remains unchanged");
    }
}
