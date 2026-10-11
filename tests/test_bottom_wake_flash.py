"""Synthetic privacy and inference checks for bottom-wake flash research."""
import contextlib
import io
from pathlib import Path
import runpy
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
MODULE = runpy.run_path(str(ROOT / "scripts/analyze-bottom-wake-flash.py"))
analyse = MODULE["analyse_lines"]
classify = MODULE["category_of"]
human_report = MODULE["human_report"]
main = MODULE["main"]

BASE = 1791740000.0


def log(delta, tag, message):
    return f"{BASE + delta:.3f} 12345 6789 D {tag}: {message}\n"


class WakeFlashOfflineTest(unittest.TestCase):
    def test_app_on_and_relative_window(self):
        lines = [
            log(0, "ThorDisplayDaemon", "BOOT READY"),
            log(1, "ThorDisplayDaemon", "WATCH_BOTH ON"),
            log(1.1, "DisplayService", "panel1-backlight brightness transition"),
            log(5, "ThorDisplayDaemon", "WATCH_TOP OFF"),
        ]
        data = analyse(lines, observed_at=BASE + 1.2, window_seconds=0.5)
        self.assertEqual(data["classification"], "TEMPORAL_PROXIMITY_ONLY")
        self.assertEqual(data["recognized_counts_all"]["jesty_watch_both_on"], 1)
        self.assertEqual(data["window_event_count"], 2)
        self.assertEqual(data["timeline"][0]["offset_ms"], -200)
        self.assertNotIn(str(BASE), human_report(data))

    def test_absence_and_missing_log_not_exculpatory(self):
        data = analyse([log(0, "ThorDisplayDaemon", "WATCH_TOP OFF")],
                       observed_at=BASE + 0.4)
        self.assertEqual(data["classification"], "INCONCLUSIVE")
        self.assertIn("cannot prove", " ".join(data["caveats"]))
        empty = analyse(["------------- beginning of main\n"])
        self.assertEqual(empty["timed_lines"], 0)
        self.assertEqual(empty["timeline"], [])

    def test_app_action_requires_provenance(self):
        self.assertIsNone(classify(log(0, "OtherApp", "WATCH_BOTH ON")))
        self.assertEqual(classify(log(0, "ThorDisplayDaemon", "WATCH_BOTH:ON")),
                         "jesty_watch_both_on")
        self.assertEqual(classify(log(0, "ThorDisplayDaemon", "WAKE_REPAIR_RESULT result=OFF_OK")),
                         "jesty_wake_repair")

    def test_no_raw_private_text_in_output(self):
        secret = "SERIAL-SECRET-1234 package=com.private.user"
        data = analyse([
            log(0, "SomePackage", secret),
            log(0.2, "ThorDisplayDaemon", "WATCH_BOTH ON " + secret),
            log(0.3, "PanelDriver", "brightness " + secret),
        ], observed_at=BASE + 0.2)
        for representation in (human_report(data), str(data)):
            self.assertNotIn(secret, representation)
            self.assertNotIn("12345", representation)
            self.assertNotIn("6789", representation)
        self.assertEqual(data["recognized_counts_all"]["jesty_watch_both_on"], 1)

    def test_no_visual_anchor_means_no_temporal_correlation(self):
        data = analyse([log(0, "ThorDisplayDaemon", "WATCH_BOTH ON")])
        self.assertFalse(data["observation_anchor_provided"])
        self.assertEqual(data["classification"], "INCONCLUSIVE")
        self.assertEqual(data["timeline"][0]["offset_ms"], 0)
        self.assertIn("NOT visual time", human_report(data))

    def test_bounded_output_and_window_validation(self):
        lines = [log(n / 10, "ThorDisplayDaemon", "WATCH_BOTH ON")
                 for n in range(110)]
        data = analyse(lines, max_events=20)
        self.assertEqual(len(data["timeline"]), 20)
        self.assertTrue(data["timeline_truncated"])
        self.assertEqual(data["recognized_counts_all"]["jesty_watch_both_on"], 110)
        with self.assertRaises(ValueError):
            analyse(lines, window_seconds=1000)

    def test_cli_never_prints_input_path_or_raw_log(self):
        with tempfile.TemporaryDirectory() as directory:
            name = Path(directory) / "person-and-serial-secret.txt"
            name.write_text(log(0, "ThorDisplayDaemon",
                                "WATCH_BOTH ON SERIAL-SECRET"), encoding="utf-8")
            stdout = io.StringIO()
            with contextlib.redirect_stdout(stdout):
                exit_code = main(["--input", str(name), "--json"])
            self.assertEqual(exit_code, 0)
            self.assertNotIn(str(name), stdout.getvalue())
            self.assertNotIn("SERIAL-SECRET", stdout.getvalue())


if __name__ == "__main__":
    unittest.main()
