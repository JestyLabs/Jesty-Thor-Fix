import com.thor.displaypowertest.BootGateModel;
import com.thor.displaypowertest.PropertyState;

/**
 * Exhaustive finite-input matrix for the current pure boot/display gate.
 * No SurfaceFlinger, daemon commands, device access or wake repair operations.
 */
public final class BootGateAdversarialTest {
    private static int assertions;
    private static final String[] MODES = {"0", "1", "2", "3", "?", "", null};
    private static final String[] CRTC = {"0", "1", "?", "2", null};

    private static void check(boolean ok, String what) {
        assertions++;
        if (!ok) throw new AssertionError(what);
    }

    private static boolean validMode(String mode) {
        return "0".equals(mode) || "1".equals(mode) || "2".equals(mode);
    }
    private static boolean validCrtc(String crtc) {
        return "0".equals(crtc) || "1".equals(crtc);
    }

    private static void displayWritesAreNeverSpeculative() {
        for (String mode : MODES) {
            for (String lower : CRTC) {
                for (boolean fixEnabled : new boolean[] {false, true}) {
                    boolean actual = BootGateModel.displayActionRequired(mode, fixEnabled, lower);
                    boolean expected = false;
                    if (validMode(mode) && !"2".equals(mode) && validCrtc(lower)) {
                        boolean shouldBeOn = !fixEnabled || !"1".equals(mode);
                        expected = shouldBeOn ? "0".equals(lower) : "1".equals(lower);
                    }
                    check(actual == expected, "display action for mode=" + mode
                            + " bottom=" + lower + " fix=" + fixEnabled);
                }
            }
        }
    }

    private static void repairOnlyWhenAllPositiveEvidenceExists() {
        for (String mode : MODES) {
            for (String top : CRTC) {
                for (String bottom : CRTC) {
                    for (boolean fix : new boolean[] {false, true}) {
                        for (boolean pending : new boolean[] {false, true}) {
                            boolean expected = "1".equals(mode) && "1".equals(top)
                                    && "1".equals(bottom) && fix && !pending;
                            boolean actual = BootGateModel.shouldRepairStableTop(
                                    mode, top, bottom, fix, pending);
                            check(expected == actual, "stable TOP repair mode=" + mode
                                    + " top=" + top + " bottom=" + bottom
                                    + " fix=" + fix + " pending=" + pending);
                        }
                    }
                }
            }
        }
    }

    private static void cpuBootNeverRestartsTwice() {
        String[] properties = {"0", "1", "?", "2", PropertyState.UNSET, null};
        for (boolean desired : new boolean[] {false, true}) {
            for (boolean post : new boolean[] {false, true}) {
                for (String state : properties) {
                    BootGateModel.CpuAction action =
                            BootGateModel.cpuAction(desired, state, post);
                    if (post) {
                        check(action != BootGateModel.CpuAction.RESTART_ONCE,
                                "never second composer restart for property " + state);
                    } else {
                        check(action != null, "non-null CPU action");
                    }
                    if (!"0".equals(state) && !"1".equals(state)
                            && !(desired && PropertyState.UNSET.equals(state) && !post)) {
                        check(action == BootGateModel.CpuAction.FAIL_SAFE,
                                "unknown/malformed property cannot authorize a restart");
                    }
                }
            }
        }
    }

    private static void everyDisplayCombinationKeepsReadinessGrace() {
        for (boolean post : new boolean[] {false, true}) {
            for (String mode : new String[] {"0", "1", "2"}) {
                for (String top : new String[] {"0", "1"}) {
                    for (String lower : new String[] {"0", "1"}) {
                        BootGateModel gate = new BootGateModel(0L, post);
                        check(gate.observe(0L, true, true, mode, top, lower)
                                == BootGateModel.Result.WAIT, "first sample");
                        check(gate.observe(500L, true, true, mode, top, lower)
                                == BootGateModel.Result.WAIT, "second sample");
                        check(gate.observe(1000L, true, true, mode, top, lower)
                                == BootGateModel.Result.WAIT, "third sample");
                        check(gate.stableSamples() == 3 && gate.stableSinceMs() == 1000L,
                                "grace starts only on third stable sample");
                        long readyAt = 1000L + gate.graceMs();
                        check(gate.observe(readyAt - 1L, true, true, mode, top, lower)
                                == BootGateModel.Result.WAIT, "no early READY");
                        check(gate.observe(readyAt, true, true, mode, top, lower)
                                == BootGateModel.Result.READY, "exact readiness boundary");

                        // One invalid device sample after READY must discard
                        // all prior stability; holding the old CRTC is unsafe.
                        check(gate.observe(readyAt + 1L, true, true, mode, "?", lower)
                                == BootGateModel.Result.WAIT, "unknown CRTC resets stability");
                        check(gate.stableSamples() == 0 && gate.stableSinceMs() < 0L,
                                "stability history fully reset");
                        check(gate.observe(readyAt + 500L, true, true, mode, top, lower)
                                == BootGateModel.Result.WAIT, "returning CRTC requires fresh samples");
                    }
                }
            }
        }
        BootGateModel timeout = new BootGateModel(0L, false);
        check(timeout.observe(60000L, true, true, "1", "1", "0")
                == BootGateModel.Result.TIMEOUT, "60s budget always enforced");

        // Unknown boot prerequisites do not accumulate false stable samples.
        BootGateModel unsafe = new BootGateModel(0L, false);
        check(unsafe.observe(0L, false, true, "1", "1", "1")
                == BootGateModel.Result.WAIT, "Android not yet booted");
        check(unsafe.observe(500L, true, false, "1", "1", "1")
                == BootGateModel.Result.WAIT, "composer not yet running");
        check(unsafe.observe(1000L, true, true, "?", "1", "1")
                == BootGateModel.Result.WAIT, "unknown mode invalid");
        check(unsafe.stableSamples() == 0, "invalid samples cannot accumulate");
        check(unsafe.observe(1500L, true, true, "1", "1", "1")
                == BootGateModel.Result.WAIT && unsafe.stableSamples() == 1,
                "recovery begins with first sample, not instant READY");
    }

    public static void main(String[] args) {
        displayWritesAreNeverSpeculative();
        repairOnlyWhenAllPositiveEvidenceExists();
        cpuBootNeverRestartsTwice();
        everyDisplayCombinationKeepsReadinessGrace();
        System.out.println("BootGateAdversarialTest passed (" + assertions
                + " deterministic cases)");
    }
}
