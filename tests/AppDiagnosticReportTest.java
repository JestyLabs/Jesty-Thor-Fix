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
    private static void check(boolean pass, String message) {
        assertions++;
        if (!pass) throw new AssertionError(message);
    }
}
