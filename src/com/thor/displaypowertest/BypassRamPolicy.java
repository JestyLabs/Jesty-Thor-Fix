package com.thor.displaypowertest;

/**
 * Pure decision for one intercepted panel-RAM bypass write. Every input is a
 * fresh read taken just before the decision; nothing here keeps state.
 *
 * The vendor framework writes the attribute on every refresh change even when
 * the lower panel is off. That write is what leaves the lower DSI link unable to
 * start until reboot, so the real attribute is only written with the lower panel
 * powered and the device interactive. Otherwise the request waits for the next ON.
 */
public final class BypassRamPolicy {
    public enum Action { NOOP, APPLY, DEFER, REJECT }

    private BypassRamPolicy() {}

    /** "0" or "1" for the forms the framework and the kernel use; null otherwise. */
    public static String parse(String raw) {
        if (raw == null) return null;
        String value = raw.trim();
        if ("0".equals(value) || "0x0".equals(value)) return "0";
        if ("1".equals(value) || "0x1".equals(value)) return "1";
        return null;
    }

    public static Action decide(String wanted, String real, String lowerDpms,
            String interactive, boolean lowerLatched) {
        if (wanted == null) return Action.REJECT;
        if (wanted.equals(real)) return Action.NOOP;
        return deferReason(real, lowerDpms, interactive, lowerLatched) == null
                ? Action.APPLY : Action.DEFER;
    }

    /** Why a write must wait, or null when it may proceed. */
    public static String deferReason(String real, String lowerDpms, String interactive,
            boolean lowerLatched) {
        if (lowerLatched) return "LOWER_LATCHED";
        if (!"0".equals(real) && !"1".equals(real)) return "REAL_UNREADABLE";
        if (!"On".equals(lowerDpms)) return "Off".equals(lowerDpms) ? "LOWER_OFF" : "LOWER_UNKNOWN";
        if (!"1".equals(interactive)) return "0".equals(interactive) ? "NON_INTERACTIVE" : "INTERACTIVE_UNKNOWN";
        return null;
    }
}
