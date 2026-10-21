package android.os;

/** Host fixture only: no OS thread; owns one manually pumped Looper. */
public class HandlerThread {
    public static int started;
    private final String name;
    private Looper looper;

    public HandlerThread(String name) { this.name = name; }

    public synchronized void start() {
        if (looper != null) throw new IllegalStateException("already started: " + name);
        looper = new Looper();
        started++;
    }

    public synchronized Looper getLooper() { return looper; }

    public synchronized boolean quitSafely() {
        if (looper == null) return false;
        looper.quitSafely();
        return true;
    }
}
