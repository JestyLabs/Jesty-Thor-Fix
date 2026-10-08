import com.thor.displaypowertest.BridgeFailureDiagnostic;
import java.lang.reflect.InvocationTargetException;

public final class BridgeFailureDiagnosticTest {
    public static void main(String[] args) {
        check("UNKNOWN", BridgeFailureDiagnostic.failureReason(null));
        check("SECURITY_DENIED", BridgeFailureDiagnostic.failureReason(
                new SecurityException("never print me")));
        check("SECURITY_DENIED", BridgeFailureDiagnostic.failureReason(
                new InvocationTargetException(new SecurityException("private"))));
        check("REFLECTION_FAILED", BridgeFailureDiagnostic.failureReason(
                new NoSuchMethodException("private")));
        check("REFLECTION_FAILED", BridgeFailureDiagnostic.failureReason(
                new InvocationTargetException(new NoSuchMethodException("private"))));
        check("CLASS_LINKAGE_FAILED", BridgeFailureDiagnostic.failureReason(
                new NoClassDefFoundError("private")));
        check("CLASS_LINKAGE_FAILED", BridgeFailureDiagnostic.failureReason(
                new InvocationTargetException(new NoClassDefFoundError("private"))));
        check("RUNTIME_FAILURE", BridgeFailureDiagnostic.failureReason(
                new IllegalArgumentException("private")));
        check("RUNTIME_FAILURE", BridgeFailureDiagnostic.failureReason(
                new InvocationTargetException(new IllegalArgumentException("private"))));
        check("SECURITY_DENIED", BridgeFailureDiagnostic.failureReason(
                new InvocationTargetException(new InvocationTargetException(
                        new SecurityException("private")))));
        check("OTHER_FAILURE", BridgeFailureDiagnostic.failureReason(
                new AssertionError("private")));
        check("OTHER_FAILURE", BridgeFailureDiagnostic.failureReason(
                new InvocationTargetException(new AssertionError("private"))));
        System.out.println("BridgeFailureDiagnosticTest passed");
    }

    private static void check(String expected, String actual) {
        if (!expected.equals(actual)) {
            throw new AssertionError("Expected " + expected + ", got " + actual);
        }
        if (actual.contains("private")) {
            throw new AssertionError("Exception message leaked into diagnostic code");
        }
    }
}
