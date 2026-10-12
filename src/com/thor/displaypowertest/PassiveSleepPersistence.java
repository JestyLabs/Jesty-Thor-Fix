package com.thor.displaypowertest;

/** Fail closed when a passive trial endpoint cannot be saved durably. */
final class PassiveSleepPersistence {
    interface CommitOperation { boolean run(); }
    private volatile boolean failed;

    boolean tryCommit(CommitOperation operation, boolean freshUserIntent) {
        boolean saved;
        try { saved = operation.run(); }
        catch (RuntimeException error) { saved = false; }
        // An explicit successful arm/cancel resets a previous failure. A
        // successful endpoint write cannot erase an earlier failed endpoint.
        if (freshUserIntent) failed = !saved;
        else if (!saved) failed = true;
        return saved;
    }
    boolean isFailed() { return failed; }
}
