package com.thor.displaypowertest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Inert boundaries for the actual controller. No files, vendor or Android. */
final class ResumeFixture {
    static final String BOOT = "00000000-0000-0000-0000-000000000001";
    static volatile CountDownLatch finished;
    static volatile Thread worker;
    static volatile int calls, waits, releases, writes, deletes;
    static boolean alwaysNotReady, interruptWait;
    static int notReadyCalls;
    static void reset() {
        finished = new CountDownLatch(1);
        worker = null;
        calls = waits = releases = writes = deletes = 0;
        alwaysNotReady = interruptWait = false;
        notReadyCalls = 0;
        CpuBootAttemptStore.result = CpuBootAttemptStore.ReadResult.valid(
            new CpuBootAttemptModel.Attempt(BOOT, "1", "0", "200",
                CpuBootAttemptModel.Phase.APPLIED, 1000L));
    }
    static void finish() { worker = Thread.currentThread(); finished.countDown(); }
    static void await() throws Exception {
        if (!finished.await(3L, TimeUnit.SECONDS)) throw new AssertionError("resume fixture did not terminate");
        worker.join(2000L);
        if (worker.isAlive()) throw new AssertionError("resume fixture thread leaked");
    }
}

final class ResumeWait {
    static void pause() throws InterruptedException {
        ResumeFixture.waits++;
        if (ResumeFixture.interruptWait) {
            // Signalled only to let the test join; the real loop restores flag.
            ResumeFixture.finish();
            throw new InterruptedException("synthetic wait interruption");
        }
    }
}

final class SystemProbe {
    static final String COMPOSER_SERVICE = "fixture-unused";
    boolean composerRunning() {
        ResumeFixture.calls++;
        return !ResumeFixture.alwaysNotReady && ResumeFixture.calls > ResumeFixture.notReadyCalls;
    }
    String composerPid() { return "201"; }
}
final class BootTrace {
    static final String PATH = "fixture-unused";
    void mark(String action, String detail) {}
}
final class TransitionWakeLock {
    boolean acquire() { throw new AssertionError("unexpected wake acquisition"); }
    void release() { ResumeFixture.releases++; ResumeFixture.finish(); }
}
final class BootSafety {
    static boolean isHeld() { return false; }
    static void handoffFailed() { throw new AssertionError("unexpected handoff mutation"); }
}
final class SecureChannel { static String currentBootId() { return ResumeFixture.BOOT; } }
final class Telemetry {
    static String systemLoadFixObservation() { return "1"; }
    static String systemLoadFixState() { return "1"; }
}
final class WatcherSupervisor { static String health() { return "RUNNING"; } }
final class DaemonState {
    static boolean isEnabled() { return true; }
    static void setLastAction(String action) { throw new AssertionError("unexpected daemon mutation"); }
}
final class LidGuard { static boolean isEnabled() { return false; } }
final class RootLogFiles {
    static final String SHELL_GUARD = "fixture-unused";
    static final String DAEMON_LOG = "fixture-unused";
}
final class ProcessWait {
    static boolean exited(Process process, long timeout) { throw new AssertionError("unexpected process"); }
}
final class CpuBootAttemptStore {
    enum State {ABSENT, VALID, CORRUPT}
    static final class ReadResult {
        final State state;
        final CpuBootAttemptModel.Attempt attempt;
        ReadResult(State state, CpuBootAttemptModel.Attempt attempt) { this.state = state; this.attempt = attempt; }
        static ReadResult valid(CpuBootAttemptModel.Attempt attempt) { return new ReadResult(State.VALID, attempt); }
        static ReadResult absent() { return new ReadResult(State.ABSENT, null); }
        static ReadResult corrupt() { return new ReadResult(State.CORRUPT, null); }
    }
    static volatile ReadResult result;
    static ReadResult read() { return result; }
    static void write(CpuBootAttemptModel.Attempt attempt) {
        ResumeFixture.writes++;
        throw new AssertionError("unexpected persistence write");
    }
    static void deleteTrusted() {
        ResumeFixture.deletes++;
        throw new AssertionError("unexpected persistence deletion");
    }
}
final class EarlyCpuBootAttemptStore {
    enum State {ABSENT, VALID, CORRUPT}
    static final class ReadResult { State state; CpuBootAttemptModel.Attempt attempt; }
    static ReadResult read() { throw new AssertionError("resume must not import early state"); }
}
