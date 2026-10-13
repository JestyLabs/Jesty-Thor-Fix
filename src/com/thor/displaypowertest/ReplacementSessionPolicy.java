package com.thor.displaypowertest;

/** A replacement is forbidden unless native session abandonment completes. */
final class ReplacementSessionPolicy {
    interface Abandoner { void abandon(int sessionId) throws Exception; }
    private ReplacementSessionPolicy() {}
    static boolean canReplace(int sessionId, Abandoner abandoner) {
        if (sessionId < 0) return true;
        try {
            abandoner.abandon(sessionId);
            return true;
        } catch (Exception notConfirmed) {
            return false;
        }
    }
}
