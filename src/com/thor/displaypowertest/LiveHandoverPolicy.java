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
        if (peerUid != 0 || peerPid <= 100) return -1;
        int pid = checkedPid(health, snapshot, displayFix, cpuFix, lidGuard, true);
        return pid == peerPid ? pid : -1;
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
        int pid = PreviousSecureDaemonIdentity.safePid(health, snapshot);
        if (pid < 1) return -1;
        // Exact published predecessors only; never a broad version comparison.
        String version = field(health, "version");
        if (!"1.7.3".equals(version) && !"1.7.2".equals(version)
                && !"1.7.1".equals(version)) return -1;
        // I and Q must describe the same process generation, not a socket takeover.
        String runtimeVersion = field(snapshot, "runtime_version");
        boolean publishedCode76 = retainedProcess && "1.7.1".equals(version)
                && snapshot.indexOf(";runtime_version=") < 0;
        if (!publishedCode76 && !version.equals(runtimeVersion)) return -1;
        if (!"READY".equals(field(health, "boot_phase"))) return -1;
        if (!expected(field(health, "fix"), displayFix)
                || !expected(field(snapshot, "fix"), displayFix)
                || !"0".equals(field(snapshot, "display_actions_held"))
                || !"CONFIRMED".equals(field(snapshot, "display_effective"))
                || !expected(field(snapshot, "cpu_fix_desired"), cpuFix)
                || !"CONFIRMED".equals(field(snapshot, "cpu_fix_phase"))
                || !expected(field(snapshot, "system_load_fix"), cpuFix)
                || !expected(field(snapshot, "lid_guard"), lidGuard)) return -1;
        // Do not retire a daemon while the lid is known closed or a dock is connected.
        // With Guard OFF the stock firmware reports external_display=unknown.
        // Missing values retain their previous policy, but duplicate values do not.
        String lid = field(snapshot, "lid");
        String externalDisplay = field(snapshot, "external_display");
        if (lid == null || externalDisplay == null
                || "closed".equals(lid)
                || "1".equals(externalDisplay)
                || (lidGuard && !"0".equals(externalDisplay))) return -1;
        return pid;
    }

    private static boolean expected(String value, boolean enabled) {
        return (enabled ? "1" : "0").equals(value);
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
