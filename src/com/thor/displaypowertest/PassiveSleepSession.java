package com.thor.displaypowertest;

/** Testable Activity-boundary state machine. No timers or background work. */
final class PassiveSleepSession {
    interface Store {
        boolean arm();
        boolean cancel();
        boolean start(String sample);
        boolean finish(String sample);
        boolean armed();
        boolean active();
        String startSample();
        String endSample();
        boolean hasTrialData();
        boolean installEpochMatches();
    }
    interface Sampler { PassiveSleepTrial.Sample capture(); }

    private final Store store;
    private final PassiveSleepPersistence persistence;

    PassiveSleepSession(Store store, PassiveSleepPersistence persistence) {
        this.store = store;
        this.persistence = persistence;
    }
    boolean arm() { return persistence.tryCommit(store::arm, true); }
    boolean cancel() { return persistence.tryCommit(store::cancel, true); }
    private boolean installEpochTrusted() {
        try { return store.installEpochMatches(); }
        catch (RuntimeException malformedOrUnavailable) { return false; }
    }
    private boolean outdatedInstall() {
        try { return store.hasTrialData() && !installEpochTrusted(); }
        catch (RuntimeException malformedOrUnavailable) { return true; }
    }
    boolean running() {
        // Expose Cancel to clear old data, even if the stored active flag is false.
        return persistence.isFailed() || outdatedInstall() || store.armed() || store.active();
    }
    void onPause(Sampler sampler) {
        if (persistence.isFailed() || !installEpochTrusted()
                || !store.armed() || store.active()) return;
        PassiveSleepTrial.Sample first = sampler.capture();
        persistence.tryCommit(() -> store.start(first.encode()), false);
    }
    void onResume(Sampler sampler) {
        if (persistence.isFailed() || !installEpochTrusted()
                || !store.active()) return;
        PassiveSleepTrial.Sample last = sampler.capture();
        persistence.tryCommit(() -> store.finish(last.encode()), false);
    }
    PassiveSleepTrial.Result result() {
        // Android commit(false) can still update the in-process preferences cache.
        if (persistence.isFailed() || !installEpochTrusted()) return null;
        PassiveSleepTrial.Sample first = PassiveSleepTrial.Sample.decode(store.startSample());
        PassiveSleepTrial.Sample last = PassiveSleepTrial.Sample.decode(store.endSample());
        return first != null && last != null ? PassiveSleepTrial.evaluate(first, last) : null;
    }
    String description() {
        if (persistence.isFailed())
            return "Trial invalid: a boundary or control change could not be saved. "
                    + "Cancel or re-arm the trial before relying on any result.";
        if (outdatedInstall())
            return "Trial invalid: application installation changed or could not be verified. "
                    + "Cancel the old trial before starting a new one.";
        if (store.armed()) return "Armed: leave the dashboard to start.";
        if (store.active()) return "Running until the dashboard resumes.";
        PassiveSleepTrial.Result r = result();
        return r != null ? r.describe()
                : "No completed trial. Arm the trial, leave the app, let the Thor sleep unplugged, "
                + "then return. No periodic background sampling occurs.";
    }
}
