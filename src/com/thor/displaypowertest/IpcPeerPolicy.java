package com.thor.displaypowertest;

/** Pure UID policy; peer credentials themselves come from the kernel. */
public final class IpcPeerPolicy {
    private IpcPeerPolicy() {}

    public static boolean trustedDaemon(int peerUid) { return peerUid == 0; }

    public static boolean trustedApp(int appUid, int peerUid) {
        return appUid >= 10000 && peerUid == appUid;
    }

    public static boolean trustedDirectory(int appUid, int filesUid) {
        return appUid >= 10000 && filesUid == appUid;
    }

    public static boolean trustedSocketInode(int appUid, int socketUid,
            int mode, boolean isSocket) {
        return appUid >= 10000 && socketUid == appUid && isSocket
                && (mode & 0777) == 0600;
    }
}
