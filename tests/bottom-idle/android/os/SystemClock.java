package android.os;

/** Deterministic elapsed clock, fixture only; never packaged. */
public final class SystemClock {
    private static volatile long now = 100000L;
    private SystemClock() {}
    public static long elapsedRealtime() { return now; }
    public static void setElapsedRealtime(long value) { now = value; }
}
