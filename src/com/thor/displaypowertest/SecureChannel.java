package com.thor.displaypowertest;

import android.net.Credentials;
import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.net.LocalSocketAddress;
import android.system.ErrnoException;
import android.system.Os;
import android.system.OsConstants;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;

/** Private Unix-domain transport. The protocol response is not authentication. */
public final class SecureChannel {
    private static final String DATA_DIR = "/data/user/0/com.thor.displaypowertest";
    private static final String FILES_DIR = DATA_DIR + "/files";
    private static final String SOCKET_PATH = FILES_DIR + "/jesty-thor-control-v2.sock";
    private static final String LOCK_PATH = FILES_DIR + "/jesty-thor-control-v2.lock";
    public static final int PROTOCOL = 2;
    private static LocalSocket listener;
    private static FileOutputStream instanceLockStream;
    private static FileLock instanceLock;

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
        FileDescriptor lockFd = null;
        FileOutputStream lockStream = null;
        FileLock lock = null;
        LocalSocket bound = null;
        try {
            // LocalSocket.bind can replace an occupied filesystem pathname on
            // this Thor. A process-wide file lock must precede any socket work.
            lockFd = Os.open(LOCK_PATH, OsConstants.O_CREAT | OsConstants.O_RDWR
                    | OsConstants.O_CLOEXEC | OsConstants.O_NOFOLLOW, 0600);
            android.system.StructStat lockStat = Os.fstat(lockFd);
            if (!OsConstants.S_ISREG(lockStat.st_mode)
                    || (lockStat.st_uid != 0 && lockStat.st_uid != appUid)) {
                throw new IOException("Untrusted instance lock file");
            }
            Os.fchown(lockFd, appUid, filesDir.st_gid);
            Os.fchmod(lockFd, 0600);
            lockStream = new FileOutputStream(lockFd);
            lockFd = null; // FileOutputStream now owns this descriptor.
            lock = lockStream.getChannel().tryLock();
            if (lock == null) throw new IOException("Daemon instance already locked");
            stampBootId(lockStream);

            removeStaleSocket(appUid);
            bound = new LocalSocket();
            bound.bind(address());
            Os.chown(SOCKET_PATH, appUid, filesDir.st_gid);
            Os.chmod(SOCKET_PATH, 0600);
            android.system.StructStat socketStat = Os.lstat(SOCKET_PATH);
            if (!IpcPeerPolicy.trustedSocketInode(appUid, socketStat.st_uid,
                    socketStat.st_mode, OsConstants.S_ISSOCK(socketStat.st_mode))) {
                throw new IOException("Secure socket owner or mode mismatch");
            }
            LocalServerSocket server = new LocalServerSocket(bound.getFileDescriptor());
            listener = bound; // owns the descriptor wrapped by LocalServerSocket
            instanceLockStream = lockStream;
            instanceLock = lock;
            return server;
        } catch (Throwable error) {
            if (bound != null) try { bound.close(); } catch (Throwable ignored) {}
            if (lock != null) try { lock.release(); } catch (Throwable ignored) {}
            if (lockStream != null) try { lockStream.close(); } catch (Throwable ignored) {}
            if (lockFd != null) try { Os.close(lockFd); } catch (Throwable ignored) {}
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

    /** Kernel boot ID; empty when unreadable in the caller's SELinux context. */
    public static String currentBootId() {
        return firstLine("/proc/sys/kernel/random/boot_id");
    }

    /** Boot ID written by the daemon that last owned the instance lock. */
    public static String stampedBootId() {
        return firstLine(LOCK_PATH);
    }

    /**
     * Lets AutoService recognise a socket inode left by an earlier kernel boot
     * without waiting for it. Best effort: an absent stamp keeps the bounded
     * conservative grace.
     */
    private static void stampBootId(FileOutputStream lockStream) {
        String bootId = currentBootId();
        if (!DaemonLaunchModel.validBootId(bootId)) return;
        try {
            FileChannel channel = lockStream.getChannel();
            channel.truncate(0L);
            channel.write(ByteBuffer.wrap((bootId + "\n")
                    .getBytes(StandardCharsets.US_ASCII)), 0L);
            channel.force(false);
        } catch (Throwable error) {
            android.util.Log.w("ThorDisplayDaemon", "could not stamp boot ID", error);
        }
    }

    private static String firstLine(String path) {
        try (BufferedReader reader = new BufferedReader(new FileReader(path))) {
            String value = reader.readLine();
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

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
        boolean reachable = false;
        try (LocalSocket probe = new LocalSocket()) {
            try {
                probe.connect(address());
                reachable = true;
            } catch (IOException ignored) {
                // Only an unreachable inode is eligible for stale cleanup.
            }
        }
        if (reachable) throw new IOException("Existing socket listener must not be replaced");
        if (!new File(SOCKET_PATH).delete()) {
            throw new IOException("Cannot remove stale secure socket");
        }
    }
}
