public final class RefreshRateEvidenceModel {
    public enum Verdict {
        INSUFFICIENT,
        LOGICAL_SHARED_120_DRM_UNKNOWN,
        LOGICAL_120_DRM_60,
        MIXED_LOGICAL_120_60,
        BOTTOM_60_CONSISTENT,
        BOTTOM_120_REPORTED_AT_BOTH_LAYERS,
        OTHER
    }

    private static final double TOLERANCE_HZ = 1.0;

    private RefreshRateEvidenceModel() {}

    public static final class Assessment {
        public final Verdict reportedLayers;
        public final double measuredOpticalHz;
        public final double measuredDistinctFrameFps;

        private Assessment(Verdict reportedLayers, double opticalHz, double distinctFps) {
            this.reportedLayers = reportedLayers;
            this.measuredOpticalHz = opticalHz;
            this.measuredDistinctFrameFps = distinctFps;
        }

        public boolean hasPhysicalMeasurement() { return valid(measuredOpticalHz); }
        public boolean hasDistinctFrameMeasurement() { return valid(measuredDistinctFrameFps); }
    }

    public static Assessment assess(double topLogicalHz, double bottomLogicalHz,
            double bottomDrmHz, double measuredOpticalHz, double measuredDistinctFrameFps) {
        return new Assessment(classify(topLogicalHz, bottomLogicalHz, bottomDrmHz),
                measuredOpticalHz, measuredDistinctFrameFps);
    }

    public static final class ModeIdentity {
        public final long physicalDisplayId;
        // SurfaceFlinger DisplayModeId, not Android Display.Mode.id.
        public final int sfModeId;
        public final int hwcConfigId;
        public final double fps;

        public ModeIdentity(long display, int sfMode, int hwcConfig, double hz) {
            physicalDisplayId = display;
            sfModeId = sfMode;
            hwcConfigId = hwcConfig;
            fps = hz;
        }
    }

    // Lookup is display-local, even when the other display has the same integer ID.
    public static ModeIdentity find(ModeIdentity[] modes, long display, int modeId) {
        for (ModeIdentity mode : modes) {
            if (mode.physicalDisplayId == display && mode.sfModeId == modeId) return mode;
        }
        return null;
    }

    public static Verdict classify(double topLogicalHz, double bottomLogicalHz,
            double bottomDrmHz) {
        if (!valid(topLogicalHz) || !valid(bottomLogicalHz)) {
            return Verdict.INSUFFICIENT;
        }

        boolean top120 = near(topLogicalHz, 120.0);
        boolean bottom120 = near(bottomLogicalHz, 120.0);
        boolean bottom60 = near(bottomLogicalHz, 60.0);
        boolean haveDrm = valid(bottomDrmHz);
        boolean drm60 = haveDrm && near(bottomDrmHz, 60.0);
        boolean drm120 = haveDrm && near(bottomDrmHz, 120.0);

        if (bottom120 && drm60) return Verdict.LOGICAL_120_DRM_60;
        if (bottom120 && drm120) return Verdict.BOTTOM_120_REPORTED_AT_BOTH_LAYERS;
        if (bottom60 && drm60) return Verdict.BOTTOM_60_CONSISTENT;
        if (top120 && bottom60) return Verdict.MIXED_LOGICAL_120_60;
        if (top120 && bottom120 && !haveDrm) {
            return Verdict.LOGICAL_SHARED_120_DRM_UNKNOWN;
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
