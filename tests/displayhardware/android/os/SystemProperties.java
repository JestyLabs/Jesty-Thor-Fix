package android.os;

import java.util.ArrayList;
import java.util.List;

/** Synthetic property store; no Android properties or device access. */
public final class SystemProperties {
    public static String value;
    public static boolean readFails;
    public static int failWrite;
    public static final List<String> attempts = new ArrayList<String>();
    public static final List<String> writes = new ArrayList<String>();
    public static String get(String key) {
        key(key);
        if (readFails) throw new IllegalStateException("synthetic property read failure");
        return value;
    }
    public static void set(String key, String next) {
        key(key);
        attempts.add(next);
        if (attempts.size() == failWrite)
            throw new IllegalStateException("synthetic property write failure");
        writes.add(next);
        value = next;
    }
    private static void key(String key) {
        if (!"display.power.state".equals(key)) throw new AssertionError("unexpected key");
    }
}
