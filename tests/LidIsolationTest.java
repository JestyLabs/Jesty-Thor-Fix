import com.thor.displaypowertest.LidGuard;
import com.thor.displaypowertest.LidGuardModel;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/** JVM-only compatibility and OFF-path characterization (not a hardware power measurement). */
public final class LidIsolationTest {
    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void method(String name, Class<?> returnType, Class<?>... args)
            throws Exception {
        java.lang.reflect.Method m = LidGuard.class.getDeclaredMethod(name, args);
        check(m.getReturnType() == returnType && Modifier.isStatic(m.getModifiers()),
                "Changed facade ABI: " + name);
    }

    public static void main(String[] args) throws Exception {
        method("setEnabled", boolean.class, boolean.class);
        method("isEnabled", boolean.class);
        method("lid", String.class);
        method("state", String.class);
        method("blockedWakes", int.class);
        method("lastAction", String.class);
        method("externalDisplay", String.class);
        method("onWakeEvent", boolean.class);
        method("externalDisplayCached", String.class);

        LidGuard.setEnabled(false);
        check(!LidGuard.isEnabled(), "Guard must be OFF");
        check("OFF".equals(LidGuard.state()), "OFF state unchanged");
        check(!LidGuard.onWakeEvent(), "OFF must never defer normal wake repair");

        Class<?> runtime = Class.forName("com.thor.displaypowertest.LidProtectionRuntime");
        Field last = runtime.getDeclaredField("externalCheckedAt");
        Field pending = runtime.getDeclaredField("externalCheckPending");
        last.setAccessible(true);
        pending.setAccessible(true);
        check(last.getLong(null) == 0L && !pending.getBoolean(null),
                "No external probe expected before Q");
        for (int i = 0; i < 100; i++) {
            check("unknown".equals(LidGuard.externalDisplay()),
                    "OFF retains external_display key with unknown cached value");
        }
        Thread.sleep(50L);
        check(last.getLong(null) == 0L && !pending.getBoolean(null),
                "OFF telemetry must not spawn display probe");
        check("unknown".equals(LidGuard.lid()), "OFF does not invent Hall state");
        check(LidGuard.blockedWakes() == 0, "OFF must not count a blocked wake");

        LidGuardModel model = new LidGuardModel();
        check(!model.maySchedule(), "UNKNOWN lid must fail safe");
        model.onSwitch(1, 0L);
        check(model.maySchedule(), "Enabled-policy model still handles closed lid");
        model.onSwitch(0, 100L);
        check(!model.maySchedule(), "Enabled-policy model still handles open lid");
        System.out.println("LidIsolationTest PASS");
    }
}
