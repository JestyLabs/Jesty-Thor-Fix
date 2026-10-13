package android.util;
import java.util.concurrent.atomic.AtomicInteger;
public final class Log {
    public static final AtomicInteger warnings = new AtomicInteger();
    public static final AtomicInteger errors = new AtomicInteger();
    private Log() {}
    public static int w(String tag, String msg) { warnings.incrementAndGet(); return 0; }
    public static int e(String tag, String msg, Throwable error) { errors.incrementAndGet(); return 0; }
}
