package com.thor.displaypowertest;

/**
 * Deliberately narrow, read-only permission to retire a published predecessor.
 * The caller has already authenticated the root peer using SecureChannel.
 * A version string, filesystem socket, or Binder submission alone is not proof.
 */
public final class LiveHandoverPolicy {
    private LiveHandoverPolicy() {}

    public static int safePid(String health, String snapshot, boolean displayFix,
            boolean cpuFix, boolean lidGuard) {
        return checkedPid(health, snapshot, displayFix, cpuFix, lidGuard, false);
    }

    /**
     * Only LiveRetirement's retained-descriptor session may use this boundary.
     * Its first I request follows descriptor acquisition, and every subsequent
     * connection must match that live root process before any request is sent.
     */
    static int safePidForRetainedProcess(String health, String snapshot,
            boolean displayFix, boolean cpuFix, boolean lidGuard, int peerUid, int peerPid) {
        return safePidForRetainedProcess(health, snapshot, displayFix, cpuFix, lidGuard,
                peerUid, peerPid, null);
    }

    /** Same decision; {@code why} receives the first failed condition (observation only). */
    static int safePidForRetainedProcess(String health, String snapshot, boolean displayFix,
            boolean cpuFix, boolean lidGuard, int peerUid, int peerPid, StringBuilder why) {
        if (peerUid != 0 || peerPid <= 100) return reject(why, "PEER", "");
        int pid = checkedPid(health, snapshot, displayFix, cpuFix, lidGuard, true, why);
        if (pid < 1) return -1;
        return pid == peerPid ? pid : reject(why, "PEER_PID", "");
    }

    /**
     * Read-only diagnostic: null when eligible, else the first failed condition. The retained
     * flag selects the same identity gate as the retained-descriptor path (peer not checked).
     */
    public static String ineligibleReason(String health, String snapshot, boolean displayFix,
            boolean cpuFix, boolean lidGuard, boolean retainedProcess) {
        StringBuilder why = new StringBuilder();
        return checkedPid(health, snapshot, displayFix, cpuFix, lidGuard, retainedProcess, why) > 0
                ? null : why.toString();
    }

    static boolean sameRetainedIdentity(String first, String second) {
        return field(first, "version") != null
                && field(first, "version").equals(field(second, "version"))
                && field(first, "pid") != null
                && field(first, "pid").equals(field(second, "pid"))
                && "2".equals(field(second, "protocol"));
    }

    private static int checkedPid(String health, String snapshot, boolean displayFix,
            boolean cpuFix, boolean lidGuard, boolean retainedProcess) {
        return checkedPid(health, snapshot, displayFix, cpuFix, lidGuard, retainedProcess, null);
    }

    private static int checkedPid(String health, String snapshot, boolean displayFix,
            boolean cpuFix, boolean lidGuard, boolean retainedProcess, StringBuilder why) {
        int pid = retainedProcess
                ? PreviousSecureDaemonIdentity.safePidForRetainedProcess(health, snapshot, displayFix, why)
                : PreviousSecureDaemonIdentity.safePid(health, snapshot);
        // The retained gate names its own condition first; the public gate stays coarse.
        if (pid < 1) return reject(why, "PREVIOUS_IDENTITY", "");
        // Exact published predecessors only; never a broad version comparison.
        String version = field(health, "version");
        if (!"1.7.5".equals(version) && !"1.7.4".equals(version) && !"1.7.3".equals(version) && !"1.7.2".equals(version)
                && !"1.7.1".equals(version)) return reject(why, "VERSION", version);
        // I and Q must describe the same process generation, not a socket takeover.
        String runtimeVersion = field(snapshot, "runtime_version");
        boolean publishedCode76 = retainedProcess && "1.7.1".equals(version)
                && snapshot.indexOf(";runtime_version=") < 0;
        if (!publishedCode76 && !version.equals(runtimeVersion))
            return reject(why, "RUNTIME_VERSION", runtimeVersion);
        if (!"READY".equals(field(health, "boot_phase")))
            return reject(why, "BOOT_PHASE", field(health, "boot_phase"));
        if (!expected(field(health, "fix"), displayFix))
            return reject(why, "FIX_HEALTH", field(health, "fix"));
        if (!expected(field(snapshot, "fix"), displayFix))
            return reject(why, "FIX_SNAPSHOT", field(snapshot, "fix"));
        if (!"0".equals(field(snapshot, "display_actions_held")))
            return reject(why, "ACTIONS_HELD", field(snapshot, "display_actions_held"));
        if (!"CONFIRMED".equals(field(snapshot, "display_effective")))
            return reject(why, "DISPLAY_EFFECTIVE", field(snapshot, "display_effective"));
        if (!expected(field(snapshot, "cpu_fix_desired"), cpuFix))
            return reject(why, "CPU_FIX_DESIRED", field(snapshot, "cpu_fix_desired"));
        if (!"CONFIRMED".equals(field(snapshot, "cpu_fix_phase")))
            return reject(why, "CPU_FIX_PHASE", field(snapshot, "cpu_fix_phase"));
        if (!expected(field(snapshot, "system_load_fix"), cpuFix))
            return reject(why, "SYSTEM_LOAD_FIX", field(snapshot, "system_load_fix"));
        if (!expected(field(snapshot, "lid_guard"), lidGuard))
            return reject(why, "LID_GUARD", field(snapshot, "lid_guard"));
        if ("1".equals(field(snapshot, "mode"))) {
            if (!"CONFIRMED".equals(field(snapshot, "display_action_status")))
                return reject(why, "TOP_ACTION_STATUS", field(snapshot, "display_action_status"));
            if (!confirmedTopSnapshot(snapshot))
                return reject(why, "TOP_REPAIR_OR_GENERATION", field(snapshot, "repair_result"));
        }
        // Do not retire a daemon while the lid is known closed or a dock is connected.
        // With Guard OFF the stock firmware reports external_display=unknown.
        // Missing values retain their previous policy, but duplicate values do not.
        String lid = field(snapshot, "lid");
        String externalDisplay = field(snapshot, "external_display");
        if (lid == null || "closed".equals(lid)) return reject(why, "LID", lid);
        if (externalDisplay == null || "1".equals(externalDisplay)
                || (lidGuard && !"0".equals(externalDisplay)))
            return reject(why, "EXTERNAL_DISPLAY", externalDisplay);
        return pid;
    }

    private static boolean confirmedTopSnapshot(String snapshot) {
        String repair = field(snapshot, "repair_result");
        return "CONFIRMED".equals(field(snapshot, "display_action_status"))
                && ("NONE".equals(repair) || "OFF_OK".equals(repair) || "CANCELLED".equals(repair))
                && validGeneration(field(snapshot, "display_generation"));
    }

    /** Endpoint checks detect observed mode/repair invalidations, not a platform freeze. */
    static boolean sameRetainedDisplayState(String first, String second) {
        String mode = field(first, "mode");
        if (mode == null || !mode.equals(field(second, "mode"))) return false;
        String generation = field(first, "display_generation");
        return "0".equals(mode) || ("1".equals(mode) && validGeneration(generation)
                && generation.equals(field(second, "display_generation")));
    }

    private static boolean validGeneration(String value) {
        if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) return false;
        try { return Long.parseLong(value) >= 0L; }
        catch (NumberFormatException invalid) { return false; }
    }

    private static boolean expected(String value, boolean enabled) {
        return (enabled ? "1" : "0").equals(value);
    }

    /**
     * Observation only: names the first failed eligibility condition with a fixed code and a
     * bounded, allowlisted value, never a raw daemon reply, and always returns the refusal -1.
     * (2026-10-28: a held TOP transfer could not be explained because the refusal had no reason.)
     */
    private static int reject(StringBuilder why, String code, String value) {
        if (why != null && why.length() == 0) why.append(code).append(";value=").append(
                value == null ? "DUPLICATE" : value.isEmpty() ? "ABSENT"
                : value.length() <= 32 && value.matches("[A-Za-z0-9._-]+") ? value : "INVALID");
        return -1;
    }

    private static String field(String response, String name) {
        if (response == null) return "";
        String needle = ";" + name + "=";
        int start = response.indexOf(needle);
        if (start < 0) return "";
        start += needle.length();
        int end = response.indexOf(';', start);
        // Null distinguishes a duplicate (invalid) from an absent field ("").
        // This matters for lid/dock fields where UNKNOWN is allowed with Guard OFF.
        if (end >= 0 && response.indexOf(needle, end) >= 0) return null;
        return response.substring(start, end < 0 ? response.length() : end);
    }
}
