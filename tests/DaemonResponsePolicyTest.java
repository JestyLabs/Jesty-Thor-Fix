import com.thor.displaypowertest.DaemonResponsePolicy;

public final class DaemonResponsePolicyTest {
    public static void main(String[] args) {
        for (String ok : new String[] {
                "ok=1", "ok=1;protocol=2;watcher=RUNNING",
                "ok=1;mode=0;top_crtc=1;bottom_crtc=1", "ok=1;fix=1;display=confirmed"
        }) {
            check(DaemonResponsePolicy.isPositiveAck(ok), "valid daemon acknowledgement: " + ok);
        }
        for (String invalid : new String[] {
                "", "ok=0", "ok=10", "ok=11", "ok=1x", "ok=1 fail",
                "OK=1", "ok=1\nother=bad", " ok=1", "ok=01"
        }) {
            check(!DaemonResponsePolicy.isPositiveAck(invalid),
                    "reject invalid/fake acknowledgement: " + invalid);
        }
        check(!DaemonResponsePolicy.isPositiveAck(null), "missing reply is never success");
        System.out.println("DaemonResponsePolicyTest passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
