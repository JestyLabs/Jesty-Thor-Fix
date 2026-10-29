package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * Poisoned-boot detection with the real coordinator and synthetic platform boundaries.
 * Not a physical test.
 *
 * Hardware basis (2026-10-29, owner-supervised): a panel-RAM bypass write with the lower
 * panel off logged "DSI_PLL_0: lock failed"; the next lower start logged six
 * "dsi-ctrl-0: Command transfer failed" and its ON call took 1407 ms; the start after
 * that logged "wait for commit done failed ret:-110" on CRTC 243 and blanked both
 * screens. Neither ON call reached the 5 s stall latch. Healthy lower ON calls in the
 * retained logs took 202-428 ms. The kernel lines below are copied from those logs.
 * Arguments: none = all, or one case name.
 */
public final class PoisonedBootTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message + "\n" + Log.LINES);
    }
    private static int count(String prefix) {
        int n = 0;
        for (String line : Log.LINES) if (line.startsWith("ThorDisplayDaemon: " + prefix)) n++;
        return n;
    }

    static final String PLL = "[12671.929086] [drm:dsi_pll_4nm_toggle [msm_drm]] *ERROR* [msm-dsi-error]: "
            + "DSI_PLL_0: lock failed, status=0x00000000";
    static final String BYPASS_SEND = "[12671.937765] [drm:dsi_panel_switch_bypass_ram [msm_drm]] *ERROR* "
            + "[msm-dsi-error]: [ch13726a video mode dsi boe panel with DSC] failed to send "
            + "DSI_CMD_SET_VID_BYPASS_RAM cmds, rc=-22";
    static final String INIT = "[12837.092748] [drm:dsi_ctrl_dma_cmd_wait_for_done [msm_drm]] *ERROR* "
            + "[msm-dsi-error]: dsi-ctrl-0: Command transfer failed";
    static final String COMMIT = "[13015.980945] [drm:sde_kms_wait_for_commit_done:1718] [sde error]crtc:243, "
            + "enc:79, cwb_d:0, wait for commit done failed ret:-110";
    static final String[] HEALTHY = {
        "[ 1545.368406] oscar bypass_ram_store 1",
        "[12836.892803] oscar bypass_ram 1",
        "[12836.860107] dsi_panel_reset:338",
        "[12837.694030] jaden DSI_CMD_SET_ON",
        "[ 1513.122326] [drm:dsi_display_set_mode [msm_drm]] [msm-dsi-info]: mdp_transfer_time=0, hactive=1080, "
            + "vactive=1240, fps=60, clk_rate=1011000000",
        "[12671.952690] [FTS_TS/E]fts_ts_panel_notifier_callback:shashank:Received fps change old fps:120 new fps:60",
        "[12616.973798] [irq][0x386a4c4f5b][00:55:06.972967] kiwi_v2: [0:I:HIF] hif_wake_interrupt_handler: "
            + "wake interrupt received on irq 325",
        "[13014.027381] [drm:dsi_ctrl_isr_configure [msm_drm]] [msm-dsi-info]: dsi-ctrl-1: IRQ 350 registered",
        // Other controller and CRTC: not this detector's evidence.
        "[  100.000000] [drm:dsi_ctrl_dma_cmd_wait_for_done [msm_drm]] *ERROR* [msm-dsi-error]: dsi-ctrl-1: Command transfer failed",
        "[  100.000000] [drm:sde_kms_wait_for_commit_done:1718] [sde error]crtc:181, enc:31, cwb_d:0, wait for commit done failed ret:-110",
        "6,4321,12671952690,-;audit: type=1400 avc: denied { read } for comm=\"dmesg\" name=\"kmsg\"",
    };

    static void model() {
        check(KernelSignalModel.PRECURSOR.equals(KernelSignalModel.classify(PLL)), "PB-01 PLL lock failure is the precursor");
        check(KernelSignalModel.PRECURSOR.equals(KernelSignalModel.classify(BYPASS_SEND)), "PB-01b failed bypass send is the precursor");
        check(KernelSignalModel.INIT_FAIL.equals(KernelSignalModel.classify(INIT)), "PB-01c lower command timeout");
        check(KernelSignalModel.COMMIT_110.equals(KernelSignalModel.classify(COMMIT)), "PB-01d lower commit timeout");
        String kmsg = "3,98765,12671929086,-;" + PLL.substring(PLL.indexOf(']') + 2) + "\n SUBSYSTEM=drm\n DEVICE=+drm:card0\n";
        check(KernelSignalModel.PRECURSOR.equals(KernelSignalModel.classify(kmsg)), "PB-03 /dev/kmsg record with continuation lines");
        for (String line : HEALTHY) {
            check(KernelSignalModel.classify(line) == null, "PB-02 healthy or unrelated line ignored: " + line);
        }
        check(KernelSignalModel.classify(null) == null && KernelSignalModel.classify("") == null, "PB-02b empty input");
        String excerpt = KernelSignalModel.excerpt(kmsg);
        check(excerpt.contains("DSI_PLL_0:_lock_failed") && excerpt.indexOf(';') < 0 && excerpt.indexOf('=') < 0
                && excerpt.length() <= 120, "PB-03b log-safe excerpt: " + excerpt);
    }

    private static void top() {
        SystemClock.now += 100000L;
        TestDisplayState.baseline();
        Log.LINES.clear();
    }
    private static void sample() {
        SystemClock.now += 50L;
        DisplayActionCoordinator.onWatcherSample(TestDisplayState.mode);
    }
    private static void toBoth(long callMs) {
        TestDisplayState.mode = "0";
        TestDisplayState.callAdvanceMs = callMs;
        sample();
        TestDisplayState.callAdvanceMs = 0L;
        SystemClock.now += 2000L;
        sample();
    }
    private static void toTop() {
        TestDisplayState.mode = "1";
        sample();
        SystemClock.now += 2000L;
        sample();
    }

    static void degradeAndSuppress() {
        top();
        toBoth(207L);
        check("1".equals(TestDisplayState.bottom), "PB-04 healthy start powers the lower panel");
        DisplayActionCoordinator.latchLowerPoisoned(KernelSignalModel.INIT_FAIL);
        check(count("LOWER_POISONED reason=KMSG_INIT_FAIL") == 1
                && "POISONED_KMSG_INIT_FAIL".equals(DisplayActionCoordinator.lowerFailureState()), "PB-04b poisoned state");
        int calls = TestDisplayState.hardwareCalls;
        SystemClock.now += 2000L;
        sample();
        check("0".equals(TestDisplayState.bottom) && TestDisplayState.hardwareCalls == calls + 1
                && "BOTH_DEGRADED".equals(DisplayActionCoordinator.status()),
                "PB-04c the lower panel is turned off in BOTH: " + DisplayActionCoordinator.status());
        toTop();
        int before = TestDisplayState.hardwareCalls;
        toBoth(207L);
        check(TestDisplayState.hardwareCalls == before && "0".equals(TestDisplayState.bottom),
                "PB-05 no automatic lower ON on the next TOP -> BOTH: calls=" + TestDisplayState.hardwareCalls);
    }

    static void manualReconcile() {
        top();
        DisplayActionCoordinator.latchLowerPoisoned(KernelSignalModel.PRECURSOR);
        int calls = TestDisplayState.hardwareCalls;
        String off = DisplayActionCoordinator.requestFix(false);
        check("ok=0;error=LOWER_POISONED_REBOOT_REQUIRED".equals(off) && TestDisplayState.hardwareCalls == calls,
                "PB-06 turning the fix off would power the lower panel: refused: " + off);
        String on = DisplayActionCoordinator.requestFix(true);
        check(on.startsWith("ok=1;") && count("LOWER_FAILURE_LATCH kept cause=USER_RECONCILE;poisoned=KMSG_PRECURSOR") == 1
                && DisplayActionCoordinator.isLowerFailureLatched(),
                "PB-07 manual reconcile keeps a poisoned latch: " + on);
    }

    static void slowOnWithoutKernelSignals() {
        top();
        DisplayActionCoordinator.setKernelSignalsAvailable(false);
        toBoth(1407L);
        check(count("SLOW_LOWER_ON reason=WATCH_BOTH;call_ms=1407;kernel_signals=0") == 1
                && "POISONED_SLOW_ON".equals(DisplayActionCoordinator.lowerFailureState()),
                "PB-08 a 1407 ms lower ON poisons the boot without kernel signals: "
                + DisplayActionCoordinator.lowerFailureState());
        SystemClock.now += 2000L;
        sample();
        check("0".equals(TestDisplayState.bottom), "PB-08b then BOTH degrades the lower panel");
    }

    static void slowOnWithKernelSignals() {
        top();
        DisplayActionCoordinator.setKernelSignalsAvailable(true);
        toBoth(1407L);
        check(count("SLOW_LOWER_ON reason=WATCH_BOTH;call_ms=1407;kernel_signals=1") == 1
                && "0".equals(DisplayActionCoordinator.lowerFailureState()),
                "PB-09 with kernel signals a slow ON is only recorded: " + DisplayActionCoordinator.lowerFailureState());
        DisplayActionCoordinator.setKernelSignalsAvailable(false);
    }

    static void thresholds() {
        for (long ms : new long[]{202L, 428L, 999L}) {
            top();
            toBoth(ms);
            check(count("SLOW_LOWER_ON") == 0 && "0".equals(DisplayActionCoordinator.lowerFailureState()),
                    "PB-10 healthy or sub-threshold ON (" + ms + " ms) is not slow");
        }
        top();
        toBoth(1000L);
        check(count("SLOW_LOWER_ON reason=WATCH_BOTH;call_ms=1000") == 1, "PB-10b 1000 ms is slow");
    }

    static void stallStillLatched() {
        top();
        DisplayActionCoordinator.setKernelSignalsAvailable(true);
        toBoth(6000L);
        check("LATCHED".equals(DisplayActionCoordinator.lowerFailureState()),
                "PB-11 a stalled call keeps the existing latch: " + DisplayActionCoordinator.lowerFailureState());
        DisplayActionCoordinator.setKernelSignalsAvailable(false);
    }

    public static void main(String[] args) {
        java.util.Map<String, Runnable> cases = new java.util.LinkedHashMap<>();
        cases.put("model", PoisonedBootTest::model);
        cases.put("degrade", PoisonedBootTest::degradeAndSuppress);
        cases.put("reconcile", PoisonedBootTest::manualReconcile);
        cases.put("slow", PoisonedBootTest::slowOnWithoutKernelSignals);
        cases.put("slowkernel", PoisonedBootTest::slowOnWithKernelSignals);
        cases.put("thresholds", PoisonedBootTest::thresholds);
        cases.put("stall", PoisonedBootTest::stallStillLatched);
        if (args.length == 1) {
            Runnable selected = cases.get(args[0]);
            if (selected == null) throw new IllegalArgumentException("unknown case " + args[0]);
            selected.run();
        } else {
            for (Runnable test : cases.values()) test.run();
        }
        System.out.println("PoisonedBootTest PASS checks=" + checks);
    }
}
