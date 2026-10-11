package com.thor.displaypowertest;

import static com.thor.displaypowertest.AutomaticBottomOnPolicy.*;

/** Pure host-only regressions; does not query Android or touch Thor hardware. */
public final class AutomaticBottomOnPolicyTest {
    private static int passed;
    private static void expect(boolean value, String description) {
        if (!value) throw new AssertionError(description);
        passed++;
    }
    private static Decision plan(String mode, String top, String bottom, Interactive awake) {
        return AutomaticBottomOnPolicy.plan(mode, top, bottom, true, false, false, awake);
    }
    private static boolean commit(Decision d, Interactive now, boolean sameEpoch,
            String mode, String top, String bottom) {
        return commitAllowed(d, now, sameEpoch, mode, top, bottom,
                true, false, false);
    }
    public static void main(String[] args) {
        expect(DisplayDecisionModel.reconcile("0", "1", "0", true, false, false)
                == DisplayDecisionModel.Action.ON, "baseline ON for BOTH 1/0");
        expect(plan("0", "1", "0", Interactive.ASLEEP) == Decision.SUPPRESS_ASLEEP,
                "sleep observation suppresses candidate");
        expect(plan("0", "1", "0", Interactive.UNKNOWN) == Decision.SUPPRESS_UNKNOWN,
                "unknown observation suppresses candidate");
        expect(plan("0", "1", "0", Interactive.AWAKE) == Decision.ALLOW_CANDIDATE_ON,
                "awake BOTH recovery remains possible");
        expect(plan("0", "1", "1", Interactive.AWAKE) == Decision.NONE,
                "already active bottom never requests ON");
        expect(plan("1", "1", "1", Interactive.AWAKE) == Decision.NONE,
                "TOP path is separate");
        expect(DisplayDecisionModel.reconcile("1", "1", "1", true, false, false)
                == DisplayDecisionModel.Action.OFF, "TOP OFF path unchanged");
        expect(plan("0", "?", "0", Interactive.AWAKE) == Decision.NONE,
                "unknown TOP CRTC is not enough");
        expect(plan("?", "1", "0", Interactive.AWAKE) == Decision.NONE,
                "unknown mode is not enough");
        expect(AutomaticBottomOnPolicy.plan("0", "1", "0", false, false,
                false, Interactive.AWAKE) == Decision.NONE, "Fix disabled blocks ON");
        expect(AutomaticBottomOnPolicy.plan("0", "1", "0", true, true,
                false, Interactive.AWAKE) == Decision.NONE, "actions held blocks ON");
        // Surprising current-production behavior: wakeRepairPending is
        // ignored in the BOTH ON clause. This prototype would close that gap.
        expect(DisplayDecisionModel.reconcile("0", "1", "0", true, false, true)
                == DisplayDecisionModel.Action.ON, "baseline ON while repair pending");
        expect(AutomaticBottomOnPolicy.plan("0", "1", "0", true, false,
                true, Interactive.AWAKE) == Decision.NONE, "prototype blocks competing repair");
        Decision planned = plan("0", "1", "0", Interactive.AWAKE);
        expect(commit(planned, Interactive.AWAKE, true, "0", "1", "0"),
                "confirmed awake with stable generation allows ON");
        expect(!commit(planned, Interactive.ASLEEP, true, "0", "1", "0"),
                "sleep between sample and commit cancels ON");
        expect(!commit(planned, Interactive.UNKNOWN, true, "0", "1", "0"),
                "unknown recheck cancels ON");
        expect(!commit(planned, Interactive.AWAKE, false, "0", "1", "0"),
                "changed generation cancels ON");
        expect(!commit(planned, Interactive.AWAKE, true, "0", "1", "1"),
                "already recovered bottom cancels stale ON");
        expect(!commit(planned, Interactive.AWAKE, true, "1", "1", "0"),
                "TOP mode cancels stale BOTH ON");
        expect(commit(plan("0", "1", "0", Interactive.AWAKE),
                Interactive.AWAKE, true, "0", "1", "0"),
                "new awake plan after sleep can recover lower panel");
        expect(!commit(Decision.SUPPRESS_ASLEEP, Interactive.AWAKE, true,
                "0", "1", "0"), "old sleeping plan cannot be revived without resampling");
        System.out.println("AutomaticBottomOnPolicyTest PASSED: " + passed + " assertions");
    }
}
