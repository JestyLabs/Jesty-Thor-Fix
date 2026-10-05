package com.thor.displaypowertest;

/** Pure state model for one CPU-fix compositor restart attempt in a kernel boot. */
public final class CpuBootAttemptModel {
    public static final long RESTART_VERIFY_TIMEOUT_MS = 20_000L;

    public enum Phase {
        PREPARED,
        PROPERTY_VERIFIED,
        RESTART_REQUESTED,
        APPLIED,
        FAILED
    }

    public enum Action {
        START_NEW,
        WRITE_PROPERTY,
        CONFIRM_PROPERTY,
        REQUEST_RESTART,
        WAIT_FOR_RESTART,
        MARK_APPLIED,
        PROCEED,
        DELETE_STALE,
        FAIL_SAFE
    }

    public static final class Attempt {
        public final String bootId;
        public final String desired;
        public final String previous;
        public final String baselineComposerPid;
        public final Phase phase;
        public final long phaseAtMs;

        public Attempt(String bootId, String desired, String previous,
                String baselineComposerPid, Phase phase, long phaseAtMs) {
            if (!validBootId(bootId)) throw new IllegalArgumentException("boot_id");
            if (!binary(desired)) throw new IllegalArgumentException("desired");
            if (!property(previous)) throw new IllegalArgumentException("previous");
            if (!pid(baselineComposerPid)) throw new IllegalArgumentException("baseline_pid");
            if (phase == null) throw new IllegalArgumentException("phase");
            if (phaseAtMs < 0L) throw new IllegalArgumentException("phase_at_ms");
            this.bootId = bootId;
            this.desired = desired;
            this.previous = previous;
            this.baselineComposerPid = baselineComposerPid;
            this.phase = phase;
            this.phaseAtMs = phaseAtMs;
        }

        public Attempt withPhase(Phase next, long nowMs) {
            return new Attempt(bootId, desired, previous, baselineComposerPid, next, nowMs);
        }

        public String encode() {
            return "v1|" + bootId + "|" + desired + "|" + previous + "|"
                    + baselineComposerPid + "|" + phase.name() + "|" + phaseAtMs;
        }

        public static Attempt decode(String value) {
            if (value == null) throw new IllegalArgumentException("null");
            String[] p = value.trim().split("\\|", -1);
            if (p.length != 7 || !"v1".equals(p[0])) throw new IllegalArgumentException("format");
            final long phaseAt;
            try { phaseAt = Long.parseLong(p[6]); }
            catch (NumberFormatException e) { throw new IllegalArgumentException("phase_at_ms"); }
            final Phase phase;
            try { phase = Phase.valueOf(p[5]); }
            catch (IllegalArgumentException e) { throw new IllegalArgumentException("phase"); }
            return new Attempt(p[1], p[2], p[3], p[4], phase, phaseAt);
        }
    }

    private CpuBootAttemptModel() {}

    /**
     * Decides the next safe action. A corrupt/unreadable marker is handled by the store as FAIL_SAFE;
     * this method only accepts absent or already-validated attempts.
     */
    public static Action decide(Attempt attempt, String currentBootId, String desired,
            String property, String composerPid, long nowMs) {
        if (!validBootId(currentBootId) || !binary(desired) || !property(property)
                || !pid(composerPid) || nowMs < 0L) {
            return Action.FAIL_SAFE;
        }

        if (attempt == null) {
            if (desired.equals(property)) return Action.PROCEED;
            if ("1".equals(desired) && ("0".equals(property) || "UNSET".equals(property))) {
                return Action.START_NEW;
            }
            if ("0".equals(desired) && "1".equals(property)) return Action.START_NEW;
            return Action.FAIL_SAFE; // notably desired=0 + UNSET, preserving current behavior
        }

        if (!currentBootId.equalsIgnoreCase(attempt.bootId)) return Action.DELETE_STALE;
        if (!desired.equals(attempt.desired) && attempt.phase != Phase.APPLIED) {
            return Action.FAIL_SAFE;
        }

        boolean sameComposer = composerPid.equals(attempt.baselineComposerPid);
        boolean desiredVisible = desired.equals(property);

        switch (attempt.phase) {
            case PREPARED:
                if (!sameComposer) return Action.FAIL_SAFE;
                if (attempt.previous.equals(property)) return Action.WRITE_PROPERTY;
                if (desiredVisible) return Action.CONFIRM_PROPERTY;
                return Action.FAIL_SAFE;

            case PROPERTY_VERIFIED:
                if (!desiredVisible) return Action.FAIL_SAFE;
                return sameComposer ? Action.REQUEST_RESTART : Action.MARK_APPLIED;

            case RESTART_REQUESTED:
                if (!desiredVisible) return Action.FAIL_SAFE;
                if (!sameComposer) return Action.MARK_APPLIED;
                return nowMs - attempt.phaseAtMs < RESTART_VERIFY_TIMEOUT_MS
                        ? Action.WAIT_FOR_RESTART : Action.FAIL_SAFE;

            case APPLIED:
                // A completed attempt may seed a new explicit transition, but
                // only while we can still prove we are on the successor
                // composer. A changed preference or late property drift both
                // require a fresh restart because the running composer caches
                // the value it saw at initialization.
                if (sameComposer) return Action.FAIL_SAFE;
                if (!desired.equals(attempt.desired) || !desiredVisible) {
                    return Action.START_NEW;
                }
                return Action.PROCEED;

            case FAILED:
            default:
                return Action.FAIL_SAFE;
        }
    }

    public static Attempt start(String bootId, String desired, String previous,
            String baselineComposerPid, long nowMs) {
        return new Attempt(bootId, desired, previous, baselineComposerPid, Phase.PREPARED, nowMs);
    }

    static boolean validBootId(String value) {
        return value != null && value.matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    }

    static boolean binary(String value) { return "0".equals(value) || "1".equals(value); }
    static boolean property(String value) {
        return binary(value) || "UNSET".equals(value);
    }
    static boolean pid(String value) { return value != null && value.matches("[1-9][0-9]{0,9}"); }
}
