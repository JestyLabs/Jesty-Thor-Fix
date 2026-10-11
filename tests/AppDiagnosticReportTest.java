import com.thor.displaypowertest.AppDiagnosticReport;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class AppDiagnosticReportTest {
    private static int assertions;
    public static void main(String[] args) {
        privacyAndUnknownValues();
        retentionAndRestart();
        exitReasonsAndClocks();
        typedTelemetryTransitions();
        System.out.println("AppDiagnosticReportTest passed: " + assertions + " assertions");
    }
    private static void privacyAndUnknownValues() {
        AppDiagnosticReport report = new AppDiagnosticReport("");
        Map<String,String> values = new HashMap<>();
        values.put("mode", "1;secret=PRIVATE_MARKER");
        values.put("top_crtc", "1");
        values.put("bottom_crtc", "0\nPRIVATE_MARKER");
        values.put("boot_id", "PRIVATE_MARKER");
        values.put("unknown_future_field", "PRIVATE_MARKER");
        values.put("fix", "1");
        values.put("cpu_fix_desired", "0");
        values.put("system_load_fix", "PRIVATE_MARKER");
        values.put("lid_guard", "1");
        check(report.sample(1000, 10, values), "first sample records an edge");
        check(!report.sample(1001, 11, values), "identical sample does not append or persist");
        report.record(1002, "PRIVATE_MARKER");
        String text = report.export(2000, 20, "PRIVATE_MARKER", 34, "PRIVATE_MARKER", null);
        check(!text.contains("PRIVATE_MARKER"), "raw values and unknown keys never leak");
        check(text.contains("mode=?;top_crtc=1;bottom_crtc=?"), "invalid flags stay unknown");
        check(text.contains("sample_age_ms=9"), "freshness follows last sample");
        check(text.contains("exit_history_state=unavailable"), "unknown state fails closed");
        check(text.contains("display_fix_requested=1;cpu_fix_desired=0;cpu_property=?;lid_guard_requested=1"),
                "three fix choices remain distinct from effective hardware state");
        check(report.sample(2000, 30, null), "unavailable is a separate edge");
        check(report.export(2001, 31, "1.7.0", 34, "empty", null).contains("available=0"), "missing telemetry stays explicit");
    }
    private static void retentionAndRestart() {
        AppDiagnosticReport report = new AppDiagnosticReport("");
        for (int i = 0; i < 200; i++) report.record(1000 + i, "FOREGROUND");
        check(report.stored().split("\n").length == 64, "history bounded");
        AppDiagnosticReport restored = new AppDiagnosticReport(report.stored()
                + "1|FOREGROUND|PRIVATE_MARKER\nbad|APP_CREATED|\n"
                + "1|TELEMETRY_CHANGED|available=1;mode=0;top_crtc=1;bottom_crtc=1;secret=PRIVATE_MARKER\n");
        check(restored.stored().equals(report.stored()), "invalid persistent records dropped");
        String text = restored.export(2000, 20, "1.7.0", 34, "unsupported", null);
        check(text.contains("sample_age_ms=unknown;available=unknown"), "previous process sample not fresh");
        check(!text.contains("PRIVATE_MARKER"), "persistent input cannot inject fields");
        check(text.length() < AppDiagnosticReport.MAX_REPORT_CHARS, "export bounded");
        check(new AppDiagnosticReport(new String(new char[16001])).stored().isEmpty(), "oversized input rejected");
    }
    private static void exitReasonsAndClocks() {
        AppDiagnosticReport report = new AppDiagnosticReport("");
        report.record(3000, "ACTIVITY_DESTROYED");
        AppDiagnosticReport.Exit crash = new AppDiagnosticReport.Exit(1000, 4, 0, 100);
        AppDiagnosticReport.Exit signal = new AppDiagnosticReport.Exit(1500, 2, 9, 400);
        AppDiagnosticReport.Exit future = new AppDiagnosticReport.Exit(4000, 900, -1, 300);
        String text = report.export(2000, 0, "1.7.0", 34, "available", Arrays.asList(crash, signal, future));
        check(text.contains("reason=CRASH;reason_code=4;status=0"), "crash mapped");
        check(text.contains("reason=SIGNALED;reason_code=2;status=9"), "signal retained without invented cause");
        check(text.contains("age_ms=unknown;reason=UNRECOGNIZED;reason_code=900"), "future reason and clock rollback explicit");
        check(text.contains("event;age_ms=unknown;code=ACTIVITY_DESTROYED"), "activity destruction not inferred death");
        check(!text.contains("timestamp="), "no absolute wall time exported");
        AppDiagnosticReport.Exit[] many = new AppDiagnosticReport.Exit[100];
        Arrays.fill(many, crash);
        String capped = report.export(2000, 0, "1.7.0", 34, "available", Arrays.asList(many));
        check(capped.split("\nexit;").length - 1 == 8, "exit records bounded");
    }
    private static Map<String,String> observed(String mode, String top, String bottom) {
        Map<String,String> values = new HashMap<>();
        values.put("mode", mode);
        values.put("top_crtc", top);
        values.put("bottom_crtc", bottom);
        values.put("fix", "1");
        values.put("cpu_fix_desired", "0");
        values.put("system_load_fix", "1");
        values.put("lid_guard", "0");
        values.put("display_actions_held", "0");
        values.put("watcher_health", "RUNNING");
        values.put("wake_id", "5");
        values.put("repair_result", "NONE");
        values.put("cpu_fix_phase", "CONFIRMED");
        return values;
    }

    private static void typedTelemetryTransitions() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String,String> both = observed("0", "1", "1");
        check(r.sample(1000, 100, both), "initial connection, configuration and watcher edges");
        check(r.stored().contains("|DAEMON_CONNECTED|Q_REPLY"), "daemon reply edge is typed");
        check(r.stored().contains("|WATCHER_RUNNING|RUNNING"), "watcher edge is typed");
        check(r.stored().contains("|CONFIG_OBSERVED|DISPLAY_1_CPU_DESIRED_0_CPU_PROP_1_LID_0"),
                "desired settings recorded separately from effective display observation");
        check(!r.stored().contains("|DISPLAY_OBSERVED|"), "one display reading does not count as confirmed");
        check(r.sample(1001, 101, both), "second consistent display reading records edge");
        check(r.stored().contains("|DISPLAY_OBSERVED|MODE_0_TOP_1_BOTTOM_1"), "dual CRTC tuple");
        for (int i = 0; i < 500; i++) {
            check(!r.sample(1002 + i, 102 + i, both), "steady Q never appends events");
        }

        Map<String,String> top = observed("1", "1", "0");
        check(!r.sample(2000, 2000, top), "first TOP reading not recorded as an edge");
        check(!r.stored().contains("MODE_1_TOP_1_BOTTOM_0"), "unconfirmed CRTC never persisted");
        check(r.sample(2001, 2001, top), "two consistent TOP readings");
        check(r.stored().contains("|DISPLAY_OBSERVED|MODE_1_TOP_1_BOTTOM_0"), "confirmed tuple persisted");

        Map<String,String> unstable = observed("2", "0", "1");
        check(!r.sample(2002, 2002, unstable), "first unstable sample ignored");
        Map<String,String> invalid = observed("1", "bad\nRAW_SECRET", "0");
        check(!r.sample(2003, 2003, invalid), "invalid CRTC discarded");
        check(!r.sample(2004, 2004, top), "candidate starts again after malformed reading");
        check(!r.sample(2005, 2005, top), "same previously confirmed tuple not duplicated");

        top.put("display_actions_held", "1");
        check(r.sample(2100, 2100, top), "hold enter");
        check(!r.sample(2101, 2101, top), "hold deduplicated");
        top.put("display_actions_held", "0");
        check(r.sample(2102, 2102, top), "hold clear");
        check(r.stored().contains("|BOOT_HOLD_CLEARED|DISPLAY_ACTIONS_RESUMED"), "hold cleared typed");

        top.put("wake_id", "6");
        top.put("repair_result", "RUNNING");
        check(r.sample(2200, 2200, top), "wake plus repair");
        top.put("repair_result", "OFF_OK");
        check(r.sample(2201, 2201, top), "repair result changed");
        check(!r.sample(2202, 2202, top), "repair deduped");
        top.put("wake_id", "0");
        check(!r.sample(2203, 2203, top), "counter reset is not wake");
        top.put("wake_id", "1");
        check(r.sample(2204, 2204, top), "wake counter advancing");
        top.put("cpu_fix_phase", "ERROR");
        check(r.sample(2205, 2205, top), "CPU error phase");
        check(r.stored().contains("|CPU_PHASE|ERROR"), "CPU phase persisted");
        top.put("cpu_fix_phase", "ERROR\nRAW_SECRET");
        top.put("repair_result", "SOMETHING|RAW_SECRET");
        top.put("watcher_health", "BAD\nRAW_SECRET");
        check(!r.sample(2206, 2206, top), "arbitrary daemon text rejected");
        check(!r.stored().contains("RAW_SECRET"), "no daemon injection in stored history");
        check(r.sample(2300, 2300, null), "connection lost");
        check(!r.sample(2301, 2301, null), "connection-loss dedup");
        check(r.sample(2302, 2302, both), "reconnect edge");
        check(!r.stored().contains("|DISPLAY_OBSERVED|MODE_0_TOP_1_BOTTOM_1\n|DISPLAY_OBSERVED"),
                "reconnect does not fabricate a display transition");
        check(r.sample(2303, 2303, both), "new display evidence takes two Q samples");
        r.pauseObservations();
        check(r.sample(2400, 2400, both), "foreground resumes with fresh baseline");
        check(r.sample(2401, 2401, null), "availability remains representable");
        // Storage is a single bounded journal, even with lifecycle and typed Q events.
        for (int i = 0; i < 100; i++) {
            r.sample(3000 + i * 2, i * 2, null);
            r.sample(3001 + i * 2, i * 2 + 1, both);
        }
        check(r.stored().split("\\n").length <= AppDiagnosticReport.MAX_EVENTS, "unified ring capped at 64");
        String saved = r.stored();
        AppDiagnosticReport roundtrip = new AppDiagnosticReport(saved);
        check(roundtrip.stored().equals(saved), "typed events validated on reload");
        String hostile = "1|DISPLAY_OBSERVED|MODE_1_TOP_1_BOTTOM_X\n"
                + "2|CPU_PHASE|RAW_SECRET\n"
                + "3|WATCHER_NOT_RUNNING|RUNNING\n"
                + "4|DAEMON_CONNECTED|UNKNOWN\n";
        check(new AppDiagnosticReport(hostile).stored().isEmpty(), "type/detail mismatch rejected");
        check(roundtrip.export(5000, 200, "1.7.0", 33, "empty", null).contains("code=DAEMON_CONNECTED;detail=Q_REPLY"),
                "typed event export");
        check(!roundtrip.export(5000, 200, "1.7.0", 33, "empty", null).contains("RAW_SECRET"),
                "no arbitrary stored data exported");
    }

    private static void check(boolean pass, String message) {
        assertions++;
        if (!pass) throw new AssertionError(message);
    }
}
