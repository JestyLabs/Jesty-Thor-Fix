package android.os;

/** Host-only fake: never compile into app src or APK. */
public final class SystemProperties {
    public static String observed = "__JESTY_PROPERTY_MISSING__";
    public static int calls;
    private SystemProperties() {}
    public static String get(String key, String defaultValue) {
        if (!"vendor.display.disable_system_load_check".equals(key))
            throw new AssertionError("Unexpected property key: " + key);
        calls++;
        return observed == null ? defaultValue : observed;
    }
}
