package com.thor.displaypowertest;
import java.util.Collections;

/** Host integration fixture: commit(false) after mutating the shared memory cache. */
public final class PassiveSleepSessionRecreationTest {
    private static int count;
    private static void test(boolean value, String label) {
        count++;
        if (!value) throw new AssertionError(label);
    }
    private static final class FakePrefs implements PassiveSleepSession.Store {
        boolean armed, active, failNext;
        String start, end;
        private boolean commit() {
            boolean success = !failNext;
            failNext = false;
            return success;
        }
        public boolean arm() {
            armed = true; active = false; start = null; end = null; return commit();
        }
        public boolean cancel() {
            armed = false; active = false; start = null; return commit();
        }
        public boolean start(String s) {
            start = s; active = true; armed = false; return commit();
        }
        public boolean finish(String s) {
            end = s; active = false; return commit();
        }
        public boolean armed() { return armed; }
        public boolean active() { return active; }
        public String startSample() { return start; }
        public String endSample() { return end; }
    }
    private static PassiveSleepSession newMonitor(FakePrefs sameCache) {
        return new PassiveSleepSession(sameCache, new PassiveSleepPersistence());
    }
    private static PassiveSleepTrial.Sample sample(long elapsed, long uptime, long energy) {
        return new PassiveSleepTrial.Sample(elapsed, uptime, energy, 1000000,
                3900, 300, 0, 12, 1);
    }
    private static String report(PassiveSleepSession monitor) {
        return new AppDiagnosticReport("").export(900000, 800000,
                "1.7.1-rc2", 33, "empty", Collections.emptyList(), monitor.result());
    }
    private static void startFailure() {
        FakePrefs cache = new FakePrefs();
        PassiveSleepSession first = newMonitor(cache);
        test(first.arm(), "arm saved");
        cache.failNext = true;
        first.onPause(() -> sample(100000, 50000, 2000000000));
        test(cache.active && cache.start != null, "failed commit mutated memory");
        cache.end = sample(800000, 70000, 1900000000).encode(); // plausible cached pair
        PassiveSleepSession recreated = newMonitor(cache);
        test(recreated.result() == null, "new monitor must still reject failed start");
        test(recreated.description().startsWith("Trial invalid"), "UI must surface invalidity");
        test(!report(recreated).contains("passive_sleep;"), "export must hide invalid pair");
        recreated.onResume(() -> { throw new AssertionError("unexpected sample"); });
        test(recreated.result() == null, "resume must not heal commit failure");
        cache.failNext = true;
        test(!recreated.cancel(), "failed cancel cannot reset failure");
        test(newMonitor(cache).result() == null, "further recreation still invalid");
        test(recreated.arm(), "explicit successfully saved re-arm");
        test(cache.start == null && cache.end == null, "re-arm clears endpoints");
        PassiveSleepSession clean = newMonitor(cache);
        test(clean.result() == null, "stale result not recovered");
        clean.onPause(() -> sample(100000, 50000, 2000000000));
        clean.onResume(() -> sample(800000, 70000, 1900000000));
        test(clean.result() != null, "fresh saved trial valid");
        test(report(clean).contains("passive_sleep;"), "fresh trial exported");
    }
    private static void finishFailure() {
        FakePrefs cache = new FakePrefs();
        PassiveSleepSession first = newMonitor(cache);
        test(first.arm(), "second trial armed");
        first.onPause(() -> sample(100000, 50000, 2000000000));
        cache.failNext = true;
        first.onResume(() -> sample(800000, 70000, 1900000000));
        test(cache.end != null && !cache.active, "failed finish mutated memory");
        PassiveSleepSession second = newMonitor(cache);
        test(second.result() == null, "failed finish rejected after recreation");
        test(!report(second).contains("passive_sleep;"), "failed finish never exported");
        test(second.cancel(), "explicit saved cancel succeeds");
        test(newMonitor(cache).result() == null, "cancelled trial remains cleared");
    }
    public static void main(String[] args) {
        startFailure();
        finishFailure();
        System.out.println("PassiveSleepSessionRecreationTest passed: " + count + " assertions");
    }
}
