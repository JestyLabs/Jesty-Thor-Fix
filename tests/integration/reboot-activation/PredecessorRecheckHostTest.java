package com.thor.displaypowertest;

/** Actual PServer + both retirement controllers; native effects remain modelled. */
public final class PredecessorRecheckHostTest {
    private static int checks;
    private static int cases;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static void run(String id, String change, boolean approved) throws Exception {
        FixtureState.reset("1.7.2");
        String health = FixtureState.health();
        String snapshot = FixtureState.snapshot();
        String secondHealth = health;
        String secondSnapshot = snapshot;
        if ("pid".equals(change)) secondHealth = health.replace("pid=12000", "pid=12001");
        if ("gone".equals(change)) secondHealth = null;
        if ("mode".equals(change)) secondSnapshot = snapshot.replace("mode=0", "mode=1");
        if ("snapshot-gone".equals(change)) secondSnapshot = null;
        if ("age".equals(change)) {
            health += ";phase_age_ms=1";
            secondHealth += ";phase_age_ms=2";
        }
        FixtureState.scriptedReplies = "gone".equals(change)
                ? new String[] {health, snapshot, secondHealth}
                : "pid".equals(change)
                ? new String[] {health, snapshot, secondHealth}
                : new String[] {health, snapshot, secondHealth, secondSnapshot};
        int result = PServer.stopLivePredecessorIfSafe(true, false, false);
        RetirementNative.awaitHelper();
        // Bridge submission itself is NOT stop admission. The lower-boundary
        // signal counter is reached only after the real root controller's gates.
        check(FixtureState.kills == (approved ? 1 : 0), id + " stop admission");
        check(result == (approved ? 12000 : -1), id + " result");
        check(FixtureState.launches == 0, id + " no successor launch from helper");
        check(FixtureState.replyIndex == FixtureState.scriptedReplies.length, id + " all scripted observations consumed");
        check(FixtureState.requests.toString().equals(
                "gone".equals(change) || "pid".equals(change) ? "IQI" : "IQIQ"),
                id + " exact retained observation order");
        check(FixtureState.events.indexOf("BIND_PROCESS")
                < FixtureState.events.indexOf("REQUEST_I"), id + " bound before first identity");
        cases++;
        System.out.println("PASS " + id + "; actual PServer/app/root controllers; native effects modelled");
    }
    private static void rawCode76() {
        FixtureState.reset("1.7.1");
        check(!FixtureState.snapshot().contains(";runtime_version="),
                "raw-code76 must not fabricate runtime identity");
        check(PServer.safeLivePredecessor(true, false, false) < 0,
                "unbound raw code76 remains denied");
        check(FixtureState.kills == 0 && FixtureState.helperSubmissions == 0,
                "raw policy has no stop side effects");
        // This historical fabricated string only demonstrates raw-policy
        // sensitivity; it is not evidence enabling the new retained route.
        check(LiveHandoverPolicy.safePid(FixtureState.health(),
                FixtureState.snapshot() + ";runtime_version=1.7.1", true, false, false)
                == 12000, "fabricated field is a raw-path negative control only");
        System.out.println("PASS raw-code76: absent field rejected; fabricated string explicitly synthetic");
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            if ("raw-code76".equals(args[0])) rawCode76();
            else run("S2-recheck-" + args[0], args[0], false);
            return;
        }
        run("S2-recheck-stable", "stable", true);
        run("S2-recheck-dynamic-age", "age", true);
        run("S2-recheck-pid", "pid", false);
        run("S2-recheck-disappeared", "gone", false);
        run("S2-recheck-mode", "mode", false);
        run("S2-recheck-snapshot-unavailable", "snapshot-gone", false);
        rawCode76();
        System.out.println("PredecessorRecheckHostTest passed: " + cases + " cases, " + checks
                + " checks; kernel process binding/credentials/native stop NOT PROVEN");
    }
}
