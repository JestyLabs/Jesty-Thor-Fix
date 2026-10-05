package com.thor.displaypowertest;

/**
 * Pure readiness gate for the early CPU-fix boot phase.
 *
 * This intentionally does not look at AYN mode or CRTC state. Those remain
 * guarded by BootGateModel before any display or lid action.
 */
public final class EarlyCpuGateModel {
    public static final long TIMEOUT_MS = 60_000L;
    public static final long POLL_MS = 200L;

    public enum Result {
        WAIT,
        READY,
        FAIL_SAFE,
        TIMEOUT
    }

    private final long startedAtMs;

    public EarlyCpuGateModel(long startedAtMs) {
        if (startedAtMs < 0L) throw new IllegalArgumentException("started_at_ms");
        this.startedAtMs = startedAtMs;
    }

    public Result observe(long nowMs, boolean bootCompleted, boolean composerRunning,
            String watcherHealth, String bootId, boolean desired, String property,
            String composerPid) {
        if (nowMs < startedAtMs) return Result.FAIL_SAFE;
        if (nowMs - startedAtMs >= TIMEOUT_MS) return Result.TIMEOUT;

        // Transient boot prerequisites are allowed to settle while BootSafety
        // remains held. No CPU property write or compositor restart is allowed
        // before all three are true.
        if (!bootCompleted || !composerRunning || !"RUNNING".equals(watcherHealth)) {
            return Result.WAIT;
        }

        // Provenance must be anchored to this kernel boot. If boot_id cannot be
        // trusted, do not start a restart attempt that could survive a daemon
        // replacement without a reliable boot scope.
        if (!CpuBootAttemptModel.validBootId(bootId)) return Result.FAIL_SAFE;

        // pidof/getprop can briefly fail while Android is settling. Waiting is
        // safe and avoids converting a transient read failure into a restart.
        if ("?".equals(composerPid) || "?".equals(property)) return Result.WAIT;

        if (!CpuBootAttemptModel.pid(composerPid)) return Result.FAIL_SAFE;
        if (!CpuBootAttemptModel.property(property)) return Result.FAIL_SAFE;

        // UNSET is safe only for enabling the vendor flag. It must never be
        // interpreted as proof that the disabled state is already effective.
        if (PropertyState.UNSET.equals(property) && !desired) return Result.FAIL_SAFE;

        return Result.READY;
    }
}
