package com.thor.displaypowertest;

/** Real PServer second confirmation; sockets and Binder remain inert fixtures. */
public final class PredecessorRecheckHostTest {
    private static int checks;
    private static int cases;
    private static void check(boolean value, String why) {
        checks++;
        if (!value) throw new AssertionError(why);
    }
    private static void run(String id, String change, boolean approved) {
        FixtureState.reset("1.7.2");
        String health = FixtureState.health();
        String snapshot = FixtureState.snapshot();
        String secondHealth = health;
        String secondSnapshot = snapshot;
        if ("pid".equals(change)) secondHealth = health.replace("pid=12000", "pid=12001");
        if ("gone".equals(change)) secondHealth = null;
        if ("mode".equals(change)) secondSnapshot = snapshot.replace("mode=0", "mode=1");
        if ("snapshot-gone".equals(change)) secondSnapshot = null;
        FixtureState.scriptedReplies = "gone".equals(change)
                ? new String[] {health, snapshot, secondHealth}
                : new String[] {health, snapshot, secondHealth, secondSnapshot};
        int result = PServer.stopLivePredecessorIfSafe(true, false, false);
        // Assert side effects before read counts so the mutation is rejected
        // for unsafe bridge admission, not merely a changed observation count.
        check(FixtureState.kills == (approved ? 1 : 0), id + " stop admission");
        check(result == (approved ? 12000 : -1), id + " result");
        check(FixtureState.launches == 0, id + " no successor launch from helper");
        check(FixtureState.replyIndex == FixtureState.scriptedReplies.length, id + " all scripted observations consumed");
        check(FixtureState.requests.toString().equals("gone".equals(change) ? "IQI" : "IQIQ"), id + " exact two-round order");
        cases++;
        System.out.println("PASS " + id + "; actual PServer, socket/Binder fixtures");
    }
    public static void main(String[] args) {
        if (args.length > 0) { run("S2-recheck-pid", "pid", false); return; }
        run("S2-recheck-stable", "stable", true);
        run("S2-recheck-pid", "pid", false);
        run("S2-recheck-disappeared", "gone", false);
        run("S2-recheck-mode", "mode", false);
        run("S2-recheck-snapshot-unavailable", "snapshot-gone", false);
        System.out.println("PredecessorRecheckHostTest passed: " + cases + " cases, " + checks
                + " checks; process birth/peer credentials/native stop NOT PROVEN");
    }
}
