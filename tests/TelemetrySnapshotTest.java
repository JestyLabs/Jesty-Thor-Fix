import android.os.SystemProperties;
import com.thor.displaypowertest.PropertyState;
import com.thor.displaypowertest.Telemetry;

/**
 * Host-only characterization of Q system_load_fix via Android SystemProperties.
 * Does not validate vendor properties or CPU savings on a real AYN Thor.
 */
public final class TelemetrySnapshotTest {
    private static void check(String observed, String expected) {
        int calls = SystemProperties.calls;
        SystemProperties.observed = observed;
        String result = Telemetry.systemLoadFixStateForSnapshot();
        if (!expected.equals(result)) {
            throw new AssertionError("observed=" + observed + " expected="
                    + expected + " actual=" + result);
        }
        if (SystemProperties.calls != calls + 1) {
            throw new AssertionError("Snapshot should query the property exactly once");
        }
        if (!PropertyState.binary(observed, 0).equals(result)) {
            throw new AssertionError("Snapshot changed existing binary property semantics");
        }
    }

    public static void main(String[] args) {
        check("0", "0");
        check("1", "1");
        check("__JESTY_PROPERTY_MISSING__", "?");
        check("", "?");
        check("unexpected", "?");
        check(" 1 ", "1");
        System.out.println("TelemetrySnapshotTest passed: 6 direct-property cases");
    }
}
