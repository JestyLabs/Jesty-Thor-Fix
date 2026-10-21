package com.thor.displaypowertest;

import static com.thor.displaypowertest.AutomaticBottomOnPolicy.*;
import static com.thor.displaypowertest.AutomaticBottomWakeRecoveryModel.*;

/** Host-only modeled liveness/safety checks; NOT real panel or Android tests. */
public final class AutomaticBottomWakeRecoveryModelTest {
    private static int checks;
    private static void check(boolean pass, String label) {
        if (!pass) throw new AssertionError(label);
        checks++;
    }

    private static Outcome observe(AutomaticBottomWakeRecoveryModel model,
            long generation, Interactive interactive) {
        return model.observe(generation, "0", "1", "0", true, false, false, interactive);
    }

    public static void main(String[] args) {
        AutomaticBottomWakeRecoveryModel normal = new AutomaticBottomWakeRecoveryModel();
        check(observe(normal, 1, Interactive.ASLEEP) == Outcome.DEFERRED_ASLEEP,
                "sleep must defer automatic ON");
        check(observe(normal, 1, Interactive.UNKNOWN) == Outcome.DEFERRED_UNKNOWN,
                "unknown wake remains deferred");
        check(observe(normal, 1, Interactive.AWAKE) == Outcome.AWAKE_STABILIZING,
                "first confirmed awake sample is not sufficient after sleep");
        check(observe(normal, 1, Interactive.AWAKE) == Outcome.ON_CANDIDATE,
                "second fresh awake sample can recover, conditional on commit check");
        check(observe(normal, 1, Interactive.AWAKE) == Outcome.ON_CANDIDATE,
                "later valid awake candidate is not permanently stranded");
        check(observe(normal, 1, Interactive.ASLEEP) == Outcome.DEFERRED_ASLEEP,
                "new lid-sleep revokes authorization");
        check(observe(normal, 1, Interactive.AWAKE) == Outcome.AWAKE_STABILIZING,
                "wake must restabilize after sleeping again");
        check(observe(normal, 2, Interactive.AWAKE) == Outcome.ON_CANDIDATE,
                "generation invalidation discards old deferral");
        check(normal.observe(2, "1", "1", "1", true, false, false, Interactive.AWAKE)
                == Outcome.NONE, "TOP mode bypasses BOTH recovery");
        check(normal.observe(2, "0", "1", "1", true, false, false, Interactive.AWAKE)
                == Outcome.NONE, "already active bottom clears state");
        check(normal.observe(2, "0", "1", "0", false, false, false, Interactive.AWAKE)
                == Outcome.NONE, "Fix OFF never requests ON");
        check(normal.observe(2, "0", "?", "0", true, false, false, Interactive.AWAKE)
                == Outcome.NONE, "unknown TOP never requests ON");

        AutomaticBottomWakeRecoveryModel unreliable = new AutomaticBottomWakeRecoveryModel();
        check(observe(unreliable, 3, Interactive.ASLEEP) == Outcome.DEFERRED_ASLEEP,
                "start asleep for unknown signal fixture");
        for (int n=0; n<100; n++) {
            Outcome r = observe(unreliable, 3, Interactive.UNKNOWN);
            check(r != Outcome.ON_CANDIDATE, "UNKNOWN cannot bypass to ON");
            if (n >= 9) check(r == Outcome.NEEDS_MANUAL_REVIEW,
                    "after repeated UNKNOWN, surface manual review rather than fake automatic recovery");
        }
        check(observe(unreliable, 3, Interactive.AWAKE) == Outcome.AWAKE_STABILIZING,
                "manual escalation still permits fresh awake recovery");
        check(observe(unreliable, 3, Interactive.AWAKE) == Outcome.ON_CANDIDATE,
                "subsequently valid signal permits recovery");
        check(!AutomaticBottomOnPolicy.commitAllowed(
                Decision.ALLOW_CANDIDATE_ON, Interactive.ASLEEP,
                true, "0", "1", "0", true, false, false),
                "even a planned ON is cancelled if power state turned asleep");
        check(!AutomaticBottomOnPolicy.commitAllowed(
                Decision.ALLOW_CANDIDATE_ON, Interactive.AWAKE,
                false, "0", "1", "0", true, false, false),
                "even an awake planned ON is cancelled on epoch change");
        System.out.println("AutomaticBottomWakeRecoveryModelTest PASSED: " + checks + " checks");
    }
}
