package com.thor.displaypowertest;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Pure resolver for pidof-style process observations during restart handoff.
 *
 * A transition sample may temporarily contain both the baseline PID and the
 * replacement PID. Runtime code must not treat the raw pidof string itself as
 * proof of a successor.
 */
public final class SuccessorPidModel {
    public enum Status {
        WAITING,
        FOUND,
        AMBIGUOUS,
        INVALID
    }

    public static final class Result {
        public final Status status;
        public final String successorPid;
        public final int successorCount;

        private Result(Status status, String successorPid, int successorCount) {
            this.status = status;
            this.successorPid = successorPid;
            this.successorCount = successorCount;
        }
    }

    private SuccessorPidModel() {}

    public static Result resolve(String baselinePid, String observedPids) {
        Long baseline = parsePositivePid(baselinePid);
        if (baseline == null) return new Result(Status.INVALID, null, 0);

        if (observedPids == null || observedPids.trim().isEmpty()) {
            return new Result(Status.WAITING, null, 0);
        }

        Set<Long> successors = new LinkedHashSet<>();
        String[] tokens = observedPids.trim().split("\\s+");
        for (String token : tokens) {
            Long pid = parsePositivePid(token);
            if (pid == null) return new Result(Status.INVALID, null, 0);
            if (!pid.equals(baseline)) successors.add(pid);
        }

        if (successors.isEmpty()) {
            return new Result(Status.WAITING, null, 0);
        }
        if (successors.size() > 1) {
            return new Result(Status.AMBIGUOUS, null, successors.size());
        }

        Long successor = successors.iterator().next();
        return new Result(Status.FOUND, Long.toString(successor), 1);
    }

    private static Long parsePositivePid(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        if (trimmed.isEmpty()) return null;
        try {
            long pid = Long.parseLong(trimmed);
            return pid > 0L ? pid : null;
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
