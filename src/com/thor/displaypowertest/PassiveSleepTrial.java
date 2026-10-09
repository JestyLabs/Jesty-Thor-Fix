package com.thor.displaypowertest;

import java.util.Locale;

/** Pure Java evaluator; never polls, schedules work, or accesses hardware. */
public final class PassiveSleepTrial {
    public static final long MIN_MS = 300000L;
    private static final long MAX_MS = 259200000L;

    public static final class Sample {
        public final long elapsedMs, uptimeMs, energyNwh, chargeUah;
        public final int voltageMv, temperatureDeciC, plugged, bootCount, fixes;
        public Sample(long elapsedMs, long uptimeMs, long energyNwh, long chargeUah,
                int voltageMv, int temperatureDeciC, int plugged, int bootCount, int fixes) {
            this.elapsedMs = elapsedMs; this.uptimeMs = uptimeMs;
            this.energyNwh = energyNwh; this.chargeUah = chargeUah;
            this.voltageMv = voltageMv; this.temperatureDeciC = temperatureDeciC;
            this.plugged = plugged; this.bootCount = bootCount; this.fixes = fixes;
        }
        public String encode() {
            return "S1|" + elapsedMs + "|" + uptimeMs + "|" + energyNwh + "|" + chargeUah
                    + "|" + voltageMv + "|" + temperatureDeciC + "|" + plugged
                    + "|" + bootCount + "|" + fixes;
        }
        public static Sample decode(String encoded) {
            if (encoded == null || encoded.length() > 180) return null;
            String[] p = encoded.split("\\|", -1);
            if (p.length != 10 || !"S1".equals(p[0])) return null;
            try {
                Sample s = new Sample(Long.parseLong(p[1]), Long.parseLong(p[2]),
                        Long.parseLong(p[3]), Long.parseLong(p[4]),
                        Integer.parseInt(p[5]), Integer.parseInt(p[6]),
                        Integer.parseInt(p[7]), Integer.parseInt(p[8]), Integer.parseInt(p[9]));
                if (s.elapsedMs < 0 || s.uptimeMs < 0 || s.uptimeMs > s.elapsedMs
                        || s.energyNwh < -1 || s.chargeUah < -1 || s.voltageMv < -1
                        || s.voltageMv > 6000 || s.temperatureDeciC < -1
                        || s.temperatureDeciC > 1200 || s.plugged < -1 || s.plugged > 15
                        || s.bootCount < -1 || s.fixes < 0 || s.fixes > 7) return null;
                return s;
            } catch (NumberFormatException ignored) { return null; }
        }
    }

    public static final class Result {
        public final String state, method;
        public final long totalMs, awakeMs, suspendedMs;
        public final double energyWh, meanW;
        public final boolean bootVerified;
        public final int fixes, voltageStartMv, voltageEndMv, temperatureStartDeciC, temperatureEndDeciC;
        public final int pluggedStart, pluggedEnd;
        private Result(String state, String method, long totalMs, long awakeMs,
                long suspendedMs, double wh, boolean bootVerified, int fixes,
                int voltageStartMv, int voltageEndMv, int temperatureStartDeciC, int temperatureEndDeciC,
                int pluggedStart, int pluggedEnd) {
            this.state = state; this.method = method;
            this.totalMs = totalMs; this.awakeMs = awakeMs; this.suspendedMs = suspendedMs;
            this.energyWh = wh; this.meanW = totalMs > 0 && wh >= 0 ? wh * 3600000d / totalMs : -1d;
            this.bootVerified = bootVerified; this.fixes = fixes;
            this.voltageStartMv = voltageStartMv; this.voltageEndMv = voltageEndMv;
            this.temperatureStartDeciC = temperatureStartDeciC;
            this.temperatureEndDeciC = temperatureEndDeciC;
            this.pluggedStart = pluggedStart; this.pluggedEnd = pluggedEnd;
        }
        public String reportLine() {
            return "passive_sleep;state=" + state + ";interval_ms=" + totalMs
                    + ";awake_clock_ms=" + awakeMs + ";suspend_clock_delta_ms=" + suspendedMs
                    + ";suspend_percent=" + percent(suspendedMs, totalMs)
                    + ";energy_method=" + method + ";energy_wh=" + number(energyWh)
                    + ";mean_device_w=" + number(meanW)
                    + ";boot_verified=" + (bootVerified ? 1 : 0) + ";fixes_requested_mask=" + fixes
                    + ";voltage_start_mv=" + known(voltageStartMv)
                    + ";voltage_end_mv=" + known(voltageEndMv)
                    + ";temperature_start_deci_c=" + known(temperatureStartDeciC)
                    + ";temperature_end_deci_c=" + known(temperatureEndDeciC)
                    + ";plugged_start_mask=" + known(pluggedStart)
                    + ";plugged_end_mask=" + known(pluggedEnd);
        }
        public String describe() {
            return "State: " + state + "\nDuration: " + totalMs / 1000 + " seconds"
                    + "\nEstimated suspension: " + suspendedMs / 1000 + " seconds ("
                    + percent(suspendedMs, totalMs) + "%)"
                    + "\nEnergy: " + number(energyWh) + " Wh (" + method + ")"
                    + "\nMean whole-device draw: " + number(meanW) + " W"
                    + "\nEndpoint power (start → end): " + endpointPower(pluggedStart)
                    + " → " + endpointPower(pluggedEnd)
                    + "\nBattery voltage: " + known(voltageStartMv) + " → " + known(voltageEndMv) + " mV"
                    + "\nBattery temperature: " + known(temperatureStartDeciC)
                    + " → " + known(temperatureEndDeciC) + " (0.1°C)"
                    + "\nOS boot count verified: " + (bootVerified ? "yes" : "no")
                    + "\nRequested switches (display, CPU, lid): "
                    + ((fixes & 1) != 0 ? "ON" : "OFF") + ", "
                    + ((fixes & 2) != 0 ? "ON" : "OFF") + ", "
                    + ((fixes & 4) != 0 ? "ON" : "OFF")
                    + "\nNot a continuous trace or a physical panel power measurement.";
        }
    }

    public static Result evaluate(Sample a, Sample b) {
        if (a == null || b == null) return invalid("MISSING_SAMPLE", false, 0, 0, 0, a, b);
        boolean bootVerified = a.bootCount >= 0 && a.bootCount == b.bootCount;
        if (a.bootCount >= 0 && b.bootCount >= 0 && a.bootCount != b.bootCount)
            return invalid("REBOOT_DETECTED", false, 0, 0, 0, a, b);
        long duration = b.elapsedMs - a.elapsedMs;
        long awake = b.uptimeMs - a.uptimeMs;
        if (duration <= 0 || duration > MAX_MS || awake < 0 || awake > duration + 2000)
            return invalid("CLOCK_INVALID_OR_REBOOT", bootVerified, 0, 0, 0, a, b);
        long suspended = Math.max(0, duration - awake);
        if (a.plugged != 0 || b.plugged != 0)
            return invalid("EXTERNAL_POWER_OR_UNKNOWN", bootVerified, duration, awake, suspended, a, b);
        if (a.fixes != b.fixes)
            return invalid("FIX_REQUEST_CHANGED", bootVerified, duration, awake, suspended, a, b);
        if ((a.energyNwh > 0 && b.energyNwh > a.energyNwh)
                || (a.chargeUah >= 0 && b.chargeUah > a.chargeUah))
            return invalid("COUNTER_INCREASE", bootVerified, duration, awake, suspended, a, b);
        String method = "UNAVAILABLE";
        double wh = -1;
        if (a.energyNwh > 0 && b.energyNwh > 0 && a.energyNwh > b.energyNwh) {
            wh = (a.energyNwh - b.energyNwh) / 1e9d;
            method = "ENERGY_COUNTER";
        } else if (a.chargeUah >= 0 && b.chargeUah >= 0 && a.chargeUah > b.chargeUah
                && a.voltageMv >= 2500 && b.voltageMv >= 2500) {
            wh = (a.chargeUah - b.chargeUah) * ((a.voltageMv + b.voltageMv) / 2d) / 1e9d;
            method = "CHARGE_VOLTAGE_ESTIMATE";
        }
        String state = duration < MIN_MS ? "SHORT_TRIAL"
                : wh < 0 ? "ENERGY_UNAVAILABLE_OR_UNRESOLVED"
                : !bootVerified ? "BOOT_NOT_VERIFIED" : "PROVISIONAL";
        return new Result(state, method, duration, awake, suspended, wh, bootVerified, a.fixes,
                a.voltageMv, b.voltageMv, a.temperatureDeciC, b.temperatureDeciC, a.plugged, b.plugged);
    }
    private static Result invalid(String reason, boolean boot, long duration,
            long awake, long suspended, Sample a, Sample b) {
        return new Result(reason, "UNAVAILABLE", duration, awake, suspended, -1, boot,
                a == null ? 0 : a.fixes, a == null ? -1 : a.voltageMv, b == null ? -1 : b.voltageMv,
                a == null ? -1 : a.temperatureDeciC, b == null ? -1 : b.temperatureDeciC,
                a == null ? -1 : a.plugged, b == null ? -1 : b.plugged);
    }
    private static String endpointPower(int mask) {
        return mask == 0 ? "battery" : mask > 0 && mask <= 15 ? "external" : "unknown";
    }
    private static String known(int n) { return n >= 0 ? Integer.toString(n) : "unknown"; }
    private static String number(double n) {
        return n >= 0 && Double.isFinite(n) ? String.format(Locale.US, "%.6f", n) : "unknown";
    }
    private static String percent(long suspended, long total) {
        return total > 0 ? String.format(Locale.US, "%.1f", 100d * suspended / total) : "unknown";
    }
}
