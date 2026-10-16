package com.thor.displaypowertest;

import java.util.concurrent.atomic.AtomicInteger;

/** One-shot worker handoff. Only a not-yet-started queued task can be reclaimed. */
final class UpdateTaskDispatch implements Runnable {
    private static final int QUEUED = 0, RUNNING = 1, FINISHED = 2, ABORTED = 3;
    private final AtomicInteger state = new AtomicInteger(QUEUED);
    private final Runnable task;
    private final Runnable abandoned;

    UpdateTaskDispatch(Runnable task, Runnable abandoned) {
        if (task == null || abandoned == null) throw new NullPointerException("callbacks");
        this.task = task;
        this.abandoned = abandoned;
    }

    @Override public void run() {
        if (!state.compareAndSet(QUEUED, RUNNING)) return;
        try {
            task.run();
        } finally {
            state.set(FINISHED);
        }
    }

    /** True iff shutdown claimed this queued task before a worker could start it. */
    boolean abortIfQueued() {
        if (!state.compareAndSet(QUEUED, ABORTED)) return false;
        abandoned.run();
        return true;
    }
}
