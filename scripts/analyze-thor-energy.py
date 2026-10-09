"""Offline, matched A/B energy summaries. Never accesses a device or toggles fixes."""
import argparse
import csv
import json
import math
import statistics
from collections import defaultdict
from pathlib import Path

FIELDS = ("pair_id arm comparison scenario build display_fix cpu_fix lid_guard mode method "
          "conditions configuration_verified suspend_verified duration_s energy_wh "
          "energy_resolution_wh valid normal_wake_verified disturbance").split()
FIXES = ("display_fix", "cpu_fix", "lid_guard")
ENUMS = {
    "arm": {"baseline", "candidate"},
    "comparison": {"fix", "bundle", "overhead"},
    "scenario": {"awake_idle", "gameplay", "normal_sleep", "closed_lid_wake"},
    "method": {"battery_energy", "external_meter", "charger_input"},
    "mode": {"0", "1", "2"},
}


def numeric(row, key, low, high):
    value = float(row[key])
    if not math.isfinite(value) or not low <= value <= high:
        raise ValueError("invalid_number")
    return value


def validate(row):
    if any(row.get(key, "") not in values for key, values in ENUMS.items()):
        raise ValueError("invalid_enum")
    if any(row.get(key) not in {"0", "1"} for key in FIXES):
        raise ValueError("unknown_configuration")
    if row.get("valid") != "yes" or row.get("configuration_verified") != "yes":
        raise ValueError("unverified_trial")
    if row.get("normal_wake_verified") != "yes" or row.get("disturbance") != "none":
        raise ValueError("disturbed_trial")
    if row["scenario"] in {"normal_sleep", "closed_lid_wake"} and row.get("suspend_verified") != "yes":
        raise ValueError("suspend_unverified")
    if any(not row.get(key, "").strip() for key in ("pair_id", "build", "conditions")):
        raise ValueError("missing_matching_metadata")
    duration = numeric(row, "duration_s", 1, 86400)
    energy = numeric(row, "energy_wh", 0, 1000)
    resolution = numeric(row, "energy_resolution_wh", 1e-12, 1000)
    if energy < resolution:
        raise ValueError("energy_below_resolution")
    row = dict(row)
    row["mean_w"] = energy * 3600 / duration
    row["resolution_w"] = resolution * 3600 / duration
    return row


def analyze(rows):
    pairs = defaultdict(list)
    rejected = defaultdict(int)
    for line_number, source in enumerate(rows, start=2):
        try:
            row = validate(source)
            row["csv_row"] = line_number
            pairs[row["pair_id"]].append(row)
        except (KeyError, ValueError, TypeError):
            # Do not echo owner labels, paths or raw trial content in the summary.
            rejected["invalid_or_unverified_row"] += 1
            # A bad row invalidates its entire pair, including any duplicate arm.
            pairs[source.get("pair_id", "")].append(None)
    groups = defaultdict(list)
    for members in pairs.values():
        if len(members) != 2 or any(row is None for row in members):
            rejected["incomplete_invalid_or_duplicate_pair"] += 1
            continue
        arms = {row["arm"]: row for row in members}
        if set(arms) != {"baseline", "candidate"}:
            rejected["duplicate_arm"] += 1
            continue
        baseline, candidate = arms["baseline"], arms["candidate"]
        match_keys = ("comparison", "scenario", "method", "mode", "conditions")
        if any(baseline[key] != candidate[key] for key in match_keys):
            rejected["unmatched_conditions"] += 1
            continue
        changed = tuple(key for key in FIXES if baseline[key] != candidate[key])
        kind = baseline["comparison"]
        if kind == "overhead":
            valid = not changed and baseline["build"] != candidate["build"]
        else:
            valid = baseline["build"] == candidate["build"] and all(
                baseline[key] == "0" and candidate[key] == "1" for key in changed)
            valid = valid and (len(changed) == 1 if kind == "fix" else len(changed) >= 2)
        if not valid:
            rejected["confounded_configuration"] += 1
            continue
        # Keep matching identities internally; do not publish owner labels.
        key = tuple(baseline[key] for key in match_keys) + (baseline["build"], candidate["build"],
                tuple(baseline[key] for key in FIXES), tuple(candidate[key] for key in FIXES))
        groups[key].append((baseline, candidate, changed))
    summaries = []
    for key, trials in groups.items():
        before = [pair[0]["mean_w"] for pair in trials]
        after = [pair[1]["mean_w"] for pair in trials]
        saving = [a - b for a, b in zip(before, after)]
        mean_before, mean_after = statistics.mean(before), statistics.mean(after)
        summaries.append({
            "comparison": key[0], "scenario": key[1], "method": key[2], "mode": key[3],
            "baseline_fixes": dict(zip(FIXES, key[-2])),
            "candidate_fixes": dict(zip(FIXES, key[-1])),
            "changed_fixes": list(trials[0][2]), "matched_pairs": len(trials),
            "csv_rows": [[a["csv_row"], b["csv_row"]] for a, b, _ in trials],
            "baseline_mean_w": mean_before, "candidate_mean_w": mean_after,
            "saving_mean_w": statistics.mean(saving), "saving_median_w": statistics.median(saving),
            "saving_stdev_w": statistics.stdev(saving) if len(saving) > 1 else None,
            "saving_percent": 100 * (mean_before - mean_after) / mean_before,
            "pairs_exceeding_quantization": sum(abs(a["mean_w"] - b["mean_w"])
                    > a["resolution_w"] + b["resolution_w"] for a, b, _ in trials),
            "result": "descriptive_only" if len(trials) < 3 else "repeated_descriptive_comparison",
            "scope": "charger_input_proxy" if key[2] == "charger_input" else "whole_device_energy",
        })
    return {"schema": "THOR_ENERGY_AB_V1", "groups": summaries, "rejected": dict(rejected),
            "limits": ["No hardware collection or fix toggling is performed.",
                       "Matching metadata and configuration verification are owner attestations.",
                       "Positive saving means candidate drew less; negative saving means more.",
                       "Repeated comparisons are not a statistical significance claim.",
                       "Quantization checks do not include full instrument uncertainty.",
                       "Whole-device differences do not isolate lower-panel consumption.",
                       "Bundle effects cannot be attributed to individual fixes."]}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("trials", type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    repo = Path(__file__).resolve().parents[1]
    output = args.output.resolve()
    if output == repo or repo in output.parents:
        parser.error("Keep measured results outside the repository")
    if output.exists():
        parser.error("Refusing to overwrite output")
    if args.trials.stat().st_size > 2_000_000:
        parser.error("Input exceeds size bound")
    with args.trials.open(encoding="utf-8-sig", newline="") as stream:
        reader = csv.DictReader(stream)
        if not set(FIELDS) <= set(reader.fieldnames or []):
            parser.error("Missing required trial columns")
        rows = list(reader)
    if len(rows) > 512:
        parser.error("Too many trial rows")
    result = analyze(rows)
    with output.open("x", encoding="utf-8") as stream:
        json.dump(result, stream, indent=2, allow_nan=False)
        stream.write("\n")
    print("Matched groups:", len(result["groups"]))
    return 0 if result["groups"] else 2


if __name__ == "__main__":
    raise SystemExit(main())
