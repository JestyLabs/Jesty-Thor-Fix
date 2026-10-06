import com.thor.displaypowertest.CpuPinningEvidenceModel;

public final class CpuPinningEvidenceModelTest {
    private static int assertions;

    public static void main(String[] args) {
        qualifiesByElapsedTimeNotSampleCount();
        normalUsesIndependentEvidence();
        busyBreaksQualification();
        droppedCadenceStillUsesElapsedTime();
        counterResetFailsClosed();
        nonMonotonicTimeFailsClosed();
        System.out.println("CpuPinningEvidenceModelTest passed: " + assertions + " assertions");
    }

    private static CpuPinningEvidenceModel model() {
        return new CpuPinningEvidenceModel(3000L, 2000L, 0.85d, 25);
    }

    private static void qualifiesByElapsedTimeNotSampleCount() {
        CpuPinningEvidenceModel m = model();
        eq(CpuPinningEvidenceModel.State.CHECKING, sample(m, 0, 0, 0, 0, 0, 5).state);
        eq(CpuPinningEvidenceModel.State.CHECKING, sample(m, 500, 9, 10, 9, 10, 5).state);
        eq(CpuPinningEvidenceModel.State.CHECKING, sample(m, 1500, 27, 30, 27, 30, 5).state);
        CpuPinningEvidenceModel.Result r = sample(m, 3000, 54, 60, 54, 60, 5);
        eq(CpuPinningEvidenceModel.State.PINNED, r.state);
        truth(r.pinnedEvidenceMs == 3000L);
    }

    private static void normalUsesIndependentEvidence() {
        CpuPinningEvidenceModel m = model();
        sample(m, 0, 0, 0, 0, 0, 5);
        sample(m, 1000, 9, 10, 9, 10, 5);
        CpuPinningEvidenceModel.Result r = sample(m, 2000, 10, 20, 10, 20, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, r.state);
        truth(r.pinnedEvidenceMs == 0L);
        r = sample(m, 3000, 11, 30, 11, 30, 5);
        eq(CpuPinningEvidenceModel.State.NORMAL, r.state);
    }

    private static void busyBreaksQualification() {
        CpuPinningEvidenceModel m = model();
        sample(m, 0, 0, 0, 0, 0, 5);
        sample(m, 2000, 18, 20, 18, 20, 5);
        CpuPinningEvidenceModel.Result busy = sample(m, 2500, 23, 25, 23, 25, 60);
        eq(CpuPinningEvidenceModel.State.BUSY, busy.state);
        truth(busy.pinnedEvidenceMs == 0L);
        CpuPinningEvidenceModel.Result after = sample(m, 5000, 45, 50, 45, 50, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, after.state);
    }

    private static void droppedCadenceStillUsesElapsedTime() {
        CpuPinningEvidenceModel m = model();
        sample(m, 100, 0, 0, 0, 0, 5);
        CpuPinningEvidenceModel.Result r = sample(m, 4100, 90, 100, 90, 100, 5);
        eq(CpuPinningEvidenceModel.State.PINNED, r.state);
        truth(r.pinnedEvidenceMs == 4000L);
    }

    private static void counterResetFailsClosed() {
        CpuPinningEvidenceModel m = model();
        sample(m, 0, 100, 100, 100, 100, 5);
        sample(m, 2000, 118, 120, 118, 120, 5);
        CpuPinningEvidenceModel.Result reset = sample(m, 2500, 1, 2, 1, 2, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, reset.state);
        truth(reset.pinnedEvidenceMs == 0L);
    }

    private static void nonMonotonicTimeFailsClosed() {
        CpuPinningEvidenceModel m = model();
        sample(m, 1000, 0, 0, 0, 0, 5);
        CpuPinningEvidenceModel.Result r = sample(m, 900, 9, 10, 9, 10, 5);
        eq(CpuPinningEvidenceModel.State.CHECKING, r.state);
    }

    private static CpuPinningEvidenceModel.Result sample(CpuPinningEvidenceModel m,
            long at, long lh, long lt, long bh, long bt, int util) {
        return m.observe(at, lh, lt, bh, bt, util);
    }

    private static void eq(Object expected, Object actual) {
        assertions++;
        if (!expected.equals(actual)) throw new AssertionError(expected + " != " + actual);
    }

    private static void truth(boolean value) {
        assertions++;
        if (!value) throw new AssertionError("expected true");
    }
}
