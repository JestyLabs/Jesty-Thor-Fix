import com.thor.displaypowertest.PreviousSecureDaemonIdentity;

public final class PreviousSecureDaemonIdentityTest {
    private static final String HEALTH = "ok=1;protocol=2;version=1.5.3;pid=11398;"
            + "boot_phase=BOOT_SAFETY_TIMEOUT;fix=1;watcher=RUNNING";
    private static final String BOTH = "ok=1;mode=0;top_crtc=1;bottom_crtc=1";

    public static void main(String[] args) {
        for (String version : new String[]{"1.7.1-rc2", "1.7.1-rc3", "1.7.1-rc4"}) {
            String health = HEALTH.replace("1.5.3", version);
            check(PreviousSecureDaemonIdentity.safePid(health, BOTH) == 11398,
                    "forward candidate accepts authenticated prior daemon " + version);
            check(PreviousSecureDaemonIdentity.safePid(health, BOTH.replace("mode=0", "mode=1")) < 0,
                    "prior candidate still requires BOTH " + version);
            check(PreviousSecureDaemonIdentity.safePid(health,
                    BOTH.replace("bottom_crtc=1", "bottom_crtc=0")) < 0,
                    "prior candidate still requires bottom active " + version);
            check(PreviousSecureDaemonIdentity.safePid(
                    health.replace("watcher=RUNNING", "watcher=FAILED"), BOTH) < 0,
                    "prior candidate still requires healthy watcher " + version);
        }
        check(PreviousSecureDaemonIdentity.safePid(HEALTH, BOTH) == 11398,
                "authenticated prior version in BOTH can be replaced");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.5"), BOTH) == 11398,
                "the second known prior version can be replaced");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.6"), BOTH) == 11398,
                "the current installed version can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.7"), BOTH) == 11398,
                "the 1.5.8 APK's older daemon identity can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.9"), BOTH) == 11398,
                "the installed 1.5.9 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.10"), BOTH) == 11398,
                "the installed 1.5.10 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.11"), BOTH) == 11398,
                "the installed 1.5.11 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.12"), BOTH) == 11398,
                "the installed 1.5.12 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.13"), BOTH) == 11398,
                "the installed 1.5.13 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.14"), BOTH) == 11398,
                "the installed 1.5.14 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.15"), BOTH) == 11398,
                "the installed 1.5.15 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.16"), BOTH) == 11398,
                "the installed 1.5.16 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.17"), BOTH) == 11398,
                "the installed 1.5.17 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.18"), BOTH) == 11398,
                "the installed 1.5.18 daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.19"), BOTH) == 11398,
                "the 1.5.19 candidate daemon can be replaced during upgrade");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.20"), BOTH) == 11398,
                "stable 1.5.20 can be replaced by the isolated prototype in BOTH");
        check(PreviousSecureDaemonIdentity.safePid(
                HEALTH.replace("1.5.3", "1.5.20-earlycpu-p33"), BOTH) == 11398,
                "the exact hardware-tested prototype may be replaced by v1.6.0");
        check(PreviousSecureDaemonIdentity.safePid(
                HEALTH.replace("1.5.3", "1.6.0"), BOTH) == 11398,
                "stable v1.6.0 may be replaced by the production candidate in BOTH");
        String exp1 = HEALTH.replace("1.5.3", "1.6.0-watcher-exp1");
        check(PreviousSecureDaemonIdentity.safePid(exp1, BOTH) == 11398,
                "only the exact earlier watcher candidate can be replaced in BOTH");
        check(PreviousSecureDaemonIdentity.safePid(exp1, BOTH.replace("mode=0", "mode=1")) < 0,
                "research handover remains BOTH-only");
        String exp2 = HEALTH.replace("1.5.3", "1.6.0-watcher-exp2");
        check(PreviousSecureDaemonIdentity.safePid(exp2, BOTH) == 11398,
                "the hardware-tested exp2 may be replaced by the production candidate");
        check(PreviousSecureDaemonIdentity.safePid(exp2, BOTH.replace("mode=0", "mode=1")) < 0,
                "exp2 handover remains BOTH-only");
        check(PreviousSecureDaemonIdentity.safePid(exp2, BOTH.replace("bottom_crtc=1", "bottom_crtc=0")) < 0,
                "exp2 handover requires the bottom CRTC active");
        check(PreviousSecureDaemonIdentity.safePid(exp2.replace("watcher=RUNNING", "watcher=FAILED"), BOTH) < 0,
                "exp2 handover requires a healthy watcher");
        check(PreviousSecureDaemonIdentity.safePid(
                HEALTH.replace("1.5.3", "1.6.0-watcher-exp3"), BOTH) < 0,
                "unrecognized experiment must not be killed");
        String stable170 = HEALTH.replace("1.5.3", "1.7.0");
        check(PreviousSecureDaemonIdentity.safePid(stable170, BOTH) == 11398,
                "stable v1.7.0 may be replaced by the release candidate in BOTH");
        check(PreviousSecureDaemonIdentity.safePid(stable170,
                BOTH.replace("mode=0", "mode=1")) < 0,
                "v1.7.0 handover remains BOTH-only");
        check(PreviousSecureDaemonIdentity.safePid(stable170,
                BOTH.replace("bottom_crtc=1", "bottom_crtc=0")) < 0,
                "v1.7.0 handover requires the bottom CRTC active");
        check(PreviousSecureDaemonIdentity.safePid(stable170,
                BOTH.replace("top_crtc=1", "top_crtc=0")) < 0,
                "v1.7.0 handover requires the top CRTC active");
        check(PreviousSecureDaemonIdentity.safePid(
                stable170.replace("watcher=RUNNING", "watcher=FAILED"), BOTH) < 0,
                "v1.7.0 handover requires a healthy watcher");
        check(PreviousSecureDaemonIdentity.safePid(
                stable170.replace("protocol=2", "protocol=1"), BOTH) < 0,
                "v1.7.0 handover requires the authenticated protocol");
        check(PreviousSecureDaemonIdentity.safePid(
                HEALTH.replace("1.5.3", "1.7.0-experimental"), BOTH) < 0,
                "v1.7.0-like unknown identities must not be replaced");
        String priorRc1 = HEALTH.replace("1.5.3", "1.7.1-rc1");
        check(PreviousSecureDaemonIdentity.safePid(priorRc1, BOTH) == 11398,
                "the released rc1 daemon may be replaced by rc2 only in BOTH");
        check(PreviousSecureDaemonIdentity.safePid(priorRc1,
                BOTH.replace("mode=0", "mode=1")) < 0,
                "rc1 handover remains BOTH-only");
        check(PreviousSecureDaemonIdentity.safePid(priorRc1,
                BOTH.replace("bottom_crtc=1", "bottom_crtc=0")) < 0,
                "rc1 handover requires the lower CRTC active");
        check(PreviousSecureDaemonIdentity.safePid(priorRc1,
                BOTH.replace("top_crtc=1", "top_crtc=0")) < 0,
                "rc1 handover requires the upper CRTC active");
        check(PreviousSecureDaemonIdentity.safePid(priorRc1.replace(
                "watcher=RUNNING", "watcher=FAILED"), BOTH) < 0,
                "rc1 handover requires a healthy watcher");
        check(PreviousSecureDaemonIdentity.safePid(
                HEALTH.replace("1.5.3", "1.7.1-rc1-experimental"), BOTH) < 0,
                "similar but unrecognized rc1 identities remain rejected");

        String gate_2 = HEALTH.replace("1.5.3", "1.7.1-rc2");
        check(PreviousSecureDaemonIdentity.safePid(gate_2, BOTH) == 11398,
                "exact prior 1.7.1-rc2 handoff allowed");
        for (String unsafe : new String[] {
                BOTH.replace("mode=0", "mode=1"),
                BOTH.replace("mode=0", "mode=2"),
                BOTH.replace("top_crtc=1", "top_crtc=0"),
                BOTH.replace("bottom_crtc=1", "bottom_crtc=0") }) {
            check(PreviousSecureDaemonIdentity.safePid(gate_2, unsafe) < 0,
                    "prior 1.7.1-rc2 requires BOTH and active CRTCs");
        }
        for (String unsafeHealth : new String[] {
                gate_2.replace("ok=1", "ok=0"),
                gate_2.replace("protocol=2", "protocol=1"),
                gate_2.replace("watcher=RUNNING", "watcher=FAILED"),
                gate_2.replace("pid=11398", "pid=1"),
                gate_2.replace("pid=11398", "pid=invalid"),
                gate_2.replace("1.7.1-rc2", "1.7.1-rc2-unknown") }) {
            check(PreviousSecureDaemonIdentity.safePid(unsafeHealth, BOTH) < 0,
                    "prior 1.7.1-rc2 retains identity/health/PID gates");
        }

        check(PreviousSecureDaemonIdentity.safePid(
                HEALTH.replace("1.5.3", "1.7.1"), BOTH) < 0,
                "current identity is not a previous-daemon allowance");

        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.4"), BOTH) < 0,
                "unknown version must not be killed");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("pid=11398", "pid=1;bad=x"), BOTH) < 0,
                "pid 1 must not be accepted");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("pid=11398", "pid=1;kill -9"), BOTH) < 0,
                "malformed pid must not be accepted");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH, BOTH.replace("mode=0", "mode=1")) < 0,
                "top only must not be replaced unattended");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH, BOTH.replace("bottom_crtc=1", "bottom_crtc=0")) < 0,
                "bottom display must be visibly on");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("watcher=RUNNING", "watcher=FAILED"), BOTH) < 0,
                "unhealthy watcher fails closed");
        System.out.println("PreviousSecureDaemonIdentityTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
