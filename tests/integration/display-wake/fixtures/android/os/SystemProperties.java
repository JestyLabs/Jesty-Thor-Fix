package android.os;
import com.thor.displaypowertest.TestDisplayState;
public final class SystemProperties {
    public static String get(String key) { return TestDisplayState.property; }
    public static void set(String key, String value) { TestDisplayState.property = value; TestDisplayState.propertyWrites++; }
}
