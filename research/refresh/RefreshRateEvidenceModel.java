public final class RefreshRateEvidenceModel {
    public enum Verdict {
        INSUFFICIENT,
        LOGICAL_SHARED_120_PHYSICAL_UNKNOWN,
        LOGICAL_120_PHYSICAL_60,
        MIXED_LOGICAL_120_60,
        BOTTOM_60_CONSISTENT,
        BOTTOM_120_REPORTED_AT_BOTH_LAYERS,
        OTHER
    }

    private static final double TOLERANCE_HZ = 1.0;

    private RefreshRateEvidenceModel() {}

    public static Verdict classify(double topLogicalHz, double bottomLogicalHz,
            double bottomPhysicalHz) {
        if (!valid(topLogicalHz) || !valid(bottomLogicalHz)) {
            return Verdict.INSUFFICIENT;
        }

        boolean top120 = near(topLogicalHz, 120.0);
        boolean bottom120 = near(bottomLogicalHz, 120.0);
        boolean bottom60 = near(bottomLogicalHz, 60.0);
        boolean havePhysical = valid(bottomPhysicalHz);
        boolean physical60 = havePhysical && near(bottomPhysicalHz, 60.0);
        boolean physical120 = havePhysical && near(bottomPhysicalHz, 120.0);

        if (bottom120 && physical60) return Verdict.LOGICAL_120_PHYSICAL_60;
        if (bottom120 && physical120) return Verdict.BOTTOM_120_REPORTED_AT_BOTH_LAYERS;
        if (top120 && bottom60 && physical60) return Verdict.BOTTOM_60_CONSISTENT;
        if (top120 && bottom60) return Verdict.MIXED_LOGICAL_120_60;
        if (top120 && bottom120 && !havePhysical) {
            return Verdict.LOGICAL_SHARED_120_PHYSICAL_UNKNOWN;
        }
        return Verdict.OTHER;
    }

    static boolean near(double value, double target) {
        return valid(value) && Math.abs(value - target) <= TOLERANCE_HZ;
    }

    private static boolean valid(double value) {
        return Double.isFinite(value) && value > 0.0;
    }
}
