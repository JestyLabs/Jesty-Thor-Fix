package com.thor.displaypowertest;

/** Pure conflict policy for importing a boot-scoped pservice attempt into the durable store. */
public final class EarlyCpuAttemptImportModel {
    public enum Action {
        NO_EARLY_ATTEMPT,
        IMPORT,
        REPLACE_STALE_DURABLE,
        KEEP_MATCHING,
        KEEP_PROGRESS,
        KEEP_LATER_APPLIED,
        FAIL_SAFE
    }

    private EarlyCpuAttemptImportModel() {}

    /** A later completed transition uses its own current proof, not the early record's proof. */
    public static Action decide(CpuBootAttemptModel.Attempt early, boolean earlyCorrupt,
            CpuBootAttemptModel.Attempt durable, boolean durableCorrupt,
            String currentBootId, boolean desiredEnabled, String property,
            String composerPid, long nowMs) {
        Action strict = decide(early, earlyCorrupt, durable, durableCorrupt,
                currentBootId, desiredEnabled);
        if (strict != Action.FAIL_SAFE) return strict;
        if (earlyCorrupt || durableCorrupt || early == null || durable == null
                || !CpuBootAttemptModel.validBootId(currentBootId)
                || !currentBootId.equalsIgnoreCase(early.bootId)
                || !currentBootId.equalsIgnoreCase(durable.bootId)
                || !"1".equals(early.desired) || !allowedEarlyPhase(early.phase)
                || durable.phase != CpuBootAttemptModel.Phase.APPLIED
                || !durable.desired.equals(desiredEnabled ? "1" : "0")
                || durable.phaseAtMs <= early.phaseAtMs || durable.phaseAtMs > nowMs
                || sameAttempt(early, durable)) {
            return Action.FAIL_SAFE;
        }
        return CpuBootAttemptModel.decide(durable, currentBootId,
                desiredEnabled ? "1" : "0", property, composerPid, nowMs)
                == CpuBootAttemptModel.Action.PROCEED
                ? Action.KEEP_LATER_APPLIED : Action.FAIL_SAFE;
    }

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
            // A daemon replacement re-reads the original /dev record after its
            // predecessor has durably completed that exact restart attempt.
            // Preserve the completed record; never replay the earlier phase.
            if (early.phase == CpuBootAttemptModel.Phase.RESTART_REQUESTED
                    && durable.phase == CpuBootAttemptModel.Phase.APPLIED
                    && sameAttempt(early, durable)
                    && durable.phaseAtMs >= early.phaseAtMs) {
                return Action.KEEP_PROGRESS;
            }
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

    private static boolean sameAttempt(CpuBootAttemptModel.Attempt early,
            CpuBootAttemptModel.Attempt durable) {
        return early.bootId.equalsIgnoreCase(durable.bootId)
                && early.desired.equals(durable.desired)
                && early.previous.equals(durable.previous)
                && early.baselineComposerPid.equals(durable.baselineComposerPid);
    }
}
