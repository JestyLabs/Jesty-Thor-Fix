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
        check(IpcPeerPolicy.trustedSocketInode(10166, 10166, 0140600, true),
                "private socket owner and permissions accepted");
        check(!IpcPeerPolicy.trustedSocketInode(10166, 10167, 0140600, true),
                "foreign socket owner rejected");
        check(!IpcPeerPolicy.trustedSocketInode(10166, 10166, 0140666, true),
                "public socket permissions rejected");
        check(!IpcPeerPolicy.trustedSocketInode(10166, 10166, 0100600, false),
                "regular file rejected");

        // Adversarial boundary matrix: kernel credentials, not caller-provided
        // claims, must match the app UID. Normal Android app UIDs begin at 10000.
        for (int owner : new int[] {-1, 0, 1000, 9999, 10000, 10166, Integer.MAX_VALUE}) {
            boolean eligible = owner >= 10000;
            check(IpcPeerPolicy.trustedApp(owner, owner) == eligible,
                    "matching client UID boundary: " + owner);
            check(IpcPeerPolicy.trustedDirectory(owner, owner) == eligible,
                    "matching private-directory UID boundary: " + owner);
            check(IpcPeerPolicy.trustedSocketInode(owner, owner, 0140600, true) == eligible,
                    "matching socket-owner UID boundary: " + owner);
            check(!IpcPeerPolicy.trustedApp(owner, owner == Integer.MAX_VALUE ? 0 : owner + 1),
                    "different client UID rejected: " + owner);
        }

        // Every permission combination is rejected except owner-read/write
        // (0600), even if the transport is a socket with the expected UID.
        for (int permissions = 0; permissions <= 0777; permissions++) {
            boolean accepted = IpcPeerPolicy.trustedSocketInode(10166, 10166,
                    0140000 | permissions, true);
            check(accepted == (permissions == 0600),
                    "socket permissions must be exactly 0600: " + Integer.toOctalString(permissions));
            check(!IpcPeerPolicy.trustedSocketInode(10166, 10166,
                    0140000 | permissions, false),
                    "non-socket inode must never be trusted");
        }

        System.out.println("IpcPeerPolicyTest passed");
    }
}
