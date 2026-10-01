package com.thor.displaypowertest;

/** Pure safety decision. Unknown or sleeping physical state never requests ON. */
public final class DisplayDecisionModel {
    public enum Action { NONE, ON, OFF }

    private DisplayDecisionModel() {}

    public static Action reconcile(String mode, String topCrtc, String bottomCrtc,
            boolean fixEnabled, boolean actionsHeld, boolean wakeRepairPending) {
        if (actionsHeld || !fixEnabled) return Action.NONE;
        if ("1".equals(mode) && "1".equals(topCrtc)
                && "1".equals(bottomCrtc) && !wakeRepairPending) return Action.OFF;
        if ("0".equals(mode) && "1".equals(topCrtc)
                && "0".equals(bottomCrtc)) return Action.ON;
        return Action.NONE;
    }

    public static String effective(String mode, String topCrtc, String bottomCrtc,
            boolean fixEnabled, boolean actionsHeld) {
        if (actionsHeld || !("0".equals(mode) || "1".equals(mode) || "2".equals(mode))
                || !("0".equals(topCrtc) || "1".equals(topCrtc))
                || !("0".equals(bottomCrtc) || "1".equals(bottomCrtc))
                || ("0".equals(topCrtc) && "0".equals(bottomCrtc))) return "PENDING";
        String expectedTop = "2".equals(mode) ? "0" : "1";
        String expectedBottom = fixEnabled && "1".equals(mode) ? "0" : "1";
        return expectedTop.equals(topCrtc) && expectedBottom.equals(bottomCrtc)
                ? "CONFIRMED" : "MISMATCH";
    }
}
