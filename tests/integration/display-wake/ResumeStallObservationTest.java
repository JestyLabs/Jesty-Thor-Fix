package com.thor.displaypowertest;

import android.os.SystemClock;
import android.util.Log;

/**
 * Real DisplayActionCoordinator/DisplayHardware with synthetic platform boundaries.
 *
 * Tier: HOST contract reproduction plus SIMULATED platform stall. It replays the
 * shape of the 2026-10-20 resume trace (a blocked lower OFF that the compositor
 * gate skipped after the platform rewrote the shared flag; then an initial-failure
 * window with no logged transition). It is not a physical blackout reproduction.
 *
 * Arguments: none = all cases, or one of "stall", "control", "crtc", "stale".
 */
public final class ResumeStallObservationTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void reset() {
        SystemClock.now += 10000L;
        TestDisplayState.baseline();
        Log.LINES.clear();
    }
    private static String only(String prefix) {
        String found = null;
        for (String line : Log.LINES) {
            if (!line.startsWith(prefix)) continue;
            check(found == null, "exactly one line expected for " + prefix + ": " + Log.LINES);
            found = line;
        }
        return found;
    }
    private static long field(String line, String name) {
        int start = line.indexOf(name + "=") + name.length() + 1;
        int end = line.indexOf(';', start);
        return Long.parseLong(end < 0 ? line.substring(start) : line.substring(start, end));
    }
    private static int count(String prefix) {
        int n = 0;
        for (String line : Log.LINES) if (line.startsWith(prefix)) n++;
        return n;
    }

    /** Trace 13.922 schedule -> blocked OFF -> 21.928 platform flag=1 -> 30.826 gate skip. */
    private static void stall() {
        reset();
        TestDisplayState.wakeTop();
        check(DisplayActionCoordinator.isWakeRepairPending(), "STALL-01 TOP wake schedules repair");
        SystemClock.now = WakeRepairScheduler.deadline;
        TestDisplayState.vendorGate = true;
        TestDisplayState.platformPropertyDuringCall = "1";
        TestDisplayState.callSleepMs = 30L;
        SystemClock.autoAdvanceMs = 700L; // lets the unchanged 1200 ms readback loop end
        Log.LINES.clear();
        DisplayActionCoordinator.onWatcherSample("1");
        SystemClock.autoAdvanceMs = 0L;
        check(TestDisplayState.hardwareCalls == 1, "STALL-02 one lower OFF request, no extra retry");
        check(TestDisplayState.gateRefusals == 1 && "1".equals(TestDisplayState.bottom),
                "STALL-03 synthetic compositor gate left the lower CRTC active");
        check(count("ThorDisplayDaemon: WAKE_REPAIR OFF") == 1,
                "STALL-04 characterization: existing action line is still emitted after a skipped OFF");
        check(count("ThorDisplayDaemon: WAKE_REPAIR_RESULT") == 0 && WakeRepairScheduler.isPending(),
                "STALL-05 unchanged readback refuses confirmation and keeps the repair pending");
        check("1".equals(TestDisplayState.property) && TestDisplayState.propertyWrites == 2,
                "STALL-06 unchanged semantics: OFF write then realignment to observed CRTC");
        String observed = only("ThorDisplayDaemon: HW_CALL_OBSERVED ");
        check(observed != null, "STALL-07 HW_CALL_OBSERVED missing: skipped OFF is indistinguishable in logs");
        check(observed.contains("reason=WAKE_REPAIR;target=0;"), "STALL-08 call identity: " + observed);
        check(field(observed, "call_ms") >= 30L, "STALL-09 synchronous call duration recorded: " + observed);
        check(observed.endsWith(";property_after=1"),
                "STALL-10 platform rewrite of shared flag visible after return: " + observed);
        check(count("ThorDisplayDaemon: APPLY_UNCONFIRMED reason=WAKE_REPAIR;target=0;top=1;bottom=1") == 1,
                "STALL-11 unconfirmed readback recorded: " + Log.LINES);
    }

    /** Live recurrence B: OFF admitted under TOP, executed after the mode became BOTH. */
    private static void stale() {
        reset();
        TestDisplayState.wakeTop();
        SystemClock.now = WakeRepairScheduler.deadline;
        TestDisplayState.modeDuringCall = "0";
        TestDisplayState.callSleepMs = 30L;
        Log.LINES.clear();
        DisplayActionCoordinator.onWatcherSample("1");
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom),
                "STALE-01 characterization: admitted OFF still executes after the mode changed");
        check(count("ThorDisplayDaemon: WAKE_REPAIR_RESULT") == 0, "STALE-02 unchanged readback refuses confirmation");
        check(count("ThorDisplayDaemon: APPLY_UNCONFIRMED reason=WAKE_REPAIR;target=0;top=1;bottom=0") == 1,
                "STALE-03 unconfirmed stale action recorded: " + Log.LINES);
        SystemClock.now += 500L;
        DisplayActionCoordinator.onWatcherSample("0");
        check(count("ThorDisplayDaemon: MODE_OBSERVED mode=0;previous=1") == 1, "STALE-04 mode edge recorded: " + Log.LINES);
        check(TestDisplayState.hardwareCalls == 2 && "1".equals(TestDisplayState.bottom),
                "STALE-05 characterization: BOTH reconciliation restores the lower panel (trace 2258.1 s)");
        int hw = -1, unconfirmed = -1, edge = -1, on = -1;
        for (int i = 0; i < Log.LINES.size(); i++) {
            String line = Log.LINES.get(i);
            if (hw < 0 && line.startsWith("ThorDisplayDaemon: HW_CALL_OBSERVED reason=WAKE_REPAIR")) hw = i;
            if (line.startsWith("ThorDisplayDaemon: APPLY_UNCONFIRMED")) unconfirmed = i;
            if (line.startsWith("ThorDisplayDaemon: MODE_OBSERVED")) edge = i;
            if (line.equals("ThorDisplayDaemon: WATCH_BOTH ON")) on = i;
        }
        check(hw >= 0 && hw < unconfirmed && unconfirmed < edge && edge < on,
                "STALE-06 log order call -> unconfirmed -> mode edge -> restore: " + Log.LINES);
    }

    /** Negative control: the same path without stall/gate/rewrite. */
    private static void control() {
        reset();
        TestDisplayState.wakeTop();
        SystemClock.now = WakeRepairScheduler.deadline;
        DisplayActionCoordinator.onWatcherSample("1");
        check(TestDisplayState.hardwareCalls == 1 && "0".equals(TestDisplayState.bottom)
                && TestDisplayState.gateRefusals == 0, "CTRL-01 normal OFF applied");
        check(count("ThorDisplayDaemon: WAKE_REPAIR_RESULT result=OFF_OK") == 1
                && !WakeRepairScheduler.isPending(), "CTRL-02 normal readback confirms");
        check("0".equals(TestDisplayState.property) && TestDisplayState.propertyWrites == 1,
                "CTRL-03 only the actual OFF publishes zero");
        String observed = only("ThorDisplayDaemon: HW_CALL_OBSERVED ");
        check(observed != null && observed.endsWith(";property_after=0"),
                "CTRL-04 property_after distinguishes an unmodified flag: " + observed);
        check(field(observed, "call_ms") >= 0L, "CTRL-05 duration is a non-negative measurement");
        check(count("ThorDisplayDaemon: APPLY_UNCONFIRMED") == 0 && count("ThorDisplayDaemon: MODE_OBSERVED") == 0,
                "CTRL-06 confirmed action in a stable mode adds no unconfirmed/mode-edge line: " + Log.LINES);
    }

    /** Initial-failure window: CRTC pair edges are recorded once; repeats stay silent. */
    private static void crtc() {
        reset();
        TestDisplayState.wakeTop();                       // pair 11
        SystemClock.now = WakeRepairScheduler.deadline;
        DisplayActionCoordinator.onWatcherSample("1");    // repair OFF, still read 11
        DisplayActionCoordinator.onWatcherSample("1");    // pair 10
        for (int i = 0; i < 20; i++) {                    // trace 2.46-12.55 s: no transition logged
            SystemClock.now += 500L;
            DisplayActionCoordinator.onWatcherSample("1");
        }
        int afterQuietWindow = count("ThorDisplayDaemon: CRTC_OBSERVED ");
        TestDisplayState.top = "0";                       // synthetic upper CRTC deactivation
        DisplayActionCoordinator.onWatcherSample("1");
        java.util.List<String> edges = new java.util.ArrayList<String>();
        for (String line : Log.LINES) if (line.startsWith("ThorDisplayDaemon: CRTC_OBSERVED ")) edges.add(line);
        check(afterQuietWindow == 2, "CRTC-01 two edges before/after OFF, none for 20 identical reads: " + edges);
        check(edges.size() == 3, "CRTC-02 upper deactivation recorded as its own edge: " + edges);
        check(edges.get(0).contains("top=1;bottom=1;mode=1;property=1"), "CRTC-03 wake edge: " + edges);
        check(edges.get(1).contains("top=1;bottom=0;mode=1;property=0"), "CRTC-04 OFF edge: " + edges);
        check(edges.get(2).contains("top=0;bottom=0;"), "CRTC-05 top-inactive edge: " + edges);
        check(TestDisplayState.hardwareCalls == 1, "CRTC-06 observation adds no hardware action");
    }

    public static void main(String[] args) {
        String only = args.length > 0 ? args[0] : "all";
        if ("all".equals(only) || "stall".equals(only)) stall();
        if ("all".equals(only) || "control".equals(only)) control();
        if ("all".equals(only) || "crtc".equals(only)) crtc();
        if ("all".equals(only) || "stale".equals(only)) stale();
        System.out.println("ResumeStallObservationTest passed (" + only + "): " + checks
                + " assertions (host contract + simulated stall; no physical reproduction)");
    }
}
