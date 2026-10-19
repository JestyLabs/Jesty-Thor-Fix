package com.thor.displaypowertest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Actual native request method, with inert syscalls: no sockets or process operations. */
public final class RetirementRequestHostTest {
    private static native void setup(byte[] reply, int sendDelayMs, int readDelayMs);
    private static native byte[] written();
    private static native int unread();

    private static byte[] ascii(String value) {
        return value.getBytes(StandardCharsets.US_ASCII);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static void denied(String message, byte[] reply) throws IOException {
        setup(reply, 0, 0);
        try {
            RetirementNative.request(42, 'I', 1000);
            throw new AssertionError("Expected native reply guard: " + message);
        } catch (IOException expected) {
            check(expected.getMessage().contains(message), "Wrong native reply guard: " + expected);
        }
    }

    public static void main(String[] args) throws Exception {
        RetirementNative.load();
        setup(ascii("identity\nnext-frame\n"), 0, 0);
        check("identity".equals(RetirementNative.request(42, 'I', 1000)), "Real I reply");
        check(Arrays.equals(new byte[] {'I'}, written()), "I must be exactly one byte without LF");
        check(unread() == ascii("next-frame\n").length, "No read-ahead into next frame");

        setup(ascii("snapshot\n"), 0, 0);
        check("snapshot".equals(RetirementNative.request(42, 'Q', 1000)), "Real Q reply");
        check(Arrays.equals(new byte[] {'Q'}, written()), "Q must be exactly one byte without LF");

        String maximum = "x".repeat(16384);
        setup(ascii(maximum + "\n"), 0, 0);
        check(maximum.equals(RetirementNative.request(42, 'I', 1000)), "16384-byte reply accepted");
        denied("Invalid or oversized line", ascii(maximum + "x\n"));
        denied("Invalid or oversized line", new byte[] {'x', 0, '\n'});
        denied("Invalid or oversized line", new byte[] {(byte) 0x80, '\n'});
        denied("EOF before complete line", ascii("partial"));

        setup(ascii("late\n"), 6, 6);
        try {
            RetirementNative.request(42, 'I', 10);
            throw new AssertionError("Request must share one deadline across send and reply");
        } catch (IOException expected) {
            check(expected.getMessage().contains("timed out"), "Shared deadline guard: " + expected);
        }
        check(Arrays.equals(new byte[] {'I'}, written()), "Timeout cannot resend request");
        System.out.println("PASS actual native request: one-byte I/Q, bounded LF, no read-ahead, shared deadline (inert OS)");
    }
}
