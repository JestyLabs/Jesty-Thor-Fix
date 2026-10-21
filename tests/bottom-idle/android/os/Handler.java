package android.os;

/** Host fixture only: posts to the manually pumped fixture Looper. */
public class Handler {
    private final Looper looper;

    public Handler(Looper looper) {
        if (looper == null) throw new IllegalArgumentException("looper");
        this.looper = looper;
    }

    public final Looper getLooper() { return looper; }

    public final boolean post(Runnable action) {
        return looper.enqueue(action, SystemClock.elapsedRealtime());
    }

    public final boolean postDelayed(Runnable action, long delayMs) {
        return looper.enqueue(action, SystemClock.elapsedRealtime() + Math.max(0L, delayMs));
    }

    public final void removeCallbacks(Runnable action) { looper.remove(action); }
}
