import com.thor.displaypowertest.AppDiagnosticPreview;
import com.thor.displaypowertest.AppDiagnosticReport;
import com.thor.displaypowertest.PassiveSleepTrial;
import java.util.Collections;

public final class PassiveSleepReportCompatibilityTest {
    private static int count;
    private static void check(boolean yes, String why) {
        if (!yes) throw new AssertionError(why);
        count++;
    }
    private static PassiveSleepTrial.Sample sample(long e, long u, long energy, int plugged) {
        return new PassiveSleepTrial.Sample(e, u, energy, 1000000, 3900, 300, plugged, 12, 1);
    }
    public static void main(String[] args) {
        AppDiagnosticReport report = new AppDiagnosticReport("");
        String baseline = report.export(10000, 1000, "1.7.1-rc2", 33, "empty", Collections.emptyList());
        String nullTrial = report.export(10000, 1000, "1.7.1-rc2", 33, "empty",
                Collections.emptyList(), null);
        check(baseline.equals(nullTrial), "not armed: V1 report byte-for-byte unchanged");
        check(baseline.contains("app_version=1.7.1-rc2;sdk=33"), "RC identity preserved");
        check(!baseline.contains("passive_sleep;"), "no unsolicited trial line");
        check(AppDiagnosticPreview.format(baseline).contains("JESTY THOR FIX"),
                "human-readable preview retained");
        PassiveSleepTrial.Result trial = PassiveSleepTrial.evaluate(
                sample(100000, 50000, 2000000000L, 0),
                sample(3700000, 110000, 1700000000L, 0));
        String trialReport = report.export(10000, 1000, "1.7.1-rc2", 33, "empty",
                Collections.emptyList(), trial);
        check(trialReport.startsWith("JESTY_THOR_DIAGNOSTICS_V1\n"), "schema preserved");
        check(trialReport.contains("\npassive_sleep;boundary_source=ACTIVITY_PAUSE_RESUME;"),
                "trial explicitly distinguishes Activity boundary from hardware sleep");
        check(trialReport.contains(";suspend_source=SUSPEND_CLOCK_ESTIMATE;"),
                "clock difference is labelled estimate, not verified suspend residency");
        check(trialReport.contains(";power_interval_trace=NOT_SAMPLED;"),
                "two battery endpoints do not prove continuously unplugged interval");
        check(trialReport.contains(";minimum_duration_met=1;state=PROVISIONAL;"),
                "full-length trial retains provisional semantics");
        check(trialReport.contains("limits=activity_events_are_not_process_deaths"),
                "original limits preserved");
        check(AppDiagnosticPreview.format(trialReport).contains("JESTY THOR FIX"),
                "preview tolerates extra allowlisted record");
        PassiveSleepTrial.Result rejected = PassiveSleepTrial.evaluate(
                sample(100000, 50000, 2000000000L, 0),
                sample(3700000, 110000, 1700000000L, 1));
        check(rejected.reportLine().contains("energy_wh=unknown"),
                "USB reconnect must not produce credible energy measurement");
        check(!trialReport.contains("PRIVATE_MARKER"), "only schema data exported");
        System.out.println("PassiveSleepReportCompatibilityTest passed: " + count + " assertions");
    }
}
