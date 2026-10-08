package com.thor.displaypowertest;

/**
 * Synchronized decision boundary between a user's Cancel tap and handing the
 * already-verified APK to Android's PackageInstaller.
 *
 * This does not cancel an Android session after commit has started. It only
 * guarantees that cancellation accepted by the UI wins over a later commit.
 * No Android types are used here so this policy can be tested on the host.
 */
public final class UpdateCommitGate {
    private enum State { IDLE, ACTIVE, CANCELLED, COMMITTING }
    private State state = State.IDLE;

    /** Start one install attempt. Reject overlap rather than resetting a live attempt. */
    public synchronized boolean start() {
        if (state != State.IDLE) return false;
        state = State.ACTIVE;
        return true;
    }

    /** True iff cancellation won before the commit boundary. */
    public synchronized boolean cancel() {
        if (state != State.ACTIVE) return false;
        state = State.CANCELLED;
        return true;
    }

    /** True iff the installer may now be given the validated APK. */
    public synchronized boolean beginCommit() {
        if (state != State.ACTIVE) return false;
        state = State.COMMITTING;
        return true;
    }

    public synchronized boolean wasCancelled() {
        return state == State.CANCELLED;
    }

    /** Called only by the owning install worker after its attempt finishes. */
    public synchronized void finish() {
        state = State.IDLE;
    }
}
