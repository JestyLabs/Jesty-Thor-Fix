package com.thor.displaypowertest;

import java.util.ArrayDeque;
import java.util.Deque;

/** Lid semantics and loop limits, independent of Android and I/O. */
public final class LidGuardModel {
    public enum Lid { UNKNOWN, OPEN, CLOSED }
    private Lid lid = Lid.UNKNOWN;
    private boolean paused;
    private long changedAt;
    private long lastSleepAt = -10000L;
    private final Deque<Long> attempts = new ArrayDeque<>();
    private int blockedWakes;
    private long lidEpoch;

    /** One atomic decision: caller must not independently inspect paused(). */
    public static final class SleepDecision {
        public final boolean allowed;
        public final boolean paused;
        public final long lidEpoch;
        private SleepDecision(boolean allowed, boolean paused, long lidEpoch) {
            this.allowed = allowed;
            this.paused = paused;
            this.lidEpoch = lidEpoch;
        }
    }

    public synchronized SleepDecision evaluateSleep(long nowMs, boolean interactive,
            boolean externalConnected, boolean environmentKnown) {
        boolean allowed = maySleep(nowMs, interactive, externalConnected, environmentKnown);
        return new SleepDecision(allowed, paused, lidEpoch);
    }

    /** Do not record success against a newer Hall state than the authorization. */
    public synchronized boolean recordSleepIfCurrent(long epoch, long nowMs,
            boolean blockedWake) {
        if (epoch != lidEpoch || lid != Lid.CLOSED || paused) return false;
        recordSleep(nowMs, blockedWake);
        return true;
    }

    public synchronized boolean isSleepDecisionCurrent(long epoch) {
        return epoch == lidEpoch && lid == Lid.CLOSED && !paused;
    }


    public synchronized void onSwitch(int value, long nowMs) {
        if (value != 0 && value != 1) return;
        Lid next = value == 1 ? Lid.CLOSED : Lid.OPEN;
        if (next != lid) {
            changedAt = nowMs;
            lidEpoch++;
        }
        lid = next;
        if (next == Lid.OPEN) {
            paused = false;
            attempts.clear();
        }
    }

    public synchronized boolean maySchedule() { return lid == Lid.CLOSED && !paused; }

    public synchronized boolean maySleep(long nowMs, boolean interactive,
            boolean externalDisplayConnected, boolean environmentKnown) {
        if (lid != Lid.CLOSED || paused || !interactive || !environmentKnown
                || externalDisplayConnected || nowMs - changedAt < 500L
                || nowMs - lastSleepAt < 1500L) return false;
        while (!attempts.isEmpty() && nowMs - attempts.peekFirst() >= 10000L) {
            attempts.removeFirst();
        }
        if (attempts.size() >= 3) {
            paused = true;
            return false;
        }
        return true;
    }

    public synchronized void recordSleep(long nowMs, boolean blockedWake) {
        attempts.addLast(nowMs);
        lastSleepAt = nowMs;
        if (blockedWake) blockedWakes++;
    }

    public synchronized Lid lid() { return lid; }
    public synchronized boolean paused() { return paused; }
    public synchronized int blockedWakes() { return blockedWakes; }

    /** Linux input_event: timeval then unsigned short type/code and signed value. */
    public static int swLidValue(byte[] event, boolean process64Bit) {
        int offset = process64Bit ? 16 : 8;
        if (event == null || event.length < offset + 8) return -1;
        int type = (event[offset] & 255) | ((event[offset + 1] & 255) << 8);
        int code = (event[offset + 2] & 255) | ((event[offset + 3] & 255) << 8);
        if (type != 5 || code != 0) return -1;
        int value = (event[offset + 4] & 255) | ((event[offset + 5] & 255) << 8)
                | ((event[offset + 6] & 255) << 16) | ((event[offset + 7] & 255) << 24);
        return value == 0 || value == 1 ? value : -1;
    }
}
