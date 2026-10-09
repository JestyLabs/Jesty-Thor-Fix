"""Synthetic energy arithmetic and exclusion fixtures; no hardware access."""
import importlib.util
from pathlib import Path
import unittest

spec = importlib.util.spec_from_file_location("energy", Path(__file__).with_name("analyze-thor-energy.py"))
energy = importlib.util.module_from_spec(spec)
spec.loader.exec_module(energy)


def pair(label="1", **changes):
    row = dict(pair_id=label, arm="baseline", comparison="fix", scenario="awake_idle",
               build="fixture", display_fix="0", cpu_fix="0", lid_guard="0", mode="1",
               method="battery_energy", conditions="controlled", configuration_verified="yes",
               suspend_verified="not_applicable", duration_s="3600", energy_wh="4",
               energy_resolution_wh="0.01", valid="yes", normal_wake_verified="yes", disturbance="none")
    candidate = dict(row, arm="candidate", display_fix="1", energy_wh="3")
    candidate.update(changes)
    return [row, candidate]


class EnergyTests(unittest.TestCase):
    def test_units_direction_and_repeats(self):
        result = energy.analyze(pair("1") + pair("2") + pair("3"))["groups"][0]
        self.assertEqual(result["matched_pairs"], 3)
        self.assertEqual(result["saving_mean_w"], 1)
        self.assertEqual(result["saving_percent"], 25)
        self.assertEqual(result["saving_stdev_w"], 0)
        self.assertEqual(energy.analyze(pair(energy_wh="5"))["groups"][0]["saving_mean_w"], -1)

    def test_reject_confounds_and_missing_evidence(self):
        for changes in ({"cpu_fix": "1"}, {"conditions": "different"}, {"energy_wh": "NaN"},
                        {"configuration_verified": "no"}, {"mode": "0"},
                        {"scenario": "normal_sleep"}, {"duration_s": "0"},
                        {"disturbance": "unexpected_wake"}, {"energy_wh": "0.001"}):
            self.assertFalse(energy.analyze(pair(**changes))["groups"], changes)
        rows = pair()
        self.assertFalse(energy.analyze(rows + [rows[1]])["groups"])

    def test_separate_builds_and_bundle(self):
        rows = pair(comparison="bundle", cpu_fix="1")
        rows[0]["comparison"] = "bundle"
        self.assertEqual(len(energy.analyze(rows)["groups"][0]["changed_fixes"]), 2)
        rows = pair(comparison="overhead", display_fix="0", build="candidate")
        rows[0]["comparison"] = "overhead"
        self.assertEqual(energy.analyze(rows)["groups"][0]["changed_fixes"], [])
        self.assertFalse(energy.analyze(pair(build="different"))["groups"])

    def test_no_pooling_unmatched_baselines(self):
        a, b = pair("a"), pair("b")
        for row in b:
            row["conditions"] = "another-controlled-condition"
            row["cpu_fix"] = "1"
        result = energy.analyze(a + b)
        self.assertEqual(len(result["groups"]), 2)
        self.assertNotIn("another-controlled-condition", str(result))
        self.assertNotIn("fixture", str(result))


if __name__ == "__main__":
    unittest.main()
