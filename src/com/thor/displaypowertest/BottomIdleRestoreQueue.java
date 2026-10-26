package com.thor.displaypowertest;

/**
 * Coalesces stale restoration requests into the latest exact generation.
 * A vendor power operation may block for many seconds: never run one on the
 * input-event Looper and never spawn a worker per MOVE/UP callback.
 *
 * Idle feature OFF creates no worker, polls, timers or event registrations.
 * A checked restore lease is always revalidated by DisplayActionCoordinator.
 */
public final class BottomIdleRestoreQueue {
    public interface RestoreAction { void apply(long lease); }
    public interface ErrorHandler { void report(Throwable error); }

    private final Object gate = new Object();
    private final RestoreAction restore;
    private final ErrorHandler errors;
    private boolean running;
    private long next = -1L;

    public BottomIdleRestoreQueue(RestoreAction restore, ErrorHandler errors) {
        if (restore == null || errors == null) throw new IllegalArgumentException("callback");
        this.restore = restore;
        this.errors = errors;
    }

    /** True if queued/coalesced; false for invalid lease or worker start failure. */
    public boolean request(long lease) {
        if (lease < 0L) return false;
        synchronized (gate) {
            next = lease; // the newest lease supersedes any waiting restore
            if (running) return true;
            running = true;
        }
        try {
            Thread worker = new Thread(this::drain, "thor-idle-restore");
            worker.setDaemon(true);
            worker.start();
            return true;
        } catch (Throwable failure) {
            synchronized (gate) {
                running = false;
                next = -1L;
            }
            try { errors.report(failure); } catch (Throwable ignored) { }
            return false;
        }
    }

    private void drain() {
        for (;;) {
            final long lease;
            synchronized (gate) {
                lease = next;
                next = -1L;
                if (lease < 0L) {
                    running = false;
                    return;
                }
            }
            try {
                restore.apply(lease);
            } catch (Throwable failure) {
                // One compositor failure must not strand later user touches.
                try { errors.report(failure); } catch (Throwable ignored) { }
            }
        }
    }
}
