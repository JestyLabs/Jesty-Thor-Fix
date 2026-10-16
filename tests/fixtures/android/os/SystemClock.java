package android.os;

/**
 * Host-only test clock. Deliberately never packaged into the Android APK.
 * The real app uses Android SystemClock.elapsedRealtime().
 */
public final class SystemClock {
    private static long now;
    private SystemClock() {}

    public static long elapsedRealtime() {
        return now;
    }

    public static void setElapsedRealtimeForTest(long value) {
        now = value;
    }
}
