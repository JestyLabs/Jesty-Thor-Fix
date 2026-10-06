package com.thor.displaypowertest;

/**
 * Pure lifecycle model for the optional boot-recovery splash.
 *
 * This model deliberately owns no process, property, compositor restart or
 * display primitive. Runtime code may translate its transitions into trace
 * marks and best-effort splash operations, but CPU restart provenance remains
 * owned by the existing CPU restart provenance path.
 */
public final class RecoverySplashModel {
    public enum State {
        DISARMED,
        ARMED,
        WAITING_FOR_SF,
        SHOW_REQUESTED,
        SHOWN,
        REMOVE_REQUESTED,
        REMOVED,
        FAIL_OPEN
    }

    public enum Failure {
        NONE,
        INVALID_SEQUENCE,
        SF_TIMEOUT,
        SHOW_TIMEOUT,
        VISIBLE_TIMEOUT,
        REMOVE_TIMEOUT,
        SHOW_FAILED,
        REMOVE_FAILED
    }

    public enum Trace {
        SPLASH_ARMED,
        SPLASH_WAIT_SF,
        SPLASH_SHOW_REQUESTED,
        SPLASH_SHOWN,
        SPLASH_REMOVE_REQUESTED,
        SPLASH_REMOVED,
        SPLASH_TIMEOUT,
        SPLASH_FAIL_OPEN
    }

    /** Injected policy: production values must come from measured Thor traces. */
    public static final class Policy {
        public final long sfWaitTimeoutMs;
        public final long showTimeoutMs;
        public final long maxVisibleMs;
        public final long removeTimeoutMs;

        public Policy(long sfWaitTimeoutMs, long showTimeoutMs,
                long maxVisibleMs, long removeTimeoutMs) {
            if (sfWaitTimeoutMs <= 0L || showTimeoutMs <= 0L
                    || maxVisibleMs <= 0L || removeTimeoutMs <= 0L) {
                throw new IllegalArgumentException("timeouts");
            }
            this.sfWaitTimeoutMs = sfWaitTimeoutMs;
            this.showTimeoutMs = showTimeoutMs;
            this.maxVisibleMs = maxVisibleMs;
            this.removeTimeoutMs = removeTimeoutMs;
        }
    }

    public static final class Session {
        public final State state;
        public final Failure failure;
        public final long stateAtMs;
        public final long shownAtMs;

        private Session(State state, Failure failure, long stateAtMs, long shownAtMs) {
            this.state = state;
            this.failure = failure;
            this.stateAtMs = stateAtMs;
            this.shownAtMs = shownAtMs;
        }
    }

    public static final class Transition {
        public final Session session;
        public final Trace[] traces;

        private Transition(Session session, Trace... traces) {
            this.session = session;
            this.traces = traces == null ? new Trace[0] : traces;
        }
    }

    private RecoverySplashModel() {}

    public static Transition arm(boolean featureEnabled, boolean bootScoped,
            boolean restartRequested, long nowMs) {
        requireTime(nowMs);
        if (!featureEnabled || !bootScoped || !restartRequested) {
            return new Transition(new Session(State.DISARMED, Failure.NONE, nowMs, -1L));
        }
        return new Transition(new Session(State.ARMED, Failure.NONE, nowMs, -1L),
                Trace.SPLASH_ARMED);
    }

    public static Transition waitForSurfaceFlinger(Session session, long nowMs) {
        requireTime(nowMs);
        if (!valid(session, State.ARMED, nowMs)) return invalid(nowMs, session);
        return new Transition(new Session(State.WAITING_FOR_SF, Failure.NONE, nowMs, -1L),
                Trace.SPLASH_WAIT_SF);
    }

    public static Transition successorSurfaceFlinger(Session session, long nowMs) {
        requireTime(nowMs);
        if (!valid(session, State.WAITING_FOR_SF, nowMs)) return invalid(nowMs, session);
        return new Transition(new Session(State.SHOW_REQUESTED, Failure.NONE, nowMs, -1L),
                Trace.SPLASH_SHOW_REQUESTED);
    }

    public static Transition shown(Session session, boolean success, long nowMs) {
        requireTime(nowMs);
        if (!valid(session, State.SHOW_REQUESTED, nowMs)) return invalid(nowMs, session);
        if (!success) return failOpen(nowMs, Failure.SHOW_FAILED);
        return new Transition(new Session(State.SHOWN, Failure.NONE, nowMs, nowMs),
                Trace.SPLASH_SHOWN);
    }

    /** Framework recovery is the normal trigger to remove a confirmed splash. */
    public static Transition frameworkRecovered(Session session, long nowMs) {
        requireTime(nowMs);
        if (!valid(session, State.SHOWN, nowMs)) return invalid(nowMs, session);
        return requestRemoval(session, nowMs, false);
    }

    public static Transition removed(Session session, boolean success, long nowMs) {
        requireTime(nowMs);
        if (!valid(session, State.REMOVE_REQUESTED, nowMs)) return invalid(nowMs, session);
        if (!success) return failOpen(nowMs, Failure.REMOVE_FAILED);
        return new Transition(new Session(State.REMOVED, Failure.NONE, nowMs,
                session.shownAtMs), Trace.SPLASH_REMOVED);
    }

    /**
     * Advances timeout policy without performing any runtime side effect.
     * A visible-lifetime timeout requests cleanup first; all other timeout
     * failures fail open immediately so splash UX can never gate boot progress.
     */
    public static Transition tick(Session session, Policy policy, long nowMs) {
        requireTime(nowMs);
        if (session == null || policy == null || nowMs < session.stateAtMs) {
            return invalid(nowMs, session);
        }
        long age = nowMs - session.stateAtMs;
        switch (session.state) {
            case WAITING_FOR_SF:
                return age >= policy.sfWaitTimeoutMs
                        ? timedFailOpen(nowMs, Failure.SF_TIMEOUT) : noChange(session);
            case SHOW_REQUESTED:
                return age >= policy.showTimeoutMs
                        ? timedFailOpen(nowMs, Failure.SHOW_TIMEOUT) : noChange(session);
            case SHOWN:
                if (session.shownAtMs < 0L || nowMs < session.shownAtMs) {
                    return invalid(nowMs, session);
                }
                return nowMs - session.shownAtMs >= policy.maxVisibleMs
                        ? requestRemoval(session, nowMs, true) : noChange(session);
            case REMOVE_REQUESTED:
                return age >= policy.removeTimeoutMs
                        ? timedFailOpen(nowMs, Failure.REMOVE_TIMEOUT) : noChange(session);
            default:
                return noChange(session);
        }
    }

    private static Transition requestRemoval(Session session, long nowMs, boolean timeout) {
        Session next = new Session(State.REMOVE_REQUESTED,
                timeout ? Failure.VISIBLE_TIMEOUT : Failure.NONE,
                nowMs, session.shownAtMs);
        return timeout
                ? new Transition(next, Trace.SPLASH_TIMEOUT, Trace.SPLASH_REMOVE_REQUESTED)
                : new Transition(next, Trace.SPLASH_REMOVE_REQUESTED);
    }

    private static Transition timedFailOpen(long nowMs, Failure failure) {
        Session next = new Session(State.FAIL_OPEN, failure, nowMs, -1L);
        return new Transition(next, Trace.SPLASH_TIMEOUT, Trace.SPLASH_FAIL_OPEN);
    }

    private static Transition failOpen(long nowMs, Failure failure) {
        Session next = new Session(State.FAIL_OPEN, failure, nowMs, -1L);
        return new Transition(next, Trace.SPLASH_FAIL_OPEN);
    }

    private static Transition invalid(long nowMs, Session session) {
        long shownAt = session == null ? -1L : session.shownAtMs;
        return new Transition(new Session(State.FAIL_OPEN, Failure.INVALID_SEQUENCE,
                Math.max(0L, nowMs), shownAt), Trace.SPLASH_FAIL_OPEN);
    }

    private static Transition noChange(Session session) {
        return new Transition(session);
    }

    private static boolean valid(Session session, State expected, long nowMs) {
        return session != null && session.state == expected && nowMs >= session.stateAtMs;
    }

    private static void requireTime(long nowMs) {
        if (nowMs < 0L) throw new IllegalArgumentException("now_ms");
    }
}
