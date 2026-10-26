import com.thor.displaypowertest.RetirementFixtureHardware;
import com.thor.displaypowertest.DaemonIdentity;

/** Inert old/successor entry preserving the product's exact D argv image guard. */
public final class D {
    private D() {}

    public static void main(String[] args) throws Exception {
        boolean old = "1.7.1".equals(DaemonIdentity.RUNTIME_ID)
                || "1.7.2".equals(DaemonIdentity.RUNTIME_ID)
                || "1.7.3".equals(DaemonIdentity.RUNTIME_ID)
                || "1.7.4".equals(DaemonIdentity.RUNTIME_ID)
                || "1.7.5".equals(DaemonIdentity.RUNTIME_ID);
        String phase = old ? "run" : "hold";
        if (args.length != 4 || !"1".equals(args[0]) || !phase.equals(args[1])
                || !"0".equals(args[2]) || !"0".equals(args[3])) {
            throw new IllegalArgumentException("Fixture requires D 1 run/hold 0 0 with exact pinned identity");
        }
        RetirementFixtureHardware.serve(old);
    }
}
