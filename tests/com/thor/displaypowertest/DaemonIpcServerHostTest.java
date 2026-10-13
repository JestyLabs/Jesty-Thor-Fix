package com.thor.displaypowertest;

import android.net.LocalServerSocket;
import android.net.LocalSocket;
import android.util.Log;

import java.lang.reflect.Field;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Compiles the real DaemonIpcServer with host-only fake Android transport.
 * No claim of kernel UID/SELinux validation or enforcement of the 1.5s timeout.
 */
public final class DaemonIpcServerHostTest {
    private static int assertions;

    public static void main(String[] args) throws Exception {
        basicCommandsAndRecovery();
        queueSaturation();
        System.out.println("DaemonIpcServerHostTest passed (" + assertions + " assertions)");
    }

    private static void basicCommandsAndRecovery() throws Exception {
        LocalServerSocket listener = new LocalServerSocket();
        DaemonCommandHandler handler = new DaemonCommandHandler();
        DaemonIpcServer ipc = new DaemonIpcServer(listener, handler);
        AtomicReference<Throwable> serverExit = new AtomicReference<>();
        Thread server = startServer(ipc, serverExit);
        try {
            LocalSocket untrusted = new LocalSocket(false, 'R');
            listener.offer(untrusted);
            awaitClosed(untrusted, "untrusted client disconnected");
            check(untrusted.reads.get() == 0 && untrusted.response().isEmpty(),
                    "untrusted request rejected before reading any command byte");
            check(handler.calls.get() == 0 && Log.warnings.get() > 0,
                    "untrusted requests never reach command dispatcher");

            LocalSocket empty = new LocalSocket(true);
            listener.offer(empty);
            awaitClosed(empty, "empty command handled");
            check("ok=0;error=EMPTY_COMMAND\n".equals(empty.response()),
                    "EOF reports failure without calling handler");
            check(empty.timeoutMs == 1500 && handler.calls.get() == 0,
                    "trusted EOF requested the configured read timeout");

            LocalSocket unknown = new LocalSocket(true, '?');
            listener.offer(unknown);
            awaitClosed(unknown, "unknown command handled");
            check("ok=0;error=UNKNOWN_COMMAND\n".equals(unknown.response()),
                    "unsupported command response is passed through with newline");

            LocalSocket extra = new LocalSocket(true, 'Q', 'R');
            listener.offer(extra);
            awaitClosed(extra, "single-byte request handled");
            check("ok=1;echo=Q\n".equals(extra.response()) && extra.reads.get() == 1,
                    "current wire protocol reads exactly one command byte");

            int beforeErrors = Log.errors.get();
            LocalSocket readError = new LocalSocket(true, 'Q');
            readError.failInput = true;
            listener.offer(readError);
            awaitClosed(readError, "failed read socket closed");
            check(Log.errors.get() > beforeErrors && readError.response().isEmpty(),
                    "read failure cannot dispatch a command");

            LocalSocket handlerError = new LocalSocket(true, 'X');
            listener.offer(handlerError);
            awaitClosed(handlerError, "faulty handler socket closed");
            check(handlerError.response().isEmpty(), "handler exception contained");

            LocalSocket outputError = new LocalSocket(true, 'Q');
            outputError.failOutput = true;
            listener.offer(outputError);
            awaitClosed(outputError, "failed write socket closed");
            check(outputError.response().isEmpty(), "write failure contained");

            LocalSocket recovery = new LocalSocket(true, 'I');
            listener.offer(recovery);
            awaitClosed(recovery, "recovery request handled");
            check("ok=1;echo=I\n".equals(recovery.response()),
                    "new client succeeds after read, write and handler failure");
        } finally {
            stop(listener, server, ipc);
        }
        check(serverExit.get() instanceof java.io.IOException,
                "listener stops on explicit synthetic accept failure");
    }

    private static void queueSaturation() throws Exception {
        LocalServerSocket listener = new LocalServerSocket();
        DaemonCommandHandler handler = new DaemonCommandHandler();
        DaemonIpcServer ipc = new DaemonIpcServer(listener, handler);
        AtomicReference<Throwable> serverExit = new AtomicReference<>();
        Thread server = startServer(ipc, serverExit);
        try {
            LocalSocket first = new LocalSocket(true, 'B');
            LocalSocket second = new LocalSocket(true, 'B');
            listener.offer(first);
            listener.offer(second);
            check(handler.blockedStarted.await(2000L, TimeUnit.MILLISECONDS),
                    "both workers occupied before flood");
            LocalSocket[] incoming = new LocalSocket[5];
            for (int i = 0; i < incoming.length; i++) {
                incoming[i] = new LocalSocket(true, 'I');
                if (i == 4) incoming[i].failClose = true;
                listener.offer(incoming[i]);
            }
            awaitAccepted(listener, 7);
            awaitClosed(incoming[4], "seventh request rejected");
            check(handler.calls.get() == 2,
                    "queued and rejected commands cannot run while workers blocked");
            check(incoming[4].response().isEmpty() && incoming[4].timeoutMs == -1,
                    "rejected client does not reach read timeout or dispatcher");
            check(handler.maxActive.get() == 2, "at most two concurrent commands");
            handler.unblock.countDown();
            awaitClosed(first, "first active worker released");
            awaitClosed(second, "second active worker released");
            for (int i = 0; i < 4; i++) awaitClosed(incoming[i], "queued request " + i);
            check(handler.calls.get() == 6 && handler.maxActive.get() == 2,
                    "two active plus four queued; overflow refused");
            check(incoming[4].reads.get() == 0,
                    "overflow socket closed before reading a byte");
            // Characterize a rare but important failure: closing the seventh
            // socket itself throws. That must not kill the listening daemon.
            LocalSocket recovered = new LocalSocket(true, 'Q');
            listener.offer(recovered);
            awaitClosed(recovered, "listener survives rejected socket close failure");
            check("ok=1;echo=Q\n".equals(recovered.response()),
                    "listener keeps serving after queue-pressure close failure");
        } finally {
            handler.unblock.countDown();
            stop(listener, server, ipc);
        }
        check(serverExit.get() instanceof java.io.IOException,
                "queue pressure does not terminate the accept loop");
    }

    private static Thread startServer(DaemonIpcServer ipc, AtomicReference<Throwable> exit) {
        Thread worker = new Thread(() -> {
            try { ipc.serveForever(); }
            catch (Throwable error) { exit.set(error); }
        }, "host-test-ipc-listener");
        worker.start();
        return worker;
    }
    private static void awaitClosed(LocalSocket socket, String description) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (socket.closed.get() == 0 && System.nanoTime() < deadline) Thread.sleep(2L);
        check(socket.closed.get() > 0, description);
    }
    private static void awaitAccepted(LocalServerSocket server, int n) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        while (server.accepted.get() < n && System.nanoTime() < deadline) Thread.sleep(2L);
        check(server.accepted.get() == n, "accepted exactly " + n + " sockets");
    }
    private static void stop(LocalServerSocket listener, Thread server, DaemonIpcServer ipc)
            throws Exception {
        listener.stop();
        server.join(3000L);
        check(!server.isAlive(), "accept thread stops");
        // Reflection is host-test-only; production has no worker-pool stop API.
        Field field = DaemonIpcServer.class.getDeclaredField("connections");
        field.setAccessible(true);
        ThreadPoolExecutor pool = (ThreadPoolExecutor) field.get(ipc);
        pool.shutdownNow();
        check(pool.awaitTermination(3L, TimeUnit.SECONDS), "host workers terminate");
    }
    private static void check(boolean value, String description) {
        assertions++;
        if (!value) throw new AssertionError(description);
    }
}
