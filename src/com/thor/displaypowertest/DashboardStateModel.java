package com.thor.displaypowertest;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/** Pure dashboard state logic, kept independent from Android for host-side tests. */
public final class DashboardStateModel {
    private static final int DISPLAY_CONFIRM_SAMPLES = 3;
    private static final int DISPLAY_MISMATCH_SAMPLES = 8;
    private static final int CLOCK_WINDOW_SAMPLES = 12;
    private static final int CLOCK_MIN_SAMPLES = 10;
    private static final double PINNED_RESIDENCY = 0.85d;
    private static final double BUSY_UTILIZATION = 25d;

    public enum Visual { BOTH_ON, AYN_FAKE_OFF, JESTY_TRUE_OFF }
    public enum Tone { MUTED, GREEN, AMBER, RED }

    public static final class DisplayStatus {
        public final String title;
        public final String detail;
        public final Tone tone;
        public final Visual confirmedVisual;
        public final boolean confirmed;

        DisplayStatus(String title, String detail, Tone tone, Visual visual, boolean confirmed) {
            this.title = title;
            this.detail = detail;
            this.tone = tone;
            this.confirmedVisual = visual;
            this.confirmed = confirmed;
        }
    }

    public static final class ClockStatus {
        public final String text;
        public final Tone tone;
        public final boolean pinned;
        public final boolean settled;

        ClockStatus(String text, Tone tone, boolean pinned, boolean settled) {
            this.text = text;
            this.tone = tone;
            this.pinned = pinned;
            this.settled = settled;
        }
    }

    private static final class ClockDelta {
        final long littleMax, littleTotal, bigMax, bigTotal;
        final int utilization;

        ClockDelta(long littleMax, long littleTotal, long bigMax, long bigTotal,
                int utilization) {
            this.littleMax = littleMax;
            this.littleTotal = littleTotal;
            this.bigMax = bigMax;
            this.bigTotal = bigTotal;
            this.utilization = utilization;
        }
    }

    private final Deque<ClockDelta> clockWindow = new ArrayDeque<>();
    private final Deque<Double> batteryWindow = new ArrayDeque<>();
    private String displayCandidate = "";
    private int displayCandidateSamples;
    private Visual confirmedVisual = Visual.BOTH_ON;
    private String clockStateKey = "";
    private boolean haveClockBaseline;
    private long previousLittleMax, previousLittleTotal, previousBigMax, previousBigTotal;

    public DisplayStatus updateDisplay(String mode, String topCrtc, String bottomCrtc,
            boolean trueOffEnabled) {
        String candidate = displayCandidate(mode, topCrtc, bottomCrtc);
        if (!candidate.equals(displayCandidate)) {
            displayCandidate = candidate;
            displayCandidateSamples = 1;
        } else {
            displayCandidateSamples++;
        }

        if ("MISMATCH".equals(candidate)) {
            if (displayCandidateSamples >= DISPLAY_MISMATCH_SAMPLES) {
                return new DisplayStatus("DISPLAY STATE MISMATCH",
                        "Requested mode and physical screens disagree", Tone.AMBER,
                        confirmedVisual, false);
            }
            return transition();
        }
        if (displayCandidateSamples < DISPLAY_CONFIRM_SAMPLES) return transition();

        if ("BOTH".equals(candidate)) {
            confirmedVisual = Visual.BOTH_ON;
            return new DisplayStatus("BOTH SCREENS", "Both panels powered",
                    Tone.GREEN, confirmedVisual, true);
        }
        if ("AYN_BLACK".equals(candidate)) {
            confirmedVisual = Visual.AYN_FAKE_OFF;
            return new DisplayStatus("TOP ONLY \u00B7 AYN BLACK SCREEN",
                    "Bottom hardware still on", Tone.AMBER, confirmedVisual, true);
        }
        if ("TRUE_OFF".equals(candidate)) {
            confirmedVisual = Visual.JESTY_TRUE_OFF;
            return new DisplayStatus("TOP ONLY \u00B7 TRUE OFF",
                    trueOffEnabled ? "Bottom hardware powered off"
                            : "Bottom hardware is physically off",
                    Tone.GREEN, confirmedVisual, true);
        }
        confirmedVisual = Visual.BOTH_ON;
        return new DisplayStatus("BOTTOM ONLY", "Top screen off",
                Tone.GREEN, confirmedVisual, true);
    }

    private DisplayStatus transition() {
        return new DisplayStatus("DISPLAY TRANSITION", "Waiting for hardware\u2026",
                Tone.MUTED, confirmedVisual, false);
    }

    private static String displayCandidate(String mode, String topCrtc, String bottomCrtc) {
        if ("0".equals(mode) && "1".equals(topCrtc) && "1".equals(bottomCrtc)) return "BOTH";
        if ("1".equals(mode) && "1".equals(topCrtc) && "1".equals(bottomCrtc)) return "AYN_BLACK";
        if ("1".equals(mode) && "1".equals(topCrtc) && "0".equals(bottomCrtc)) return "TRUE_OFF";
        if ("2".equals(mode) && "0".equals(topCrtc) && "1".equals(bottomCrtc)) return "BOTTOM";
        return "MISMATCH";
    }

    public void resetDisplay() {
        displayCandidate = "";
        displayCandidateSamples = 0;
    }

    public ClockStatus updateClocks(String stateKey, long littleMaxTicks, long littleTotalTicks,
            long bigMaxTicks, long bigTotalTicks, int utilization,
            boolean fixDesired, boolean fixActive) {
        if (!stateKey.equals(clockStateKey)) {
            resetClocks();
            clockStateKey = stateKey;
        }
        if (!haveClockBaseline) {
            setClockBaseline(littleMaxTicks, littleTotalTicks, bigMaxTicks, bigTotalTicks);
            return checking();
        }

        long littleMaxDelta = littleMaxTicks - previousLittleMax;
        long littleTotalDelta = littleTotalTicks - previousLittleTotal;
        long bigMaxDelta = bigMaxTicks - previousBigMax;
        long bigTotalDelta = bigTotalTicks - previousBigTotal;
        setClockBaseline(littleMaxTicks, littleTotalTicks, bigMaxTicks, bigTotalTicks);
        if (littleMaxDelta < 0 || littleTotalDelta <= 0 || bigMaxDelta < 0 || bigTotalDelta <= 0
                || utilization < 0) {
            clockWindow.clear();
            return checking();
        }

        clockWindow.addLast(new ClockDelta(littleMaxDelta, littleTotalDelta,
                bigMaxDelta, bigTotalDelta, utilization));
        while (clockWindow.size() > CLOCK_WINDOW_SAMPLES) clockWindow.removeFirst();
        if (fixDesired != fixActive || clockWindow.size() < CLOCK_MIN_SAMPLES) return checking();

        long littleMax = 0, littleTotal = 0, bigMax = 0, bigTotal = 0, load = 0;
        for (ClockDelta sample : clockWindow) {
            littleMax += sample.littleMax;
            littleTotal += sample.littleTotal;
            bigMax += sample.bigMax;
            bigTotal += sample.bigTotal;
            load += sample.utilization;
        }
        double averageLoad = (double) load / clockWindow.size();
        if (averageLoad >= BUSY_UTILIZATION) {
            return new ClockStatus("CPU BUSY \u00B7 MONITORING PAUSED", Tone.AMBER,
                    false, true);
        }
        boolean pinned = ratio(littleMax, littleTotal) >= PINNED_RESIDENCY
                && ratio(bigMax, bigTotal) >= PINNED_RESIDENCY;
        if (pinned) {
            return fixActive
                    ? new ClockStatus("UNEXPECTED PINNING \u00B7 FIX ACTIVE", Tone.RED, true, true)
                    : new ClockStatus("LITTLE + BIG PINNED AT MAX", Tone.RED, true, true);
        }
        return new ClockStatus(fixActive ? "CPU FIX ACTIVE \u00B7 CLOCKS NORMAL"
                : "CLOCKS SCALING NORMALLY", Tone.GREEN, false, true);
    }

    public void resetClocks() {
        clockWindow.clear();
        haveClockBaseline = false;
        clockStateKey = "";
    }

    private void setClockBaseline(long littleMax, long littleTotal, long bigMax, long bigTotal) {
        previousLittleMax = littleMax;
        previousLittleTotal = littleTotal;
        previousBigMax = bigMax;
        previousBigTotal = bigTotal;
        haveClockBaseline = true;
    }

    private static ClockStatus checking() {
        return new ClockStatus("CHECKING CPU CLOCKS\u2026", Tone.MUTED, false, false);
    }

    public void resetBattery() { batteryWindow.clear(); }

    public double smoothBattery(double watts) {
        if (Double.isNaN(watts) || watts < 0d) return Double.NaN;
        batteryWindow.addLast(watts);
        while (batteryWindow.size() > 5) batteryWindow.removeFirst();
        List<Double> sorted = new ArrayList<>(batteryWindow);
        Collections.sort(sorted);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 0
                ? (sorted.get(middle - 1) + sorted.get(middle)) / 2d
                : sorted.get(middle);
    }

    public static double batteryWatts(long currentMicroAmps, long voltageMicroVolts,
            boolean externalPower, String status) {
        if (externalPower || !"Discharging".equalsIgnoreCase(status)
                || currentMicroAmps == Long.MIN_VALUE || voltageMicroVolts <= 0L) {
            return Double.NaN;
        }
        return (Math.abs((double) currentMicroAmps) * (double) voltageMicroVolts)
                / 1_000_000_000_000d;
    }

    private static double ratio(long numerator, long denominator) {
        return denominator > 0L ? (double) numerator / denominator : 0d;
    }
}
