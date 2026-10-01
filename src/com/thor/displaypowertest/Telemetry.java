package com.thor.displaypowertest;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.util.Locale;

public final class Telemetry {
    private static long previousTotal = -1L;
    private static long previousIdle = -1L;

    private Telemetry() {}

    public static final class CpuSnapshot {
        public long littleCurrent, littleMax, bigCurrent, bigMax, primeCurrent, primeMax;
        public long littleMaxTicks, littleHighTicks, littleTotalTicks;
        public long bigMaxTicks, bigHighTicks, bigTotalTicks;
        public int utilization;
    }

    public static final class PowerSnapshot {
        public double usbWatts = Double.NaN;
        public double batteryChargeWatts = Double.NaN;
        public double systemProxyWatts = Double.NaN;
        public double batteryWatts = Double.NaN;
        public String source = "unknown";
    }

    public static synchronized CpuSnapshot readCpu() {
        CpuSnapshot result = new CpuSnapshot();
        result.littleCurrent = frequency(0, "scaling_cur_freq");
        result.littleMax = maxFrequency(0);
        result.bigCurrent = frequency(3, "scaling_cur_freq");
        result.bigMax = maxFrequency(3);
        result.primeCurrent = frequency(7, "scaling_cur_freq");
        result.primeMax = maxFrequency(7);
        Residency littleResidency = residency(0, result.littleMax);
        Residency bigResidency = residency(3, result.bigMax);
        result.littleMaxTicks = littleResidency.maxTicks;
        result.littleHighTicks = littleResidency.highTicks;
        result.littleTotalTicks = littleResidency.totalTicks;
        result.bigMaxTicks = bigResidency.maxTicks;
        result.bigHighTicks = bigResidency.highTicks;
        result.bigTotalTicks = bigResidency.totalTicks;
        result.utilization = readUtilization();
        return result;
    }

    public static PowerSnapshot readPower() {
        PowerSnapshot result = new PowerSnapshot();
        long usbOnline = readLong("/sys/class/power_supply/usb/online");
        long usbCurrent = readLong("/sys/class/power_supply/usb/current_now");
        long usbVoltage = readLong("/sys/class/power_supply/usb/voltage_now");
        long batteryCurrent = readLongOrMin("/sys/class/power_supply/battery/current_now");
        long batteryVoltage = readLong("/sys/class/power_supply/battery/voltage_now");
        String batteryStatus = readText("/sys/class/power_supply/battery/status");
        boolean external = usbOnline == 1L;
        if (external) {
            result.source = "external";
            if (usbCurrent >= 0L && usbVoltage > 0L
                    && batteryCurrent != -1L && batteryVoltage > 0L) {
                result.usbWatts = watts(usbCurrent, usbVoltage);
                result.batteryChargeWatts = watts(batteryCurrent, batteryVoltage);
                result.systemProxyWatts = result.usbWatts - result.batteryChargeWatts;
            }
        } else if (usbOnline == 0L) {
            result.batteryWatts = DashboardStateModel.batteryWatts(
                    batteryCurrent, batteryVoltage, false, batteryStatus);
            result.source = Double.isNaN(result.batteryWatts) ? "unknown" : "battery";
        }
        return result;
    }

    public static String verifyDrm() {
        File state = new File("/sys/kernel/debug/dri/0/state");
        if (!state.canRead()) return "ok=0;error=DRM_STATE_UNREADABLE";
        StringBuilder out = new StringBuilder("ok=1");
        try {
            BufferedReader reader = new BufferedReader(new FileReader(state));
            String line;
            String crtc = null;
            int remaining = 0;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("crtc[181]")) { crtc = "181"; remaining = 14; }
                else if (trimmed.startsWith("crtc[243]")) { crtc = "243"; remaining = 14; }
                if (crtc != null && trimmed.startsWith("active=")) {
                    out.append(";crtc").append(crtc).append("=").append(trimmed.substring(7));
                    crtc = null;
                } else if (crtc != null && --remaining <= 0) {
                    out.append(";crtc").append(crtc).append("=UNKNOWN");
                    crtc = null;
                }
            }
            reader.close();
            if (out.indexOf("crtc181=") < 0) out.append(";crtc181=NOT_FOUND");
            if (out.indexOf("crtc243=") < 0) out.append(";crtc243=NOT_FOUND");
            return out.toString();
        } catch (Throwable error) {
            return "ok=0;error=" + error.getClass().getSimpleName().toUpperCase(Locale.US);
        }
    }

    public static String bottomCrtcActive() {
        return crtcActive("243");
    }

    public static String topCrtcActive() {
        return crtcActive("181");
    }

    private static String crtcActive(String target) {
        File state = new File("/sys/kernel/debug/dri/0/state");
        if (!state.canRead()) return "?";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(state));
            String line;
            boolean bottom = false;
            int remaining = 0;
            while ((line = reader.readLine()) != null) {
                String trimmed = line.trim();
                if (trimmed.startsWith("crtc[" + target + "]")) {
                    bottom = true;
                    remaining = 14;
                } else if (bottom && trimmed.startsWith("active=")) {
                    reader.close();
                    return trimmed.substring(7);
                } else if (bottom && --remaining <= 0) {
                    break;
                }
            }
            reader.close();
        } catch (Throwable ignored) {}
        return "?";
    }

    public static String systemLoadFixState() {
        Process process = null;
        try {
            process = new ProcessBuilder("getprop",
                    "vendor.display.disable_system_load_check").start();
            if (!process.waitFor(2L, java.util.concurrent.TimeUnit.SECONDS)) return "?";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                return PropertyState.binary(reader.readLine(), process.exitValue());
            }
        } catch (Throwable ignored) {
            return "?";
        } finally {
            if (process != null) process.destroy();
        }
    }

    private static long maxFrequency(int policy) {
        long value = frequency(policy, "scaling_max_freq");
        return value > 0 ? value : frequency(policy, "cpuinfo_max_freq");
    }

    private static long frequency(int policy, String file) {
        return readLong("/sys/devices/system/cpu/cpufreq/policy" + policy + "/" + file);
    }

    private static long readLong(String path) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            long value = Long.parseLong(reader.readLine().trim());
            reader.close();
            return value;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    private static long readLongOrMin(String path) {
        String value = readText(path);
        try { return Long.parseLong(value); }
        catch (Throwable ignored) { return Long.MIN_VALUE; }
    }

    private static String readText(String path) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String value = reader.readLine();
            reader.close();
            return value == null ? "" : value.trim();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private static final class Residency {
        long maxTicks;
        long highTicks;
        long totalTicks;
    }

    private static Residency residency(int policy, long maximum) {
        Residency result = new Residency();
        String path = "/sys/devices/system/cpu/cpufreq/policy" + policy
                + "/stats/time_in_state";
        try {
            BufferedReader reader = new BufferedReader(new FileReader(path));
            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 2) continue;
                long frequency = Long.parseLong(fields[0]);
                long ticks = Long.parseLong(fields[1]);
                result.totalTicks += ticks;
                if (frequency == maximum) result.maxTicks = ticks;
                // The reproduced Thor lock holds BIG at 2.707 GHz while the
                // policy advertises a 2.803 GHz ceiling. Treat the top 5% as
                // the pinned band instead of requiring the exact last step.
                if (maximum > 0L && frequency * 100L >= maximum * 95L) {
                    result.highTicks += ticks;
                }
            }
            reader.close();
        } catch (Throwable ignored) {
            result.maxTicks = result.highTicks = result.totalTicks = -1L;
        }
        return result;
    }

    private static double watts(long microAmps, long microVolts) {
        return ((double) microAmps * (double) microVolts) / 1_000_000_000_000d;
    }

    private static int readUtilization() {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/stat"));
            String[] fields = reader.readLine().trim().split("\\s+");
            reader.close();
            long total = 0L;
            for (int i = 1; i < fields.length; i++) total += Long.parseLong(fields[i]);
            long idle = Long.parseLong(fields[4]) + (fields.length > 5 ? Long.parseLong(fields[5]) : 0L);
            int value = 0;
            if (previousTotal >= 0 && total > previousTotal) {
                long deltaTotal = total - previousTotal;
                long deltaIdle = idle - previousIdle;
                value = (int) Math.max(0L, Math.min(100L, 100L * (deltaTotal - deltaIdle) / deltaTotal));
            }
            previousTotal = total;
            previousIdle = idle;
            return value;
        } catch (Throwable ignored) {
            return -1;
        }
    }
}
