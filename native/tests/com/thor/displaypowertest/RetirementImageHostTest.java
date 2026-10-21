package com.thor.displaypowertest;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/** Actual native argv parser; inert bytes do not prove Linux /proc enforcement. */
public final class RetirementImageHostTest {
    private static native boolean allowed(byte[] cmdline);

    private static byte[] argv(String... arguments) {
        return (String.join("\u0000", arguments) + "\u0000").getBytes(StandardCharsets.US_ASCII);
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) throws Exception {
        RetirementNative.load();
        byte[] held = argv("app_process", "/", "D", "1", "hold", "0", "1");
        check(allowed(held), "Exact held D argv must match existing guard");
        check(allowed(argv("app_process", "/", "D", "0", "run", "1", "0")),
                "Exact running D argv must match existing guard");
        check(allowed(argv("app_process", "/", "D", "1", "hold", "0", "1", "extra")),
                "Preserve existing prefix semantics");
        check(!allowed(argv("app_process", "/", "R", "1", "hold", "0", "1")),
                "Root helper is not daemon D");
        check(!allowed(argv("/system/bin/app_process", "/", "D", "1", "hold", "0", "1")),
                "Do not broaden the existing app_process argv0 guard");
        check(!allowed(argv("app_process64", "/", "D", "1", "hold", "0", "1")),
                "Do not broaden argv0");
        check(!allowed(argv("app_process", "/", "D", "2", "hold", "0", "1")),
                "Nonbinary flag rejected");
        check(!allowed(argv("app_process", "/", "D", "1", "unknown", "0", "1")),
                "Unknown mode rejected");
        check(!allowed(argv("app_process", "/", "D", "1", "hold", "0")),
                "Missing flag rejected");
        check(!allowed(argv("app_process / D 1 hold 0 1")), "Require real argv boundaries");
        check(!allowed(Arrays.copyOf(held, held.length - 1)), "Truncated final argv rejected");
        check(!allowed(new byte[4097]), "Oversized cmdline rejected");
        System.out.println("PASS actual native cmdline prefix parser (inert input, no /proc execution)");
    }
}
