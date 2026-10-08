import com.thor.displaypowertest.UpdateCommitGate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateCommitGateTest {
    public static void main(String[] args) throws Exception {
        UpdateCommitGate gate = new UpdateCommitGate();
        check(!gate.cancel(), "idle cannot be cancelled");
        check(!gate.beginCommit(), "idle cannot commit");
        check(gate.start(), "start a single attempt");
        check(!gate.start(), "an active attempt cannot be reset");
        check(gate.cancel(), "cancel is accepted before commit");
        check(gate.wasCancelled(), "accepted cancellation is visible");
        check(!gate.cancel(), "a cancelled attempt is not cancelled twice");
        check(!gate.beginCommit(), "accepted cancellation forbids commit");
        gate.finish();

        check(gate.start(), "next attempt can start after cleanup");
        check(!gate.wasCancelled(), "the next attempt is not cancelled");
        check(gate.beginCommit(), "a valid attempt can start commit");
        check(!gate.beginCommit(), "commit may start only once");
        check(!gate.cancel(), "Cancel cannot falsely claim success after commit begins");
        check(!gate.wasCancelled(), "commit in progress is not cancellation");
        gate.finish();

        // Exercise both possible orders of Cancel and commit on separate threads.
        // Exactly one request may be accepted; this is a policy/model test,
        // not a substitute for an Android PackageInstaller integration test.
        for (int iteration = 0; iteration < 64; iteration++) {
            UpdateCommitGate concurrent = new UpdateCommitGate();
            check(concurrent.start(), "start race attempt");
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            AtomicBoolean cancelWon = new AtomicBoolean(false);
            AtomicBoolean commitWon = new AtomicBoolean(false);
            Thread cancel = new Thread(() -> {
                ready.countDown();
                await(start);
                cancelWon.set(concurrent.cancel());
            }, "cancel-thread");
            Thread commit = new Thread(() -> {
                ready.countDown();
                await(start);
                commitWon.set(concurrent.beginCommit());
            }, "commit-thread");
            cancel.start();
            commit.start();
            ready.await();
            start.countDown();
            cancel.join();
            commit.join();
            check(cancelWon.get() != commitWon.get(),
                    "either Cancel or commit must win, never both/neither");
            check(concurrent.wasCancelled() == cancelWon.get(),
                    "accepted cancellation matches model state");
            concurrent.finish();
        }
        System.out.println("UpdateCommitGateTest passed");
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AssertionError("test thread interrupted", error);
        }
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
