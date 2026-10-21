package android.os;

import java.util.ArrayList;
import java.util.List;

/**
 * Host fixture only: a manually pumped message queue on the test clock. No
 * background thread exists; the test decides when due messages run.
 */
public final class Looper {
    private static final ThreadLocal<Looper> CURRENT = new ThreadLocal<>();
    public static volatile Looper latest;

    private static final class Task {
        final Runnable action;
        final long dueAt;
        final long order;
        Task(Runnable action, long dueAt, long order) {
            this.action = action;
            this.dueAt = dueAt;
            this.order = order;
        }
    }

    private final List<Task> tasks = new ArrayList<>();
    private long sequence;
    public volatile boolean quit;

    Looper() { latest = this; }

    public static Looper myLooper() { return CURRENT.get(); }

    synchronized boolean enqueue(Runnable action, long dueAt) {
        if (quit) return false;
        tasks.add(new Task(action, dueAt, sequence++));
        return true;
    }

    synchronized void remove(Runnable action) {
        tasks.removeIf(task -> task.action == action);
    }

    synchronized void quitSafely() { quit = true; }

    /** Runs every message due at the current fixture time, in due/post order. */
    public void runDue() {
        for (;;) {
            Task next = null;
            synchronized (this) {
                long now = SystemClock.elapsedRealtime();
                for (Task task : tasks) {
                    if (task.dueAt > now) continue;
                    if (next == null || task.dueAt < next.dueAt
                            || (task.dueAt == next.dueAt && task.order < next.order)) next = task;
                }
                if (next == null) return;
                tasks.remove(next);
            }
            Looper previous = CURRENT.get();
            CURRENT.set(this);
            try {
                next.action.run();
            } finally {
                CURRENT.set(previous);
            }
        }
    }

    /** Due time of the earliest message strictly in the future, or -1. */
    public synchronized long nextFutureDueAt() {
        long now = SystemClock.elapsedRealtime();
        long result = -1L;
        for (Task task : tasks) {
            if (task.dueAt > now && (result < 0L || task.dueAt < result)) result = task.dueAt;
        }
        return result;
    }

    public synchronized int pending() { return tasks.size(); }
}
