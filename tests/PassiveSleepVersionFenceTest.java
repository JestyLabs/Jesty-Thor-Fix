package com.thor.displaypowertest;

/** Deterministic in-memory RC3 -> RC4 -> next-install regression. */
public final class PassiveSleepVersionFenceTest {
    private static int assertions;
    private static void check(boolean ok, String reason) {
        assertions++;
        if (!ok) throw new AssertionError(reason);
    }

    private static final class Cache implements PassiveSleepSession.Store {
        String current = PassiveSleepInstallEpoch.token("1.7.1-rc3", 74, 1800000000000L);
        String saved, start, end, events = "EVENTS_KEPT";
        boolean armed, active, failNext, throwEpoch;
        int fixes = 3;
        boolean commit() { boolean ok = !failNext; failNext = false; return ok; }
        public boolean arm() {
            if (current == null) return false;
            saved = current; armed = true; active = false; start = end = null;
            return commit();
        }
        public boolean cancel() {
            armed = false; active = false; start = end = saved = null;
            return commit();
        }
        public boolean start(String value) {
            start = value; armed = false; active = true;
            return commit();
        }
        public boolean finish(String value) {
            end = value; active = false;
            return commit();
        }
        public boolean armed() { return armed; }
        public boolean active() { return active; }
        public String startSample() { return start; }
        public String endSample() { return end; }
        public boolean hasTrialData() {
            return armed || active || start != null || end != null || saved != null;
        }
        public boolean installEpochMatches() {
            if (throwEpoch) throw new IllegalStateException("malformed preference type");
            return PassiveSleepInstallEpoch.matches(saved, current);
        }
        void recovery() { current = PassiveSleepInstallEpoch.token("1.7.1-rc4",75,1800000001000L); }
        void futureStable() { current = PassiveSleepInstallEpoch.token("1.7.1",76,1800000002000L); }
        void backToRc3NewInstall() {
            current = PassiveSleepInstallEpoch.token("1.7.1-rc3",74,1800000003000L);
        }
    }
    private static PassiveSleepSession recreated(Cache cache) {
        return new PassiveSleepSession(cache, new PassiveSleepPersistence());
    }
    private static PassiveSleepTrial.Sample sample(long elapsed, long uptime, long energy) {
        return new PassiveSleepTrial.Sample(elapsed, uptime, energy, 1000000,
                3900, 300, 0, 12, 3);
    }
    private static String exported(PassiveSleepSession session) {
        return session.result() == null ? "JESTY_THOR_DIAGNOSTICS_V1" : "passive_sleep;";
    }
    private static void t1InProgressAcrossRecovery() {
        Cache c = new Cache(); PassiveSleepSession v3 = recreated(c);
        check(v3.arm(), "arm saved");
        v3.onPause(() -> sample(100000, 40000, 2000000000));
        check(c.active && c.start != null, "start persisted");
        c.recovery(); // RC4 ignores opt-in keys, but normal same-package install keeps them.
        check(recreated(c).result() == null, "RC4 cannot treat RC3 trial as valid");
        c.backToRc3NewInstall();
        PassiveSleepSession newV3 = recreated(c);
        check(newV3.running(), "old trial exposes cancel action");
        check(newV3.description().startsWith("Trial invalid"), "old trial visibly invalid");
        newV3.onResume(() -> { throw new AssertionError("must not sample past upgrade"); });
        check(newV3.result() == null, "no new result across installs");
        check(!exported(newV3).contains("passive_sleep;"), "no stale export");
    }
    private static void t2ArmedAcrossInstall() {
        Cache c = new Cache(); PassiveSleepSession before = recreated(c);
        check(before.arm(), "armed"); c.recovery();
        PassiveSleepSession after = recreated(c);
        after.onPause(() -> { throw new AssertionError("no late sampling"); });
        check(after.result() == null, "armed on RC3 cannot start in recovery");
        check(after.running(), "cancel remains available");
    }
    private static void t3UnknownEpoch() {
        Cache c = new Cache(); PassiveSleepSession first = recreated(c);
        check(first.arm(), "armed");
        c.current = null;
        first.onPause(() -> { throw new AssertionError("missing version must not sample"); });
        check(first.result() == null, "unknown package version fails closed");
        check(!first.description().isEmpty(), "explains inability to trust trial");
        check(first.cancel(), "explicit cancellation can still clear unknown epoch");
        check(!recreated(c).running(), "cancelled unknown trial removed");
    }
    private static void t4SavedCancelAndNoPreferenceLoss() {
        Cache c = new Cache(); PassiveSleepSession first = recreated(c);
        check(first.arm(), "arm");
        first.onPause(() -> sample(100000, 40000, 2000000000));
        c.recovery();
        PassiveSleepSession after = recreated(c);
        check(after.cancel(), "saved cancellation");
        check(!c.active && !c.armed && c.start == null && c.end == null && c.saved == null,
                "cleared controls, both endpoints, epoch");
        check("EVENTS_KEPT".equals(c.events) && c.fixes == 3,
                "unrelated events and fixes untouched");
        check(after.result() == null, "no post-cancel result");
    }
    private static void t5CompletedHistoricalTrial() {
        Cache c = new Cache(); PassiveSleepSession first = recreated(c);
        check(first.arm(), "arm");
        first.onPause(() -> sample(100000, 40000, 2000000000));
        first.onResume(() -> sample(800000, 70000, 1900000000));
        check(first.result() != null, "completed same-install trial is readable");
        c.recovery();
        check(recreated(c).result() == null, "old complete trial does not masquerade as new");
        c.futureStable();
        check(recreated(c).result() == null, "stable keeps installed-build fence");
    }
    private static void t6FailingCancelThenRecovery() {
        Cache c = new Cache(); PassiveSleepSession first = recreated(c);
        check(first.arm(), "arm"); first.onPause(() -> sample(100000, 40000, 2000000000));
        c.failNext = true;
        check(!first.cancel(), "failed commit does not clear failure");
        c.recovery();
        check(recreated(c).result() == null, "failed cancel cannot un-fence a changed install");
        PassiveSleepSession after = recreated(c);
        check(after.cancel(), "explicit saved cancel can recover");
        check(!after.running(), "no stale state after confirmed cancel");
        check(after.arm(), "new install can arm new trial");
        check(c.saved.equals(c.current), "new epoch saved with new arm");
    }
    private static void t7CorruptEpochFailsClosed() {
        Cache c = new Cache(); PassiveSleepSession before = recreated(c);
        check(before.arm(), "arm saved");
        before.onPause(() -> sample(100000, 40000, 2000000000));
        c.throwEpoch = true;
        PassiveSleepSession second = recreated(c);
        check(second.result() == null, "malformed epoch never exports trial");
        check(second.description().startsWith("Trial invalid"), "malformed epoch reports invalid state");
        second.onResume(() -> { throw new AssertionError("untrusted epoch cannot sample"); });
        check(second.running(), "cancel exposed for untrusted epoch");
        check(second.cancel(), "cancel remains possible");
    }
    private static void t8MarkerValidation() {
        check(PassiveSleepInstallEpoch.token(null, 74, 2L) == null, "missing name");
        check(PassiveSleepInstallEpoch.token("1.7.1-rc3",0,2L) == null, "invalid code");
        check(PassiveSleepInstallEpoch.token("1.7.1-rc3",74,0L) == null, "missing update timestamp");
        check(!PassiveSleepInstallEpoch.matches(null,"I1|74|2|1.7.1-rc3"),"legacy trial invalid");
        check(!PassiveSleepInstallEpoch.matches("I1|74|1|1.7.1-rc3", "I1|74|2|1.7.1-rc3"),
                "reinstallation of same APK invalidates old trial");
    }
    public static void main(String[] args) {
        t1InProgressAcrossRecovery(); t2ArmedAcrossInstall(); t3UnknownEpoch();
        t4SavedCancelAndNoPreferenceLoss(); t5CompletedHistoricalTrial();
        t6FailingCancelThenRecovery(); t7CorruptEpochFailsClosed(); t8MarkerValidation();
        System.out.println("PassiveSleepVersionFenceTest: " + assertions + " assertions PASS");
    }
}
