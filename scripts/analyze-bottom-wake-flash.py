#!/usr/bin/env python3
"""Offline, read-only classification of existing Thor logcat epoch or monotonic text.

Never starts ADB or prints raw log lines, device IDs, PIDs, absolute timestamps,
process names, or user-supplied file paths. It cannot prove optical brightness
or distinguish a missing event from incomplete logs.
"""
import argparse
from collections import Counter
import json
from pathlib import Path
import re
import sys

TIMED_LOGCAT_LINE = re.compile(r"^\s*(\d{1,13}(?:\.\d+)?)\s+\d+\s+\d+\s+[A-Z]\s+")
# Only the actual logcat tag is authoritative. Untrusted messages from
# unrelated apps may quote "ThorDisplayDaemon: WATCH_BOTH ON".
APP_TAG = re.compile(
    r"^\\s*\\d{1,13}(?:\\.\\d+)?\\s+\\d+\\s+\\d+\\s+[A-Z]\\s+"
    r"(?:ThorDisplayDaemon|ThorLidGuard)\\s*:", re.IGNORECASE
)
VENDOR_BRIGHTNESS = re.compile(
    r"\b(?:backlight|brightness|panel1-backlight|onBrightnessFade|bypass_ram)\b",
    re.IGNORECASE,
)

APP_PATTERNS = (
    ("jesty_watch_both_on", re.compile(r"\bWATCH_BOTH(?:\s+ON|:ON)\b", re.I)),
    ("jesty_watch_top_off", re.compile(r"\bWATCH_TOP(?:\s+OFF|:OFF)\b", re.I)),
    ("jesty_wake_repair", re.compile(r"\b(?:WAKE_REPAIR_RESULT|WAKE REPAIR(?: SCHEDULED| RESCHEDULED| CANCELLED)?|WAKE_REPAIR:OFF)\b", re.I)),
    ("jesty_display_on_event", re.compile(r"\bEVENT ON CONFIRMED\b", re.I)),
    ("jesty_mode_change", re.compile(r"\bMODE_CHANGED\b", re.I)),
    ("jesty_boot_ready", re.compile(r"\bBOOT READY\b", re.I)),
    ("jesty_other_display_action", re.compile(r"\b(?:BOOT_RECONCILE|USER_RECONCILE)(?:\s+ON|:ON|\s+OFF|:OFF)\b", re.I)),
)


def category_of(line):
    """Return only a finite, privacy-safe category; never raw line data."""
    if APP_TAG.search(line):
        for name, pattern in APP_PATTERNS:
            if pattern.search(line):
                return name
    if VENDOR_BRIGHTNESS.search(line):
        return "possible_vendor_brightness_log"
    return None


def analyse_lines(lines, observed_at=None, window_seconds=6.0, max_events=80):
    if not (0.5 <= window_seconds <= 120):
        raise ValueError("window_seconds must be between 0.5 and 120")
    events = []
    counts = Counter()
    timed_lines = 0
    first_time = None
    clock = None
    for line in lines:
        match = TIMED_LOGCAT_LINE.match(line)
        if match is None:
            continue
        timestamp = float(match.group(1))
        line_clock = "epoch" if timestamp >= 1_000_000_000 else "monotonic"
        if clock is not None and clock != line_clock:
            raise ValueError("mixed logcat clock formats in one input")
        clock = line_clock
        timed_lines += 1
        if first_time is None:
            first_time = timestamp
        category = category_of(line)
        if category is None:
            continue
        counts[category] += 1
        if observed_at is None or abs(timestamp - observed_at) <= window_seconds:
            events.append((timestamp, category))
    if observed_at is not None and clock is not None:
        anchor_clock = "epoch" if observed_at >= 1_000_000_000 else "monotonic"
        if clock != anchor_clock:
            raise ValueError("observation anchor clock differs from logcat clock")
    reference = observed_at if observed_at is not None else first_time
    # Log order is preserved; a relative offset is NOT a true user-visible
    # flash time unless an independently timestamped observation was supplied.
    timeline = [
        {"offset_ms": round((when - reference) * 1000),
         "category": category}
        for when, category in events[:max_events]
    ] if reference is not None else []
    seen_near = any(category == "jesty_watch_both_on" for _, category in events)
    return {
        "classification": "TEMPORAL_PROXIMITY_ONLY" if observed_at is not None and seen_near
                          else "INCONCLUSIVE",
        "observation_anchor_provided": observed_at is not None,
        "log_clock": clock or "unknown",
        "timed_lines": timed_lines,
        "recognized_counts_all": dict(sorted(counts.items())),
        "window_event_count": len(events),
        "timeline_truncated": len(events) > max_events,
        "timeline": timeline,
        "caveats": [
            "Recognized log messages do not measure screen luminance.",
            "A nearby ON command does not establish causation.",
            "Missing log messages cannot prove no action occurred.",
            "CRTC, refresh and brightness transitions require independent evidence.",
        ],
    }


def human_report(result):
    out = [
        "Thor BOTTOM bright-flash: OFFLINE LOG CLASSIFICATION",
        "Classification: " + result["classification"],
        "Visual timestamp provided: " +
            ("yes" if result["observation_anchor_provided"] else "no"),
        "Logcat timebase: " + result["log_clock"],
        "Logcat timestamped lines parsed: " + str(result["timed_lines"]),
        "Recognized event counts (entire input):",
    ]
    out.extend("  " + name + ": " + str(count)
               for name, count in result["recognized_counts_all"].items())
    out.append("Timeline: relative milliseconds " +
               ("to observed visual time" if result["observation_anchor_provided"]
                else "to first parsed line (NOT visual time)"))
    for event in result["timeline"]:
        out.append("  {:+d} ms  {}".format(event["offset_ms"], event["category"]))
    if result["timeline_truncated"]:
        out.append("  [timeline truncated: review locally with smaller input]")
    out.extend("CAVEAT: " + caveat for caveat in result["caveats"])
    return "\n".join(out)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--input", required=True, type=Path,
                        help="existing local logcat -v epoch or -v monotonic text; never uploaded")
    parser.add_argument("--observed-at", type=float,
                        help="owner-observed flash timestamp in THE SAME CLOCK as logcat: Unix seconds for epoch or boot-relative seconds for monotonic")
    parser.add_argument("--window-seconds", type=float, default=6.0,
                        help="symmetric observation window; default 6 s")
    parser.add_argument("--json", action="store_true",
                        help="emit only privacy-limited category JSON")
    args = parser.parse_args(argv)
    if not 0.5 <= args.window_seconds <= 120:
        parser.error("--window-seconds must be between 0.5 and 120")
    try:
        with args.input.open("r", encoding="utf-8", errors="replace") as f:
            result = analyse_lines(f, args.observed_at, args.window_seconds)
    except OSError:
        print("Cannot read supplied local log file (path omitted).", file=sys.stderr)
        return 2
    except ValueError:
        print("Invalid logcat clock or observation anchor (raw input omitted).", file=sys.stderr)
        return 2
    print(json.dumps(result, indent=2, sort_keys=True) if args.json
          else human_report(result))
    return 0


if __name__ == "__main__":
    sys.exit(main())
