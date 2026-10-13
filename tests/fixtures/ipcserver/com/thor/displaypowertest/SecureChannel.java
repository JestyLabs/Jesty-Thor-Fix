package com.thor.displaypowertest;
import android.net.LocalSocket;
/** Host-only stand-in. Peer UID/inode validation is deliberately NOT simulated here. */
public final class SecureChannel {
    private SecureChannel() {}
    public static boolean isTrustedApp(LocalSocket socket) {
        if (socket.failTrust) throw new SecurityException("synthetic credentials denied");
        return socket.trusted;
    }
}
