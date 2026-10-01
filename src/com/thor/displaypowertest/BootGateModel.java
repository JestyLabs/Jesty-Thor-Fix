package com.thor.displaypowertest;

/** Deterministic boot readiness rule; caller supplies observed hardware and time. */
public final class BootGateModel {
    public enum Result { WAIT, READY, TIMEOUT }
    public enum CpuAction { PROCEED, RESTART_ONCE, FAIL_SAFE }
    private final long startedAtMs;
    private final long graceMs;
    private String candidate = "";
    private int stableSamples;
    private long stableSinceMs = -1L;

    public BootGateModel(long startedAtMs, boolean afterComposerRestart) {
        this.startedAtMs = startedAtMs;
        graceMs = afterComposerRestart ? 5000L : 10000L;
    }

    public Result observe(long nowMs, boolean androidReady, boolean composerRunning,
            String mode, String topCrtc, String bottomCrtc) {
        if (nowMs - startedAtMs >= 60000L) return Result.TIMEOUT;
        boolean valid = androidReady && composerRunning && knownMode(mode)
                && ("0".equals(topCrtc) || "1".equals(topCrtc))
                && ("0".equals(bottomCrtc) || "1".equals(bottomCrtc));
        if (!valid) {
            candidate = "";
            stableSamples = 0;
            stableSinceMs = -1L;
            return Result.WAIT;
        }
        String observed = mode + ':' + topCrtc + ':' + bottomCrtc;
        if (!observed.equals(candidate)) {
            candidate = observed;
            stableSamples = 1;
            stableSinceMs = -1L;
        } else {
            stableSamples++;
            if (stableSamples == 3) stableSinceMs = nowMs;
        }
        return stableSamples >= 3 && stableSinceMs >= 0L
                && nowMs - stableSinceMs >= graceMs
                ? Result.READY : Result.WAIT;
    }

    /** Read-only observability for the boot trace; never influences the decision. */
    public int stableSamples() { return stableSamples; }
    public long stableSinceMs() { return stableSinceMs; }
    public long graceMs() { return graceMs; }
    public String candidate() { return candidate; }

    private static boolean knownMode(String mode) {
        return "0".equals(mode) || "1".equals(mode) || "2".equals(mode);
    }

    /** No speculative ON for unknown mode/CRTC; no duplicate power command. */
    public static boolean displayActionRequired(String mode, boolean fixEnabled,
            String bottomCrtc) {
        if (!knownMode(mode) || !("0".equals(bottomCrtc) || "1".equals(bottomCrtc))) {
            return false;
        }
        if ("2".equals(mode)) return false;
        boolean targetOn = !fixEnabled || !"1".equals(mode);
        return targetOn ? !"1".equals(bottomCrtc) : !"0".equals(bottomCrtc);
    }

    /** Repair lower hardware reactivated after AYN has already selected TOP. */
    public static boolean shouldRepairStableTop(String mode, String topCrtc,
            String bottomCrtc, boolean fixEnabled, boolean wakeRepairPending) {
        return fixEnabled && !wakeRepairPending && "1".equals(mode)
                && "1".equals(topCrtc) && "1".equals(bottomCrtc);
    }

    public static CpuAction cpuAction(boolean desired, String actual,
            boolean afterComposerRestart) {
        if (PropertyState.UNSET.equals(actual) && desired && !afterComposerRestart) {
            return CpuAction.RESTART_ONCE;
        }
        if (!("0".equals(actual) || "1".equals(actual))) return CpuAction.FAIL_SAFE;
        if (desired == "1".equals(actual)) return CpuAction.PROCEED;
        return afterComposerRestart ? CpuAction.FAIL_SAFE : CpuAction.RESTART_ONCE;
    }
}
