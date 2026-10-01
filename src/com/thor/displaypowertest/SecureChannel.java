package com.thor.displaypowertest;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.File;
import java.io.IOException;

/** Private Unix-domain transport. The protocol response is not authentication. */
public final class SecureChannel {
    private static final String DATA_DIR = "/data/user/0/com.thor.displaypowertest";
    private static final String FILES_DIR = DATA_DIR + "/files";
    private static final String SOCKET_PATH = FILES_DIR + "/jesty-thor-control-v2.sock";
    public static final int PROTOCOL = 2;
    private static LocalSocket listener;

    private SecureChannel() {}

    public static LocalSocket connect(int timeoutMs) throws IOException {
        LocalSocket socket = new LocalSocket();
        try {
            // Android's LocalSocket timeout overload throws
            // UnsupportedOperationException on the Thor. The filesystem
            // connect is local; bound the subsequent protocol read instead.
            socket.connect(address());
            socket.setSoTimeout(timeoutMs);
            Credentials peer = socket.getPeerCredentials();
            if (peer == null || !IpcPeerPolicy.trustedDaemon(peer.getUid())) {
                throw new IOException("Untrusted daemon UID");
            }
            return socket;
        } catch (Throwable error) {
            try { socket.close(); } catch (Throwable ignored) {}
            if (error instanceof IOException) throw (IOException) error;
            throw new IOException("Cannot connect to trusted daemon", error);
        }
    }

    /** Called only by the privileged daemon, after the app has created files/. */
    public static synchronized LocalServerSocket listen() throws Exception {
        if (listener != null) throw new IOException("Secure listener already open");
        android.system.StructStat appDir = Os.stat(DATA_DIR);
        android.system.StructStat filesDir = Os.stat(FILES_DIR);
        int appUid = appDir.st_uid;
        if (!IpcPeerPolicy.trustedDirectory(appUid, filesDir.st_uid)
                || !OsConstants.S_ISDIR(appDir.st_mode)
                || !OsConstants.S_ISDIR(filesDir.st_mode)) {
            throw new IOException("Untrusted app data directory");
        }
        removeStaleSocket(appUid);
        LocalSocket bound = new LocalSocket();
        try {
            bound.bind(address());
            Os.chown(SOCKET_PATH, appUid, filesDir.st_gid);
            Os.chmod(SOCKET_PATH, 0600);
            LocalServerSocket server = new LocalServerSocket(bound.getFileDescriptor());
            listener = bound; // owns the descriptor wrapped by LocalServerSocket
            return server;
        } catch (Throwable error) {
            try { bound.close(); } catch (Throwable ignored) {}
            if (error instanceof Exception) throw (Exception) error;
            throw new IOException("Cannot bind secure socket", error);
        }
    }

    public static boolean isTrustedApp(LocalSocket socket) {
        try {
            int expected = Os.stat(DATA_DIR).st_uid;
            Credentials peer = socket.getPeerCredentials();
            return peer != null && IpcPeerPolicy.trustedApp(expected, peer.getUid());
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static String path() { return SOCKET_PATH; }

    private static LocalSocketAddress address() {
        return new LocalSocketAddress(SOCKET_PATH,
                LocalSocketAddress.Namespace.FILESYSTEM);
    }

    private static void removeStaleSocket(int appUid) throws Exception {
        android.system.StructStat stat;
        try {
            stat = Os.lstat(SOCKET_PATH);
        } catch (ErrnoException error) {
            if (error.errno == OsConstants.ENOENT) return;
            throw error;
        }
        if (!OsConstants.S_ISSOCK(stat.st_mode)
                || (stat.st_uid != 0 && stat.st_uid != appUid)) {
            throw new IOException("Unexpected object at secure socket path");
        }
        try (LocalSocket probe = new LocalSocket()) {
            probe.connect(address());
            Credentials peer = probe.getPeerCredentials();
            if (peer != null && peer.getUid() == 0) {
                throw new IOException("Trusted daemon already listening");
            }
        } catch (IOException error) {
            if ("Trusted daemon already listening".equals(error.getMessage())) throw error;
            // An abandoned socket inode or untrusted pre-bind can be replaced
            // only after its exact type and owner have been checked above.
        }
        if (!new File(SOCKET_PATH).delete()) {
            throw new IOException("Cannot remove stale secure socket");
        }
    }
}
