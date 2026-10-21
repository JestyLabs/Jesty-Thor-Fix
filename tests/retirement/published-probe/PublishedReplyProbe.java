package com.thor.displaypowertest;

import java.util.HashSet;
import java.util.Set;

/** Owner-local: actual pinned predecessor producers, actual candidate readers. */
public final class PublishedReplyProbe {
    public static void main(String[] args) {
        DaemonState.setMode("0");
        DaemonCommandHandler handler = new DaemonCommandHandler(new CpuFixController(), new BootTrace(), 12000);
        String identity = handler.handle('I');
        String snapshot = handler.handle('Q');
        for (String response : new String[] {identity, snapshot}) {
            Set<String> keys = new HashSet<String>();
            for (String item : response.split(";")) {
                String key = item.split("=", 2)[0];
                if (!keys.add(key)) throw new AssertionError("real producer emits duplicate field: " + key);
            }
        }
        int base = PreviousSecureDaemonIdentity.safePid(identity, snapshot);
        int live = LiveHandoverPolicy.safePid(identity, snapshot, true, false, false);
        if (base != 12000) throw new AssertionError("unexpected base policy rejection");
        int expected = "1.7.1".equals(DaemonIdentity.RUNTIME_ID) ? -1 : 12000;
        if (live != expected) throw new AssertionError("unexpected live policy result: " + live);
        System.out.println("SOURCE_PROBE version=" + DaemonIdentity.RUNTIME_ID
            + ";q_runtime_version=" + snapshot.contains(";runtime_version=")
            + ";base_pid=" + base + ";live_pid=" + live + ";unique_fields=true");
        System.out.println("I=" + identity);
        System.out.println("Q=" + snapshot);
    }
}

final class CpuFixController {
    boolean desired() { return false; }
    String phase() { return "CONFIRMED"; }
    String handoffState() { return "NONE"; }
    String apply(boolean enabled) { throw new AssertionError("unexpected CPU effect"); }
}
final class BootTrace { void mark(String action, String detail) {} }
final class BootSafety {
    static boolean isHeld() { return false; }
    static String phase() { return "READY"; }
    static long phaseAgeMs() { return 0L; }
}
final class SecureChannel { static final int PROTOCOL = 2; }
final class DisplayActionCoordinator {
    static String observedStatus = "CONFIRMED";
    static long observedGeneration = 1L;
    static String status() { return observedStatus; }
    static long generation() { return observedGeneration; }
    static String requestFix(boolean on) { throw new AssertionError("unexpected display effect"); }
}
final class WatcherSupervisor {
    static String health() { return "RUNNING"; }
    static long lastSampleAt() { return 100000L; }
}
final class WatcherCadence { static String snapshotFields() { return ";fixture_sample=1"; } }
final class DisplayHardware { static String getProperty() { return "1"; } }
final class LidGuard {
    static boolean observedEnabled;
    static String observedLid = "open";
    static String observedExternal = "unknown";
    static boolean isEnabled() { return observedEnabled; }
    static boolean setEnabled(boolean on) { throw new AssertionError("unexpected Guard effect"); }
    static String lid() { return observedLid; }
    static String state() { return "OFF"; }
    static int blockedWakes() { return 0; }
    static String lastAction() { return "NONE"; }
    static String externalDisplay() { return observedExternal; }
}
final class Telemetry {
    static String observedBottom = "1";
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
    static String[] crtcActivePair() { return new String[] {"1", observedBottom}; }
    static String systemLoadFixStateForSnapshot() { return "0"; }
    static String verifyDrm() { throw new AssertionError("unexpected DRM query"); }
}
