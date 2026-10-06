package com.thor.displaypowertest;

/**
 * Time-based CPU pinning evidence model.
 *
 * This is intentionally not wired into the dashboard yet. It exists so the
 * current fixed sample-count diagnostic can be replaced later without tying
 * confidence to a 1 Hz UI polling cadence.
 */
public final class CpuPinningEvidenceModel {
    public enum State { CHECKING, NORMAL, PINNED, BUSY }

    public static final class Result {
        public final State state;
        public final long pinnedEvidenceMs;
        public final long normalEvidenceMs;

        Result(State state, long pinnedEvidenceMs, long normalEvidenceMs) {
            this.state = state;
            this.pinnedEvidenceMs = pinnedEvidenceMs;
            this.normalEvidenceMs = normalEvidenceMs;
        }
    }

    private final long pinnedQualifyMs;
    private final long normalQualifyMs;
    private final double pinnedResidency;
    private final int busyUtilization;

    private boolean haveBaseline;
    private long previousAtMs;
    private long previousLittleHigh;
    private long previousLittleTotal;
    private long previousBigHigh;
    private long previousBigTotal;
    private long pinnedEvidenceMs;
    private long normalEvidenceMs;
    private State confirmed = State.CHECKING;

    public CpuPinningEvidenceModel(long pinnedQualifyMs, long normalQualifyMs,
            double pinnedResidency, int busyUtilization) {
        if (pinnedQualifyMs <= 0L || normalQualifyMs <= 0L)
            throw new IllegalArgumentException("qualification time must be positive");
        if (!(pinnedResidency > 0d && pinnedResidency <= 1d))
            throw new IllegalArgumentException("residency threshold out of range");
        if (busyUtilization < 0 || busyUtilization > 100)
            throw new IllegalArgumentException("busy utilization out of range");
        this.pinnedQualifyMs = pinnedQualifyMs;
        this.normalQualifyMs = normalQualifyMs;
        this.pinnedResidency = pinnedResidency;
        this.busyUtilization = busyUtilization;
    }

    public Result observe(long nowMs,
            long littleHighTicks, long littleTotalTicks,
            long bigHighTicks, long bigTotalTicks,
            int utilization) {
        if (!validCounters(littleHighTicks, littleTotalTicks, bigHighTicks, bigTotalTicks)
                || utilization < 0 || utilization > 100 || nowMs < 0L) {
            reset();
            return result(State.CHECKING);
        }

        if (!haveBaseline) {
            setBaseline(nowMs, littleHighTicks, littleTotalTicks, bigHighTicks, bigTotalTicks);
            return result(State.CHECKING);
        }

        long elapsedMs = nowMs - previousAtMs;
        long littleHighDelta = littleHighTicks - previousLittleHigh;
        long littleTotalDelta = littleTotalTicks - previousLittleTotal;
        long bigHighDelta = bigHighTicks - previousBigHigh;
        long bigTotalDelta = bigTotalTicks - previousBigTotal;

        setBaseline(nowMs, littleHighTicks, littleTotalTicks, bigHighTicks, bigTotalTicks);

        if (elapsedMs <= 0L || littleHighDelta < 0L || littleTotalDelta <= 0L
                || bigHighDelta < 0L || bigTotalDelta <= 0L) {
            clearEvidence();
            confirmed = State.CHECKING;
            return result(State.CHECKING);
        }

        if (utilization >= busyUtilization) {
            clearEvidence();
            return result(State.BUSY);
        }

        boolean pinned = ratio(littleHighDelta, littleTotalDelta) >= pinnedResidency
                && ratio(bigHighDelta, bigTotalDelta) >= pinnedResidency;
        if (pinned) {
            pinnedEvidenceMs = saturatingAdd(pinnedEvidenceMs, elapsedMs);
            normalEvidenceMs = 0L;
            if (pinnedEvidenceMs >= pinnedQualifyMs) confirmed = State.PINNED;
        } else {
            normalEvidenceMs = saturatingAdd(normalEvidenceMs, elapsedMs);
            pinnedEvidenceMs = 0L;
            if (normalEvidenceMs >= normalQualifyMs) confirmed = State.NORMAL;
        }

        return result(confirmed);
    }

    public void reset() {
        haveBaseline = false;
        previousAtMs = 0L;
        previousLittleHigh = previousLittleTotal = 0L;
        previousBigHigh = previousBigTotal = 0L;
        clearEvidence();
        confirmed = State.CHECKING;
    }

    private Result result(State state) {
        return new Result(state, pinnedEvidenceMs, normalEvidenceMs);
    }

    private void setBaseline(long atMs, long littleHigh, long littleTotal,
            long bigHigh, long bigTotal) {
        previousAtMs = atMs;
        previousLittleHigh = littleHigh;
        previousLittleTotal = littleTotal;
        previousBigHigh = bigHigh;
        previousBigTotal = bigTotal;
        haveBaseline = true;
    }

    private void clearEvidence() {
        pinnedEvidenceMs = 0L;
        normalEvidenceMs = 0L;
    }

    private static boolean validCounters(long littleHigh, long littleTotal,
            long bigHigh, long bigTotal) {
        return littleHigh >= 0L && littleTotal >= 0L
                && bigHigh >= 0L && bigTotal >= 0L;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator > 0L ? (double) numerator / denominator : 0d;
    }

    private static long saturatingAdd(long a, long b) {
        return Long.MAX_VALUE - a < b ? Long.MAX_VALUE : a + b;
    }
}
