package com.thor.displaypowertest;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Host JDK executor; no Android/native services or APK operations. */
public final class UpdateTaskDispatchTest {
    private static int checks;
    private static void check(boolean value, String label) {
        checks++;
        if (!value) throw new AssertionError(label);
    }
    private static ThreadPoolExecutor executor() {
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS,
                new LinkedBlockingQueue<Runnable>());
    }
    private static void queuedThenDrained() throws Exception {
        ThreadPoolExecutor pool = executor();
        CountDownLatch occupied = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger();
        AtomicInteger abandoned = new AtomicInteger();
        try {
            pool.execute(() -> {
                occupied.countDown();
                try { release.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
            });
            check(occupied.await(3, TimeUnit.SECONDS), "worker occupied");
            UpdateTaskDispatch item = new UpdateTaskDispatch(executions::incrementAndGet,
                    abandoned::incrementAndGet);
            pool.execute(item);
            check(pool.getQueue().size() == 1, "install task actually queued");
            List<Runnable> drained = pool.shutdownNow();
            check(drained.size() == 1 && drained.get(0) == item,
                    "shutdownNow returns the exact accepted but unstarted item");
            check(item.abortIfQueued(), "drained task claimed");
            check(!item.abortIfQueued(), "double drain cannot clean twice");
            item.run();
            check(executions.get() == 0 && abandoned.get() == 1,
                    "aborted queued item cannot execute after cleanup");
        } finally {
            release.countDown();
            pool.shutdownNow();
            check(pool.awaitTermination(3, TimeUnit.SECONDS), "pool terminates");
        }
    }
    private static void startedBeforeShutdown() throws Exception {
        ThreadPoolExecutor pool = executor();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger executions = new AtomicInteger(), abandoned = new AtomicInteger();
        try {
            UpdateTaskDispatch item = new UpdateTaskDispatch(() -> {
                started.countDown();
                try { release.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); }
                executions.incrementAndGet();
            }, abandoned::incrementAndGet);
            pool.execute(item);
            check(started.await(3, TimeUnit.SECONDS), "task started in JDK worker");
            List<Runnable> drained = pool.shutdownNow();
            check(!drained.contains(item) && !item.abortIfQueued(),
                    "running worker must keep its own cleanup ownership");
            release.countDown();
            check(pool.awaitTermination(3, TimeUnit.SECONDS), "worker finishes");
            check(executions.get() == 1 && abandoned.get() == 0,
                    "worker owns exactly one completion");
        } finally {
            release.countDown(); pool.shutdownNow();
        }
    }
    private static void concurrentStartVsDrain() throws Exception {
        for (int n = 0; n < 150; n++) {
            AtomicInteger ran = new AtomicInteger(), aborted = new AtomicInteger();
            UpdateTaskDispatch item = new UpdateTaskDispatch(ran::incrementAndGet,
                    aborted::incrementAndGet);
            CountDownLatch go = new CountDownLatch(1);
            Thread starter = new Thread(() -> {
                try { go.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
                item.run();
            });
            Thread stopper = new Thread(() -> {
                try { go.await(); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); return; }
                item.abortIfQueued();
            });
            starter.start(); stopper.start(); go.countDown();
            starter.join(3000L); stopper.join(3000L);
            check(!starter.isAlive() && !stopper.isAlive(), "bounded contenders");
            check(ran.get() + aborted.get() == 1, "one start or abort, never both");
            item.run(); item.abortIfQueued();
            check(ran.get() + aborted.get() == 1, "retired task remains one-shot");
        }
    }
    public static void main(String[] args) throws Exception {
        queuedThenDrained(); startedBeforeShutdown(); concurrentStartVsDrain();
        System.out.println("UpdateTaskDispatchTest passed: " + checks
                + " checks, including JDK queue draining and 150 competing interleavings");
    }
}
