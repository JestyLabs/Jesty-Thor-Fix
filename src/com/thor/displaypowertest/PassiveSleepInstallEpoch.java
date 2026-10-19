package com.thor.displaypowertest;

/**
 * Fail-closed install identity for one-shot opt-in sleep trials.
 * Distinguishes even a reinstall of the same version when PackageManager
 * supplies a distinct lastUpdateTime. No network, device ID, or polling.
 * A version marker alone cannot reveal an intermediate recovery installation.
 */
final class PassiveSleepInstallEpoch {
    private PassiveSleepInstallEpoch() { }

    static String token(String name, int code, long lastUpdateTime) {
        if (name == null || !name.matches("[0-9]+\\.[0-9]+\\.[0-9]+(?:-rc[1-9][0-9]*)?")
                || code <= 0 || lastUpdateTime <= 0L) return null;
        return "I1|" + code + "|" + lastUpdateTime + "|" + name;
    }

    static boolean matches(String saved, String current) {
        return current != null && saved != null && current.equals(saved);
    }
}
