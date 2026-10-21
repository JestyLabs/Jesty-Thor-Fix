package com.thor.displaypowertest;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Inert lower boundary only: queues model connected sockets, SCM_RIGHTS and a
 * retained process descriptor. Both app and root controllers are the actual
 * LiveRetirement class. No native library, process, shell or socket is opened.
 * Credentials, accepted fixed D process image, descriptor exit and I/Q replies are synthetic, NOT
 * kernel/Android acceptance or the actual published-producer evidence.
 */
public final class RetirementNative {
    static boolean available, bridgeAckOnly, cancelBeforeCommit, cancelAfterCommit, processCloseFailure;
    static int daemonUid, helperUid, appUid, changedPeerAt, bindCalls, signalCalls, transfers;
    static String receiptFault;
    private static final int APP_UID = 11000;
    private static final Object CLOSED = new Object();
    private static final AtomicInteger next = new AtomicInteger(100);
    private static final Map<Integer, Node> descriptors = new ConcurrentHashMap<>();
    private static final Map<String, Integer> listeners = new ConcurrentHashMap<>();
    private static Thread helper;
    private static volatile Throwable helperFailure;
    private static int connected;

    private static class Node {}
    private static final class Listener extends Node {
        final BlockingQueue<Object> accepted = new LinkedBlockingQueue<>();
    }
    private static final class Connection extends Node {
        final BlockingQueue<Object> incoming = new LinkedBlockingQueue<>();
        final int uid, pid;
        Connection peer;
        Connection(int uid, int pid) { this.uid = uid; this.pid = pid; }
    }
    private static final class OldSocket extends Node {
        final int uid, pid;
        OldSocket(int uid, int pid) { this.uid = uid; this.pid = pid; }
    }
    private static final class ProcessHandle extends Node {
        final int pid;
        ProcessHandle(int pid) { this.pid = pid; }
    }

    static void reset() {
        if (helper != null && helper.isAlive()) throw new AssertionError("helper not joined");
        if (!descriptors.isEmpty()) throw new AssertionError("retirement descriptor leak");
        listeners.clear();
        available = true;
        bridgeAckOnly = cancelBeforeCommit = cancelAfterCommit = processCloseFailure = false;
        daemonUid = helperUid = 0;
        appUid = APP_UID;
        changedPeerAt = -1;
        bindCalls = signalCalls = connected = transfers = 0;
        receiptFault = "";
        helper = null;
        helperFailure = null;
    }

    static void awaitHelper() throws InterruptedException {
        if (helper != null) {
            helper.join(4000);
            if (helper.isAlive()) throw new AssertionError("modelled root helper did not stop");
        }
        if (!descriptors.isEmpty()) throw new AssertionError("retirement descriptor leak");
        if (helperFailure != null) throw new AssertionError("root controller fixture failed", helperFailure);
    }

    static void submit(String command) {
        Matcher match = Pattern.compile(" app_process / R ([0-9a-f]{32}) ([01]{3})( recovery)? ")
                .matcher(command);
        if (!match.find()) throw new AssertionError("not the fixed product helper command");
        final String[] args = match.group(3) == null
                ? new String[] {match.group(1), match.group(2)}
                : new String[] {match.group(1), match.group(2), "recovery"};
        if (bridgeAckOnly) return;
        connected = 0;
        helper = new Thread(() -> {
            try {
                LiveRetirement.runHelper(args);
            } catch (IOException expected) {
                FixtureState.events.add("HELPER_REFUSED");
            } catch (Throwable unexpected) {
                helperFailure = unexpected;
            }
        }, "inert-retirement-root-controller");
        helper.start();
    }

    public static void load() throws IOException {
        if (!available) throw new IOException("modelled unsupported native support");
    }
    public static void loadHelper() throws IOException { load(); }
    public static int applicationUid() { return APP_UID; }

    private static int put(Node node) {
        int fd = next.getAndIncrement();
        descriptors.put(fd, node);
        return fd;
    }
    private static Node node(int fd) throws IOException {
        Node node = descriptors.get(fd);
        if (node == null) throw new IOException("modelled closed descriptor");
        return node;
    }
    private static Connection connection(int fd) throws IOException {
        Node node = node(fd);
        if (!(node instanceof Connection)) throw new IOException("modelled non-control socket");
        return (Connection) node;
    }
    private static Object take(BlockingQueue<Object> queue, int timeout) throws IOException {
        try {
            Object item = queue.poll(timeout, TimeUnit.MILLISECONDS);
            if (item == null || item == CLOSED) throw new IOException("modelled missing/closed frame");
            return item;
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("modelled cancelled control read");
        }
    }

    public static int listen(String path) throws IOException {
        int fd = put(new Listener());
        if (listeners.putIfAbsent(path, fd) != null) {
            close(fd);
            throw new IOException("modelled occupied endpoint");
        }
        return fd;
    }
    public static void cleanupServer(int fd, String path) throws IOException {
        listeners.remove(path);
        close(fd);
    }
    public static int accept(int fd, int uid, int timeout) throws IOException {
        if (bridgeAckOnly) throw new IOException("ACK with no helper effect");
        int accepted = (Integer) take(((Listener) node(fd)).accepted, timeout);
        if (peerUid(accepted) != uid) {
            close(accepted);
            throw new IOException("modelled accept credential mismatch");
        }
        return accepted;
    }
    public static int connect(String path, int uid, int timeout) throws IOException {
        Integer listenerFd = listeners.get(path);
        if (listenerFd != null) {
            Connection app = new Connection(helperUid, 13000);
            Connection root = new Connection(appUid, 11001);
            app.peer = root;
            root.peer = app;
            int rootFd = put(root);
            ((Listener) node(listenerFd)).accepted.add(put(app));
            return rootFd;
        }
        if (!SecureChannel.path().equals(path) || FixtureState.version == null) {
            throw new IOException("modelled absent old socket");
        }
        if (Thread.currentThread() == helper) {
            throw new IOException("modelled old server rejects direct root connection");
        }
        int index = connected++;
        FixtureState.events.add("APP_SOCKET_" + index);
        int peer = Integer.parseInt(FixtureState.pid) + (index == changedPeerAt ? 1 : 0);
        return put(new OldSocket(daemonUid, peer));
    }
    public static int peerUid(int fd) throws IOException {
        Node node = node(fd);
        return node instanceof Connection ? ((Connection) node).uid : ((OldSocket) node).uid;
    }
    public static int peerPid(int fd) throws IOException {
        Node node = node(fd);
        return node instanceof Connection ? ((Connection) node).pid : ((OldSocket) node).pid;
    }
    public static void sendFd(int control, int socket, int timeout) throws IOException {
        // A duplicate descriptor survives the sender closing its local handle.
        transfers++;
        connection(control).peer.incoming.add(put(node(socket)));
    }
    public static int receiveFd(int control, int timeout) throws IOException {
        Object value = take(connection(control).incoming, timeout);
        if (!(value instanceof Integer)) throw new IOException("modelled descriptor frame");
        return (Integer) value;
    }
    public static void writeLine(int fd, String line, int timeout) throws IOException {
        if (line.startsWith("COMMIT ")) FixtureState.events.add("COMMIT");
        if (line.startsWith("EXIT ")) {
            FixtureState.events.add("EXIT_RECEIPT");
            if ("missing".equals(receiptFault)) return;
            if ("stale".equals(receiptFault)) {
                String nonce = line.split(" ")[1];
                line = "EXIT " + (nonce.charAt(0) == '0' ? "1" : "0")
                        + nonce.substring(1) + " 12000";
            }
            if ("malformed".equals(receiptFault)) line = "EXIT invalid";
            if ("oversized".equals(receiptFault)) line = repeat('X', 129);
        }
        connection(fd).peer.incoming.add(line);
    }
    public static String readLine(int fd, int limit, int timeout) throws IOException {
        Object value = take(connection(fd).incoming, timeout);
        if (!(value instanceof String)) throw new IOException("modelled line frame");
        String line = (String) value;
        if (line.length() > limit) throw new IOException("modelled oversized receipt");
        if (line.startsWith("PREPARED ") && FixtureState.beforeCommit != null) {
            Runnable hook = FixtureState.beforeCommit;
            FixtureState.beforeCommit = null;
            hook.run();
        }
        if ((cancelBeforeCommit && line.startsWith("PREPARED "))
                || (cancelAfterCommit && line.startsWith("EXIT "))) {
            Thread.currentThread().interrupt();
        }
        return line;
    }
    public static int bindProcess(int socket) throws IOException {
        bindCalls++;
        FixtureState.events.add("BIND_PROCESS");
        return put(new ProcessHandle(peerPid(socket)));
    }
    public static void requireSameProcess(int process, int socket, int pid) throws IOException {
        ProcessHandle bound = (ProcessHandle) node(process);
        if (FixtureState.version == null || bound.pid != pid || peerPid(socket) != pid
                || peerUid(socket) != 0) throw new IOException("modelled changed/exited process");
    }
    public static String request(int socket, char command, int timeout) throws IOException {
        FixtureState.events.add("REQUEST_" + command);
        boolean retained = false;
        for (Node descriptor : descriptors.values()) {
            if (descriptor instanceof ProcessHandle
                    && ((ProcessHandle) descriptor).pid == peerPid(socket)) retained = true;
        }
        if (!retained) throw new AssertionError("request before retained process binding");
        try {
            return SocketClient.request(command, timeout);
        } catch (Exception error) {
            throw new IOException("modelled old endpoint unavailable", error);
        }
    }
    public static void signalAndAwaitExit(int process, int control, int timeout) throws IOException {
        node(process);
        signalCalls++;
        FixtureState.kills++;
        FixtureState.events.add("SIGNAL");
        if (!FixtureState.stopEffective) throw new IOException("modelled no-effect signal");
        FixtureState.version = null;
        FixtureState.events.add("EXIT_CONFIRMED");
    }
    public static void close(int fd) throws IOException {
        Node node = descriptors.remove(fd);
        if (node instanceof ProcessHandle) {
            FixtureState.events.add("PROCESS_DESCRIPTOR_CLOSED");
            if (processCloseFailure) throw new IOException("modelled process descriptor close failure");
        }
        if (node instanceof Connection) ((Connection) node).peer.incoming.add(CLOSED);
        if (node instanceof Listener) ((Listener) node).accepted.add(CLOSED);
    }
    private static String repeat(char value, int length) {
        char[] chars = new char[length];
        java.util.Arrays.fill(chars, value);
        return new String(chars);
    }
}
