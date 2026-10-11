import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Arrays;
import com.thor.displaypowertest.Telemetry;
import com.thor.displaypowertest.DisplayDecisionModel;

/** Paired DRM parser and safety-gate regression tests (host, synthetic). */
public final class DisplayOptimizationSafetyTest {
    private static String[] parse(String s) throws Exception {
        return Telemetry.parseCrtcPair(new BufferedReader(new StringReader(s)));
    }
    private static void check(String[] expected, String state) throws Exception {
        String[] actual = parse(state);
        if (!Arrays.equals(expected, actual)) throw new AssertionError(Arrays.toString(actual));
    }
    public static void main(String[] args) throws Exception {
        check(new String[]{"1", "0"},
                "crtc[181]\nfoo\nactive=1\ncrtc[243]\nfoo\nactive=0\n");
        check(new String[]{"1", "1"},
                "crtc[243]\nactive=1\ncrtc[181]\nactive=1\n");
        check(new String[]{"?", "1"},
                "crtc[181]\nactive=broken\ncrtc[243]\nactive=1\n");
        check(new String[]{"?", "?"}, "invalid data\n");
        String[] unknown = parse("crtc[181]\nactive=broken\ncrtc[243]\nactive=1\n");
        if (DisplayDecisionModel.readyForReconcile("1", unknown[0], unknown[1]))
            throw new AssertionError("Must fail closed on unknown top CRTC");
        if (DisplayDecisionModel.reconcile("1", unknown[0], unknown[1], true, false, false)
                != DisplayDecisionModel.Action.NONE)
            throw new AssertionError("Must not command transition on unknown CRTC");
        System.out.println("DisplayOptimizationSafetyTest passed: 6 cases");
    }
}
