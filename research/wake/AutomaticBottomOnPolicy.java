package com.thor.displaypowertest;

/**
 * RESEARCH-ONLY, NOT CONNECTED TO THE DAEMON.
 *
 * Decision model for a possible awake gate on the automatic BOTH panel-ON
 * reconciliation. The production Display Fix keeps using DisplayDecisionModel;
 * this class deliberately changes no runtime behavior.
 *
 * An interactive observation is only a software signal, not an atomic promise
 * that the device will remain awake until the hardware command executes.
 */
public final class AutomaticBottomOnPolicy {
    public enum Interactive { AWAKE, ASLEEP, UNKNOWN }
    public enum Decision { NONE, ALLOW_CANDIDATE_ON, SUPPRESS_ASLEEP, SUPPRESS_UNKNOWN }

    private AutomaticBottomOnPolicy() {}

    public static Decision plan(String mode, String top, String bottom,
            boolean fixEnabled, boolean actionsHeld, boolean wakeRepairPending,
            Interactive interactive) {
        DisplayDecisionModel.Action base = DisplayDecisionModel.reconcile(mode, top, bottom,
                fixEnabled, actionsHeld, wakeRepairPending);
        if (base != DisplayDecisionModel.Action.ON) return Decision.NONE;
        // Legacy model ignores wakeRepairPending on its BOTH 1/0 ON path.
        // Never compound an outstanding repair with an automatic ON.
        if (wakeRepairPending) return Decision.NONE;
        if (interactive == Interactive.ASLEEP) return Decision.SUPPRESS_ASLEEP;
        if (interactive != Interactive.AWAKE) return Decision.SUPPRESS_UNKNOWN;
        return Decision.ALLOW_CANDIDATE_ON;
    }

    /**
     * Provisional second-stage check at the command boundary. A generation
     * mismatch or a new sleep observation revokes the earlier authorization.
     * This cannot eliminate the final asynchronous awake->sleep race.
     */
    public static boolean commitAllowed(Decision planned, Interactive interactiveNow,
            boolean sameObservationGeneration, String mode, String top, String bottom,
            boolean fixEnabled, boolean actionsHeld, boolean wakeRepairPending) {
        return planned == Decision.ALLOW_CANDIDATE_ON
                && sameObservationGeneration
                && interactiveNow == Interactive.AWAKE
                && plan(mode, top, bottom, fixEnabled, actionsHeld, wakeRepairPending,
                        interactiveNow) == Decision.ALLOW_CANDIDATE_ON;
    }
}
