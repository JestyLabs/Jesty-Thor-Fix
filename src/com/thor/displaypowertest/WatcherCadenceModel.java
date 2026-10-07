package com.thor.displaypowertest;

/**
 * Pure cadence policy for the display-mode watcher.
 *
 * Display callbacks are hints, never the sole correctness path: an idle
 * safety poll still samples Settings and DRM periodically if every callback is
 * lost. Short bursts retain the old 20 ms response window around real display
 * activity without paying that cost continuously.
 */
public final class WatcherCadenceModel {
    public static final long IDLE_SAMPLE_MS = 500L;
    public static final long BURST_SAMPLE_MS = 20L;
    public static final long BURST_WINDOW_MS = 1600L;
    public static final long DRM_SAFETY_MS = 1000L;

    private long burstUntilMs = -1L;
    private long eventGeneration;
    private long consumedGeneration;

    public void onDisplayEvent(long nowMs) {
        markEvent(nowMs);
    }

    public void onModeChanged(long nowMs) {
        markEvent(nowMs);
    }

    public boolean inBurst(long nowMs) {
        return nowMs >= 0L && nowMs < burstUntilMs;
    }

    public long nextDelayMs(long nowMs) {
        return inBurst(nowMs) ? BURST_SAMPLE_MS : IDLE_SAMPLE_MS;
    }

    /**
     * Returns the event generation that this DRM read should cover, or -1 when
     * no hardware read is needed yet.
     *
     * The generation is captured before the read. completeDrmRead() consumes
     * only that generation, so an event arriving during debugfs I/O remains
     * pending for the next sample.
     */
    public long beginDrmRead(long nowMs, long lastDrmReadAtMs,
            boolean modeChanged, boolean repairDue, boolean stableTopWake) {
        boolean eventPending = eventGeneration != consumedGeneration;
        boolean safetyDue = lastDrmReadAtMs < 0L
                || nowMs - lastDrmReadAtMs >= DRM_SAFETY_MS;
        return modeChanged || repairDue || stableTopWake || eventPending || safetyDue
                ? eventGeneration : -1L;
    }

    public void completeDrmRead(long generationAtStart) {
        if (generationAtStart > consumedGeneration) {
            consumedGeneration = generationAtStart;
        }
    }

    public long eventGeneration() {
        return eventGeneration;
    }

    public long consumedGeneration() {
        return consumedGeneration;
    }

    private void markEvent(long nowMs) {
        if (eventGeneration != Long.MAX_VALUE) {
            eventGeneration++;
        } else {
            // Practically unreachable; retain the dirty-state invariant if a
            // daemon somehow survives enough events to wrap a signed long.
            eventGeneration = 1L;
            consumedGeneration = 0L;
        }
        long until = nowMs > Long.MAX_VALUE - BURST_WINDOW_MS
                ? Long.MAX_VALUE : nowMs + BURST_WINDOW_MS;
        if (until > burstUntilMs) burstUntilMs = until;
    }
}
