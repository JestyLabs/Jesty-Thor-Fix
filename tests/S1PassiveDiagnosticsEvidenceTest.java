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
    /** S1-15: a gap in Q polling is not evidence of physical transition. */
    private static void missingQSampleDoesNotInventATransition() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String, String> both = sample("0", "1", "1");
        Map<String, String> top = sample("1", "1", "0");
        yes(r.sample(1000L, 100L, both), "S1-D07 first BOTH observation");
        yes(r.sample(1001L, 101L, both), "S1-D07 confirmed BOTH tuple");
        String checkpoint = r.stored();
        yes(!r.sample(1002L, 102L, top),
                "S1-D07 one TOP sample is insufficient to claim a transition");
        // The following Q observations are deliberately dropped by the harness.
        // No background watcher or synthetic report event is installed.
        yes(!r.sample(2000L, 200L, both),
                "S1-D07 returning to known BOTH is not evidence of the unseen interval");
        yes(!r.sample(2001L, 201L, both),
                "S1-D07 identical returned BOTH is not a new display edge");
        yes(checkpoint.equals(r.stored()),
                "S1-D07 unobserved TOP never enters durable event history");
        String exported = export(r, 3000L, 250L);
        yes(!exported.contains("MODE_1_TOP_1_BOTTOM_0"),
                "S1-D07 no invented TOP evidence in report");
        yes(!exported.contains("code=WAKE_OBSERVED")
                && !exported.contains("code=REPAIR_STATUS"),
                "S1-D07 gap cannot fabricate wake or repair events");
        yes(exported.contains("foreground_samples_are_not_sleep_measurements"),
                "S1-D07 report retains explicit evidence limitation");
    }

    /** S1-15: simulate a hard kill between event classification and persistence. */
    private static void processKilledBeforeJournalCommitKeepsOnlyDurableEvidence() {
        AppDiagnosticReport beforeDeath = new AppDiagnosticReport("");
        Map<String, String> both = sample("0", "1", "1");
        Map<String, String> top = sample("1", "1", "0");
        yes(beforeDeath.sample(1000L, 100L, both),
                "S1-D08 initial Q yields durable connection/configuration");
        String durableCheckpoint = beforeDeath.stored();
        yes(beforeDeath.sample(1001L, 101L, both),
                "S1-D08 second Q classifies display in memory");
        yes(beforeDeath.stored().contains("|DISPLAY_OBSERVED|MODE_0_TOP_1_BOTTOM_1"),
                "S1-D08 transient uncommitted display observation exists in RAM");
        // No further persisted write. Recreate the real model from last
        // successfully saved journal, just as a new process would.
        AppDiagnosticReport restarted = new AppDiagnosticReport(durableCheckpoint);
        yes(!restarted.stored().contains("|DISPLAY_OBSERVED|"),
                "S1-D08 hard kill cannot resurrect an uncommitted observation");
        String initial = export(restarted, 2000L, 500L);
        yes(initial.contains("sample_age_ms=unknown;available=unknown"),
                "S1-D08 after process restart no foreground sample is fresh");
        yes(!initial.contains("code=DISPLAY_OBSERVED"),
                "S1-D08 no invented display event after restart");
        yes(initial.contains("exit_history_state=empty"),
                "S1-D08 no app exit cause inferred from absent Android exit data");
        yes(restarted.sample(2001L, 501L, top),
                "S1-D08 new session records connection and configuration");
        yes(!restarted.stored().contains("|DISPLAY_OBSERVED|MODE_1_TOP_1_BOTTOM_0"),
                "S1-D08 first post-restart Q cannot confirm TOP");
        yes(restarted.sample(2002L, 502L, top),
                "S1-D08 second consistent post-restart TOP is observed");
        yes(restarted.stored().contains("|DISPLAY_OBSERVED|MODE_1_TOP_1_BOTTOM_0"),
                "S1-D08 subsequent TOP is a new observation, not a reconstructed missing trace");
        yes(!restarted.stored().contains("|DISPLAY_OBSERVED|MODE_0_TOP_1_BOTTOM_1"),
                "S1-D08 lost uncommitted BOTH observation stays lost");
    }

    /** S1-15: explicit missing Q differs from an unobserved process death. */
    private static void unavailableReplyDoesNotClaimAnAndroidDeath() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        Map<String, String> q = sample("1", "1", "0");
        r.sample(100L, 10L, q);
        yes(r.sample(101L, 11L, null), "S1-D09 first missing Q is observable");
        String journal = r.stored();
        yes(!r.sample(102L, 12L, null), "S1-D09 repeated missing Q is deduplicated");
        yes(journal.equals(r.stored()), "S1-D09 no missing-Q journal flood");
        String text = export(r, 120L, 20L);
        yes(text.contains("available=0") && text.contains("code=DAEMON_UNAVAILABLE"),
                "S1-D09 Q unavailable is described as telemetry absence");
        yes(text.contains("exit_history_state=empty")
                && !text.contains("code=ACTIVITY_DESTROYED"),
                "S1-D09 no process death or activity destruction inferred");
        yes(!text.contains("code=DISPLAY_OBSERVED"),
                "S1-D09 missing samples cannot prove pixel state");
    }

    /** S1-15: a 64-entry ring is not full incident history. */
    private static void retentionTruncationDoesNotReconstructLostIncident() {
        AppDiagnosticReport r = new AppDiagnosticReport("");
        r.record(1L, "BACKGROUND");
        for (int i = 0; i < 128; i++) r.record(100L + i, "FOREGROUND");
        String journal = r.stored();
        yes(journal.split("\n").length == AppDiagnosticReport.MAX_EVENTS,
                "S1-D10 max 64 events retained");
        yes(!journal.contains("1|BACKGROUND|"),
                "S1-D10 oldest incident marker can be evicted");
        AppDiagnosticReport restarted = new AppDiagnosticReport(journal);
        yes(restarted.stored().equals(journal), "S1-D10 bounded ring survives restoration");
        String output = export(restarted, 300L, 0L);
        yes(!output.contains("code=BACKGROUND"),
                "S1-D10 report cannot reconstruct evicted incident");
        yes(!output.contains("code=ACTIVITY_DESTROYED"),
                "S1-D10 gap is not proof of process death");
        yes(output.contains("sample_age_ms=unknown"),
                "S1-D10 no post-restart telemetry recency claim");
        yes(output.length() <= AppDiagnosticReport.MAX_REPORT_CHARS,
                "S1-D10 report length remains bounded");
    }

    public static void main(String[] args) {
        sampleClockIsElapsedAndBoundedToLastForeground();
        wallClockSkewDoesNotForgeActivityOrdering();
        longSteadyForegroundCannotFloodHistory();
        pausesCannotInventUnseenDisplayEdges();
        rolloverAndRestoreCannotLeakOpaqueReplies();
        absentOrMalformedQIsNotPanelEvidence();
        missingQSampleDoesNotInventATransition();
        processKilledBeforeJournalCommitKeepsOnlyDurableEvidence();
        unavailableReplyDoesNotClaimAnAndroidDeath();
        retentionTruncationDoesNotReconstructLostIncident();
        System.out.println("S1PassiveDiagnosticsEvidenceTest PASS: " + checks
                + " checks; real report model, HOST ONLY");
    }
}
