package com.thor.displaypowertest;

/** Actual native connect admission helpers with inert UID/stat/peer metadata. */
public final class RetirementConnectHostTest {
    private static final int APP = 10001;
    private static final int PRIVATE_SOCKET = 0140000 | 0600;
    private static final String FILES = "/data/user/0/com.thor.displaypowertest/files/";
    private static final String LEGACY = FILES + "jesty-thor-control-v2.sock";
    private static final String NONCE = FILES + "retire-0123456789abcdef0123456789abcdef";

    private static native boolean allowed(String path, int realUid, int effectiveUid,
            int appUid, int expectedUid, int peerUid, int peerPid,
            int fileUid, int mode, boolean sameInode);

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        RetirementNative.load();
        check(allowed(LEGACY, APP, APP, APP, 0, 0, 234, APP, PRIVATE_SOCKET, true),
                "Actual app must connect to fixed legacy root peer");
        check(allowed(NONCE, 0, 0, APP, APP, APP, 345, APP, PRIVATE_SOCKET, true),
                "Root helper must connect to app nonce endpoint");
        check(!allowed(LEGACY + ".other", APP, APP, APP, 0, 0, 234, APP, PRIVATE_SOCKET, true),
                "App path must be exact legacy socket");
        check(!allowed(NONCE, APP, APP, APP, 0, 0, 234, APP, PRIVATE_SOCKET, true),
                "App cannot use helper-control role");
        check(!allowed(LEGACY, 0, 0, APP, 0, 0, 234, APP, PRIVATE_SOCKET, true),
                "Root cannot bypass legacy application-UID admission");
        check(!allowed(LEGACY, APP, 0, APP, 0, 0, 234, APP, PRIVATE_SOCKET, true),
                "Mixed effective UID rejected");
        check(!allowed(LEGACY, APP + 1, APP + 1, APP, 0, 0, 234, APP, PRIVATE_SOCKET, true),
                "Different app UID rejected");
        check(!allowed(NONCE, 0, 0, APP, APP + 1, APP, 345, APP, PRIVATE_SOCKET, true),
                "Supplied UID cannot replace actual application UID");
        check(!allowed(NONCE.replace("/files/", "/other/"), 0, 0, APP, APP,
                APP, 345, APP, PRIVATE_SOCKET, true), "Nonce directory fixed");
        check(!allowed(NONCE.substring(0, NONCE.length() - 1) + "g", 0, 0, APP, APP,
                APP, 345, APP, PRIVATE_SOCKET, true), "Nonce must be 32 lowercase hex");
        check(!allowed(LEGACY, APP, APP, APP, 0, APP, 234, APP, PRIVATE_SOCKET, true),
                "Legacy peer must be root");
        check(!allowed(LEGACY, APP, APP, APP, 0, 0, 100, APP, PRIVATE_SOCKET, true),
                "Legacy peer PID must exceed 100");
        check(!allowed(NONCE, 0, 0, APP, APP, 0, 345, APP, PRIVATE_SOCKET, true),
                "Helper peer must be actual app UID");
        check(!allowed(LEGACY, APP, APP, APP, 0, 0, 234, 0, PRIVATE_SOCKET, true),
                "Legacy endpoint remains application-owned");
        check(!allowed(LEGACY, APP, APP, APP, 0, 0, 234, APP, 0140000 | 0666, true),
                "Legacy endpoint mode must be 0600");
        check(!allowed(LEGACY, APP, APP, APP, 0, 0, 234, APP, 0100000 | 0600, true),
                "Endpoint must be a socket");
        check(!allowed(LEGACY, APP, APP, APP, 0, 0, 234, APP, PRIVATE_SOCKET, false),
                "Changed endpoint inode rejected");
        System.out.println("PASS actual native connect two-role/path/metadata admission (inert metadata, no sockets)");
    }
}
