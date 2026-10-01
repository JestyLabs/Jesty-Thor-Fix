import com.thor.displaypowertest.DisplayDecisionModel;

public final class DisplayDecisionModelTest {
    private static void check(DisplayDecisionModel.Action expected, String mode,
            String top, String bottom, boolean enabled, boolean held,
            boolean pending, String reason) {
        DisplayDecisionModel.Action actual = DisplayDecisionModel.reconcile(mode,
                top, bottom, enabled, held, pending);
        if (actual != expected) throw new AssertionError(reason + ": " + actual);
    }

    public static void main(String[] args) {
        DisplayDecisionModel.Action none = DisplayDecisionModel.Action.NONE;
        DisplayDecisionModel.Action on = DisplayDecisionModel.Action.ON;
        DisplayDecisionModel.Action off = DisplayDecisionModel.Action.OFF;
        check(off, "1", "1", "1", true, false, false, "TOP repair");
        check(none, "1", "1", "0", true, false, false, "TOP already off");
        check(none, "1", "1", "1", true, false, true, "preserve delayed wake repair");
        check(on, "0", "1", "0", true, false, false, "BOTH restore");
        check(none, "0", "1", "1", true, false, false, "BOTH already on");
        check(none, "2", "0", "0", true, false, false, "BOTTOM may be asleep");
        check(none, "0", "0", "0", true, false, false, "both CRTCs off may be sleep");
        check(none, "?", "1", "0", true, false, false, "unknown mode");
        check(none, "0", "?", "0", true, false, false, "unknown top CRTC");
        check(none, "1", "1", "?", true, false, false, "unknown bottom CRTC");
        check(none, "1", "1", "1", false, false, false, "fix disabled");
        check(none, "1", "1", "1", true, true, false, "boot hold");
        if (!"CONFIRMED".equals(DisplayDecisionModel.effective("1", "1", "0", true, false)))
            throw new AssertionError("TOP true-off effective");
        if (!"CONFIRMED".equals(DisplayDecisionModel.effective("1", "1", "1", false, false)))
            throw new AssertionError("native TOP effective");
        if (!"MISMATCH".equals(DisplayDecisionModel.effective("1", "1", "1", true, false)))
            throw new AssertionError("fix intent must not conceal active lower hardware");
        if (!"PENDING".equals(DisplayDecisionModel.effective("1", "0", "0", true, false)))
            throw new AssertionError("sleep cannot confirm display fix");
        if (!"PENDING".equals(DisplayDecisionModel.effective("?", "1", "0", true, false)))
            throw new AssertionError("unknown mode cannot confirm display fix");
        if (!"CONFIRMED".equals(DisplayDecisionModel.effective("0", "1", "1", true, false)))
            throw new AssertionError("BOTH with fix desired keeps lower hardware on");
        if (!"CONFIRMED".equals(DisplayDecisionModel.effective("2", "1", "1", true, false)))
            throw new AssertionError("BOTTOM with active upper CRTC is observed stock behavior");
        if (!"CONFIRMED".equals(DisplayDecisionModel.effective("2", "0", "1", true, false)))
            throw new AssertionError("BOTTOM with inactive upper CRTC is also valid");
        if (!"MISMATCH".equals(DisplayDecisionModel.effective("2", "1", "0", true, false)))
            throw new AssertionError("BOTTOM with lower CRTC off is not confirmed");
        if (DisplayDecisionModel.readyForReconcile("2", "1", "0"))
            throw new AssertionError("BOTTOM must not force the lower panel on while transitioning");
        if (!DisplayDecisionModel.readyForReconcile("2", "1", "1"))
            throw new AssertionError("observed BOTTOM state permits idempotent confirmation");
        if (DisplayDecisionModel.readyForReconcile("?", "1", "1"))
            throw new AssertionError("unknown mode must not be reconciled");
        System.out.println("DisplayDecisionModelTest passed");
    }
}
