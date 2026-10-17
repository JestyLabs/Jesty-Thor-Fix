package com.thor.displaypowertest;

/** A narrow identity and physical-state gate for replacing our own older daemon. */
public final class PreviousSecureDaemonIdentity {
    private PreviousSecureDaemonIdentity() {}

    public static int safePid(String health, String snapshot) {
        if (health == null || snapshot == null
                || !health.startsWith("ok=1;")
                || !snapshot.startsWith("ok=1;")
                || health.contains(";ok=")
                || snapshot.contains(";ok=")) return -1;
        String version = field(health, "version");
        if (!field(health, "protocol").equals("2")
                || !(version.equals("1.5.3") || version.equals("1.5.5")
                        || version.equals("1.5.6") || version.equals("1.5.7")
                        || version.equals("1.5.9") || version.equals("1.5.10")
                        || version.equals("1.5.11") || version.equals("1.5.12")
                        || version.equals("1.5.13") || version.equals("1.5.14")
                        || version.equals("1.5.15") || version.equals("1.5.16")
                        || version.equals("1.5.17") || version.equals("1.5.18")
                        || version.equals("1.5.19") || version.equals("1.5.20")
                        || version.equals("1.5.20-earlycpu-p33")
                        || version.equals("1.6.0")
                        || version.equals("1.6.0-watcher-exp1")
                        || version.equals("1.6.0-watcher-exp2")
                        || version.equals("1.7.0")
                        || version.equals("1.7.1-rc1")
                        || version.equals("1.7.1-rc2")
                        || version.equals("1.7.1-rc3")
                        || version.equals("1.7.1-rc4")
                        || version.equals("1.7.1") || version.equals("1.7.2"))
                || !field(health, "watcher").equals("RUNNING")) return -1;
        if (!field(snapshot, "mode").equals("0")
                || !field(snapshot, "top_crtc").equals("1")
                || !field(snapshot, "bottom_crtc").equals("1")) return -1;
        String pid = field(health, "pid");
        if (!pid.matches("[1-9][0-9]{0,8}")) return -1;
        try {
            int parsed = Integer.parseInt(pid);
            return parsed > 100 ? parsed : -1;
        }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static String field(String response, String name) {
        String needle = ";" + name + "=";
        int start = response.indexOf(needle);
        if (start < 0) return "";
        start += needle.length();
        int end = response.indexOf(';', start);
        // A second occurrence makes this identity/state field ambiguous.
        // Fail closed rather than trusting whichever occurrence came first.
        if (end >= 0 && response.indexOf(needle, end) >= 0) return "";
        return response.substring(start, end < 0 ? response.length() : end);
    }
}
