package android.util;
/** Synthetic log boundary. Captures debug lines for host assertions; never exported. */
public final class Log {
    public static final java.util.List<String> LINES = new java.util.ArrayList<String>();
    public static int d(String tag, String message) { LINES.add(tag + ": " + message); return 0; }
    public static int e(String tag, String message, Throwable error) { return 0; }
}
