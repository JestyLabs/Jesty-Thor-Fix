package com.thor.displaypowertest;

/**
 * Command line of {@code app_process / D}, written by PServer and by the
 * compositor helper: {@code enabled [hold|run] cpuFix lidGuard [startedAt] [post]}.
 * The format is part of the identity checks; do not change it here alone.
 */
public final class DaemonArgs {
    public final boolean displayFixEnabled;
    public final boolean holdRequested;
    public final boolean cpuFixDesired;
    public final boolean lidGuardDesired;
    /** Boot-clock start of the current readiness phase, or -1 when not given. */
    public final long phaseStartedAt;
    public final boolean afterComposerRestart;

    private DaemonArgs(String[] args) {
        displayFixEnabled = args.length == 0 || !"0".equals(args[0]);
        holdRequested = args.length > 1 && "hold".equals(args[1]);
        cpuFixDesired = args.length > 2 && "1".equals(args[2]);
        lidGuardDesired = args.length > 3 && "1".equals(args[3]);
        phaseStartedAt = args.length > 4 ? Long.parseLong(args[4]) : -1L;
        afterComposerRestart = args.length > 5 && "post".equals(args[5]);
    }

    public static DaemonArgs parse(String[] args) {
        return new DaemonArgs(args == null ? new String[0] : args);
    }

    public String launchKind() {
        return afterComposerRestart ? "post" : holdRequested ? "hold" : "run";
    }
}
