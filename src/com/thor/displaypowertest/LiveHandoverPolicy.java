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
        int pid = PreviousSecureDaemonIdentity.safePid(health, snapshot);
        if (pid < 1) return -1;
        // Exact published predecessors only; never a broad version comparison.
        String version = field(health, "version");
        if (!"1.7.2".equals(version) && !"1.7.1".equals(version)) return -1;
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
        if ("closed".equals(field(snapshot, "lid"))
                || "1".equals(field(snapshot, "external_display"))) return -1;
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
        return response.substring(start, end < 0 ? response.length() : end);
    }
}
