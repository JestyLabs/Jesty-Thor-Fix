package com.thor.displaypowertest;

/**
 * Pure monotonic-clock policy. No polling, Android APIs or hardware writes.
 * A fresh confirmed touch on display 4 is required in each BOTH session;
 * unknown or missing input never proves inactivity.
 */
public final class BottomIdlePolicy {
    private boolean enabled;
    private boolean both;
    private boolean touchVerified;
    private boolean off;
    private boolean pending;
    private boolean pointerDown;
    private boolean restorePending;
    private int minutes;
    private long lastTouchAt = -1L;
    private long generation;

    /** Hardware observation is useful only while the lower panel is visible. */
    public static boolean shouldObserve(String mode, boolean displayFixEnabled) {
        return displayFixEnabled && "0".equals(mode);
    }

    public synchronized void configure(int configuredMinutes) {
        if (configuredMinutes != 0 && !BottomIdleSettings.validMinutes(configuredMinutes))
            throw new IllegalArgumentException("timeout");
        enabled = configuredMinutes != 0;
        minutes = configuredMinutes;
        both = false;
        clear();
    }

    public synchronized boolean mode(String actual) {
        boolean newBoth = "0".equals(actual);
        if (newBoth == both) return false;
        both = newBoth;
        clear();
        return true;
    }

    /**
     * Atomically check admission and process a mode sample. -1 means disabled,
     * 0 means unchanged, and 1 means a mode edge invalidated previous touch.
     * A single policy monitor covers the entire decision and mutation.
     */
    public synchronized int sampleModeIfEnabled(String actual) {
        if (!enabled) return -1;
        boolean nextBoth = "0".equals(actual);
        if (nextBoth == both) return 0;
        both = nextBoth;
        clear();
        return 1;
    }

    /** Explicitly invalidate touch evidence only while the feature is enabled. */
    public synchronized boolean wakeIfEnabled() {
        if (!enabled) return false;
        clear();
        return true;
    }

    /** As wakeIfEnabled, but an owned OFF or pending OFF lease is preserved. */
    public synchronized boolean wakeUnlessOwned() {
        if (!enabled || off || pending) return false;
        clear();
        return true;
    }

    /**
     * On input monitor failure, revoke all touch proof and atomically return
     * a new exact restoration lease, if a prior OFF/restore may still be owned.
     * No separate check/read/wake steps are exposed to the caller.
     */
    public synchronized long inputUnavailableRestoreLease() {
        if (!enabled) return REJECTED_TOUCH;
        boolean restore = off || pending || restorePending;
        clear();
        if (!restore) return -1L;
        restorePending = true;
        return ++generation;
    }

    /** Resets stale timers after a display wake without guessing if it was interactive. */
    public synchronized void wake() { clear(); }

    /** Returns true if the auto-off panel needs a best-effort restore. */
    public synchronized boolean touch(long nowMs) {
        if (!enabled || !both || nowMs < 0L) return false;
        boolean restore = off || pending || restorePending;
        restorePending = restore;
        touchVerified = true;
        off = false;
        pending = false;
        lastTouchAt = nowMs;
        generation++;
        return restore;
    }


    /** Failed/disarmed input is never touch proof and never starts a timer. */
    public static final long REJECTED_TOUCH = Long.MIN_VALUE;

    /**
     * A complete touch + pointer transition and its restoration lease are a
     * single synchronized policy operation. The caller must never capture
     * generation before applying the DOWN/UP pointer edge.
     *
     * pointerState: 1 = held/down, 0 = released, -1 = unchanged.
     * Returns REJECTED_TOUCH, -1 (accepted; no restore) or the exact ON lease.
     */
    public synchronized long touchEvent(long nowMs, int pointerState) {
        if (!enabled || !both || nowMs < 0L) return REJECTED_TOUCH;
        boolean restore = touch(nowMs);
        if (pointerState == 1) pointerDown(true);
        else if (pointerState == 0) pointerDown(false);
        return restore ? generation : -1L;
    }

    /** The generation is an exact lease for a single delayed OFF attempt. */
    /** A held gesture is active usage, even when it has no MOVE samples. */
    public synchronized void pointerDown(boolean pressed) {
        if (pointerDown != pressed) {
            pointerDown = pressed;
            generation++;
        }
    }

    public synchronized long beginOff(long nowMs) {
        if (delayMs(nowMs) != 0L) return -1L;
        pending = true;
        return ++generation;
    }

    public synchronized boolean isPending(long ticket) {
        return pending && ticket == generation && enabled && both && touchVerified;
    }

    public synchronized boolean completeOff(long ticket, boolean confirmed, long nowMs) {
        if (!isPending(ticket)) return false;
        pending = false;
        off = confirmed;
        if (!confirmed) lastTouchAt = nowMs;
        generation++;
        return confirmed;
    }

    /** -1 means not armed; 0 means due; otherwise milliseconds until due. */
    public synchronized long delayMs(long nowMs) {
        if (!enabled || !both || !touchVerified || pointerDown || off || pending
                || restorePending || lastTouchAt < 0L)
            return -1L;
        if (nowMs < lastTouchAt) return -1L;
        return Math.max(0L, minutes * 60000L - (nowMs - lastTouchAt));
    }

    public synchronized boolean suppressNativeRestore() { return off || pending; }
    public synchronized boolean isOff() { return off; }
    public synchronized boolean enabled() { return enabled; }
    public synchronized int minutes() { return minutes; }
    public synchronized boolean inputVerified() { return touchVerified; }
    public synchronized long generation() { return generation; }
    /** Outdated restores must not wake a newer pending/confirmed OFF. */
    public synchronized long requireRestore() {
        restorePending = true;
        return ++generation;
    }
    public synchronized boolean canRestore(long lease) {
        return restorePending && lease == generation && !off && !pending;
    }
    public synchronized boolean completeRestore(long lease, boolean confirmed) {
        if (!canRestore(lease) || !confirmed) return false;
        restorePending = false;
        return true;
    }

    private void clear() {
        touchVerified = false;
        pointerDown = false;
        restorePending = false;
        off = false;
        pending = false;
        lastTouchAt = -1L;
        generation++;
    }
}
