package com.thor.displaypowertest;

/**
 * Research-only host state machine (NOT compiled into / called by the APK).
 *
 * Demonstrates recoverability of a suppressed BOTH lower-panel ON **if** the
 * existing watcher later receives confirmed-awake samples. It never treats a
 * previously deferred power command as reusable: each fresh sample must still
 * pass AutomaticBottomOnPolicy and the hardware-boundary commit check.
 */
public final class AutomaticBottomWakeRecoveryModel {
    public enum Outcome {
        NONE, DEFERRED_ASLEEP, DEFERRED_UNKNOWN,
        AWAKE_STABILIZING, ON_CANDIDATE, NEEDS_MANUAL_REVIEW
    }

    private long generation = Long.MIN_VALUE;
    private boolean deferred;
    private int consecutiveAwake;
    private int unknownStreak;

    public Outcome observe(long observationGeneration, String mode, String top,
            String bottom, boolean fixEnabled, boolean actionsHeld,
            boolean wakeRepairPending, AutomaticBottomOnPolicy.Interactive interactive) {
        if (generation != observationGeneration) {
            generation = observationGeneration;
            reset();
        }
        AutomaticBottomOnPolicy.Decision plan = AutomaticBottomOnPolicy.plan(
                mode, top, bottom, fixEnabled, actionsHeld, wakeRepairPending, interactive);
        if (plan == AutomaticBottomOnPolicy.Decision.NONE) {
            reset(); // Mode changed, already restored, Fix off or other no-op.
            return Outcome.NONE;
        }
        if (plan == AutomaticBottomOnPolicy.Decision.SUPPRESS_ASLEEP) {
            deferred = true;
            consecutiveAwake = 0;
            unknownStreak = 0;
            return Outcome.DEFERRED_ASLEEP;
        }
        if (plan == AutomaticBottomOnPolicy.Decision.SUPPRESS_UNKNOWN) {
            deferred = true;
            consecutiveAwake = 0;
            if (unknownStreak < 10) unknownStreak++;
            // Report a need for explicit, owner-approved manual evaluation.
            // NEVER turn the panel on automatically merely because of timeout.
            return unknownStreak >= 10
                    ? Outcome.NEEDS_MANUAL_REVIEW : Outcome.DEFERRED_UNKNOWN;
        }
        unknownStreak = 0;
        if (deferred && ++consecutiveAwake < 2) {
            return Outcome.AWAKE_STABILIZING;
        }
        deferred = false;
        consecutiveAwake = 0;
        return Outcome.ON_CANDIDATE;
    }

    private void reset() {
        deferred = false;
        consecutiveAwake = 0;
        unknownStreak = 0;
    }
}
