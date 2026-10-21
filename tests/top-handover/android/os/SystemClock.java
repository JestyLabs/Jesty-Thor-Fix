package android.os;
/** Controlled monotonic input. Product waits and grace constants are unchanged. */
public final class SystemClock {
    private static long now = 100000L;
    public static long elapsedRealtime() { long value = now; now += 250L; return value; }
}
