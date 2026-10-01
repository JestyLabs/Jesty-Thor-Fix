import com.thor.displaypowertest.IpcPeerPolicy;

public final class IpcPeerPolicyTest {
    private static void check(boolean value, String reason) {
        if (!value) throw new AssertionError(reason);
    }

    public static void main(String[] args) {
        check(IpcPeerPolicy.trustedDaemon(0), "root daemon accepted");
        check(!IpcPeerPolicy.trustedDaemon(1000), "system impersonator rejected");
        check(!IpcPeerPolicy.trustedDaemon(10123), "app impersonator rejected");
        check(IpcPeerPolicy.trustedApp(10166, 10166), "own app accepted");
        check(!IpcPeerPolicy.trustedApp(10166, 10167), "other app rejected");
        check(!IpcPeerPolicy.trustedApp(10166, 0), "root client is not the app");
        check(!IpcPeerPolicy.trustedApp(0, 0), "invalid app owner rejected");
        check(IpcPeerPolicy.trustedDirectory(10166, 10166), "private directory accepted");
        check(!IpcPeerPolicy.trustedDirectory(10166, 10167), "foreign directory rejected");
        System.out.println("IpcPeerPolicyTest passed");
    }
}
