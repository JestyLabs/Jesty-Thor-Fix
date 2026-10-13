package com.thor.displaypowertest;

/** Host-only characterization of the original BootCoordinator trace fields. */
public final class BootTraceFieldPolicyTest {
    private static int cases;
    public static void main(String[] args) {
        eq(BootTraceFieldPolicy.token(null), "?", "null watcher");
        eq(BootTraceFieldPolicy.token(""), "?", "empty watcher");
        eq(BootTraceFieldPolicy.token("RUNNING"), "RUNNING", "known watcher");
        eq(BootTraceFieldPolicy.token("UNSET"), "UNSET", "known property");
        eq(BootTraceFieldPolicy.token("?"), "?", "unknown token");
        eq(BootTraceFieldPolicy.token(repeat("A", 40)), repeat("A", 40), "exact token limit");
        eq(BootTraceFieldPolicy.token(repeat("A", 41)), "?", "long token");
        eq(BootTraceFieldPolicy.token("value;boot_id=bad"), "?", "no delimiter injection");
        eq(BootTraceFieldPolicy.token("a"), "?", "case is intentional");
        eq(BootTraceFieldPolicy.token("A\nINJECT"), "?", "no newline injection");

        eq(BootTraceFieldPolicy.pid(null), "?", "no PID");
        eq(BootTraceFieldPolicy.pid("0"), "?", "zero PID");
        eq(BootTraceFieldPolicy.pid("1"), "1", "positive PID");
        eq(BootTraceFieldPolicy.pid("1234567890"), "1234567890", "10-digit PID permitted");
        eq(BootTraceFieldPolicy.pid("12345678901"), "?", "11-digit PID rejected");
        eq(BootTraceFieldPolicy.pid("-7"), "?", "negative PID rejected");
        eq(BootTraceFieldPolicy.pid("01"), "?", "leading zero rejected");
        eq(BootTraceFieldPolicy.pid("42\nX"), "?", "PID injection rejected");

        eq(BootTraceFieldPolicy.result(null), "?", "missing result");
        eq(BootTraceFieldPolicy.result("ok=1;composer_restart=scheduled_once"),
                "ok=1;composer_restart=scheduled_once", "known result");
        eq(BootTraceFieldPolicy.result(""), "?", "empty result");
        eq(BootTraceFieldPolicy.result(repeat("A", 160)), repeat("A", 160), "max result length");
        eq(BootTraceFieldPolicy.result(repeat("A", 161)), "?", "oversized result");
        eq(BootTraceFieldPolicy.result("ok=1\nother=bad"), "?", "result newline rejected");
        eq(BootTraceFieldPolicy.result("ok=1;reason=hello world"), "?", "whitespace rejected");

        eq(BootTraceFieldPolicy.candidate(null), "-", "no candidate");
        eq(BootTraceFieldPolicy.candidate(""), "-", "empty candidate");
        eq(BootTraceFieldPolicy.candidate("0:0:0"), "0:0:0", "valid BOTH candidate");
        eq(BootTraceFieldPolicy.candidate("1:1:0"), "1:1:0", "valid TOP candidate");
        eq(BootTraceFieldPolicy.candidate("2:1:1"), "2:1:1", "valid unknown mode candidate");
        eq(BootTraceFieldPolicy.candidate("3:1:1"), "-", "invalid mode");
        eq(BootTraceFieldPolicy.candidate("1:2:0"), "-", "invalid CRTC");
        eq(BootTraceFieldPolicy.candidate("1:1:0;evil"), "-", "candidate injection rejected");

        System.out.println("BootTraceFieldPolicyTest passed (" + cases + " assertions)");
    }
    private static String repeat(String value, int count) {
        StringBuilder out = new StringBuilder(count);
        for (int i = 0; i < count; i++) out.append(value);
        return out.toString();
    }
    private static void eq(String actual, String expected, String description) {
        cases++;
        if (!expected.equals(actual)) throw new AssertionError(description + " expected '"
                + expected + "' but got '" + actual + "'");
    }
}
