package com.thor.displaypowertest;

import android.os.SystemClock;

/**
 * Observability only, for the daemon launched after a compositor restart.
 * sys.boot_completed keeps its first-boot value across that restart, so it
 * cannot show when Android finishes its second visual boot. Record edges of
 * signals that do restart with the framework. Nothing here gates or changes
 * display, CPU or lid state.
 */
public final class AndroidRecoveryTrace implements Runnable {
    private final SystemProbe probe;
    private final BootTrace trace;

    public AndroidRecoveryTrace(SystemProbe probe, BootTrace trace) {
        this.probe = probe;
        this.trace = trace;
    }

    @Override public void run() {
        long started = SystemClock.elapsedRealtime();
        String bootanim = null;
        String systemServer = null;
        String packageService = null;
        String settingsService = null;
        boolean sawBootanimRunning = false;
        String reason = "TIMEOUT";
        while (SystemClock.elapsedRealtime() - started < 60000L) {
            String value = probe.property("service.bootanim.exit");
            String nextBootanim = SystemProbe.binary(value) ? value : "?";
            String nextSystemServer = probe.pidOf("system_server");
            String nextPackage = probe.serviceFound("package");
            String nextSettings = probe.serviceFound("settings");
            if (!nextBootanim.equals(bootanim)) {
                trace.mark("ANDROID_BOOTANIM_EXIT", "value=" + nextBootanim);
            }
            if (!nextSystemServer.equals(systemServer)) {
                trace.mark("ANDROID_SYSTEM_SERVER", "pid=" + nextSystemServer);
            }
            if (!nextPackage.equals(packageService)) {
                trace.mark("ANDROID_PACKAGE_SERVICE", "found=" + nextPackage);
            }
            if (!nextSettings.equals(settingsService)) {
                trace.mark("ANDROID_SETTINGS_SERVICE", "found=" + nextSettings);
            }
            bootanim = nextBootanim;
            systemServer = nextSystemServer;
            packageService = nextPackage;
            settingsService = nextSettings;
            if ("0".equals(bootanim)) {
                sawBootanimRunning = true;
            } else if ("1".equals(bootanim) && sawBootanimRunning) {
                reason = "BOOTANIM_EXIT";
                break;
            }
            // Four subprocess probes per pass: keep this diagnostic watcher
            // light enough not to distort the boot timing it records.
            try { Thread.sleep(1000L); } catch (InterruptedException ignored) {
                reason = "INTERRUPTED";
                break;
            }
        }
        trace.mark("ANDROID_RECOVERY_TRACE_END", "reason=" + reason);
    }
}
