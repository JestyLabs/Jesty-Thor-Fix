import com.thor.displaypowertest.CpuBootAttemptModel;
import com.thor.displaypowertest.EarlyCpuBootHookModel;
import com.thor.displaypowertest.EarlyCpuBootHookModel.HandoffAction;
import com.thor.displaypowertest.EarlyCpuBootHookModel.HookAction;
import com.thor.displaypowertest.EarlyCpuBootHookModel.HookState;

public final class EarlyCpuBootHookModelTest {
    private static final String BOOT = "11111111-2222-3333-4444-555555555555";
    private static final String OLD_BOOT = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static CpuBootAttemptModel.Attempt attempt(
            CpuBootAttemptModel.Phase phase, String previous, String pid) {
        return new CpuBootAttemptModel.Attempt(
                BOOT, "1", previous, pid, phase, 4200L);
    }

    public static void main(String[] args) {
        eq(EarlyCpuBootHookModel.hookAction(true, true, HookState.ABSENT),
                HookAction.INSTALL_OWNED,
                "enabled app may install only into an absent global hook");
        eq(EarlyCpuBootHookModel.hookAction(true, true, HookState.OWNED_EXACT),
                HookAction.KEEP_OWNED,
                "exact owned hook may be retained");
        eq(EarlyCpuBootHookModel.hookAction(true, true, HookState.OCCUPIED_UNKNOWN),
                HookAction.REFUSE_OCCUPIED,
                "unknown global hook must never be overwritten");
        eq(EarlyCpuBootHookModel.hookAction(false, true, HookState.OWNED_EXACT),
                HookAction.REMOVE_OWNED,
                "disabling removes only the exact owned hook");
        eq(EarlyCpuBootHookModel.hookAction(false, true, HookState.OCCUPIED_UNKNOWN),
                HookAction.NONE,
                "disabling never removes somebody else's hook");
        eq(EarlyCpuBootHookModel.hookAction(true, false, HookState.OWNED_EXACT),
                HookAction.REMOVE_OWNED,
                "missing installation identity cleans owned residue");
        eq(EarlyCpuBootHookModel.hookAction(true, false, HookState.OCCUPIED_UNKNOWN),
                HookAction.NONE,
                "missing app identity still leaves unknown hook untouched");

        eq(EarlyCpuBootHookModel.handoff(null, BOOT, true, "UNSET", "100"),
                HandoffAction.USE_NORMAL_PATH,
                "no early record keeps the current normal path");

        CpuBootAttemptModel.Attempt requested =
                attempt(CpuBootAttemptModel.Phase.RESTART_REQUESTED, "UNSET", "100");
        eq(EarlyCpuBootHookModel.handoff(requested, BOOT, true, "1", "200"),
                HandoffAction.ADOPT_EARLY_ATTEMPT,
                "same-boot requested attempt is handed to existing provenance");
        eq(EarlyCpuBootHookModel.handoff(requested, OLD_BOOT, true, "1", "200"),
                HandoffAction.DELETE_STALE,
                "old-boot early record is disposable");
        eq(EarlyCpuBootHookModel.handoff(requested, BOOT, false, "1", "200"),
                HandoffAction.FAIL_SAFE,
                "early enable proof cannot authorize saved OFF");

        CpuBootAttemptModel.Attempt failed =
                attempt(CpuBootAttemptModel.Phase.FAILED, "UNSET", "100");
        eq(EarlyCpuBootHookModel.handoff(failed, BOOT, true, "UNSET", "100"),
                HandoffAction.FALLBACK_AFTER_PROVEN_FAILURE,
                "normal restart is allowed only after proven no-restart failure and restore");
        eq(EarlyCpuBootHookModel.handoff(failed, BOOT, true, "1", "100"),
                HandoffAction.FAIL_SAFE,
                "failed early attempt with un-restored property cannot retry");
        eq(EarlyCpuBootHookModel.handoff(failed, BOOT, true, "UNSET", "200"),
                HandoffAction.FAIL_SAFE,
                "failed marker cannot claim failure after composer already changed");

        System.out.println("Early CPU boot-hook model tests passed");
    }
}
