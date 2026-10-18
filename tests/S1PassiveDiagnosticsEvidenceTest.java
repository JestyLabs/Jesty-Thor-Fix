import com.thor.displaypowertest.AppDiagnosticReport;
import java.util.HashMap;
import java.util.Map;

/**
 * S1 host characterization of the REAL app diagnostics model.
 * No daemon, ADB, Android clocks, wake injection or vendor services.
 * Observe output semantics; never construe wall-clock age as event ordering.
 */
public final class S1PassiveDiagnosticsEvidenceTest {
    private static int checks;
    private static void yes(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static Map<String, String> sample(String mode, String top, String bottom) {
        Map<String, String> v = new HashMap<String, String>();
        v.put("mode", mode);
        v.put("top_crtc", top);
        v.put("bottom_crtc", bottom);
        v.put("fix", "1");
        v.put("cpu_fix_desired", "0");
        v.put("system_load_fix", "0");
        v.put("lid_guard", "0");
        v.put("display_actions_held", "0");
        v.put("watcher_health", "RUNNING");
        v.put("repair_result", "NONE");
        v.put("cpu_fix_phase", "CONFIRMED");
        v.put("wake_id", "1");
        return v;
    }
    private static String export(AppDiagnosticReport report, long wall, long elapsed) {
        return report.export(wall, elapsed, "1.7.3", 33, "empty", null);
    }
    private static void sampleClockIsElapsedAndBoundedToLastForeground() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String, String> q = sample("1", "1", "0");
        yes(r.sample(1700000000000L, 1000L, q),
                "S1-D01 first foreground observation records connection/configuration");
        String first = export(r, 1700000008000L, 1250L);
        yes(first.contains("sample_age_ms=250;available=1;mode=1"),
                "S1-D01 sample freshness is monotonic elapsed delta, not wall delta");
        yes(!first.contains("1700000000000") && !first.contains("1700000008000"),
                "S1-D01 raw absolute timestamps are not in report");
        yes(!first.contains("boot_id=") && !first.contains("pid="),
                "S1-D01 app report does not inject privileged daemon identity");
        String backwards = export(r, 1700000009000L, 900L);
        yes(backwards.contains("sample_age_ms=unknown"),
                "S1-D01 elapsed regression is UNKNOWN instead of a negative age");
        yes(r.sample(1700000001000L, 1450L, null),
                "S1-D01 disconnected Q is an explicit edge");
        yes(export(r, 1700000009000L, 1550L)
                .contains("sample_age_ms=100;available=0"),
                "S1-D01 last disconnected foreground sample has age and unavailable state");
    }
    private static void wallClockSkewDoesNotForgeActivityOrdering() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        r.record(5000L, "FOREGROUND");
        r.record(2000L, "BACKGROUND");
        r.record(7000L, "APP_CREATED");
        String text = export(r, 6000L, 0L);
        yes(text.contains("event;age_ms=1000;code=FOREGROUND"),
                "S1-D02 app event wall-clock age is relative");
        yes(text.contains("event;age_ms=4000;code=BACKGROUND"),
                "S1-D02 clock rollback is retained as observation");
        yes(text.contains("event;age_ms=unknown;code=APP_CREATED"),
                "S1-D02 future wall-clock event age is UNKNOWN");
        int a = text.indexOf("event;age_ms=1000;code=FOREGROUND");
        int c = text.indexOf("event;age_ms=4000;code=BACKGROUND");
        int e = text.indexOf("event;age_ms=unknown;code=APP_CREATED");
        yes(a >= 0 && a < c && c < e,
                "S1-D02 insertion order preserved; age is not a monotonic event clock");
        yes(!text.contains("timestamp="), "S1-D02 no raw timestamps");
    }
    private static void longSteadyForegroundCannotFloodHistory() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String, String> both = sample("0", "1", "1");
        yes(r.sample(1000L, 100L, both), "S1-D03 first Q is typed");
        yes(r.sample(1001L, 101L, both), "S1-D03 second consistent CRTC snapshot classified");
        String journal = r.stored();
        for (int i = 0; i < 3000; i++) {
            yes(!r.sample(1100L + i, 110L + i, both),
                    "S1-D03 steady Q does not append");
        }
        yes(journal.equals(r.stored()), "S1-D03 no redundant history writes in steady state");
        String text = export(r, 6000L, 3300L);
        yes(text.contains("sample_age_ms=191;available=1"),
                "S1-D03 most recent foreground snapshot tracked without journaling");
        yes(text.length() <= AppDiagnosticReport.MAX_REPORT_CHARS,
                "S1-D03 export remains bounded after high-frequency sampling");
    }
    private static void pausesCannotInventUnseenDisplayEdges() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String, String> both = sample("0", "1", "1");
        Map<String, String> top = sample("1", "1", "0");
        r.sample(1000L, 100L, both);
        r.sample(1001L, 101L, both);
        yes(r.stored().contains("|DISPLAY_OBSERVED|MODE_0_TOP_1_BOTTOM_1"),
                "S1-D04 observed BOTH evidence exists before pause");
        r.pauseObservations();
        int before = r.stored().split("\n").length;
        yes(r.sample(2000L, 200L, top),
                "S1-D04 resume reconnect/configuration is reported, but not display edge");
        yes(!r.stored().contains("|DISPLAY_OBSERVED|MODE_1_TOP_1_BOTTOM_0"),
                "S1-D04 first post-pause TOP cannot claim intervening transition");
        yes(r.sample(2001L, 201L, top),
                "S1-D04 two foreground TOP readings establish a typed observation");
        yes(r.stored().contains("|DISPLAY_OBSERVED|MODE_1_TOP_1_BOTTOM_0"),
                "S1-D04 second sample may classify observed TOP, not its cause");
        yes(r.stored().split("\n").length > before,
                "S1-D04 classified observations remain in one bounded journal");
    }
    private static void rolloverAndRestoreCannotLeakOpaqueReplies() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String, String> q = sample("1;SECRET_SENTINEL", "1", "0\nSECRET_SENTINEL");
        q.put("watcher_health", "RUNNING\nSECRET_SENTINEL");
        q.put("repair_result", "FAILED|SECRET_SENTINEL");
        q.put("unknown_vendor", "SECRET_SENTINEL");
        r.sample(100L, 10L, q);
        for (int i = 0; i < 200; i++) r.record(1000L + i, "FOREGROUND");
        String saved = r.stored();
        yes(saved.split("\n").length == AppDiagnosticReport.MAX_EVENTS,
                "S1-D05 persisted events capped at 64");
        AppDiagnosticReport restored = new AppDiagnosticReport(saved
                + "1200|DISPLAY_OBSERVED|MODE_1_TOP_1_BOTTOM_0;SECRET_SENTINEL\n"
                + "1201|FOREGROUND|SECRET_SENTINEL\n");
        yes(saved.equals(restored.stored()),
                "S1-D05 invalid persisted lines stripped without changing accepted history");
        String out = export(restored, 1300L, 20L);
        yes(!out.contains("SECRET_SENTINEL"),
                "S1-D05 opaque Q/persisted values never exported");
        yes(out.contains("sample_age_ms=unknown;available=unknown"),
                "S1-D05 process restoration cannot invent a fresh foreground sample");
        yes(out.length() <= AppDiagnosticReport.MAX_REPORT_CHARS,
                "S1-D05 combined restored output bounded");
    }
    private static void absentOrMalformedQIsNotPanelEvidence() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String, String> q = sample("1", "?", "0");
        yes(r.sample(100L, 10L, q), "S1-D06 connection and configuration are observable");
        yes(!r.sample(101L, 11L, q), "S1-D06 malformed CRTC does not establish display");
        yes(!r.stored().contains("|DISPLAY_OBSERVED|"), "S1-D06 no display claim");
        String report = export(r, 200L, 20L);
        yes(report.contains("top_crtc=?;bottom_crtc=0"),
                "S1-D06 unknown CRTC explicitly unknown");
        yes(report.contains("crtc_flags_are_not_panel_power"),
                "S1-D06 report disclaims physical luminance");
        yes(!report.contains("SECRET_SENTINEL"), "S1-D06 no unexpected payload");
    }
    public static void main(String[] args) {
        sampleClockIsElapsedAndBoundedToLastForeground();
        wallClockSkewDoesNotForgeActivityOrdering();
        longSteadyForegroundCannotFloodHistory();
        pausesCannotInventUnseenDisplayEdges();
        rolloverAndRestoreCannotLeakOpaqueReplies();
        absentOrMalformedQIsNotPanelEvidence();
        System.out.println("S1PassiveDiagnosticsEvidenceTest PASS: " + checks
                + " checks; real report model, HOST ONLY");
    }
}
