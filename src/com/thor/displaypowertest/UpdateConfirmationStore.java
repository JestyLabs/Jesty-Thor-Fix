package com.thor.displaypowertest;

/**
 * Holds the single deferred Android installer confirmation across an Activity pause.
 * No Android objects are required: the actual Intent is owned by AppUpdater.
 *
 * This deliberately characterizes existing one-slot/last-callback behavior.
 * Validating callback/session age is a separate, integration-tested policy change.
 */
final class UpdateConfirmationStore<T> {
    static final class Pending<T> {
        final T confirmation;
        final int sessionId;

        Pending(T confirmation, int sessionId) {
            this.confirmation = confirmation;
            this.sessionId = sessionId;
        }
    }

    private Pending<T> pending;

    synchronized void defer(T confirmation, int sessionId) {
        pending = confirmation == null ? null : new Pending<>(confirmation, sessionId);
    }

    /** Atomically consume before trying to start Android's confirmation Activity. */
    synchronized Pending<T> take() {
        Pending<T> result = pending;
        pending = null;
        return result;
    }

    /** Discard only the matching terminal session; preserve any newer pending one. */
    synchronized boolean discardSession(int sessionId) {
        if (pending == null || pending.sessionId != sessionId) return false;
        pending = null;
        return true;
    }

    /** A deliberately newer update makes every older deferred prompt obsolete. */
    synchronized void discardAll() {
        pending = null;
    }

    /** The still-deferred install session is excluded from stale-session cleanup. */
    synchronized int pendingSessionId() {
        return pending == null ? -1 : pending.sessionId;
    }
}
