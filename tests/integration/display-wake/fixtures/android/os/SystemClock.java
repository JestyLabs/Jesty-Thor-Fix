package android.os;
public final class SystemClock {
    public static long now = 100000L;
    /** Synthetic stall boundary: 0 keeps the original frozen clock used by existing tests. */
    public static long autoAdvanceMs;
    public static long elapsedRealtime() { long value = now; now += autoAdvanceMs; return value; }
}
