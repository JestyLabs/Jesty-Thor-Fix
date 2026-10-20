package android.util;

/** Test completion signal only; no Android logging or trace export. */
public final class Log {
    public static int e(String tag, String message) {
        com.thor.displaypowertest.ResumeControllerHostTest.observeLog(message);
        return 0;
    }
    public static int e(String tag, String message, Throwable error) { return e(tag, message); }
}
