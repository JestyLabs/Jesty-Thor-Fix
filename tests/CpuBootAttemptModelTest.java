import com.thor.displaypowertest.CpuBootAttemptModel;
import com.thor.displaypowertest.CpuBootAttemptModel.Action;
import com.thor.displaypowertest.CpuBootAttemptModel.Attempt;
import com.thor.displaypowertest.CpuBootAttemptModel.Phase;

public final class CpuBootAttemptModelTest {
    private static final String BOOT = "11111111-2222-3333-4444-555555555555";
    private static final String OLD_BOOT = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static Attempt a(Phase p, String desired, String previous, String pid, long at) {
        return new Attempt(BOOT, desired, previous, pid, p, at);
    }

    public static void main(String[] args) {
        eq(CpuBootAttemptModel.decide(null, BOOT, "1", "UNSET", "100", 1000),
                Action.START_NEW, "cold boot enable starts one attempt");
        eq(CpuBootAttemptModel.decide(null, BOOT, "1", "0", "100", 1000),
                Action.START_NEW, "mismatch enable starts one attempt");
        eq(CpuBootAttemptModel.decide(null, BOOT, "0", "1", "100", 1000),
                Action.START_NEW, "disable mismatch starts one attempt");
        eq(CpuBootAttemptModel.decide(null, BOOT, "0", "UNSET", "100", 1000),
                Action.FAIL_SAFE, "unset is not confirmed OFF");
        eq(CpuBootAttemptModel.decide(null, BOOT, "1", "1", "100", 1000),
                Action.PROCEED, "matching property preserves current behavior");

        Attempt prepared = a(Phase.PREPARED, "1", "UNSET", "100", 1000);
        eq(CpuBootAttemptModel.decide(prepared, BOOT, "1", "UNSET", "100", 1100),
                Action.WRITE_PROPERTY, "prepared before write resumes safely");
        eq(CpuBootAttemptModel.decide(prepared, BOOT, "1", "1", "100", 1100),
                Action.CONFIRM_PROPERTY, "crash after setprop but before phase write is recoverable");
        eq(CpuBootAttemptModel.decide(prepared, BOOT, "1", "1", "200", 1100),
                Action.FAIL_SAFE, "unproven composer change during PREPARED fails safe");

        Attempt verified = a(Phase.PROPERTY_VERIFIED, "1", "UNSET", "100", 1200);
        eq(CpuBootAttemptModel.decide(verified, BOOT, "1", "1", "100", 1300),
                Action.REQUEST_RESTART, "verified property may request the one restart");
        eq(CpuBootAttemptModel.decide(verified, BOOT, "1", "1", "200", 1300),
                Action.MARK_APPLIED, "external/new composer after verified write is sufficient");

        Attempt requested = a(Phase.RESTART_REQUESTED, "1", "UNSET", "100", 2000);
        eq(CpuBootAttemptModel.decide(requested, BOOT, "1", "1", "100", 2100),
                Action.WAIT_FOR_RESTART, "never issue a second restart while outcome is pending");
        eq(CpuBootAttemptModel.decide(requested, BOOT, "1", "1", "200", 2100),
                Action.MARK_APPLIED, "new composer proves handoff");
        eq(CpuBootAttemptModel.decide(requested, BOOT, "1", "1", "100",
                        2000 + CpuBootAttemptModel.RESTART_VERIFY_TIMEOUT_MS),
                Action.FAIL_SAFE, "stalled restart fails safe without retry");

        Attempt applied = a(Phase.APPLIED, "1", "UNSET", "100", 3000);
        eq(CpuBootAttemptModel.decide(applied, BOOT, "1", "1", "200", 3100),
                Action.PROCEED, "applied marker and successor composer proceed");
        eq(CpuBootAttemptModel.decide(applied, BOOT, "1", "1", "100", 3100),
                Action.FAIL_SAFE, "APPLIED cannot point at the baseline composer");
        eq(CpuBootAttemptModel.decide(applied, BOOT, "0", "1", "200", 3200),
                Action.START_NEW, "completed attempt may start a new explicit desired state");
        eq(CpuBootAttemptModel.decide(applied, BOOT, "1", "0", "200", 3250),
                Action.START_NEW, "completed attempt repairs late property drift with a fresh restart");
        eq(CpuBootAttemptModel.decide(applied, BOOT, "0", "1", "100", 3300),
                Action.FAIL_SAFE, "completed marker still requires the proven successor composer");
        eq(CpuBootAttemptModel.decide(requested, BOOT, "0", "1", "100", 2200),
                Action.FAIL_SAFE, "in-flight attempt suppresses a second desired restart");

        Attempt stale = new Attempt(OLD_BOOT, "1", "UNSET", "100", Phase.RESTART_REQUESTED, 1);
        eq(CpuBootAttemptModel.decide(stale, BOOT, "1", "1", "100", 3100),
                Action.DELETE_STALE, "old boot marker may be discarded");

        Attempt failed = a(Phase.FAILED, "1", "UNSET", "100", 3000);
        eq(CpuBootAttemptModel.decide(failed, BOOT, "1", "1", "100", 3100),
                Action.FAIL_SAFE, "failed attempt suppresses all retries in this boot");

        String encoded = requested.encode();
        Attempt decoded = Attempt.decode(encoded);
        eq(decoded.encode(), encoded, "marker format round-trips exactly");
        boolean corruptRejected = false;
        try { Attempt.decode("v1|bad|1|UNSET|100|RESTART_REQUESTED|2000"); }
        catch (IllegalArgumentException expected) { corruptRejected = true; }
        if (!corruptRejected) throw new AssertionError("corrupt marker must be rejected");

        System.out.println("CPU boot attempt model tests passed");
    }
}
