import com.thor.displaypowertest.PreviousSecureDaemonIdentity;

public final class PreviousSecureDaemonIdentityTest {
    private static final String HEALTH = "ok=1;protocol=2;version=1.5.3;pid=11398;"
            + "boot_phase=BOOT_SAFETY_TIMEOUT;fix=1;watcher=RUNNING";
    private static final String BOTH = "ok=1;mode=0;top_crtc=1;bottom_crtc=1";

    public static void main(String[] args) {
        check(PreviousSecureDaemonIdentity.safePid(HEALTH, BOTH) == 11398,
                "authenticated prior version in BOTH can be replaced");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.5"), BOTH) == 11398,
                "the second known prior version can be replaced");
        check(PreviousSecureDaemonIdentity.safePid(HEALTH.replace("1.5.3", "1.5.6"), BOTH) == 11398,
                "the current installed version can be replaced during upgrade");
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
