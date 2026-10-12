package com.thor.displaypowertest;

public final class PassiveSleepPersistenceTest {
    private static int count;
    private static void check(boolean yes, String why) {
        count++;
        if (!yes) throw new AssertionError(why);
    }
    public static void main(String[] args) {
        PassiveSleepPersistence state = new PassiveSleepPersistence();
        check(state.tryCommit(() -> true, true), "arm success");
        check(!state.isFailed(), "valid after arm");
        check(!state.tryCommit(() -> false, false), "endpoint commit failure");
        check(state.isFailed(), "failed endpoint invalidates trial");
        check(state.tryCommit(() -> true, false), "later successful boundary");
        check(state.isFailed(), "later commit does not undo invalidation");
        check(state.tryCommit(() -> true, true), "user retry success");
        check(!state.isFailed(), "successful retry resets failure");
        check(!state.tryCommit(() -> { throw new IllegalStateException(); }, false),
                "exception is treated as failed commit");
        check(state.isFailed(), "exception invalidates trial");
        check(!state.tryCommit(() -> false, true), "failed cancellation");
        check(state.isFailed(), "failed cancellation does not reset");
        check(state.tryCommit(() -> true, true), "confirmed cancellation");
        check(!state.isFailed(), "confirmed cancellation resets");
        System.out.println("PassiveSleepPersistenceTest passed: " + count + " assertions");
    }
}
