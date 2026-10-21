package com.thor.displaypowertest;

import android.net.LocalSocket;
import android.system.Os;
import java.nio.charset.StandardCharsets;

/** Real client + transport admission + ACK policy; native socket is inert. */
public final class SocketClientHostTest {
    private static int checks;
    private static int cases;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static LocalSocket.Plan plan(Integer uid, String response) {
        LocalSocket.reset();
        Os.reset();
        LocalSocket.Plan plan = new LocalSocket.Plan(uid);
        plan.response = response;
        LocalSocket.enqueue(plan);
        return plan;
    }
    private static void denied(String id, boolean beforeCommand) {
        boolean failed = false;
        try { SocketClient.request('Q', 432); }
        catch (Exception expected) { failed = true; }
        check(failed, id + " rejects success");
        check(LocalSocket.latest.closes == 1, id + " closes exactly once");
        if (beforeCommand) {
            check(LocalSocket.latest.writes == 0, id + " no command bytes");
            check(LocalSocket.latest.reads == 0, id + " no response read");
        }
        cases++;
        System.out.println("PASS " + id);
    }
    private static void untrusted() {
        plan(10042, "ok=1\n");
        denied("S3-client-untrusted", true);
    }
    private static void malformed() {
        plan(0, "ok=10;value=1\n");
        denied("S3-client-malformed-ack", false);
    }
    private static void accepted(String id, String wire, String expected) throws Exception {
        plan(0, wire);
        check(expected.equals(SocketClient.request('Q', 432)), id + " exact first line");
        LocalSocket socket = LocalSocket.latest;
        check(socket.timeout == 432, id + " timeout forwarded to real transport admission");
        check("Q".equals(new String(socket.sent.toByteArray(), StandardCharsets.UTF_8)), id + " one byte command");
        check(socket.flushes == 1, id + " command flushed");
        check(socket.closes == 1, id + " caller closed successful transport");
        cases++;
        System.out.println("PASS " + id);
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if ("peer".equals(args[0])) untrusted(); else malformed();
            return;
        }
        accepted("S3-client-valid-fragments", "ok=1;label=olá\nignored second line\n", "ok=1;label=olá");
        accepted("S3-client-valid-at-eof", "ok=1;field=known", "ok=1;field=known");
        untrusted();
        plan(null, "ok=1\n");
        denied("S3-client-missing-peer", true);
        malformed();
        plan(0, "");
        denied("S3-client-eof-without-ack", false);
        LocalSocket.Plan read = plan(0, "ok=1\n");
        read.readFailure = true;
        denied("S3-client-read-failure", false);
        check(LocalSocket.latest.flushes == 1, "S3-client-read command sent before simulated timeout");
        LocalSocket.Plan write = plan(0, "ok=1\n");
        write.writeFailure = true;
        denied("S3-client-write-failure", false);
        check(LocalSocket.latest.reads == 0, "S3-client-write no read after failure");
        System.out.println("SocketClientHostTest passed: " + cases + " cases, " + checks
                + " checks; kernel UID/timeouts/SELinux NOT VERIFIED");
    }
}
