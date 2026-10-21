package com.thor.displaypowertest;

import android.os.Process;
import android.os.SystemClock;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Synthetic hardware/boot state ONLY. Real producer, UID admission, singleton
 * lock and server are compiled unchanged apart from the disposable namespace.
 */
public final class RetirementFixtureHardware {
    static final String DISPOSABLE = "org.example.rfixture";
    static final File DIRECTORY = new File("/data/user/0/" + DISPOSABLE + "/files");
    static final File ARMED = new File(DIRECTORY, "retirement-fixture-armed");
    static final File READY = new File(DIRECTORY, "retirement-fixture-ready");
    static final File STOP = new File(DIRECTORY, "retirement-fixture-stop");
    static final File MODE = new File(DIRECTORY, "retirement-fixture-mode");
    static final File FAILED = new File(DIRECTORY, "retirement-fixture-failed");
    static String selectedMode = "0";
    static boolean predecessor;
    static boolean failedStart;
    static long began;

    private RetirementFixtureHardware() {}

    public static void serve(boolean old) throws Exception {
        if (Process.myUid() != 0 || !SecureChannel.path().startsWith(DIRECTORY + "/")) {
            throw new IllegalStateException("Disposable namespace/root prerequisite not satisfied");
        }
        String armed = new String(Files.readAllBytes(ARMED.toPath()), StandardCharsets.UTF_8);
        if (!"1.7.1".equals(armed) && !"1.7.2".equals(armed) && !"1.7.3".equals(armed)
                && !"1.7.4".equals(armed) && !"1.7.5".equals(armed)) {
            throw new IllegalStateException("Fixture was not prepared by the disposable app");
        }
        if (old && (!DaemonIdentity.RUNTIME_ID.equals(armed) || READY.exists() || STOP.exists())) {
            throw new IllegalStateException("Wrong predecessor or non-fresh fixture");
        }
        predecessor = old;
        failedStart = FAILED.isFile();
        if (failedStart && old) throw new IllegalStateException("Recovery uses the current real producer");
        selectedMode = new String(Files.readAllBytes(MODE.toPath()), StandardCharsets.US_ASCII);
        if (!"0".equals(selectedMode) && !"1".equals(selectedMode)) {
            throw new IllegalStateException("Prepared fixture mode is invalid");
        }
        began = SystemClock.elapsedRealtime();
        DaemonState.setEnabled(true);
        DaemonState.setMode(selectedMode);
        Thread expiry = new Thread(() -> {
            // Self-exit only. Never signal another process or touch panel/CPU state.
            while (SystemClock.elapsedRealtime() - began < 120000L) {
                if (STOP.isFile()) break;
                SystemClock.sleep(100L);
            }
            System.exit(0);
        }, "disposable-retirement-self-exit");
        expiry.setDaemon(true);
        expiry.start();
        new DaemonIpcServer(SecureChannel.listen(),
                new DaemonCommandHandler(new CpuFixController(), new BootTrace(), Process.myPid()))
                .serveForever();
    }
}

final class CpuFixController {
    boolean desired() { return false; }
    String phase() { return "CONFIRMED"; }
    String handoffState() { return "NONE"; }
    String apply(boolean enabled) { throw new AssertionError("Fixture forbids CPU effects"); }
}
final class BootTrace { void mark(String action, String detail) {} }
final class BootSafety {
    static boolean isHeld() {
        return !RetirementFixtureHardware.predecessor && !RetirementFixtureHardware.READY.isFile();
    }
    static String phase() {
        return RetirementFixtureHardware.failedStart ? "HANDOFF_FAILED"
                : isHeld() ? "WAITING_FOR_ANDROID" : "READY";
    }
    static long phaseAgeMs() {
        return SystemClock.elapsedRealtime() - RetirementFixtureHardware.began;
    }
}
final class DisplayActionCoordinator {
    static String status() { return "CONFIRMED"; }
    static long generation() { return 1L; }
    static String requestFix(boolean enabled) { throw new AssertionError("Fixture forbids display effects"); }
}
final class WatcherSupervisor {
    static String health() { return "RUNNING"; }
    static long lastSampleAt() { return SystemClock.elapsedRealtime(); }
}
final class WatcherCadence { static String snapshotFields() { return ";fixture_hardware=1"; } }
final class DisplayHardware { static String getProperty() { return "1"; } }
final class LidGuard {
    static boolean isEnabled() { return false; }
    static boolean setEnabled(boolean enabled) { throw new AssertionError("Fixture forbids Guard effects"); }
    static String lid() { return "1".equals(RetirementFixtureHardware.selectedMode) ? "unknown" : "open"; }
    static String state() { return "OFF"; }
    static int blockedWakes() { return 0; }
    static String lastAction() { return "NONE"; }
    static String externalDisplay() { return "unknown"; }
}
final class Telemetry {
    static final class CpuSnapshot {
        long littleCurrent, littleMax, bigCurrent, bigMax, primeCurrent, primeMax;
        long littleMaxTicks, littleHighTicks, littleTotalTicks;
        long bigMaxTicks, bigHighTicks, bigTotalTicks;
        int utilization;
    }
    static final class PowerSnapshot {
        double usbWatts, batteryChargeWatts, systemProxyWatts, batteryWatts;
        String source = "unknown";
    }
    static CpuSnapshot readCpu() { return new CpuSnapshot(); }
    static PowerSnapshot readPower() { return new PowerSnapshot(); }
    static String[] crtcActivePair() { return new String[] {"1", "1".equals(RetirementFixtureHardware.selectedMode) ? "0" : "1"}; }
    static String systemLoadFixStateForSnapshot() { return "0"; }
    static String verifyDrm() { throw new AssertionError("Fixture forbids DRM effects"); }
}
final class EarlyCpuOptIn {
    static void setEnabled(android.content.Context context, boolean enabled) {
        if (enabled) throw new AssertionError("Fixture forbids Direct-Boot CPU effects");
    }
}
final class RootLogFiles { static final String DAEMON_LOG = "/dev/null"; }
