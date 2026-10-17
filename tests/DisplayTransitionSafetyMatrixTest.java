import com.thor.displaypowertest.DisplayDecisionModel;
import com.thor.displaypowertest.DisplayGenerationModel;

/**
 * S1 offline characterization against the REAL production decision/generation
 * classes, not a replacement coordinator. This is NOT a Thor wake/blackout
 * reproduction and does not exercise vendor power, Android or physical CRTC.
 */
public final class DisplayTransitionSafetyMatrixTest {
    private static int assertions;

    private static void require(boolean value, String reason) {
        assertions++;
        if (!value) throw new AssertionError(reason);
    }

    private static void action(DisplayDecisionModel.Action expected, String mode,
            String top, String bottom, boolean fix, boolean held, boolean pending,
            String reason) {
        require(DisplayDecisionModel.reconcile(mode, top, bottom, fix, held, pending)
                == expected, reason);
    }

    private static void exhaustiveCommandAdmission() {
        String[] modes = { "0", "1", "2", "?", "", null };
        String[] crtc = { "0", "1", "?", "", null };
        for (String mode : modes) {
            for (String top : crtc) {
                for (String bottom : crtc) {
                    for (int flags = 0; flags < 8; flags++) {
                        boolean fix = (flags & 1) != 0;
                        boolean held = (flags & 2) != 0;
                        boolean pending = (flags & 4) != 0;
                        DisplayDecisionModel.Action actual =
                                DisplayDecisionModel.reconcile(mode, top, bottom,
                                        fix, held, pending);
                        String context = "mode=" + mode + ",top=" + top +
                                ",bottom=" + bottom + ",flags=" + flags;
                        // Power ON is allowed only for an active known BOTH
                        // state with bottom observed OFF and no boot hold.
                        if (actual == DisplayDecisionModel.Action.ON) {
                            require("0".equals(mode) && "1".equals(top)
                                    && "0".equals(bottom) && fix && !held,
                                    "unauthorized ON: " + context);
                        }
                        // Power OFF is allowed only in known TOP with both
                        // CRTCs active, no boot hold and no delayed repair.
                        if (actual == DisplayDecisionModel.Action.OFF) {
                            require("1".equals(mode) && "1".equals(top)
                                    && "1".equals(bottom) && fix && !held
                                    && !pending, "unauthorized OFF: " + context);
                        }
                        if (!fix || held || !("0".equals(mode) || "1".equals(mode))
                                || !"1".equals(top)
                                || !("0".equals(bottom) || "1".equals(bottom))) {
                            require(actual == DisplayDecisionModel.Action.NONE,
                                    "unknown/asleep/held state commanded panel: " + context);
                        }
                        if (held) {
                            require("PENDING".equals(DisplayDecisionModel.effective(
                                    mode, top, bottom, fix, true)),
                                    "boot hold must not claim confirmed: " + context);
                        }
                    }
                }
            }
        }
        action(DisplayDecisionModel.Action.ON, "0", "1", "0", true,
                false, false, "S1-02 BOTH needs restoration");
        action(DisplayDecisionModel.Action.NONE, "0", "1", "1", true,
                false, false, "S1-01 BOTH already active");
        action(DisplayDecisionModel.Action.OFF, "1", "1", "1", true,
                false, false, "S1-03 TOP needs true-off");
        action(DisplayDecisionModel.Action.NONE, "1", "1", "1", true,
                false, true, "pending TOP wake repair reserves reconciliation");
        action(DisplayDecisionModel.Action.NONE, "1", "0", "0", true,
                false, false, "asleep display cannot receive speculative action");
        action(DisplayDecisionModel.Action.NONE, "2", "0", "0", true,
                false, false, "BOTTOM only must not unconditionally power panel");
        action(DisplayDecisionModel.Action.NONE, "?", "1", "0", true,
                false, false, "unknown mode must fail closed");
    }

    private static void deterministicGenerationInterleavings() {
        DisplayGenerationModel generations = new DisplayGenerationModel();
        require(!generations.isRepairCurrent(), "initial generation has no repair");
        long topRepair = generations.scheduleRepair();
        require(generations.isRepairCurrent(), "TOP wake repair scheduled");
        action(DisplayDecisionModel.Action.NONE, "1", "1", "1", true,
                false, generations.isRepairCurrent(),
                "pending TOP repair prevents immediate OFF");
        generations.invalidate(); // TOP -> BOTH mode edge
        require(generations.current() > topRepair && !generations.isRepairCurrent(),
                "S1-06 TOP repair invalid after BOTH edge");
        action(DisplayDecisionModel.Action.ON, "0", "1", "0", true,
                false, generations.isRepairCurrent(), "BOTH restore may proceed");
        generations.invalidate(); // BOTH -> TOP
        long newest = generations.scheduleRepair();
        require(generations.current() == newest && generations.isRepairCurrent(),
                "S1-07 newest TOP repair wins");
        generations.completeRepair();
        require(!generations.isRepairCurrent(), "completed repair no longer current");
        for (int i = 0; i < 100; i++) {
            long prior = generations.scheduleRepair();
            generations.invalidate(); // stale wake after mode edge or sleep
            require(!generations.isRepairCurrent() && generations.current() > prior,
                    "stale scheduled repair survived invalidation: " + i);
            long fresh = generations.scheduleRepair();
            require(fresh > prior && generations.isRepairCurrent(),
                    "fresh repair not admitted: " + i);
            generations.completeRepair();
            require(!generations.isRepairCurrent(),
                    "duplicate stale completion left repair live: " + i);
        }
    }

    public static void main(String[] args) {
        exhaustiveCommandAdmission();
        deterministicGenerationInterleavings();
        System.out.println("DisplayTransitionSafetyMatrixTest passed (" +
                assertions + " assertions, production model classes only)");
    }
}
