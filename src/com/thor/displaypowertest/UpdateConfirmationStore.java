package com.thor.displaypowertest;

/**
 * Holds the single deferred Android installer confirmation across an Activity pause.
 * No Android objects are required: the actual Intent is owned by AppUpdater.
 *
 * The caller validates native session ownership. Deferred callbacks are coalesced
 * and a launched session stays consumed until its terminal status or replacement.
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
    private int launchedSessionId = -1;

    synchronized boolean defer(T confirmation, int sessionId) {
        if (confirmation == null || sessionId < 0 || launchedSessionId == sessionId
                || (pending != null && pending.sessionId == sessionId)) return false;
        pending = new Pending<>(confirmation, sessionId);
        return true;
    }

    /** Claim before startActivity, including synchronous/reentrant callbacks. */
    synchronized boolean claimLaunch(int sessionId) {
        if (sessionId < 0 || launchedSessionId == sessionId) return false;
        launchedSessionId = sessionId;
        if (pending != null && pending.sessionId == sessionId) pending = null;
        return true;
    }

    /** Atomically consume before trying to start Android's confirmation Activity. */
    synchronized Pending<T> take() {
        Pending<T> result = pending;
        pending = null;
        return result;
    }

    /** Discard only the matching terminal session; preserve any newer pending one. */
    synchronized boolean discardSession(int sessionId) {
        boolean changed = false;
        if (pending != null && pending.sessionId == sessionId) {
            pending = null;
            changed = true;
        }
        if (sessionId >= 0 && launchedSessionId == sessionId) {
            launchedSessionId = -1;
            changed = true;
        }
        return changed;
    }

    /** A deliberately newer update makes every older deferred prompt obsolete. */
    synchronized void discardAll() {
        pending = null;
        launchedSessionId = -1;
    }

    /** The still-deferred install session is excluded from stale-session cleanup. */
    synchronized int pendingSessionId() {
        return pending == null ? -1 : pending.sessionId;
    }
}
