package com.thor.displaypowertest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Real product controller/policy and real published producers; inert OS only. */
public final class RetirementHostTest {
    private static final String NONCE = "0123456789abcdef0123456789abcdef";
    private static int cases;

    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }

    private static class OsBoundary implements LiveRetirement.Io {
        final String fault;
        final List<String> events = new ArrayList<>();
        final List<String> frames = new ArrayList<>();
        String health;
        String snapshot;
        final DaemonCommandHandler producer;
        int sockets, reads, requests, signals;
        boolean bound, exited, cleaned;

        OsBoundary(String fault) {
            this.fault = fault;
            boolean top = fault.startsWith("top-");
            DaemonState.setMode(top ? "1" : "0");
            DaemonState.onRepairEnd("NONE");
            Telemetry.observedBottom = top ? "0" : "1";
            LidGuard.observedEnabled = fault.startsWith("top-guard");
            LidGuard.observedLid = top && !LidGuard.observedEnabled ? "unknown" : "open";
            LidGuard.observedExternal = fault.equals("top-guard") ? "0" : "unknown";
            if (fault.equals("top-closed")) LidGuard.observedLid = "closed";
            if (fault.equals("top-dock")) LidGuard.observedExternal = "1";
            DisplayActionCoordinator.observedStatus = "CONFIRMED";
            DisplayActionCoordinator.observedGeneration = 1L;
            if (fault.equals("top-pending")) DaemonState.onWake(false);
            if (fault.equals("top-rescheduled")) DaemonState.onWake(true);
            if (fault.equals("top-running")) DaemonState.onRepairBegin();
            if (fault.equals("top-retry")) DaemonState.onRepairEnd("RETRY_PENDING");
            if (fault.equals("top-unknown-repair")) DaemonState.onRepairEnd("?");
            if (fault.equals("top-off-ok")) DaemonState.onRepairEnd("OFF_OK");
            if (fault.equals("top-cancelled")) {
                DaemonState.onWake(false);
                DaemonState.onRepairCancelled();
            }
            if (fault.equals("top-busy")) DisplayActionCoordinator.observedStatus = "UNCONFIRMED";
            producer = new DaemonCommandHandler(new CpuFixController(), new BootTrace(), 12000);
            health = producer.handle('I');
            snapshot = producer.handle('Q');
        }

        public int applicationUid() { return 10123; }
        public int listen(String path) { events.add("listen"); return 9; }
        public int accept(int server, int uid, int timeout) { return 10; }
        public int connect(String path, int uid, int timeout) {
            if (path.endsWith(NONCE)) return 10;
            sockets++;
            return 100 + sockets - 1;
        }
        public int uid(int fd) {
            if (fd == 10) return fault.equals("helper-uid") ? 2000
                    : fault.equals("control-uid") ? 0 : 10123;
            return fault.equals("socket-uid") || fault.equals("top-socket-uid") ? 2000 : 0;
        }
        public int pid(int fd) { return fault.equals("changed-peer") && fd >= 102 ? 12001 : 12000; }
        public void sendFd(int control, int socket, int timeout) { events.add("transfer"); }
        public int receiveFd(int control, int timeout) { return 100 + sockets++; }
        public void write(int fd, String frame, int timeout) {
            frames.add(frame);
            events.add(frame.split(" ")[0]);
        }
        public String read(int fd, int limit, int timeout) throws IOException {
            if (frames.size() > 0 && frames.get(0).startsWith("HELLO")) {
                if (fault.equals("cancel")) Thread.currentThread().interrupt();
                return "COMMIT " + (fault.equals("stale-commit") ? "stale" : NONCE);
            }
            String frame;
            int at = reads++;
            if (at >= 1 && at <= 5) {
                check(timeout >= 5000, "app wait preserves Q and identity-check budget");
            }
            if (at == 0) frame = "HELLO " + (fault.equals("stale-hello") ? "stale" : NONCE);
            else if (at <= 4) frame = "SOCKET " + NONCE + " " + (at - 1);
            else if (at == 5) frame = "PREPARED " + NONCE + " "
                    + (fault.equals("prepared-pid") ? 12001 : 12000);
            else {
                if (fault.equals("missing-receipt")) throw new IOException("EOF");
                if (fault.equals("cancel-receipt")) Thread.currentThread().interrupt();
                frame = fault.equals("oversized-receipt") ? new String(new char[200]).replace('\0', 'x')
                        : fault.equals("malformed-receipt") ? "EXIT"
                        : "EXIT " + (fault.equals("stale-receipt") ? "stale" : NONCE) + " 12000";
            }
            if (frame.length() > limit) throw new IOException("Frame limit");
            return frame;
        }
        public int bindProcess(int fd) throws IOException {
            events.add("bind");
            if (fault.equals("unsupported") || fault.equals("dead-before-bind")) {
                throw new IOException("No live process descriptor");
            }
            bound = true;
            return 50;
        }
        public void requireSameProcess(int process, int socket, int pid) throws IOException {
            check(bound && process == 50, "retained process descriptor");
            if (fault.equals("dead-after-bind")) throw new IOException("Process exited");
        }
        public String request(int fd, char command, int timeout) throws IOException {
            check(bound, "descriptor acquired BEFORE first I");
            events.add(Character.toString(command));
            requests++;
            String value = command == 'I' ? health : snapshot;
            if (requests == 4 && (fault.equals("top-to-both") || fault.equals("both-to-top"))) {
                boolean top = fault.equals("both-to-top");
                DaemonState.setMode(top ? "1" : "0");
                Telemetry.observedBottom = top ? "0" : "1";
                DisplayActionCoordinator.observedGeneration++;
                value = producer.handle('Q');
            }
            if (requests == 4 && fault.equals("top-aba")) {
                // Existing mode/repair producer observes an invalidation token change.
                DisplayActionCoordinator.observedGeneration += 2;
                value = producer.handle('Q');
            }
            if (requests == 4 && fault.equals("top-second-pending")) {
                DaemonState.onWake(false);
                value = producer.handle('Q');
            }
            if (fault.equals("top-missing-repair") && command == 'Q') value = value.replace(";repair_result=NONE", "");
            if (fault.equals("top-duplicate-repair") && command == 'Q') value += ";repair_result=NONE";
            if (fault.equals("top-missing-generation") && command == 'Q') value = value.replace(";display_generation=1", "");
            if (fault.equals("top-duplicate-generation") && command == 'Q') value += ";display_generation=1";
            if (fault.equals("top-invalid-generation") && command == 'Q') value = value.replace(";display_generation=1", ";display_generation=-1");
            if (fault.equals("top-overflow-generation") && command == 'Q') value = value.replace(";display_generation=1", ";display_generation=9223372036854775808");
            if (fault.equals("top-noncanonical-generation") && command == 'Q') value = value.replace(";display_generation=1", ";display_generation=01");
            if (fault.equals("top-missing-status") && command == 'Q') value = value.replace(";display_action_status=CONFIRMED", "");
            if (fault.equals("top-duplicate-status") && command == 'Q') value += ";display_action_status=CONFIRMED";
            if ((fault.equals("claimed-pid") || fault.equals("top-claimed-pid")) && command == 'I') value = value.replace("pid=12000", "pid=13000");
            if (fault.equals("duplicate-I") && command == 'I') value += ";pid=12000";
            if (fault.equals("duplicate-Q") && command == 'Q') value += ";top_crtc=1";
            if (fault.equals("duplicate-runtime") && command == 'Q') value += ";runtime_version=1.7.1;runtime_version=1.7.1";
            if (fault.equals("wrong-runtime") && command == 'Q') value += ";runtime_version=0.0.0";
            if (fault.equals("empty-runtime") && command == 'Q') value += ";runtime_version=";
            if (fault.equals("version-change") && requests == 3) value = value.replace("version=" + DaemonIdentity.RUNTIME_ID, "version=1.7.9");
            if (fault.equals("missing-Q") && command == 'Q') throw new IOException("EOF");
            if (fault.equals("held") && command == 'Q') value = value.replace("display_actions_held=0", "display_actions_held=1");
            if (fault.equals("cpu") && command == 'Q') value = value.replace("cpu_fix_phase=CONFIRMED", "cpu_fix_phase=UNKNOWN");
            if (fault.equals("lid") && command == 'Q') value = value.replace("lid=open", "lid=closed");
            if (fault.equals("dock") && command == 'Q') value = value.replace("external_display=unknown", "external_display=1");
            if (fault.equals("second-mode") && requests == 4) value = value.replace(";mode=0;", ";mode=1;");
            if (fault.equals("missing-runtime") && command == 'Q') value = value.replace(";runtime_version=" + DaemonIdentity.RUNTIME_ID, "");
            // Ages and samples are observations, not immutable identity fields.
            if (fault.equals("dynamic-age") && requests == 3) value += ";fixture_age=2";
            return value;
        }
        public void signalAndAwaitExit(int process, int control, int timeout) throws IOException {
            check(bound && requests == 4, "two I/Q rounds before signal");
            events.add("signal");
            if (fault.equals("dead-before-signal")) throw new IOException("Already exited");
            signals++;
            if (fault.equals("no-effect") || fault.equals("top-no-effect")) throw new IOException("No exit event");
            exited = true;
            events.add("exit");
        }
        public void close(int fd) { events.add("close:" + fd); }
        public void cleanupServer(int fd, String path) throws IOException {
            cleaned = true;
            if (fault.equals("cleanup")) throw new IOException("Cleanup failed");
        }
    }

    private static void rootCase(String fault, boolean allowed, int signals) throws Exception {
        OsBoundary io = new OsBoundary(fault);
        boolean completed = false;
        try {
            LiveRetirement.serve(io, NONCE, true, false, fault.startsWith("top-guard"));
            completed = true;
        } catch (IOException expected) {
            System.out.println("DENIED root " + fault + ": " + expected.getMessage());
        } finally {
            Thread.interrupted();
        }
        boolean receipt = io.frames.contains("EXIT " + NONCE + " 12000");
        check(io.signals == signals, fault + ": unsafe stop admission");
        check(receipt == allowed, fault + ": receipt requires confirmed exit");
        check(completed == allowed, fault + ": root outcome");
        if (allowed) {
            check(io.exited, fault + ": exit observed");
            check(io.events.indexOf("bind") < io.events.indexOf("I"), "bind before identity");
            check(io.events.indexOf("exit") < io.events.indexOf("EXIT"), "exit before receipt");
        }
        if (io.bound) check(io.events.contains("close:50"), fault + ": process descriptor released");
        check(io.events.contains("close:10"), fault + ": control released");
        cases++;
        System.out.println("PASS root " + fault);
    }

    private static void appCase(String fault, boolean allowed) throws Exception {
        OsBoundary io = new OsBoundary(fault) {
            @Override public int uid(int fd) {
                return fd == 10 ? (fault.equals("helper-uid") ? 2000 : 0) : super.uid(fd);
            }
        };
        boolean completed = false;
        try {
            int pid = LiveRetirement.retire(io, NONCE, true, false, false, command -> {
                check(command.contains("app_process / R " + NONCE + " 100"), "fixed helper argv");
                check(!command.contains("kill") && !command.contains("12000"), "no numeric stop");
                return !fault.equals("bridge-rejected");
            });
            check(pid == 12000, "receipt PID");
            completed = true;
        } catch (IOException expected) {
            System.out.println("DENIED app " + fault + ": " + expected.getMessage());
        } finally {
            Thread.interrupted();
        }
        check(completed == allowed, fault + ": app receipt outcome");
        check(io.cleaned, fault + ": private endpoint cleaned");
        if (allowed) check(io.events.contains("COMMIT"), "fresh commit");
        cases++;
        System.out.println("PASS app " + fault);
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if ("top-stable".equals(args[0])) rootCase("top-stable", true, 1);
            else if ("top-pending".equals(args[0])) rootCase("top-pending", false, 0);
            else if ("top-aba".equals(args[0])) rootCase("top-aba", false, 0);
            else if ("both-to-top".equals(args[0])) rootCase("both-to-top", false, 0);
            else if ("top-public".equals(args[0])) {
                OsBoundary top = new OsBoundary("top-stable");
                check(PreviousSecureDaemonIdentity.safePid(top.health, top.snapshot) < 0,
                        "public unbound retirement stays BOTH-only");
                check(LiveHandoverPolicy.safePid(top.health, top.snapshot, true, false, false) < 0,
                        "public live policy stays BOTH-only");
                check(PreviousSecureDaemonIdentity.safePidForRetainedProcess(top.health, top.snapshot, false) < 0,
                        "TOP requires the display fix enabled");
            }
            else if ("second-mode".equals(args[0])) rootCase("second-mode", false, 0);
            else if ("app-wait".equals(args[0])) appCase("stable", true);
            else rootCase("no-effect", false, 1);
            return;
        }
        OsBoundary producer = new OsBoundary("stable");
        int raw = LiveHandoverPolicy.safePid(producer.health, producer.snapshot, true, false, false);
        check(raw == ("1.7.1".equals(DaemonIdentity.RUNTIME_ID) ? -1 : 12000), "raw policy unchanged");
        check(LiveHandoverPolicy.safePidForRetainedProcess(producer.health, producer.snapshot,
                true, false, false, 0, 13000) == -1, "kernel PID must match I.pid");
        rootCase("stable", true, 1);
        rootCase("dynamic-age", true, 1);
        for (String top : new String[]{"top-stable", "top-off-ok", "top-cancelled", "top-guard"}) rootCase(top, true, 1);
        for (String denied : new String[]{"top-pending", "top-rescheduled", "top-running", "top-retry",
                "top-unknown-repair", "top-busy", "top-missing-repair", "top-duplicate-repair",
                "top-missing-generation", "top-duplicate-generation", "top-to-both", "both-to-top",
                "top-invalid-generation", "top-overflow-generation", "top-noncanonical-generation",
                "top-missing-status", "top-duplicate-status", "top-aba", "top-second-pending",
                "top-closed", "top-dock", "top-guard-unknown-dock", "top-socket-uid", "top-claimed-pid"}) rootCase(denied, false, 0);
        rootCase("top-no-effect", false, 1);
        for (String fault : new String[]{"control-uid", "socket-uid", "changed-peer", "claimed-pid",
                "duplicate-I", "duplicate-Q", "duplicate-runtime", "wrong-runtime", "empty-runtime",
                "version-change", "missing-Q", "held", "cpu", "lid", "dock", "second-mode",
                "unsupported", "dead-before-bind", "dead-after-bind", "dead-before-signal",
                "stale-commit", "cancel"}) rootCase(fault, false, 0);
        if (!"1.7.1".equals(DaemonIdentity.RUNTIME_ID)) rootCase("missing-runtime", false, 0);
        rootCase("no-effect", false, 1);
        appCase("stable", true);
        for (String fault : new String[]{"helper-uid", "socket-uid", "changed-peer", "stale-hello",
                "prepared-pid", "missing-receipt", "oversized-receipt", "malformed-receipt",
                "stale-receipt", "cancel-receipt", "bridge-rejected", "cleanup"}) {
            appCase(fault, false);
        }
        System.out.println("PASS actual LiveRetirement + actual published " + DaemonIdentity.RUNTIME_ID
                + " producer: " + cases + " cases; native/platform effects INERT");
    }
}
