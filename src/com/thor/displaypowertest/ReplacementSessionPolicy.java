package com.thor.displaypowertest;

/** A replacement is forbidden unless native session abandonment completes. */
final class ReplacementSessionPolicy {
    interface Abandoner { void abandon(int sessionId) throws Exception; }
    private ReplacementSessionPolicy() {}
    interface NativeSessions {
        int[] ids() throws Exception;
        void abandon(int sessionId) throws Exception;
    }

    /**
     * Reconcile *all* native sessions, not only the process-local ID, which is
     * lost after process death. Exceptions or sessions still reported afterward
     * are an UNKNOWN platform state; no new download may start.
     */
    static boolean canBeginTransfer(NativeSessions sessions) {
        try {
            int[] before = sessions.ids();
            if (before == null) return false;
            for (int id : before) {
                if (id < 0 || !canReplace(id, sessions::abandon)) return false;
            }
            int[] after = sessions.ids();
            return after != null && after.length == 0;
        } catch (Exception unknownNativeState) {
            return false;
        }
    }

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
