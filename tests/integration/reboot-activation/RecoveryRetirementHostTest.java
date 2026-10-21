package com.thor.displaypowertest;

/** Actual PServer and retirement controllers; OS/process effects remain inert. */
public final class RecoveryRetirementHostTest {
    private static int cases;
    private static void check(boolean value, String why) {
        if (!value) throw new AssertionError(why);
    }

    private static String health(String phase) {
        return "ok=1;protocol=2;version=1.7.5;pid=12000;boot_phase=" + phase
                + ";phase_ms=90000;fix=1;watcher=STOPPED";
    }

    private static void run(String id, String first, String second, boolean allowed,
            int signals, java.util.function.Consumer<String> setup) throws Exception {
        run(id, first, second, allowed, signals, setup, () -> true);
    }

    private static void run(String id, String first, String second, boolean allowed,
            int signals, java.util.function.Consumer<String> setup, LiveRetirement.CommitGate gate) throws Exception {
        FixtureState.reset("1.7.5");
        FixtureState.phase = "HANDOFF_FAILED";
        FixtureState.scriptedReplies = new String[] {first, first, second};
        setup.accept(id);
        boolean result = PServer.stopReplaceableSecureDaemonIfSafe(true, gate);
        Thread.interrupted();
        RetirementNative.awaitHelper();
        check(result == allowed, id + ": recovery result requires confirmed exit");
        check(FixtureState.kills == signals, id + ": stop admission");
        check(FixtureState.launches == 0, id + ": helper never launches successor");
        check(!FixtureState.requests.toString().contains("Q"), id + ": failed start needs no Q");
        if (allowed) {
            check(FixtureState.requests.toString().equals("III"), id + ": prefilter plus two retained identities");
            check(FixtureState.events.indexOf("BIND_PROCESS")
                    < FixtureState.events.indexOf("REQUEST_I"), id + ": retained before first I");
            check(FixtureState.events.indexOf("EXIT_CONFIRMED")
                    < FixtureState.events.indexOf("EXIT_RECEIPT"), id + ": exit before receipt");
        }
        cases++;
        System.out.println("PASS recovery " + id);
    }

    public static void main(String[] args) throws Exception {
        String failed = health("HANDOFF_FAILED");
        java.util.function.Consumer<String> inert = ignored -> {};
        if (args.length > 0 && args[0].equals("no-effect")) {
            run("no-effect", failed, failed, false, 1, ignored -> FixtureState.stopEffective = false);
            return;
        }
        run("failed", failed, failed, true, 1, inert);
        run("gate-denied", failed, failed, false, 0, inert, () -> false);
        check(!FixtureState.events.contains("COMMIT"), "gate-denied: no COMMIT");
        check("1.7.5".equals(FixtureState.version), "gate-denied: daemon preserved");
        for (String phase : new String[] {"BOOT_HOLD", "WAITING_FOR_ANDROID", "APPLYING_CPU_FIX",
                "WAITING_AFTER_COMPOSER", "RECONCILING_DISPLAY"}) {
            run("stalled-" + phase, health(phase), health(phase), true, 1, inert);
        }
        for (String phase : new String[] {"READY", "BOOT_SAFETY_TIMEOUT", "UNKNOWN"}) {
            run("deny-" + phase, health(phase), health(phase), false, 0, inert);
        }
        run("native-unavailable", failed, failed, false, 0,
                ignored -> RetirementNative.available = false);
        run("wrong-peer", failed, failed, false, 0, ignored -> RetirementNative.daemonUid = 2000);
        run("changed-peer", failed, failed, false, 0, ignored -> RetirementNative.changedPeerAt = 1);
        run("wrong-helper", failed, failed, false, 0, ignored -> RetirementNative.helperUid = 2000);
        run("wrong-version", failed.replace("1.7.5", "1.7.4"), failed, false, 0, inert);
        run("claimed-pid", failed.replace("12000", "13000"), failed, false, 0, inert);
        run("wrong-intent", failed.replace("fix=1", "fix=0"), failed, false, 0, inert);
        run("starting-young", health("BOOT_HOLD").replace("90000", "89999"), failed, false, 0, inert);
        for (String key : new String[] {"ok", "protocol", "version", "pid", "fix", "boot_phase", "phase_ms"}) {
            String value = key.equals("boot_phase") ? "HANDOFF_FAILED" : key.equals("pid") ? "12000"
                    : key.equals("version") ? "1.7.5" : key.equals("protocol") ? "2"
                    : key.equals("phase_ms") ? "90000" : "1";
            run("duplicate-" + key, failed + ";" + key + "=" + value, failed, false, 0, inert);
        }
        run("second-ready", failed, health("READY"), false, 0, inert);
        run("changed-failed-phase", failed, health("BOOT_HOLD"), false, 0, inert);
        run("second-wrong-version", failed, failed.replace("1.7.5", "1.7.4"), false, 0, inert);
        run("second-duplicate", failed, failed + ";pid=12000", false, 0, inert);
        run("second-gone", failed, null, false, 0, inert);
        run("no-effect", failed, failed, false, 1, ignored -> FixtureState.stopEffective = false);
        run("bridge-ack-only", failed, failed, false, 0, ignored -> RetirementNative.bridgeAckOnly = true);
        run("cancel-before", failed, failed, false, 0, ignored -> RetirementNative.cancelBeforeCommit = true);
        run("cancel-after", failed, failed, false, 1, ignored -> RetirementNative.cancelAfterCommit = true);
        for (String fault : new String[] {"missing", "stale", "malformed", "oversized"}) {
            run("receipt-" + fault, failed, failed, false, 1, ignored -> RetirementNative.receiptFault = fault);
        }
        run("close-failure", failed, failed, false, 1, ignored -> RetirementNative.processCloseFailure = true);
        System.out.println("RecoveryRetirementHostTest passed: " + cases + " cases; native effects MODELLED");
    }
}
