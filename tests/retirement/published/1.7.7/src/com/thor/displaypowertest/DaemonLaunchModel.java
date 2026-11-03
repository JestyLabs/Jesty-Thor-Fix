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
        if (!sameIdentity(response, version, expectedFix)) return false;
        String phase = field(response, "boot_phase");
        for (String candidate : BOOT_PHASES) {
            if (candidate.equals(phase)) return true;
        }
        return false;
    }

    /**
     * A boot phase that has not changed for this long is stalled: every gate
     * phase has its own 60-second timeout and a normal compositor handover
     * replaces the daemon within roughly 10-20 seconds.
     */
    public static final long STUCK_PHASE_MS = 90000L;

    /** Held phase of a daemon whose compositor handover failed (from v1.5.18). */
    public static final String HANDOFF_FAILED_PHASE = "HANDOFF_FAILED";

    /**
     * PID of an authenticated same-version, same-intent daemon that asks to be
     * replaced (HANDOFF_FAILED) or whose starting phase has stalled, or -1. A
     * response without a numeric phase_ms (any daemon before 1.5.18) is never
     * classified as stalled. BOOT SAFETY TIMEOUT is never replaced here.
     */
    public static int replaceablePid(String response, String version, boolean expectedFix) {
        if (!sameIdentity(response, version, expectedFix)) return -1;
        boolean replaceable = HANDOFF_FAILED_PHASE.equals(field(response, "boot_phase"));
        if (!replaceable && starting(response, version, expectedFix)) {
            String age = field(response, "phase_ms");
            replaceable = age.matches("[0-9]{1,12}") && Long.parseLong(age) >= STUCK_PHASE_MS;
        }
        if (!replaceable) return -1;
        int pid = Integer.parseInt(field(response, "pid"));
        return pid > 100 ? pid : -1;
    }

    private static boolean sameIdentity(String response, String version, boolean expectedFix) {
        return response != null && response.startsWith("ok=1;")
                && "2".equals(field(response, "protocol"))
                && version.equals(field(response, "version"))
                && field(response, "pid").matches("[1-9][0-9]{0,8}")
                && (expectedFix ? "1" : "0").equals(field(response, "fix"));
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
