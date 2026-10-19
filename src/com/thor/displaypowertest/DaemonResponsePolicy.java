package com.thor.displaypowertest;

/** Validates the complete acknowledgement token on our trusted local daemon channel. */
public final class DaemonResponsePolicy {
    private DaemonResponsePolicy() {}

    /** Accept only `ok=1` or `ok=1;...`, not malformed prefixes like `ok=10`. */
    public static boolean isPositiveAck(String response) {
        return "ok=1".equals(response) || (response != null && response.startsWith("ok=1;"));
    }
}
