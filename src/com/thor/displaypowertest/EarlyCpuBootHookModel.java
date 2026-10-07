package com.thor.displaypowertest;

/**
 * Pure policy model for a future stock-pservice early CPU-restart hook.
 *
 * This class does not install or execute the stock boot hook, write properties
 * or restart services. It only models ownership/cleanup decisions and whether a
 * boot-scoped early attempt may be handed to the existing CpuBootAttemptModel.
 */
public final class EarlyCpuBootHookModel {
    public enum HookState {
        ABSENT,
        OWNED_EXACT,
        OCCUPIED_UNKNOWN
    }

    public enum HookAction {
        NONE,
        INSTALL_OWNED,
        KEEP_OWNED,
        REMOVE_OWNED,
        REFUSE_OCCUPIED
    }

    public enum HandoffAction {
        USE_NORMAL_PATH,
        ADOPT_EARLY_ATTEMPT,
        FALLBACK_AFTER_PROVEN_FAILURE,
        DELETE_STALE,
        FAIL_SAFE
    }

    private EarlyCpuBootHookModel() {}

    /**
     * Never overwrite or remove an unknown firmware-global hook file.
     *
     * installationIdentityPresent is intentionally generic for now. A runtime
     * implementation must define a strong app-owned identity that survives
     * cold boot but disappears or becomes invalid after uninstall.
     */
    public static HookAction hookAction(boolean desiredEnabled,
            boolean installationIdentityPresent, HookState state) {
        if (state == null) return HookAction.REFUSE_OCCUPIED;

        if (!installationIdentityPresent) {
            return state == HookState.OWNED_EXACT
                    ? HookAction.REMOVE_OWNED : HookAction.NONE;
        }

        if (!desiredEnabled) {
            return state == HookState.OWNED_EXACT
                    ? HookAction.REMOVE_OWNED : HookAction.NONE;
        }

        switch (state) {
            case ABSENT:
                return HookAction.INSTALL_OWNED;
            case OWNED_EXACT:
                return HookAction.KEEP_OWNED;
            case OCCUPIED_UNKNOWN:
            default:
                return HookAction.REFUSE_OCCUPIED;
        }
    }

    /**
     * Decides whether a /dev early-attempt record can be imported into the
     * already-proven CpuBootAttemptModel.
     *
     * A FAILED early attempt only allows the normal path when the original
     * composer is still running and the global property was restored exactly to
     * the recorded previous value. Otherwise a second automatic restart would
     * be an unproven retry and must fail safe.
     */
    public static HandoffAction handoff(CpuBootAttemptModel.Attempt attempt,
            String currentBootId, boolean desiredEnabled,
            String observedProperty, String currentComposerPid) {
        if (attempt == null) return HandoffAction.USE_NORMAL_PATH;
        if (!CpuBootAttemptModel.validBootId(currentBootId)
                || !CpuBootAttemptModel.property(observedProperty)
                || !CpuBootAttemptModel.pid(currentComposerPid)) {
            return HandoffAction.FAIL_SAFE;
        }
        if (!currentBootId.equalsIgnoreCase(attempt.bootId)) {
            return HandoffAction.DELETE_STALE;
        }
        if (!desiredEnabled || !"1".equals(attempt.desired)) {
            return HandoffAction.FAIL_SAFE;
        }

        if (attempt.phase == CpuBootAttemptModel.Phase.FAILED) {
            boolean originalComposer = attempt.baselineComposerPid.equals(currentComposerPid);
            boolean restored = attempt.previous.equals(observedProperty);
            return originalComposer && restored
                    ? HandoffAction.FALLBACK_AFTER_PROVEN_FAILURE
                    : HandoffAction.FAIL_SAFE;
        }

        return HandoffAction.ADOPT_EARLY_ATTEMPT;
    }
}
