package com.thor.displaypowertest;

import java.io.BufferedReader;
import java.io.InputStreamReader;

/** Fresh mode read at the display-command boundary. */
final class DisplayModeReader {
    private DisplayModeReader() {}

    static String current() {
        Process process = null;
        try {
            process = new ProcessBuilder("/system/bin/settings", "get", "system",
                    "dual_screen_display_mode").start();
            if (!ProcessWait.exited(process, 2000L) || process.exitValue() != 0) return "?";
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream()))) {
                String value = reader.readLine();
                return BootSafety.knownMode(value) ? value : "?";
            }
        } catch (Throwable error) {
            return "?";
        } finally {
            if (process != null) process.destroy();
        }
    }
}
