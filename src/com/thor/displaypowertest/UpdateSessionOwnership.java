package com.thor.displaypowertest;

/**
 * In-process owner of a single updater transfer and its Android installer session.
 * Session IDs are assigned by Android; an older callback must never clean an
 * unrelated later transfer. After process death, unknown callbacks fail closed;
 * stale native sessions are separately abandoned during app startup.
 */
final class UpdateSessionOwnership {
    private long serial;
    private long attempt = -1L;
    private boolean transferInProgress;
    private int sessionId = -1;

    synchronized long beginTransfer() {
        if (transferInProgress) return -1L;
        attempt = ++serial;
        transferInProgress = true;
        sessionId = -1;
        return attempt;
    }

    synchronized boolean registerSession(long token, int id) {
        if (!transferInProgress || attempt != token || id < 0 || sessionId >= 0) return false;
        sessionId = id;
        return true;
    }

    synchronized boolean ownsSession(int id) {
        return id >= 0 && attempt >= 0 && sessionId == id;
    }

    /** Reject duplicate, malformed, or stale status callbacks, atomically. */
    synchronized boolean finishSession(int id) {
        if (!ownsSession(id)) return false;
        sessionId = -1;
        return true;
    }

    /** A failed (non-committed) session must no longer accept installer callbacks. */
    synchronized void revokeSession(long token, int id) {
        if (attempt == token && sessionId == id) sessionId = -1;
    }

    synchronized void finishTransfer(long token) {
        if (attempt == token) transferInProgress = false;
    }

    synchronized boolean transferInProgress() {
        return transferInProgress;
    }

    synchronized int currentSessionId() {
        return sessionId;
    }

    synchronized boolean ownsTransfer(long token) {
        return token >= 0 && token == attempt;
    }
}
