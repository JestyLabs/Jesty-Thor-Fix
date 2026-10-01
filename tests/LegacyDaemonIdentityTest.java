import com.thor.displaypowertest.LegacyDaemonIdentity;

public final class LegacyDaemonIdentityTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        String realShape = "ok=1;boot_phase=READY;display_actions_held=0;fix=1;mode=0;"
                + "wake_id=6;repair_result=OFF_OK;action=BOOT_READY";
        check(LegacyDaemonIdentity.expectedResponse(realShape), "legacy Q fingerprint");
        check(!LegacyDaemonIdentity.expectedResponse("ok=1;protocol=2;version=1.5.1"),
                "new protocol is not legacy");
        check(!LegacyDaemonIdentity.expectedResponse("ok=1;boot_phase=READY;fix=1;mode=0"),
                "incomplete or spoofed occupant rejected");
        check(!LegacyDaemonIdentity.expectedResponse(null), "missing response rejected");
        System.out.println("LegacyDaemonIdentityTest passed");
    }
}
