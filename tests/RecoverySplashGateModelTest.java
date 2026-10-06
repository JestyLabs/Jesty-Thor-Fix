import com.thor.displaypowertest.RecoverySplashGateModel;

public final class RecoverySplashGateModelTest {
    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    public static void main(String[] args) {
        eq(RecoverySplashGateModel.decide(false, true, true, true),
                RecoverySplashGateModel.Decision.NOT_BOOT_SCOPED,
                "runtime toggles must never arm the splash");
        eq(RecoverySplashGateModel.decide(true, false, true, true),
                RecoverySplashGateModel.Decision.BOOTANIM_SUPPRESSION_INACTIVE,
                "visible-second-animation fallback must stay untouched");
        eq(RecoverySplashGateModel.decide(true, true, false, true),
                RecoverySplashGateModel.Decision.FLAG_DISABLED,
                "prototype is disabled by default");
        eq(RecoverySplashGateModel.decide(true, true, true, false),
                RecoverySplashGateModel.Decision.CLASSPATH_UNUSABLE,
                "unsafe APK path must fail open");
        eq(RecoverySplashGateModel.decide(true, true, true, true),
                RecoverySplashGateModel.Decision.ARMED,
                "all explicit prerequisites arm the prototype");
        System.out.println("Recovery splash gate model tests passed");
    }
}
