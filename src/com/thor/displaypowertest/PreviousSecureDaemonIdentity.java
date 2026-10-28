package com.thor.displaypowertest;

/** A narrow identity and physical-state gate for replacing our own older daemon. */
public final class PreviousSecureDaemonIdentity {
    private PreviousSecureDaemonIdentity() {}

    public static int safePid(String health, String snapshot) {
        return checkedPid(health, snapshot, false);
    }

    /** Only the retained-process caller may consider a confirmed TOP state. */
    static int safePidForRetainedProcess(String health, String snapshot, boolean displayFix) {
        return checkedPid(health, snapshot, displayFix, null);
    }

    /** Same decision; {@code why} receives the first failed condition (observation only). */
    static int safePidForRetainedProcess(String health, String snapshot, boolean displayFix,
            StringBuilder why) {
        return checkedPid(health, snapshot, displayFix, why);
    }

    private static int checkedPid(String health, String snapshot, boolean allowTop) {
        return checkedPid(health, snapshot, allowTop, null);
    }

    private static int checkedPid(String health, String snapshot, boolean allowTop, StringBuilder why) {
        if (health == null || snapshot == null
                || !health.startsWith("ok=1;")
                || !snapshot.startsWith("ok=1;")
                || health.contains(";ok=")
                || snapshot.contains(";ok=")) return reject(why, "REPLY_SHAPE", "");
        String version = field(health, "version");
        if (!field(health, "protocol").equals("2"))
            return reject(why, "PROTOCOL", field(health, "protocol"));
        if (!(version.equals("1.5.3") || version.equals("1.5.5")
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
                        || version.equals("1.7.1") || version.equals("1.7.2")
                        || version.equals("1.7.3") || version.equals("1.7.4") || version.equals("1.7.5")))
            return reject(why, "PREVIOUS_VERSION", version);
        if (!field(health, "watcher").equals("RUNNING"))
            return reject(why, "WATCHER", field(health, "watcher"));
        boolean both = field(snapshot, "mode").equals("0")
                && field(snapshot, "top_crtc").equals("1")
                && field(snapshot, "bottom_crtc").equals("1");
        boolean top = allowTop && field(snapshot, "mode").equals("1")
                && field(snapshot, "top_crtc").equals("1")
                && field(snapshot, "bottom_crtc").equals("0");
        if (!both && !top) return reject(why, "DISPLAY_STATE", "m" + field(snapshot, "mode")
                + "t" + field(snapshot, "top_crtc") + "b" + field(snapshot, "bottom_crtc")
                + (allowTop ? "" : "-both-only"));
        String pid = field(health, "pid");
        if (!pid.matches("[1-9][0-9]{0,8}")) return reject(why, "PID", "");
        try {
            int parsed = Integer.parseInt(pid);
            return parsed > 100 ? parsed : reject(why, "PID", "");
        }
        catch (NumberFormatException ignored) { return reject(why, "PID", ""); }
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
