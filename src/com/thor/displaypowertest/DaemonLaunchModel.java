package com.thor.displaypowertest;

/** Pure launch decisions for AutoService; callers supply socket and identity observations. */
public final class DaemonLaunchModel {
    /** State of the private socket path when no healthy daemon answered. */
    public enum SocketState { ABSENT, STALE_PREVIOUS_BOOT, STALE_UNKNOWN, ALIVE }

    /** Authenticated daemon response classification. */
    public enum Probe { HEALTHY, STARTING, UNHEALTHY, UNREACHABLE }

    /** 200 ms polls allowed for an unreachable inode that may belong to this boot. */
    public static final int SAME_BOOT_GRACE_POLLS = 25;

    private static final String[] BOOT_PHASES = {
        "BOOT_HOLD", "WAITING_FOR_ANDROID", "APPLYING_CPU_FIX",
        "WAITING_AFTER_COMPOSER", "RECONCILING_DISPLAY"
    };

    private DaemonLaunchModel() {}

    /**
     * A filesystem socket inode survives power-off. Only a kernel boot ID stamped
     * by the daemon that differs from the current one proves it is from an
     * earlier boot; anything unreadable stays in the conservative branch.
     */
    public static SocketState classify(boolean socketExists, boolean reachable,
            String stampedBootId, String currentBootId) {
        if (reachable) return SocketState.ALIVE;
        if (!socketExists) return SocketState.ABSENT;
        if (!validBootId(stampedBootId) || !validBootId(currentBootId)) {
            return SocketState.STALE_UNKNOWN;
        }
        return stampedBootId.equals(currentBootId)
                ? SocketState.STALE_UNKNOWN : SocketState.STALE_PREVIOUS_BOOT;
    }

    /** No wait when the inode cannot belong to a live daemon of this kernel boot. */
    public static int unreachableGracePolls(SocketState state) {
        return state == SocketState.STALE_UNKNOWN ? SAME_BOOT_GRACE_POLLS : 0;
    }

    /**
     * A same-version authenticated daemon still inside its boot coordinator owns
     * the transition; AutoService must neither wait for READY nor relaunch it.
     * BOOT SAFETY TIMEOUT is deliberately not a starting phase.
     */
    public static boolean starting(String response, String version, boolean expectedFix) {
        if (response == null || !response.startsWith("ok=1;")) return false;
        if (!"2".equals(field(response, "protocol"))
                || !version.equals(field(response, "version"))
                || !field(response, "pid").matches("[1-9][0-9]{0,8}")
                || !(expectedFix ? "1" : "0").equals(field(response, "fix"))) return false;
        String phase = field(response, "boot_phase");
        for (String candidate : BOOT_PHASES) {
            if (candidate.equals(phase)) return true;
        }
        return false;
    }

    public static boolean validBootId(String value) {
        return value != null && value.trim().matches("[0-9a-fA-F-]{32,36}");
    }

    private static String field(String response, String name) {
        String needle = ";" + name + "=";
        int start = response.indexOf(needle);
        if (start < 0) return "";
        start += needle.length();
        int end = response.indexOf(';', start);
        return response.substring(start, end < 0 ? response.length() : end);
    }
}
