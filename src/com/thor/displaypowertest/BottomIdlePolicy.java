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

    /** Resets stale timers after a display wake without guessing if it was interactive. */
    public synchronized void wake() { clear(); }

    /** Returns true if the auto-off panel needs a best-effort restore. */
    public synchronized boolean touch(long nowMs) {
        if (!enabled || !both || nowMs < 0L) return false;
        boolean restore = off || pending;
        touchVerified = true;
        off = false;
        pending = false;
        lastTouchAt = nowMs;
        generation++;
        return restore;
    }

    /** The generation is an exact lease for a single delayed OFF attempt. */
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
        if (!enabled || !both || !touchVerified || off || pending || lastTouchAt < 0L)
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

    private void clear() {
        touchVerified = false;
        off = false;
        pending = false;
        lastTouchAt = -1L;
        generation++;
    }
}
