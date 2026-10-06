import com.thor.displaypowertest.PreComposerCpuProofModel;
import com.thor.displaypowertest.PreComposerCpuProofModel.Proof;
import com.thor.displaypowertest.PreComposerCpuProofModel.Result;

public final class PreComposerCpuProofModelTest {
    private static final String BOOT = "11111111-2222-3333-4444-555555555555";
    private static final String OLD_BOOT = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";

    private static void eq(Object actual, Object expected, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + ": " + actual);
        }
    }

    private static Proof proof(String boot, long writeAt,
            boolean absentBefore, boolean absentAfter, boolean verified) {
        return new Proof(boot, "1", writeAt, absentBefore, absentAfter, verified);
    }

    public static void main(String[] args) {
        Proof good = proof(BOOT, 3700L, true, true, true);

        eq(PreComposerCpuProofModel.evaluate(
                        good, BOOT, true, "1", 4074L),
                Result.PROVEN,
                "write before first composer with verified readback is proven");

        eq(PreComposerCpuProofModel.evaluate(
                        null, BOOT, true, "1", 4074L),
                Result.ABSENT,
                "matching property alone is never pre-composer proof");

        eq(PreComposerCpuProofModel.evaluate(
                        good, OLD_BOOT, true, "1", 4074L),
                Result.WRONG_BOOT,
                "proof is boot scoped");

        eq(PreComposerCpuProofModel.evaluate(
                        good, BOOT, false, "1", 4074L),
                Result.WRONG_DESIRED,
                "pre-composer path is enable-only");

        eq(PreComposerCpuProofModel.evaluate(
                        good, BOOT, true, "0", 4074L),
                Result.PROPERTY_MISMATCH,
                "later property mismatch invalidates proof");

        eq(PreComposerCpuProofModel.evaluate(
                        proof(BOOT, 3700L, false, true, true),
                        BOOT, true, "1", 4074L),
                Result.COMPOSER_PRESENT_DURING_WRITE,
                "composer must be absent before write");

        eq(PreComposerCpuProofModel.evaluate(
                        proof(BOOT, 3700L, true, false, true),
                        BOOT, true, "1", 4074L),
                Result.COMPOSER_PRESENT_DURING_WRITE,
                "composer must remain absent after verified write");

        eq(PreComposerCpuProofModel.evaluate(
                        proof(BOOT, 3700L, true, true, false),
                        BOOT, true, "1", 4074L),
                Result.PROPERTY_MISMATCH,
                "unverified setprop is not proof");

        eq(PreComposerCpuProofModel.evaluate(
                        proof(BOOT, 4074L, true, true, true),
                        BOOT, true, "1", 4074L),
                Result.WRITE_NOT_BEFORE_COMPOSER,
                "equal timestamp is not before composer");

        eq(PreComposerCpuProofModel.evaluate(
                        proof(BOOT, 4100L, true, true, true),
                        BOOT, true, "1", 4074L),
                Result.WRITE_NOT_BEFORE_COMPOSER,
                "late write is rejected");

        String encoded = good.encode();
        eq(Proof.decode(encoded).encode(), encoded,
                "proof format round-trips exactly");

        boolean badDesired = false;
        try {
            new Proof(BOOT, "0", 1L, true, true, true);
        } catch (IllegalArgumentException expected) {
            badDesired = true;
        }
        if (!badDesired) {
            throw new AssertionError("proof must never authorize an early OFF write");
        }

        boolean corruptRejected = false;
        try {
            Proof.decode("v1|" + BOOT + "|1|x|1|1|1");
        } catch (IllegalArgumentException expected) {
            corruptRejected = true;
        }
        if (!corruptRejected) {
            throw new AssertionError("corrupt proof must be rejected");
        }

        System.out.println("Pre-composer CPU proof model tests passed");
    }
}
