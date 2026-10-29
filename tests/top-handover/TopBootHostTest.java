package com.thor.displaypowertest;

/** Actual boot, CPU and display coordinators; inert OS/store/clock boundaries. */
public final class TopBootHostTest {
    static final String BOOT = "00000000-0000-0000-0000-000000000001";
    static int processStarts, powerCalls, wakeAcquires, wakeReleases, writes, deletes;
    static String lastAction = "";
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        boolean applied = args.length == 1 && args[0].equals("applied");
        CpuBootAttemptStore.result = applied
                ? CpuBootAttemptStore.ReadResult.valid(new CpuBootAttemptModel.Attempt(
                    BOOT, "1", "0", "101", CpuBootAttemptModel.Phase.APPLIED, 90000L))
                : CpuBootAttemptStore.ReadResult.absent();
        BootSession session = new BootSession(true, android.os.SystemClock.elapsedRealtime(), true, false);
        BootSafety.begin(true);
        BootTrace trace = new BootTrace();
        SystemProbe probe = new SystemProbe();
        BootCoordinator boot = new BootCoordinator(session, probe, trace);
        CpuFixController cpu = new CpuFixController(probe, trace, new TransitionWakeLock(), session, boot);
        boot.attach(cpu);
        check(cpu.adoptEarlyAttempt().startsWith("ok=1"), "early absent boundary");
        check(cpu.currentComposerHasAppliedAttempt() == applied, "actual persisted CPU-proof classification");
        boot.run(false);
        check(powerCalls == 0, "TOP boot must not issue a power command");
        check(processStarts == 0 && wakeAcquires == 0, "TOP boot must not restart composer or write property");
        check(writes == 0 && deletes == 0, "TOP boot keeps the confirmed CPU marker unchanged");
        check(!BootSafety.isHeld() && "READY".equals(BootSafety.phase()) && !session.active(),
                "TOP boot reaches actual READY");
        check("BOOT_READY".equals(lastAction), "real coordinator completed");
        check("CONFIRMED".equals(cpu.phase()) && "CONFIRMED".equals(DisplayActionCoordinator.status()),
                "real CPU and display controllers confirmed");
        check(wakeReleases == 1, "normal boot wake-release boundary");
        System.out.println("PASS real TOP boot; marker=" + (applied ? "APPLIED" : "ABSENT")
                + ";READY;power_calls=0;process_starts=0;marker_writes=0;HOST only");
    }
}

final class SystemProbe {
    static final String COMPOSER_SERVICE = "fixture-unused";
    boolean bootCompleted() { return true; }
    boolean composerRunning() { return true; }
    String composerPid() { return "201"; }
    static boolean binary(String value) { return "0".equals(value) || "1".equals(value); }
}
final class BootTrace {
    static final String PATH = "fixture-unused";
    void mark(String event, String detail) {}
    void boot(String event) {}
}
final class TransitionWakeLock {
    boolean acquire() { TopBootHostTest.wakeAcquires++; return false; }
    void release() { TopBootHostTest.wakeReleases++; }
}
final class SecureChannel { static String currentBootId() { return TopBootHostTest.BOOT; } }
final class Telemetry {
    static String systemLoadFixObservation() { return "1"; }
    static String systemLoadFixState() { return "1"; }
    static String[] crtcActivePair() { return new String[]{"1", "0"}; }
    static String topCrtcActive() { return "1"; }
    static String bottomCrtcActive() { return "0"; }
}
final class WatcherSupervisor { static String health() { return "RUNNING"; } }
final class DaemonState {
    static boolean isEnabled() { return true; }
    static String getMode() { return "1"; }
    static void setEnabled(boolean value) { throw new AssertionError("unexpected preference change"); }
    static void setMode(String value) { throw new AssertionError("unexpected mode change"); }
    static void setLastAction(String action) { TopBootHostTest.lastAction = action; }
    static void onRepairBegin() { throw new AssertionError("unexpected repair"); }
    static void onRepairEnd(String result) { throw new AssertionError("unexpected repair"); }
}
final class DisplayModeReader { static String current() { return "1"; } }
final class DisplayHardware {
    static String getProperty() { return "0"; }
    static boolean apply(boolean on, String reason) { TopBootHostTest.powerCalls++; return false; }
    static void alignPropertyWithCrtc(String value) { throw new AssertionError("unexpected alignment"); }
    static void notifyWatcherSample(boolean fixEnabled, String bottomCrtc) {}
}
final class WatcherCadence {
    static void onModeChanged() {}
    static long beginDrmRead(Object... args) { return 1L; }
    static void completeDrmRead(long token) {}
    static void onWakeRepairScheduled() {}
}
final class WakeRepairScheduler {
    static boolean isPending() { return false; }
    static boolean isDue() { return false; }
    static void cancel() {}
    static void scheduleFromWake() { throw new AssertionError("unexpected scheduling"); }
    static void markDone() {}
    static void noteDisplayOn() {}
}
final class LidGuard {
    static boolean isEnabled() { return false; }
    static boolean setEnabled(boolean value) { throw new AssertionError("unexpected Guard change"); }
    static boolean isSleepDecisionCurrent(long epoch) { return false; }
    static String externalDisplayCached() { return "unknown"; }
    static boolean onWakeEvent() { return false; }
}
final class RootLogFiles {
    static final String SHELL_GUARD = "fixture-unused";
    static final String DAEMON_LOG = "fixture-unused";
}
final class ProcessWait {
    static boolean exited(Process process, long timeout) { throw new AssertionError("unexpected process wait"); }
}
/** Shadows only the OS-launch boundary; no shell/native process can execute. */
final class ProcessBuilder {
    ProcessBuilder(String... argv) {}
    ProcessBuilder redirectErrorStream(boolean value) { return this; }
    java.util.Map<String, String> environment() { return new java.util.HashMap<>(); }
    Process start() throws java.io.IOException {
        TopBootHostTest.processStarts++;
        throw new java.io.IOException("fixture forbids OS execution");
    }
}
final class CpuBootAttemptStore {
    enum State {ABSENT, VALID, CORRUPT}
    static final class ReadResult {
        final State state;
        final CpuBootAttemptModel.Attempt attempt;
        ReadResult(State state, CpuBootAttemptModel.Attempt attempt) { this.state = state; this.attempt = attempt; }
        static ReadResult valid(CpuBootAttemptModel.Attempt attempt) { return new ReadResult(State.VALID, attempt); }
        static ReadResult absent() { return new ReadResult(State.ABSENT, null); }
    }
    static ReadResult result;
    static ReadResult read() { return result; }
    static void write(CpuBootAttemptModel.Attempt attempt) { TopBootHostTest.writes++; throw new AssertionError("unexpected marker write"); }
    static void deleteTrusted() { TopBootHostTest.deletes++; throw new AssertionError("unexpected marker deletion"); }
}
final class EarlyCpuBootAttemptStore {
    enum State {ABSENT, VALID, CORRUPT}
    static final class ReadResult { State state = State.ABSENT; CpuBootAttemptModel.Attempt attempt; }
    static ReadResult read() { return new ReadResult(); }
}
