package com.thor.displaypowertest;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;

/**
 * Read-only process and property probes used by the root daemon. Every
 * subprocess wait is bounded by ProcessWait; any failure reads as "?".
 */
public final class SystemProbe {
    public static final String COMPOSER_SERVICE = "vendor.qti.hardware.display.composer";
    private static final String COMPOSER_PROCESS = "vendor.qti.hardware.display.composer-service";

    public String property(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("getprop", name).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0)
                return "?";
            BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()));
            String value = reader.readLine();
            reader.close();
            return value == null || value.trim().isEmpty() ? "?" : value.trim();
        } catch (Throwable ignored) { return "?"; }
        finally { if (process != null) process.destroy(); }
    }

    public boolean bootCompleted() {
        return "1".equals(property("sys.boot_completed"));
    }

    public boolean composerRunning() {
        return "running".equals(property("init.svc." + COMPOSER_SERVICE));
    }

    public String composerPid() {
        return pidOf(COMPOSER_PROCESS);
    }

    public String pidOf(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("pidof", name).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0)
                return "?";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return value != null && value.trim().matches("[0-9]+")
                        ? value.trim() : "?";
            }
        } catch (Throwable ignored) { return "?"; }
        finally { if (process != null) process.destroy(); }
    }

    /** "1" when servicemanager lists the service, "0" when not, "?" on failure. */
    public String serviceFound(String name) {
        Process process = null;
        try {
            process = new ProcessBuilder("service", "check", name).start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0)
                return "?";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                if (value == null) return "?";
                if (value.endsWith(": found")) return "1";
                return value.endsWith(": not found") ? "0" : "?";
            }
        } catch (Throwable ignored) { return "?"; }
        finally { if (process != null) process.destroy(); }
    }

    /** Process start on the boot clock, so it is comparable with elapsed_ms. */
    public long processStartMs() {
        try (BufferedReader reader = new BufferedReader(new FileReader("/proc/self/stat"))) {
            String stat = reader.readLine();
            String[] fields = stat.substring(stat.lastIndexOf(')') + 2).split(" ");
            long ticks = Long.parseLong(fields[19]);
            long hz = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK);
            return hz > 0 ? ticks * 1000L / hz : -1L;
        } catch (Throwable ignored) {
            return -1L;
        }
    }

    public static String envNumber(String name) {
        String value = System.getenv(name);
        return value != null && value.matches("-?[0-9]{1,18}") ? value : "?";
    }

    public static String envToken(String name) {
        String value = System.getenv(name);
        return value != null && value.matches("[A-Z_]{1,32}") ? value : "?";
    }

    public static boolean binary(String value) {
        return "0".equals(value) || "1".equals(value);
    }
}
