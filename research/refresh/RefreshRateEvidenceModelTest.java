public final class RefreshRateEvidenceModelTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(RefreshRateEvidenceModel.classify(120.0, 120.0, Double.NaN)
                        == RefreshRateEvidenceModel.Verdict.LOGICAL_SHARED_120_DRM_UNKNOWN,
                "shared logical 120 without DRM timing must stay unproven");

        check(RefreshRateEvidenceModel.classify(120.00001, 120.0, 60.0)
                        == RefreshRateEvidenceModel.Verdict.LOGICAL_120_DRM_60,
                "logical 120 over reported DRM 60 must be explicit");

        check(RefreshRateEvidenceModel.classify(120.0, 60.000004, Double.NaN)
                        == RefreshRateEvidenceModel.Verdict.MIXED_LOGICAL_120_60,
                "mixed logical modes must be recognized");

        check(RefreshRateEvidenceModel.classify(120.0, 59.94, 60.0)
                        == RefreshRateEvidenceModel.Verdict.BOTTOM_60_CONSISTENT,
                "60-ish logical and scanout lower rates must be consistent");

        check(RefreshRateEvidenceModel.classify(120.0, 120.0, 120.0)
                        == RefreshRateEvidenceModel.Verdict.BOTTOM_120_REPORTED_AT_BOTH_LAYERS,
                "120 at Android and kernel layers must not be called fake");

        check(RefreshRateEvidenceModel.classify(Double.NaN, 120.0, 60.0)
                        == RefreshRateEvidenceModel.Verdict.INSUFFICIENT,
                "missing top logical evidence must remain insufficient");

        check(RefreshRateEvidenceModel.classify(90.0, 90.0, 60.0)
                        == RefreshRateEvidenceModel.Verdict.OTHER,
                "unexpected rates must not be forced into a 60/120 theory");

        RefreshRateEvidenceModel.Assessment drmOnly = RefreshRateEvidenceModel.assess(
                120, 120, 120, Double.NaN, Double.NaN);
        check(!drmOnly.hasPhysicalMeasurement() && !drmOnly.hasDistinctFrameMeasurement(),
                "DRM 120 must never become an optical or distinct-frame measurement");
        RefreshRateEvidenceModel.Assessment separate = RefreshRateEvidenceModel.assess(
                120, 120, 120, 60, 30);
        check(separate.measuredOpticalHz == 60 && separate.measuredDistinctFrameFps == 30,
                "transport, optical refresh and distinct frames must remain separate");
        check(RefreshRateEvidenceModel.classify(60, 60, 60)
                == RefreshRateEvidenceModel.Verdict.BOTTOM_60_CONSISTENT,
                "60/60 baseline is consistent without requiring TOP 120");

        RefreshRateEvidenceModel.ModeIdentity[] modes = {
            new RefreshRateEvidenceModel.ModeIdentity(10, 0, 0, 60),
            new RefreshRateEvidenceModel.ModeIdentity(10, 1, 1, 120),
            new RefreshRateEvidenceModel.ModeIdentity(20, 0, 0, 120),
            new RefreshRateEvidenceModel.ModeIdentity(20, 1, 1, 60)
        };
        check(RefreshRateEvidenceModel.find(modes, 10, 1).fps == 120
                && RefreshRateEvidenceModel.find(modes, 20, 1).fps == 60,
                "inverted mode IDs are local to each display");
        check(RefreshRateEvidenceModel.find(modes, 30, 1) == null,
                "a matching numeric ID from another display is not a valid local lookup");

        System.out.println("RefreshRateEvidenceModel tests passed.");
    }
}
