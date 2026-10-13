package com.thor.displaypowertest;

/** Fail closed when a passive trial endpoint cannot be saved durably. */
final class PassiveSleepPersistence {
    interface CommitOperation { boolean run(); }
    // One opt-in diagnostics trial per Android process. A recreated monitor
    // cannot reset a failed endpoint while cached preferences remain in RAM.
    private static volatile boolean failed;

    boolean tryCommit(CommitOperation operation, boolean freshUserIntent) {
        synchronized (PassiveSleepPersistence.class) {
            boolean saved;
            try { saved = operation.run(); }
            catch (RuntimeException error) { saved = false; }
            // Only a successfully saved explicit arm/cancel clears invalidity.
            if (freshUserIntent) failed = !saved;
            else if (!saved) failed = true;
            return saved;
        }
    }
    boolean isFailed() { return failed; }
}
