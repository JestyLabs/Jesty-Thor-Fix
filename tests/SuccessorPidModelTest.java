import com.thor.displaypowertest.SuccessorPidModel;
import com.thor.displaypowertest.SuccessorPidModel.Result;
import com.thor.displaypowertest.SuccessorPidModel.Status;

public final class SuccessorPidModelTest {
    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) throw new AssertionError(message + ": " + actual);
    }

    private static void waitingOnBaselineOnly() {
        Result r = SuccessorPidModel.resolve("2301", "2301");
        eq(r.status, Status.WAITING, "baseline alone is not a successor");
        eq(r.successorCount, 0, "baseline alone has no successor");
    }

    private static void resolvesOldAndNewTransitionSample() {
        Result r = SuccessorPidModel.resolve("2301", "2301 7081");
        eq(r.status, Status.FOUND, "old+new sample resolves exact successor");
        eq(r.successorPid, "7081", "new PID is selected");
        eq(r.successorCount, 1, "exactly one successor");
    }

    private static void resolvesNewOnlySample() {
        Result r = SuccessorPidModel.resolve("2301", "7081");
        eq(r.status, Status.FOUND, "new-only sample resolves");
        eq(r.successorPid, "7081", "new-only PID selected");
    }

    private static void duplicateSuccessorDoesNotBecomeAmbiguous() {
        Result r = SuccessorPidModel.resolve("2301", "2301 7081 7081");
        eq(r.status, Status.FOUND, "duplicate token is still one successor");
        eq(r.successorPid, "7081", "duplicate successor resolves identically");
    }

    private static void rejectsAmbiguousMultipleSuccessors() {
        Result r = SuccessorPidModel.resolve("2301", "7081 8123");
        eq(r.status, Status.AMBIGUOUS, "multiple non-baseline PIDs are ambiguous");
        eq(r.successorCount, 2, "ambiguous count retained");
    }

    private static void emptyIsStillWaiting() {
        eq(SuccessorPidModel.resolve("2301", "").status, Status.WAITING,
                "empty pidof result waits");
        eq(SuccessorPidModel.resolve("2301", "   ").status, Status.WAITING,
                "whitespace pidof result waits");
    }

    private static void malformedInputIsInvalid() {
        eq(SuccessorPidModel.resolve("2301", "garbage").status, Status.INVALID,
                "garbage observation is invalid");
        eq(SuccessorPidModel.resolve("2301", "0").status, Status.INVALID,
                "zero is not a process PID");
        eq(SuccessorPidModel.resolve("bad", "7081").status, Status.INVALID,
                "invalid baseline is rejected");
    }

    public static void main(String[] args) {
        waitingOnBaselineOnly();
        resolvesOldAndNewTransitionSample();
        resolvesNewOnlySample();
        duplicateSuccessorDoesNotBecomeAmbiguous();
        rejectsAmbiguousMultipleSuccessors();
        emptyIsStillWaiting();
        malformedInputIsInvalid();
        System.out.println("SuccessorPidModel tests passed");
    }
}
