public final class RefreshRateEvidenceModel {
    public enum Verdict {
        INSUFFICIENT,
        LOGICAL_SHARED_120_SCANOUT_UNKNOWN,
        LOGICAL_120_SCANOUT_60,
        MIXED_LOGICAL_120_60,
        BOTTOM_60_CONSISTENT,
        BOTTOM_120_REPORTED_AT_BOTH_LAYERS,
        OTHER
    }

    private static final double TOLERANCE_HZ = 1.0;

    private RefreshRateEvidenceModel() {}

    public static Verdict classify(double topLogicalHz, double bottomLogicalHz,
            double bottomScanoutHz) {
        if (!valid(topLogicalHz) || !valid(bottomLogicalHz)) {
            return Verdict.INSUFFICIENT;
        }

        boolean top120 = near(topLogicalHz, 120.0);
        boolean bottom120 = near(bottomLogicalHz, 120.0);
        boolean bottom60 = near(bottomLogicalHz, 60.0);
        boolean haveScanout = valid(bottomScanoutHz);
        boolean scanout60 = haveScanout && near(bottomScanoutHz, 60.0);
        boolean scanout120 = haveScanout && near(bottomScanoutHz, 120.0);

        if (bottom120 && scanout60) return Verdict.LOGICAL_120_SCANOUT_60;
        if (bottom120 && scanout120) return Verdict.BOTTOM_120_REPORTED_AT_BOTH_LAYERS;
        if (top120 && bottom60 && scanout60) return Verdict.BOTTOM_60_CONSISTENT;
        if (top120 && bottom60) return Verdict.MIXED_LOGICAL_120_60;
        if (top120 && bottom120 && !haveScanout) {
            return Verdict.LOGICAL_SHARED_120_SCANOUT_UNKNOWN;
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
