package com.thor.displaypowertest;

/**
 * Pure policy model for a future adaptive display-mode watcher.
 *
 * It is intentionally not wired into v1.6.0 runtime behavior. The production
 * watcher stays unchanged until on-device measurements justify the change.
 */
public final class WatcherCadenceModel {
    public static final long FAST_POLL_MS = 20L;
    public static final long STEADY_SAFETY_POLL_MS = 1000L;
    public static final long BURST_WINDOW_MS = 1500L;
    public static final long BURST_DRM_POLL_MS = 250L;
    public static final long STEADY_DRM_POLL_MS = 1000L;

    private long burstUntil = -1L;
    private long lastDrmAt = -1L;

    public void noteEvent(long nowMs) {
        if (nowMs < 0L) {
            burstUntil = Long.MAX_VALUE;
            return;
        }
        long candidate = saturatingAdd(nowMs, BURST_WINDOW_MS);
        if (candidate > burstUntil) burstUntil = candidate;
    }

    public boolean burstActive(long nowMs) {
        return nowMs < 0L || (burstUntil >= 0L && nowMs < burstUntil);
    }

    public long nextModePollDelayMs(long nowMs, boolean modeKnown,
            boolean repairPending) {
        if (nowMs < 0L || !modeKnown || repairPending || burstActive(nowMs)) {
            return FAST_POLL_MS;
        }
        return STEADY_SAFETY_POLL_MS;
    }

    /**
     * Force is for a newly observed mode/display event or a due repair.
     * Otherwise the model enforces a bounded DRM safety cadence.
     */
    public boolean shouldProbeDrm(long nowMs, boolean force) {
        if (force || nowMs < 0L || lastDrmAt < 0L) {
            lastDrmAt = nowMs;
            return true;
        }
        long interval = burstActive(nowMs) ? BURST_DRM_POLL_MS : STEADY_DRM_POLL_MS;
        if (nowMs - lastDrmAt >= interval) {
            lastDrmAt = nowMs;
            return true;
        }
        return false;
    }

    public void reset() {
        burstUntil = -1L;
        lastDrmAt = -1L;
    }

    private static long saturatingAdd(long a, long b) {
        if (a < 0L || b < 0L) return Long.MAX_VALUE;
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }
}
