package com.thor.displaypowertest;

/** Actual controller and attempt model; only wait/probes/stores are adapted. */
public final class ResumeControllerHostTest {
    private static int checks;
    private static int cases;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    public static void observeLog(String message) {
        if (message.startsWith("CPU attempt resume stopped:")
                || message.equals("CPU attempt resume exceeded bounded wait")) ResumeFixture.finish();
    }
    private static CpuFixController controller() {
        ResumeFixture.reset();
        return new CpuFixController(new SystemProbe(), new BootTrace(), new TransitionWakeLock(),
            new BootSession(false, 0L, true, false), reason -> { throw new AssertionError("unexpected recovery"); });
    }
    private static void done(String id, int attempts, int waits, int releases) {
        check(ResumeFixture.calls == attempts, id + " attempts");
        check(ResumeFixture.waits == waits, id + " waits");
        check(ResumeFixture.releases == releases, id + " wake release");
        check(ResumeFixture.writes == 0 && ResumeFixture.deletes == 0, id + " no marker mutation");
        cases++;
        System.out.println("PASS " + id + ";attempts=" + attempts + ";waits=" + waits + ";releases=" + releases);
    }
    private static void terminal() throws Exception {
        CpuFixController controller = controller();
        CpuBootAttemptStore.result = CpuBootAttemptStore.ReadResult.corrupt();
        controller.resumePersistedAttempt();
        ResumeFixture.await();
        check("ERROR".equals(controller.phase()), "CR-terminal controller failure flag");
        done("CR-terminal", 1, 0, 1);
    }
    private static void exhausted() throws Exception {
        CpuFixController controller = controller();
        ResumeFixture.alwaysNotReady = true;
        controller.resumePersistedAttempt();
        ResumeFixture.await();
        done("CR-exhausted", 130, 130, 0);
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if ("terminal".equals(args[0])) terminal(); else exhausted();
            return;
        }
        CpuFixController controller = controller();
        CpuBootAttemptStore.result = CpuBootAttemptStore.ReadResult.absent();
        controller.resumePersistedAttempt();
        done("CR-absent", 0, 0, 0);
        controller = controller();
        controller.resumePersistedAttempt();
        // The inert release endpoint identifies the actual thread; join its
        // completion instead of inventing a successful controller callback.
        awaitSuccessfulWorker();
        check("CONFIRMED".equals(controller.phase()), "CR-success confirmed state");
        done("CR-success", 1, 0, 1);
        terminal();
        controller = controller();
        ResumeFixture.notReadyCalls = 2;
        controller.resumePersistedAttempt();
        awaitSuccessfulWorker();
        done("CR-transient", 3, 2, 1);
        exhausted();
        controller = controller();
        ResumeFixture.alwaysNotReady = ResumeFixture.interruptWait = true;
        controller.resumePersistedAttempt();
        ResumeFixture.await();
        check(ResumeFixture.worker.isInterrupted(), "CR-interrupted flag restored");
        done("CR-interrupted", 1, 1, 0);
        System.out.println("ResumeControllerHostTest passed: " + cases + " cases, " + checks
                + " checks; real controller/model, wait adapted, no vendor/Android");
    }
    private static void awaitSuccessfulWorker() throws Exception {
        ResumeFixture.await();
        check(ResumeFixture.worker.isDaemon(), "CR-success daemon thread retained");
        check("cpu-fix-attempt-resume".equals(ResumeFixture.worker.getName()), "CR-success thread identity");
    }
}
