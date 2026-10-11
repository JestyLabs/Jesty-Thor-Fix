import com.thor.displaypowertest.AppDiagnosticPreview;
import com.thor.displaypowertest.AppDiagnosticReport;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/** On-demand UI formatting does not change sanitized export or request more data. */
public final class AppDiagnosticPreviewTest {
    private static int assertions;
    private static void check(boolean ok, String message) {
        assertions++;
        if (!ok) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        AppDiagnosticReport report = new AppDiagnosticReport("");
        Map<String, String> q = new HashMap<>();
        q.put("mode", "1");
        q.put("top_crtc", "1");
        q.put("bottom_crtc", "0");
        q.put("fix", "1");
        q.put("cpu_fix_desired", "0");
        q.put("system_load_fix", "1");
        q.put("lid_guard", "0");
        q.put("display_actions_held", "0");
        q.put("watcher_health", "RUNNING");
        q.put("cpu_fix_phase", "CONFIRMED");
        q.put("repair_result", "OFF_OK");
        report.record(1000L, "APP_CREATED");
        report.sample(2000L, 1000L, q);
        report.sample(3000L, 2000L, q);
        String raw = report.export(9000L, 3000L, "1.7.1", 33, "available",
                Arrays.asList(new AppDiagnosticReport.Exit(4000L, 10, 0, 100),
                        new AppDiagnosticReport.Exit(5000L, 4, 1, 100)));
        String preview = AppDiagnosticPreview.format(raw);
        check(preview.contains("CURRENTLY REPORTED STATE"), "state section");
        check(preview.contains("Mode: TOP only"), "human mode");
        check(preview.contains("Bottom CRTC: Inactive"), "human CRTC");
        check(preview.contains("Display Fix requested: ON"), "requested is not actual");
        check(preview.contains("CPU Fix desired: OFF"), "desired is not effective");
        check(preview.contains("CPU property reported: ON"), "observed CPU property");
        check(preview.contains("APP EXIT HISTORY"), "exit section");
        check(preview.contains("User-requested app stop"), "OS exit mapping");
        check(preview.contains("App crash"), "crash exit mapping");
        check(preview.contains("RECENT EVENTS"), "events section");
        check(preview.contains("Display: TOP only"), "display event formatted");
        check(preview.contains("Settings: display ON"), "config event formatted");
        check(!preview.contains("JESTY_THOR_DIAGNOSTICS_V1"), "UI does not dump raw schema");
        check(!preview.contains("reason_code="), "no raw semicolon key-value clutter");
        check(raw.contains("JESTY_THOR_DIAGNOSTICS_V1"), "machine-readable export remains intact");
        check(raw.contains("exit;age_ms=5000;reason=USER_REQUESTED"), "raw exit format remains intact");
        check(preview.contains("not the root daemon"), "exit caveat retained");
        check(AppDiagnosticPreview.format("invalid").contains("No valid"), "unknown schema rejected");
        check(AppDiagnosticPreview.format(null).contains("No valid"), "null safely handled");
        check(AppDiagnosticPreview.format(new String(new char[24001]))
                .contains("No valid"), "oversized input rejected");
        check(AppDiagnosticPreview.format("JESTY_THOR_DIAGNOSTICS_V1\n"
                + "app_version=1.7.1;sdk=33\ntelemetry_scope=last_foreground_sample"
                + ";sample_age_ms=unknown;available=0\nexit_history_state=empty\n"
                + "event;age_ms=unknown;code=ACTIVITY_DESTROYED\n")
                .contains("Activity destroyed (not a confirmed crash)"),
                "unknown times and Activity close never become crash allegations");
        System.out.println("AppDiagnosticPreviewTest passed: " + assertions + " assertions");
    }
}
