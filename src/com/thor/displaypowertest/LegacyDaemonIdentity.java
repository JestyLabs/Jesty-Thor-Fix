package com.thor.displaypowertest;

/** Read-only fingerprint used only to gate in-place protocol migration. */
public final class LegacyDaemonIdentity {
    private LegacyDaemonIdentity() {}

    public static boolean expectedResponse(String response) {
        return response != null && response.startsWith("ok=1;boot_phase=")
                && response.contains(";display_actions_held=")
                && response.contains(";fix=")
                && response.contains(";mode=")
                && response.contains(";wake_id=")
                && response.contains(";repair_result=");
    }
}
