public final class RefreshRateEvidenceModelTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(RefreshRateEvidenceModel.classify(120.0, 120.0, Double.NaN)
                        == RefreshRateEvidenceModel.Verdict.LOGICAL_SHARED_120_PHYSICAL_UNKNOWN,
                "shared logical 120 without physical timing must stay unproven");

        check(RefreshRateEvidenceModel.classify(120.00001, 120.0, 60.0)
                        == RefreshRateEvidenceModel.Verdict.LOGICAL_120_PHYSICAL_60,
                "logical 120 over physical 60 must be explicit");

        check(RefreshRateEvidenceModel.classify(120.0, 60.000004, Double.NaN)
                        == RefreshRateEvidenceModel.Verdict.MIXED_LOGICAL_120_60,
                "mixed logical modes must be recognized");

        check(RefreshRateEvidenceModel.classify(120.0, 59.94, 60.0)
                        == RefreshRateEvidenceModel.Verdict.BOTTOM_60_CONSISTENT,
                "60-ish logical and physical lower rates must be consistent");

        check(RefreshRateEvidenceModel.classify(120.0, 120.0, 120.0)
                        == RefreshRateEvidenceModel.Verdict.BOTTOM_120_REPORTED_AT_BOTH_LAYERS,
                "120 at Android and kernel layers must not be called fake");

        check(RefreshRateEvidenceModel.classify(Double.NaN, 120.0, 60.0)
                        == RefreshRateEvidenceModel.Verdict.INSUFFICIENT,
                "missing top logical evidence must remain insufficient");

        check(RefreshRateEvidenceModel.classify(90.0, 90.0, 60.0)
                        == RefreshRateEvidenceModel.Verdict.OTHER,
                "unexpected rates must not be forced into a 60/120 theory");

        System.out.println("RefreshRateEvidenceModel tests passed.");
    }
}
