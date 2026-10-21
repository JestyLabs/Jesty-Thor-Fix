package com.thor.displaypowertest;

import java.util.HashMap;
import java.util.Map;

/** Failed-start admission only; process identity and exit belong to LiveRetirement. */
final class FailedStartRetirementPolicy {
    private FailedStartRetirementPolicy() {}

    static int safePid(String health, boolean display, int uid, int peerPid) {
        if (uid != 0 || peerPid <= 100 || fields(health) == null) return -1;
        int pid = DaemonLaunchModel.replaceablePid(health, DaemonIdentity.RUNTIME_ID, display);
        return pid == peerPid ? pid : -1;
    }

    static boolean unchanged(String first, String second) {
        Map<String, String> a = fields(first);
        Map<String, String> b = fields(second);
        return a != null && b != null && a.get("boot_phase") != null
                && a.get("boot_phase").equals(b.get("boot_phase"));
    }

    private static Map<String, String> fields(String response) {
        if (response == null) return null;
        Map<String, String> result = new HashMap<>();
        for (String entry : response.split(";", -1)) {
            int at = entry.indexOf('=');
            if (at <= 0 || at == entry.length() - 1
                    || result.put(entry.substring(0, at), entry.substring(at + 1)) != null) {
                return null;
            }
        }
        return result;
    }
}
