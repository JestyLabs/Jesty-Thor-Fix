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
        // Deterministically force both thread orders. A scheduling-dependent
        // race alone cannot prove that a prior accepted Cancel blocks handoff.
        for (int iteration = 0; iteration < 32; iteration++) {
            checkOrderedHandoff(true);
            checkOrderedHandoff(false);
        }
        System.out.println("UpdateCommitGateTest passed");
    }

    private static void checkOrderedHandoff(boolean cancelFirst) throws Exception {
        UpdateCommitGate gate = new UpdateCommitGate();
        check(gate.start(), "ordered handoff starts");
        CountDownLatch firstFinished = new CountDownLatch(1);
        AtomicBoolean firstAccepted = new AtomicBoolean(false);
        AtomicBoolean secondAccepted = new AtomicBoolean(false);
        Thread first = new Thread(() -> {
            try {
                firstAccepted.set(cancelFirst ? gate.cancel() : gate.beginCommit());
            } finally {
                firstFinished.countDown();
            }
        }, "ordered-first");
        Thread second = new Thread(() -> {
            await(firstFinished);
            secondAccepted.set(cancelFirst ? gate.beginCommit() : gate.cancel());
        }, "ordered-second");
        first.start();
        second.start();
        first.join();
        second.join();

        check(firstAccepted.get(), "first decision must win");
        check(!secondAccepted.get(), "late decision must be rejected");
        check(gate.wasCancelled() == cancelFirst,
                "cancellation state must reflect winning decision");
        check(!gate.beginCommit(), "cannot start a second commit in same attempt");
        gate.finish();
        check(gate.start(), "cleanup permits a fresh attempt");
        check(gate.cancel(), "fresh attempt may be cancelled");
        check(!gate.beginCommit(), "fresh cancellation still blocks commit");
        gate.finish();
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
