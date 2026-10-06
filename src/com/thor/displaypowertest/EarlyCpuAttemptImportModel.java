package com.thor.displaypowertest;

/** Pure conflict policy for importing a boot-scoped pservice attempt into the durable store. */
public final class EarlyCpuAttemptImportModel {
    public enum Action {
        NO_EARLY_ATTEMPT,
        IMPORT,
        REPLACE_STALE_DURABLE,
        KEEP_MATCHING,
        FAIL_SAFE
    }

    private EarlyCpuAttemptImportModel() {}

    public static Action decide(CpuBootAttemptModel.Attempt early, boolean earlyCorrupt,
            CpuBootAttemptModel.Attempt durable, boolean durableCorrupt,
            String currentBootId, boolean desiredEnabled) {
        if (earlyCorrupt || durableCorrupt) return Action.FAIL_SAFE;
        if (early == null) return Action.NO_EARLY_ATTEMPT;
        if (!CpuBootAttemptModel.validBootId(currentBootId)
                || !currentBootId.equalsIgnoreCase(early.bootId)
                || !desiredEnabled
                || !"1".equals(early.desired)
                || !allowedEarlyPhase(early.phase)) {
            return Action.FAIL_SAFE;
        }

        if (durable == null) return Action.IMPORT;
        if (currentBootId.equalsIgnoreCase(durable.bootId)) {
            return durable.encode().equals(early.encode())
                    ? Action.KEEP_MATCHING : Action.FAIL_SAFE;
        }
        return Action.REPLACE_STALE_DURABLE;
    }

    private static boolean allowedEarlyPhase(CpuBootAttemptModel.Phase phase) {
        return phase == CpuBootAttemptModel.Phase.PREPARED
                || phase == CpuBootAttemptModel.Phase.PROPERTY_VERIFIED
                || phase == CpuBootAttemptModel.Phase.RESTART_REQUESTED;
    }
}
