import com.thor.displaypowertest.EventHistoryModel;

import java.util.HashMap;
import java.util.Map;

public final class EventHistoryModelTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static Map<String, String> sample(String mode, String top, String bottom) {
        Map<String, String> values = new HashMap<>();
        values.put("mode", mode);
        values.put("top_crtc", top);
        values.put("bottom_crtc", bottom);
        values.put("display_actions_held", "0");
        values.put("watcher_health", "RUNNING");
        values.put("wake_id", "5");
        values.put("repair_result", "NONE");
        values.put("cpu_fix_phase", "CONFIRMED");
        return values;
    }

    public static void main(String[] args) {
        EventHistoryModel model = new EventHistoryModel();
        Map<String, String> both = sample("0", "1", "1");
        check(model.sample(both, 1000), "first reply emits observations");
        int count = model.size();
        check(model.sample(both, 1001), "stable display state after two samples");
        check(model.size() == count + 1, "only one new display event");
        for (int i = 0; i < 500; i++) check(!model.sample(both, 1002 + i),
                "no event on steady telemetry");
        check(model.size() == count + 1, "steady telemetry has zero writes");

        Map<String, String> fake = sample("1", "1", "1");
        check(!model.sample(fake, 2000), "one observation is not confirmed");
        check(model.sample(fake, 2001), "two observations confirm new state");
        check(model.entries().get(model.size() - 1).detail.equals("MODE_1_TOP_1_BOTTOM_1"),
                "full mode/CRTC tuple reported");

        Map<String, String> off = sample("1", "1", "0");
        check(!model.sample(off, 2010), "new CRTC needs second observation");
        check(model.sample(off, 2011), "true off observed from CRTC, not UI");
        check(!model.sample(off, 2012), "same stable CRTC not repeated");

        Map<String, String> unknown = sample("1", "?", "0");
        check(!model.sample(unknown, 2013), "unknown hardware never called off");
        check(!model.sample(unknown, 2014), "unknown not a display event");
        check(!model.sample(off, 2015), "candidate reset on unknown");
        check(!model.sample(off, 2016), "prior stable hardware not duplicated");

        Map<String, String> hold = sample("1", "1", "0");
        hold.put("display_actions_held", "1");
        check(model.sample(hold, 2100), "hold enter");
        check(!model.sample(hold, 2101), "no per-poll hold event");
        check(model.sample(off, 2102), "hold exit");

        Map<String, String> repair = sample("1", "1", "0");
        repair.put("wake_id", "6");
        repair.put("repair_result", "RUNNING");
        check(model.sample(repair, 2200), "wake and repair changed");
        repair.put("repair_result", "OFF_OK");
        check(model.sample(repair, 2201), "repair completion");
        check(!model.sample(repair, 2202), "repair result deduped");
        repair.put("wake_id", "0");
        check(!model.sample(repair, 2203), "wake counter reset is not a new wake");
        repair.put("wake_id", "1");
        check(model.sample(repair, 2204), "new wake counter increment");

        check(model.unavailable(2300), "loss emitted");
        check(!model.unavailable(2301), "loss deduped");
        check(model.sample(off, 2302), "reconnection emitted");
        check(model.sample(off, 2303), "display observed again after two new valid samples");

        int size = model.size();
        Map<String, String> malicious = sample("1\nAPI_KEY", "1", "0");
        malicious.put("cpu_fix_phase", "ERROR\nSECRET");
        malicious.put("repair_result", "SUCCESS|SURPRISE");
        malicious.put("watcher_health", "BAD\nSTATE");
        check(!model.sample(malicious, 2400), "unrecognized values are ignored");
        check(model.size() == size, "no raw text leaked");

        String saved = model.encode();
        EventHistoryModel restored = EventHistoryModel.decode(saved);
        check(restored.size() == model.size(), "round trip preserves bounded entries");
        check(restored.encode().equals(saved), "round trip exact");
        check(EventHistoryModel.decode("junk").size() == 0, "unknown schema rejected");
        check(EventHistoryModel.decode("THOR_EVENTS_V1\n4|CPU_PHASE|SECRET|leak\n").size() == 0,
                "extra delimiters rejected");
        check(EventHistoryModel.decode("THOR_EVENTS_V1\n4|CPU_PHASE|GOOD\n").size() == 1,
                "valid schema accepted");
        model.clear();
        check(model.size() == 0, "clear");
        check(model.encode().equals("THOR_EVENTS_V1\n"), "cleared representation");

        EventHistoryModel many = new EventHistoryModel();
        for (int i = 0; i < 200; i++) {
            many.unavailable(i * 2L);
            many.sample(sample("0", "1", "1"), i * 2L + 1);
        }
        check(many.size() == EventHistoryModel.MAX_EVENTS, "bounded max 64");
        check(EventHistoryModel.decode(many.encode()).size() == EventHistoryModel.MAX_EVENTS,
                "bounded reload");
        System.out.println("EventHistoryModelTest passed: " + assertions + " assertions");
    }
}
