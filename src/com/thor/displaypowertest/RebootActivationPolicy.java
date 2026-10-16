package com.thor.displaypowertest;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Observed post-install boot transition and truthful activation states. */
public final class RebootActivationPolicy {
    public enum State { READY, PENDING, RESTART_REQUIRED, UNAVAILABLE }

    private RebootActivationPolicy() {}

    public static boolean authorized(String expectedPackage, String savedPackage,
            String boot, String savedBoot) {
        return expectedPackage != null && !expectedPackage.isEmpty()
                && expectedPackage.equals(savedPackage)
                && validBoot(boot) && validBoot(savedBoot)
                && !boot.equalsIgnoreCase(savedBoot);
    }

    public static boolean validBoot(String boot) {
        return boot != null
                && boot.matches("[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
                && !"00000000-0000-0000-0000-000000000000".equals(boot);
    }

    /** Checksum detects accidental corruption; private app storage is the trust boundary. */
    public static String baselineRecord(String stamp, String boot) {
        if (stamp == null || stamp.isEmpty() || stamp.indexOf('|') >= 0 || !validBoot(boot)) return "";
        String payload = "v3|" + stamp + "|" + boot;
        String checksum = checksum(payload);
        return checksum.isEmpty() ? "" : payload + "|" + checksum;
    }

    public static String baselineBoot(String record, String expectedStamp) {
        if (record == null || record.length() > 512 || expectedStamp == null || expectedStamp.isEmpty()) return null;
        String[] fields = record.split("\\|", -1);
        if (fields.length != 4 || !"v3".equals(fields[0]) || !expectedStamp.equals(fields[1])
                || !validBoot(fields[2])) return null;
        return record.equals(baselineRecord(expectedStamp, fields[2])) ? fields[2] : null;
    }

    private static String checksum(String payload) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(payload.getBytes(StandardCharsets.UTF_8));
            char[] hex = "0123456789abcdef".toCharArray();
            StringBuilder result = new StringBuilder(64);
            for (byte b : bytes) { result.append(hex[(b & 255) >>> 4]); result.append(hex[b & 15]); }
            return result.toString();
        } catch (NoSuchAlgorithmException unavailable) { return ""; }
    }

    public static State telemetry(Map<String, String> values, String expectedVersion) {
        if (values == null) return State.UNAVAILABLE;
        if (expectedVersion == null || expectedVersion.isEmpty()) return State.UNAVAILABLE;
        if (!expectedVersion.equals(values.get("runtime_version"))) return State.RESTART_REQUIRED;
        if (!"RUNNING".equals(values.get("watcher_health"))) return State.UNAVAILABLE;
        if ("READY".equals(values.get("boot_phase"))
                && "0".equals(values.get("display_actions_held"))) return State.READY;
        return State.PENDING;
    }

    public static String restartMessage() {
        return "Open Jesty Thor Fix once after installation to prepare activation, then restart the Thor normally. "
                + "Reopen the app to verify the background service. If activation remains blocked, "
                + "use owner-approved external recovery; do not uninstall or clear app data.";
    }
}
