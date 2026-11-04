package android.os;

/**
 * Host stand-in for the request-file watch: the test delivers the kernel's events itself.
 * The constants are Android's, which are inotify's. AOSP passes the raw inotify mask to
 * onEvent, including IN_IGNORED (0x8000) once a watch is removed.
 */
public abstract class FileObserver {
    public static final int MODIFY = 0x00000002;
    public static final int CLOSE_WRITE = 0x00000008;
    /** The last observer created, for the test to drive. */
    public static FileObserver last;
    public final String watchedPath;
    public final int mask;
    public boolean watching;

    public FileObserver(String path, int mask) {
        this.watchedPath = path;
        this.mask = mask;
        last = this;
    }

    public void startWatching() { watching = true; }
    public void stopWatching() { watching = false; }
    public abstract void onEvent(int event, String path);
}
