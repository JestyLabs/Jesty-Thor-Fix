package com.thor.displaypowertest;

/**
 * Pure decision after the compositor-restart helper exits while the daemon that
 * started it is still alive. On the normal path the helper stops this daemon
 * before it exits, so an observed exit means the handover did not happen.
 *
 * Only an abort that proves the compositor kept running leaves this daemon's
 * framework binders usable. After any other abort Android's framework may have
 * restarted underneath it: its mode watcher and display callback would be
 * stale, so it must not reconcile the display and is held for replacement.
 */
public final class HandoffRecoveryModel {
    /** Helper abort codes; kept above the shell's own 1, 2, 126, 127 and 128+n. */
    public static final int EXIT_COMPOSER_NOT_RESTARTED = 10;
    public static final int EXIT_PACKAGE_PATH = 11;
    public static final int EXIT_DAEMON_IDENTITY = 12;
    public static final int EXIT_KILL_FAILED = 13;
    public static final int EXIT_OLD_DAEMON_ALIVE = 14;

    public enum Action {
        /** Successor launched: this daemon is being replaced; do nothing. */
        NONE,
        /** Runtime toggle, compositor unchanged: only end the transition. */
        RELEASE_WAKE_LOCK,
        /** Boot handover, compositor unchanged: run the post-restart gate here. */
        RECOVER_IN_PLACE,
        /** Framework may have restarted: hold display actions until replaced. */
        HOLD_FOR_REPLACEMENT
    }

    private HandoffRecoveryModel() {}

    public static Action afterHelperExit(int exit, boolean bootHandoff, boolean held,
            boolean watcherRunning) {
        if (exit == 0) return Action.NONE;
        if (exit != EXIT_COMPOSER_NOT_RESTARTED || !watcherRunning) {
            return Action.HOLD_FOR_REPLACEMENT;
        }
        return bootHandoff && held ? Action.RECOVER_IN_PLACE : Action.RELEASE_WAKE_LOCK;
    }

    /** Only this abort proves the old compositor kept running. */
    public static boolean composerNotRestarted(int exit) {
        return exit == EXIT_COMPOSER_NOT_RESTARTED;
    }

    public static String reason(int exit) {
        switch (exit) {
            case 0: return "HANDED_OFF";
            case EXIT_COMPOSER_NOT_RESTARTED: return "COMPOSER_NOT_RESTARTED";
            case EXIT_PACKAGE_PATH: return "PACKAGE_PATH";
            case EXIT_DAEMON_IDENTITY: return "DAEMON_IDENTITY_MISMATCH";
            case EXIT_KILL_FAILED: return "KILL_FAILED";
            case EXIT_OLD_DAEMON_ALIVE: return "OLD_DAEMON_ALIVE";
            default: return "HELPER_EXIT_" + (exit < 0 ? "NEGATIVE" : Integer.toString(exit));
        }
    }
}
