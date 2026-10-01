package com.thor.displaypowertest;

/**
 * Bounded child wait with a fine poll.
 *
 * The inherited timed Process.waitFor implementation can sleep up to 100 ms
 * between exit checks. The v1.5.16 boot trace had approximately 100 ms gaps
 * around short commands, but the exact cost on the Thor is not yet isolated.
 * This keeps the same timeout and failure semantics and shortens the polling
 * interval independently of the runtime's Process implementation.
 */
public final class ProcessWait {
    public static final long POLL_MS = 5L;

    private ProcessWait() {}

    /** Returns true once the process has exited, false after timeoutMs. */
    public static boolean exited(Process process, long timeoutMs)
            throws InterruptedException {
        long deadline = System.nanoTime() + timeoutMs * 1_000_000L;
        while (true) {
            try {
                process.exitValue();
                return true;
            } catch (IllegalThreadStateException running) {
                long remainingMs = (deadline - System.nanoTime()) / 1_000_000L;
                if (remainingMs <= 0L) return false;
                Thread.sleep(Math.min(POLL_MS, remainingMs));
            }
        }
    }
}
