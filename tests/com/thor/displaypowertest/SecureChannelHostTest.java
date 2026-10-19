package com.thor.displaypowertest;

import android.net.LocalSocket;
import android.system.Os;
import android.system.OsConstants;
import android.system.ErrnoException;
import android.system.StructStat;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Exercises real SecureChannel.connect/isTrustedApp/listen entry checks
 * with host-only fake Android services. Does NOT prove kernel UIDs, SELinux,
 * inode race resistance, native file locking, or actual stale-socket removal.
 * Os.open deliberately fails before creating a real descriptor.
 */
public final class SecureChannelHostTest {
    private static int checks;

    private static void require(boolean condition, String name) {
        checks++;
        if (!condition) throw new AssertionError(name);
    }
    private static void reset() {
        LocalSocket.reset();
        Os.reset();
    }
    private static boolean failsConnect(LocalSocket.Plan plan) {
        LocalSocket.enqueue(plan);
        try { SecureChannel.connect(1500); return false; }
        catch (IOException expected) { return true; }
    }

    private static void clientPeerTrust() throws Exception {
        reset();
        LocalSocket.enqueue(new LocalSocket.Plan(0));
        LocalSocket client = SecureChannel.connect(432);
        require(client == LocalSocket.latest && client.timeout == 432
                && client.closes == 0 && client.connects == 1,
                "only root peer returns an open, timeout-configured socket");
        client.close();
        require(client.closes == 1, "caller owns successful socket closure");

        for (Integer uid : new Integer[]{10042, 1000, -1}) {
            reset();
            require(failsConnect(new LocalSocket.Plan(uid))
                    && LocalSocket.latest.closes == 1,
                    "non-root daemon UID must be rejected and socket closed");
        }
        reset();
        require(failsConnect(new LocalSocket.Plan(null))
                && LocalSocket.latest.closes == 1,
                "missing kernel peer credentials must fail closed");

        reset();
        LocalSocket.Plan refused = new LocalSocket.Plan(0);
        refused.connectFailure = true;
        require(failsConnect(refused) && LocalSocket.latest.closes == 1,
                "connect IOException closes socket");

        reset();
        LocalSocket.Plan noTimeout = new LocalSocket.Plan(0);
        noTimeout.timeoutFailure = true;
        require(failsConnect(noTimeout) && LocalSocket.latest.closes == 1,
                "timeout setup exception closes socket");

        reset();
        LocalSocket.Plan noCred = new LocalSocket.Plan(0);
        noCred.credentialFailure = true;
        require(failsConnect(noCred) && LocalSocket.latest.closes == 1,
                "credential read exception closes socket");
    }

    private static void appPeerTrust() {
        reset();
        LocalSocket.enqueue(new LocalSocket.Plan(10042));
        require(SecureChannel.isTrustedApp(new LocalSocket()),
                "matching app UID accepted by real SecureChannel");
        for (Integer uid : new Integer[]{0, 1000, 10043}) {
            reset();
            LocalSocket.enqueue(new LocalSocket.Plan(uid));
            require(!SecureChannel.isTrustedApp(new LocalSocket()),
                    "nonmatching app peer rejected");
        }
        reset();
        LocalSocket.enqueue(new LocalSocket.Plan(null));
        require(!SecureChannel.isTrustedApp(new LocalSocket()),
                "missing app credentials rejected");
        reset();
        Os.appUid = 9999;
        LocalSocket.enqueue(new LocalSocket.Plan(9999));
        require(!SecureChannel.isTrustedApp(new LocalSocket()),
                "Android application UIDs below threshold rejected");
        reset();
        Os.failStat = true;
        LocalSocket.enqueue(new LocalSocket.Plan(10042));
        require(!SecureChannel.isTrustedApp(new LocalSocket()),
                "stat failure rejects caller");
        reset();
        LocalSocket.Plan corrupt = new LocalSocket.Plan(10042);
        corrupt.credentialFailure = true;
        LocalSocket.enqueue(corrupt);
        require(!SecureChannel.isTrustedApp(new LocalSocket()),
                "peer credential failure rejects caller");
    }

    private static boolean listenDenied() {
        try { SecureChannel.listen(); return false; }
        catch (Exception expected) { return true; }
    }

    private static void listenerPreflight() {
        reset();
        Os.appUid = 9999;
        Os.filesUid = 9999;
        require(listenDenied() && Os.openCalls == 0,
                "non-application UID blocks before opening lock");

        reset();
        Os.filesUid = 10043;
        require(listenDenied() && Os.openCalls == 0,
                "different files owner blocks before opening lock");

        reset();
        Os.appMode = OsConstants.S_IFREG | 0600;
        require(listenDenied() && Os.openCalls == 0,
                "non-directory app path blocks before opening lock");

        reset();
        Os.filesMode = OsConstants.S_IFREG | 0600;
        require(listenDenied() && Os.openCalls == 0,
                "non-directory files path blocks before opening lock");

        reset();
        Os.failStat = true;
        require(listenDenied() && Os.openCalls == 0,
                "stat error fails closed before lock file");

        reset();
        require(listenDenied() && Os.openCalls == 1,
                "valid metadata reaches lock opening, blocked by test fake");
        require((Os.lastOpenFlags & OsConstants.O_NOFOLLOW) != 0
                && (Os.lastOpenFlags & OsConstants.O_CLOEXEC) != 0
                && Os.lastOpenPath.equals(SecureChannel.path().replace(
                    "jesty-thor-control-v2.sock", "jesty-thor-control-v2.lock")),
                "real listen requests no-follow, close-on-exec private instance lock");
    }

    private static Throwable staleProbeError(IOException failure) throws Exception {
        reset();
        // The fake inode never creates a host file. Refuse an existing device-shaped
        // path before invoking the real helper; this test must never unlink host data.
        if (new File(SecureChannel.path()).exists()) throw new AssertionError("unexpected host device path");
        Os.socketStat = new StructStat(10042, 10042, OsConstants.S_IFSOCK | 0600);
        LocalSocket.Plan plan = new LocalSocket.Plan(0);
        plan.connectError = failure;
        LocalSocket.enqueue(plan);
        Method cleanup = SecureChannel.class.getDeclaredMethod("removeStaleSocket", int.class);
        cleanup.setAccessible(true);
        try {
            cleanup.invoke(null, 10042);
            throw new AssertionError("fake pathname must not be removed");
        } catch (InvocationTargetException error) {
            require(LocalSocket.latest.closes == 1, "failed stale probe socket closed");
            return error.getCause();
        }
    }

    private static void staleProbeFailureClassification() throws Exception {
        IOException[] unknown = {
                new IOException("socket failed", new ErrnoException("socket", OsConstants.EMFILE)),
                new IOException("permission failure", new ErrnoException("connect", OsConstants.EACCES)),
                new IOException("Connection refused", new ErrnoException("connect", OsConstants.EACCES)),
                new IOException("unclassified transport failure"),
                new IOException((String) null),
                new IOException("connect failed: Connection refused"),
                new IOException("Connection refused", new IllegalStateException("unclassified cause")),
                new FileNotFoundException("Connection refused")
        };
        for (IOException failure : unknown) {
            require(staleProbeError(failure) == failure,
                    "unclassified/resource/permission error preserved before stale unlink");
        }
        for (IOException refused : new IOException[]{
                new IOException("connect", new ErrnoException("connect", OsConstants.ECONNREFUSED)),
                new IOException(Os.strerror(OsConstants.ECONNREFUSED))}) {
            Throwable result = staleProbeError(refused);
            require(result instanceof IOException && result != refused
                    && "Cannot remove stale secure socket".equals(result.getMessage()),
                    "known refusal reaches missing fake pathname; no native unlink claimed");
        }
    }

    public static void main(String[] args) throws Exception {
        clientPeerTrust();
        appPeerTrust();
        listenerPreflight();
        staleProbeFailureClassification();
        System.out.println("SecureChannelHostTest passed: " + checks
                + " assertions; native peer/file locking/stale socket NOT VERIFIED");
    }
}
