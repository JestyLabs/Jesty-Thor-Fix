package com.thor.displaypowertest;

import java.io.File;
import java.io.IOException;
import java.security.SecureRandom;

/**
 * One-shot app-authenticated descriptor transfer. No numeric process-stop API
 * or persistent receipt exists here. A lost receipt requires normal restart.
 */
public final class LiveRetirement {
    private static final int TOTAL_MS = 15000;
    private static final int CONTROL_MS = 1000;
    private static final int OBSERVATION_WAIT_MS = 6000;
    private static final int FRAME_LIMIT = 128;

    interface Bridge { boolean submit(String command); }

    interface Io {
        int applicationUid() throws IOException;
        int listen(String path) throws IOException;
        int accept(int listener, int uid, int timeout) throws IOException;
        int connect(String path, int uid, int timeout) throws IOException;
        int uid(int socket) throws IOException;
        int pid(int socket) throws IOException;
        void sendFd(int control, int socket, int timeout) throws IOException;
        int receiveFd(int control, int timeout) throws IOException;
        void write(int socket, String line, int timeout) throws IOException;
        String read(int socket, int limit, int timeout) throws IOException;
        int bindProcess(int socket) throws IOException;
        void requireSameProcess(int process, int socket, int pid) throws IOException;
        String request(int socket, char command, int timeout) throws IOException;
        void signalAndAwaitExit(int process, int control, int timeout) throws IOException;
        void close(int descriptor) throws IOException;
        void cleanupServer(int descriptor, String path) throws IOException;
    }

    private LiveRetirement() {}

    static int retire(boolean display, boolean cpu, boolean guard, Bridge bridge)
            throws IOException {
        RetirementNative.load();
        byte[] random = new byte[16];
        new SecureRandom().nextBytes(random);
        StringBuilder nonce = new StringBuilder(32);
        for (byte value : random) {
            nonce.append(Character.forDigit((value >>> 4) & 15, 16));
            nonce.append(Character.forDigit(value & 15, 16));
        }
        return retire(new NativeIo(), nonce.toString(), display, cpu, guard, bridge);
    }

    static int retire(Io io, String nonce, boolean display, boolean cpu,
            boolean guard, Bridge bridge) throws IOException {
        validateNonce(nonce);
        Deadline deadline = new Deadline();
        String path = controlPath(nonce);
        try (Endpoint endpoint = new Endpoint(io, io.listen(path), path)) {
            deadline.remaining(CONTROL_MS);
            if (!bridge.submit(helperCommand(nonce, display, cpu, guard))) {
                throw new IOException("Retirement bridge rejected");
            }
            try (Handle control = new Handle(io,
                    io.accept(endpoint.fd, 0, deadline.remaining(CONTROL_MS)))) {
                if (io.uid(control.fd) != 0) throw new IOException("Retirement helper UID");
                expect(io, control.fd, "HELLO " + nonce, deadline);
                int peerPid = -1;
                for (int index = 0; index < 4; index++) {
                    expect(io, control.fd, "SOCKET " + nonce + " " + index, deadline,
                            OBSERVATION_WAIT_MS);
                    try (Handle socket = new Handle(io,
                            io.connect(SecureChannel.path(), 0, deadline.remaining(CONTROL_MS)))) {
                        int pid = io.pid(socket.fd);
                        if (io.uid(socket.fd) != 0 || pid <= 100
                                || (peerPid > 0 && pid != peerPid)) {
                            throw new IOException("Retirement daemon peer changed");
                        }
                        peerPid = pid;
                        io.sendFd(control.fd, socket.fd, deadline.remaining(CONTROL_MS));
                    }
                }
                expect(io, control.fd, "PREPARED " + nonce + " " + peerPid, deadline,
                        OBSERVATION_WAIT_MS);
                // COMMIT is the irreversible-operation boundary. Cancellation
                // afterwards cannot undo a signal, but still cannot launch.
                io.write(control.fd, "COMMIT " + nonce, deadline.remaining(CONTROL_MS));
                expect(io, control.fd, "EXIT " + nonce + " " + peerPid, deadline, 3500);
                deadline.remaining(CONTROL_MS);
                return peerPid;
            }
        }
    }

    /** Fixed main, endpoint and flags only; never a caller-selected PID/command. */
    static String helperCommand(String nonce, boolean display, boolean cpu, boolean guard) {
        validateNonce(nonce);
        String command = "A=$(pm path com.thor.displaypowertest);A=${A#*:};"
                + "CLASSPATH=$A app_process / R " + nonce + " "
                + (display ? "1" : "0") + (cpu ? "1" : "0") + (guard ? "1" : "0")
                + " >/dev/null 2>&1 &";
        if (command.length() > DaemonLaunchScript.MAX_COMMAND_CHARS) {
            throw new IllegalArgumentException("Retirement helper command too long");
        }
        return command;
    }

    public static void runHelper(String[] args) throws IOException {
        if (args.length != 2 || !args[1].matches("[01]{3}")) {
            throw new IOException("Retirement helper arguments");
        }
        validateNonce(args[0]);
        RetirementNative.loadHelper();
        serve(new NativeIo(), args[0], args[1].charAt(0) == '1',
                args[1].charAt(1) == '1', args[1].charAt(2) == '1');
    }

    static void serve(Io io, String nonce, boolean display, boolean cpu, boolean guard)
            throws IOException {
        validateNonce(nonce);
        Deadline deadline = new Deadline();
        int expectedUid = io.applicationUid();
        if (expectedUid <= 0) throw new IOException("Retirement app UID");
        try (Handle control = new Handle(io,
                io.connect(controlPath(nonce), expectedUid, deadline.remaining(CONTROL_MS)))) {
            if (io.uid(control.fd) != expectedUid) throw new IOException("Retirement app peer");
            io.write(control.fd, "HELLO " + nonce, deadline.remaining(CONTROL_MS));
            int processFd = -1;
            try {
                int peerPid = -1;
                String firstHealth = null;
                String firstSnapshot = null;
                for (int index = 0; index < 4; index++) {
                    io.write(control.fd, "SOCKET " + nonce + " " + index,
                            deadline.remaining(CONTROL_MS));
                    try (Handle socket = new Handle(io,
                            io.receiveFd(control.fd, deadline.remaining(CONTROL_MS)))) {
                        int uid = io.uid(socket.fd);
                        int pid = io.pid(socket.fd);
                        if (uid != 0 || pid <= 100 || (peerPid > 0 && peerPid != pid)) {
                            throw new IOException("Retirement root peer changed");
                        }
                        if (index == 0) {
                            peerPid = pid;
                            processFd = io.bindProcess(socket.fd);
                            if (processFd < 0) throw new IOException("Retirement descriptor missing");
                        }
                        io.requireSameProcess(processFd, socket.fd, peerPid);
                        char command = index % 2 == 0 ? 'I' : 'Q';
                        String response = io.request(socket.fd, command,
                                deadline.remaining(command == 'I' ? 700 : 2500));
                        io.requireSameProcess(processFd, socket.fd, peerPid);
                        if (index == 0) firstHealth = response;
                        if (index == 1) {
                            firstSnapshot = response;
                            requireEligible(firstHealth, firstSnapshot, display, cpu, guard,
                                    uid, peerPid);
                        }
                        if (index == 2) {
                            if (!LiveHandoverPolicy.sameRetainedIdentity(firstHealth, response)) {
                                throw new IOException("Retirement identity changed");
                            }
                            firstHealth = response;
                        }
                        if (index == 3) {
                            requireUnchangedEligible(firstHealth, firstSnapshot, response,
                                    display, cpu, guard, uid, peerPid);
                        }
                    }
                }
                io.write(control.fd, "PREPARED " + nonce + " " + peerPid,
                        deadline.remaining(CONTROL_MS));
                expect(io, control.fd, "COMMIT " + nonce, deadline);
                io.signalAndAwaitExit(processFd, control.fd, deadline.remaining(3000));
                int exitedProcess = processFd;
                processFd = -1;
                io.close(exitedProcess);
                io.write(control.fd, "EXIT " + nonce + " " + peerPid,
                        deadline.remaining(CONTROL_MS));
            } finally {
                if (processFd >= 0) io.close(processFd);
            }
        }
    }

    private static void requireEligible(String health, String snapshot, boolean display,
            boolean cpu, boolean guard, int uid, int pid) throws IOException {
        if (LiveHandoverPolicy.safePidForRetainedProcess(health, snapshot,
                display, cpu, guard, uid, pid) != pid) {
            throw new IOException("Retirement predecessor ineligible");
        }
    }

    private static void requireUnchangedEligible(String health, String firstSnapshot, String snapshot,
            boolean display, boolean cpu, boolean guard, int uid, int pid) throws IOException {
        requireEligible(health, snapshot, display, cpu, guard, uid, pid);
        if (!LiveHandoverPolicy.sameRetainedDisplayState(firstSnapshot, snapshot)) {
            throw new IOException("Retirement display state changed");
        }
    }

    private static void expect(Io io, int fd, String expected, Deadline deadline)
            throws IOException {
        expect(io, fd, expected, deadline, CONTROL_MS);
    }

    private static void expect(Io io, int fd, String expected, Deadline deadline, int max)
            throws IOException {
        if (!expected.equals(io.read(fd, FRAME_LIMIT, deadline.remaining(max)))) {
            throw new IOException("Retirement frame mismatch");
        }
    }

    static String controlPath(String nonce) {
        validateNonce(nonce);
        return new File(SecureChannel.path()).getParent() + "/retire-" + nonce;
    }

    private static void validateNonce(String nonce) {
        if (nonce == null || !nonce.matches("[0-9a-f]{32}")) {
            throw new IllegalArgumentException("Retirement request identity");
        }
    }

    private static final class Deadline {
        private final long end = System.nanoTime() + TOTAL_MS * 1000000L;
        int remaining(int max) throws IOException {
            if (Thread.currentThread().isInterrupted()) {
                throw new java.io.InterruptedIOException("Retirement cancelled");
            }
            long ms = (end - System.nanoTime()) / 1000000L;
            if (ms <= 0) throw new IOException("Retirement deadline");
            return (int) Math.min(ms, max);
        }
    }

    private static class Handle implements AutoCloseable {
        final Io io;
        final int fd;
        Handle(Io io, int fd) throws IOException {
            if (fd < 0) throw new IOException("Retirement invalid descriptor");
            this.io = io;
            this.fd = fd;
        }
        @Override public void close() throws IOException { io.close(fd); }
    }

    private static final class Endpoint extends Handle {
        private final String path;
        Endpoint(Io io, int fd, String path) throws IOException {
            super(io, fd);
            this.path = path;
        }
        @Override public void close() throws IOException { io.cleanupServer(fd, path); }
    }

    private static final class NativeIo implements Io {
        public int applicationUid() throws IOException { return RetirementNative.applicationUid(); }
        public int listen(String path) throws IOException { return RetirementNative.listen(path); }
        public int accept(int fd, int uid, int timeout) throws IOException {
            return RetirementNative.accept(fd, uid, timeout);
        }
        public int connect(String path, int uid, int timeout) throws IOException {
            return RetirementNative.connect(path, uid, timeout);
        }
        public int uid(int fd) throws IOException { return RetirementNative.peerUid(fd); }
        public int pid(int fd) throws IOException { return RetirementNative.peerPid(fd); }
        public void sendFd(int fd, int socket, int timeout) throws IOException {
            RetirementNative.sendFd(fd, socket, timeout);
        }
        public int receiveFd(int fd, int timeout) throws IOException {
            return RetirementNative.receiveFd(fd, timeout);
        }
        public void write(int fd, String line, int timeout) throws IOException {
            RetirementNative.writeLine(fd, line, timeout);
        }
        public String read(int fd, int limit, int timeout) throws IOException {
            return RetirementNative.readLine(fd, limit, timeout);
        }
        public int bindProcess(int socket) throws IOException {
            return RetirementNative.bindProcess(socket);
        }
        public void requireSameProcess(int process, int socket, int pid) throws IOException {
            RetirementNative.requireSameProcess(process, socket, pid);
        }
        public String request(int socket, char command, int timeout) throws IOException {
            return RetirementNative.request(socket, command, timeout);
        }
        public void signalAndAwaitExit(int process, int control, int timeout) throws IOException {
            RetirementNative.signalAndAwaitExit(process, control, timeout);
        }
        public void close(int fd) throws IOException { RetirementNative.close(fd); }
        public void cleanupServer(int fd, String path) throws IOException {
            RetirementNative.cleanupServer(fd, path);
        }
    }
}
