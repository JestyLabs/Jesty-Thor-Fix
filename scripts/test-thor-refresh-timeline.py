"""Synthetic host tests. No Thor data, binary or device access required."""

import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
import sys

sys.dont_write_bytecode = True

spec = importlib.util.spec_from_file_location("timeline", Path(__file__).with_name("analyze-thor-refresh-timeline.py"))
timeline = importlib.util.module_from_spec(spec)
spec.loader.exec_module(timeline)


def trace(stamp, payload, tid=100, pid=100):
    return f"  process-{tid} ( {pid}) [001] ..... {stamp:.6f}: tracing_mark_write: {payload}\n"


class TimelineTests(unittest.TestCase):
    def run_capture(self, content, suffix=".txt", year=None, offset=None):
        with tempfile.TemporaryDirectory(prefix="thor-timeline-test-") as root:
            path = Path(root) / ("capture" + suffix)
            path.write_text(content, encoding="utf-8")
            return timeline.analyze([path], year, offset)

    def test_inverted_display_local_ids(self):
        text = """10-07 23:42:17.246 100 100 I DisplayDevice: Display 10 policy changed
10-07 23:42:17.246 100 100 I DisplayDevice: Current: {{defaultModeId=1, primaryRange=[120.00 Hz, 120.00 Hz]}}
10-07 23:42:17.248 100 100 I DisplayDevice: Display 20 policy changed
10-07 23:42:17.248 100 100 I DisplayDevice: Current: {{defaultModeId=0, primaryRange=[120.00 Hz, 120.00 Hz]}}
10-07 23:42:17.252 100 100 E DisplayDevice: Trying to initiate a mode change to invalid mode 1 on display 20
"""
        result = self.run_capture(text)["perDisplay"]
        self.assertEqual(result["10"][0]["frameworkModeId"], 1)
        self.assertEqual(result["20"][0]["frameworkModeId"], 0)
        invalid = result["20"][1]
        self.assertEqual(invalid["frameworkModeId"], 1)
        self.assertIsNone(invalid["fps"])
        self.assertNotIn("BAD_VALUE", invalid["outcome"])

    def test_invalid_does_not_imply_rejection_or_distinct_invocation(self):
        text = "10-07 23:42:17.252 100 100 E DisplayDevice: Trying to initiate a mode change to invalid mode 1 on display 20\n"
        text += trace(10.174, "C|100|ActiveModeFPS_HWC -20|120")
        result = self.run_capture(text)
        events = result["perDisplay"]["20"]
        self.assertEqual(len(events), 2)
        self.assertTrue(all(e["result"] is None for e in events))
        self.assertTrue(all(not e["sameInvocationAsOtherEventProven"] for e in events))
        self.assertFalse(result["automaticCrossClockPairing"])

    def test_null_is_not_mode_zero(self):
        text = "10-07 23:42:17.252 100 100 E DisplayDevice: Trying to initiate a mode change to invalid mode null on display PhysicalDisplayId{20}\n"
        event = self.run_capture(text)["perDisplay"]["20"][0]
        self.assertIsNone(event["frameworkModeId"])
        self.assertEqual(event["modeToken"], "null")

    def test_hwc_calls_remain_distinct_and_unassigned(self):
        text = trace(10, "B|200|HWCDisplay::SetActiveConfigWithConstraints::", 201, 200)
        text += trace(10.001, "E|200", 201, 200)
        text += trace(10.002, "B|200|HWCDisplay::SetActiveConfigWithConstraints::", 201, 200)
        text += trace(10.003, "E|200", 201, 200)
        result = self.run_capture(text)["perDisplay"]["UNASSIGNED"]
        self.assertEqual(len(result), 2)
        self.assertNotEqual(result[0]["scopeId"], result[1]["scopeId"])
        self.assertEqual(result[0]["durationUs"], 1000)
        self.assertIsNone(result[0]["result"])
        self.assertIsNone(result[0]["physicalDisplayId"])

    def test_no_clock_anchor_means_no_trace_wall_time(self):
        result = self.run_capture(trace(10, "C|100|ActiveModeFPS_HWC -20|120"), year=2026, offset="+01:00")
        self.assertEqual(result["clockMappings"], [])
        self.assertNotIn("mappedUtc", result["perDisplay"]["20"][0])

    def test_real_anchor_maps_time_without_matching_invocations(self):
        text = trace(76246.000855, "trace_event_clock_sync: realtime_ts=1791412922079")
        text += trace(76261.174043, "C|100|ActiveModeFPS_HWC -20|120")
        event = self.run_capture(text)["perDisplay"]["20"][0]
        self.assertEqual(event["mappedUtc"], "2026-10-07T22:42:17.252188+00:00")
        self.assertFalse(event["sameInvocationAsOtherEventProven"])

    def test_inconsistent_anchors_disable_mapping(self):
        text = trace(10, "trace_event_clock_sync: realtime_ts=100000")
        text += trace(11, "trace_event_clock_sync: realtime_ts=111000")
        text += trace(12, "C|100|ActiveModeFPS_HWC -20|120")
        result = self.run_capture(text)
        self.assertFalse(result["clockMappings"][0]["consistent"])
        self.assertNotIn("mappedUtc", result["perDisplay"]["20"][0])

    def test_global_vsync_is_not_bottom_cadence(self):
        result = self.run_capture(trace(10, "C|100|VsyncPeriod|8333333"))
        self.assertFalse(result["physicalCadenceMeasured"])
        self.assertEqual(list(result["perDisplay"]), ["UNASSIGNED"])

    def test_android_mode_namespace_is_not_sf_mode_namespace(self):
        text = '10-07 23:42:17.268 100 100 I DisplayDeviceRepository: Display device changed: DisplayDeviceInfo{uniqueId="local:10", modeId 2, supportedModes [{id=1, fps=60.0}, {id=2, fps=120.0}]}\n'
        event = self.run_capture(text)["perDisplay"]["10"][0]
        self.assertEqual(event["frameworkModeId"], 2)
        self.assertEqual(event["modeIdNamespace"], "Android Display.Mode.id")
        self.assertEqual(event["fps"], 120)
        self.assertIsNone(event["hwcConfigId"])

    def test_commit_entries_and_zero_misses_do_not_measure_tearing(self):
        text = trace(10, "B|100|HWDeviceDRM::AtomicCommit::")
        text += trace(10.001, "E|100")
        text += trace(10.002, "C|100|PrevFrameMissed|0")
        result = self.run_capture(text)
        self.assertEqual(result["globalPacing"]["commitFunctionEntries"]["HWDeviceDRM::AtomicCommit::"], 1)
        self.assertEqual(result["globalPacing"]["frameCounterSamples"]["PrevFrameMissed"]["nonzeroCount"], 0)
        self.assertFalse(result["physicalCadenceMeasured"])

    def test_markdown_and_wrong_log_tag_are_not_runtime_events(self):
        text = "10-07 23:42:17.252 100 100 E DisplayDevice: Trying to initiate a mode change to invalid mode 1 on display 20\n"
        self.assertEqual(self.run_capture(text, suffix=".md")["perDisplay"], {})
        self.assertEqual(self.run_capture(text.replace("DisplayDevice:", "SomeApp:"))["perDisplay"], {})
        self.assertEqual(self.run_capture("example: " + text)["perDisplay"], {})

    def test_logcat_needs_explicit_calendar_and_timezone(self):
        text = "10-07 23:42:17.252 100 100 E DisplayDevice: Trying to initiate a mode change to invalid mode 1 on display 20\n"
        self.assertNotIn("mappedUtc", self.run_capture(text)["perDisplay"]["20"][0])
        event = self.run_capture(text, year=2026, offset="+01:00")["perDisplay"]["20"][0]
        self.assertEqual(event["mappedUtc"], "2026-10-07T22:42:17.252000+00:00")

    def test_nested_events_use_thread_local_stacks(self):
        text = trace(10, "B|100|outer", 101)
        text += trace(10.001, "B|100|setDesiredActiveMode", 101)
        text += trace(10.002, "E|100", 102)
        text += trace(10.003, "E|100", 101)
        event = self.run_capture(text)["perDisplay"]["UNASSIGNED"][0]
        self.assertEqual(event["ancestors"], ["outer"])
        self.assertEqual(event["durationUs"], 2000)


if __name__ == "__main__":
    unittest.main()
