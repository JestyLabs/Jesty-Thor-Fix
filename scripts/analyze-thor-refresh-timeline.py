"""Offline, provenance-preserving logcat/atrace timeline. Standard library only.

No device access. Function entry/exit is not a successful return. A physical ID
must be explicit; generic composer/vsync tracks remain unassigned. Clock sync
requires the trace's realtime_ts marker plus an explicit logcat year/UTC offset.
"""

import argparse
from collections import defaultdict
from datetime import datetime, timedelta, timezone
import hashlib
import json
from pathlib import Path
import re
import statistics


LOGCAT = re.compile(r"^(\d\d-\d\d \d\d:\d\d:\d\d\.\d+)\s+(\d+)\s+(\d+)\s+([VDIWEF])\s+([^:]+):\s?(.*)$")
TRACE = re.compile(r"^\s*(.+?)-(\d+)\s+\(\s*(\d+|-+)\)\s+\[\d+\]\s+\S+\s+(\d+\.\d+):\s+tracing_mark_write:\s+(.*)$")
INVALID = re.compile(r"Trying to initiate a mode change to invalid mode\s+(null|-?\d+)\s+on display\s+(?:PhysicalDisplayId\{)?(\d+)\}?$")
POLICY = re.compile(r"Display\s+(?:PhysicalDisplayId\{)?(\d+)\}?\s+policy changed$")
TARGET = re.compile(r"ActiveModeFPS_HWC\s*-(\d+)$")
FUNCTIONS = {
    "setDesiredActiveMode": "SF_DESIRED_MODE_FUNCTION",
    "updateInternalStateWithChangedMode": "SF_COMPLETION_FUNCTION",
    "HWCDisplay::SetActiveConfigWithConstraints::": "HWC_REQUEST_FUNCTION",
    "HWCDisplay::ProcessActiveConfigChange::": "HWC_PROCESS_FUNCTION",
    "HWCDisplay::SubmitDisplayConfig::": "SDM_SUBMIT_FUNCTION",
}


def utc_offset(text):
    match = re.fullmatch(r"([+-])(\d\d):(\d\d)", text)
    if not match or int(match[2]) > 23 or int(match[3]) > 59:
        raise ValueError("UTC offset must be +/-HH:MM")
    minutes = int(match[2]) * 60 + int(match[3])
    return timezone(timedelta(minutes=minutes if match[1] == "+" else -minutes))


def analyze(paths, year=None, offset=None):
    events, sources, anchors, policy_events = [], [], [], {}
    stacks, periods = defaultdict(list), {}
    vsync_samples, frame_counters, commit_scopes = defaultdict(list), defaultdict(list), defaultdict(list)
    for path in sorted(set(Path(p).resolve() for p in paths)):
        # Documentation, JSON reports and binary-string inventories are never events.
        if path.suffix.lower() not in (".txt", ".log", ".logcat", ".trace"):
            continue
        source = {"file": str(path), "sha256": hashlib.sha256(path.read_bytes()).hexdigest()}
        sources.append(source)
        for number, line in enumerate(path.read_text(encoding="utf-8-sig", errors="replace").splitlines(), 1):
            base = {"file": str(path), "line": number, "physicalDisplayId": None,
                    "frameworkModeId": None, "hwcConfigId": None, "fps": None,
                    "modeIdNamespace": None,
                    "result": None, "sameInvocationAsOtherEventProven": False}
            log = LOGCAT.match(line)
            if log:
                timestamp, pid, tid, level, tag, message = log.groups()
                tag = tag.strip()
                base.update(timestamp=timestamp, clockDomain="logcat-wall-clock", pid=int(pid), tid=int(tid))
                invalid = INVALID.fullmatch(message)
                policy = POLICY.fullmatch(message)
                if tag in ("DisplayDevice", "SurfaceFlinger") and invalid:
                    token, display = invalid.groups()
                    base.update(stage="SF_INVALID_MODE_MESSAGE", physicalDisplayId=display,
                                frameworkModeId=None if token == "null" else int(token),
                                modeIdNamespace="SurfaceFlinger DisplayModeId (incoming object; owner unknown in log)",
                                modeToken=token, outcome="unknown; exact binary may continue after logging")
                    events.append(base)
                elif tag in ("DisplayDevice", "SurfaceFlinger") and policy:
                    base.update(stage="SF_POLICY_CHANGED", physicalDisplayId=policy[1])
                    events.append(base)
                    policy_events[(str(path), pid, tid)] = base
                elif tag in ("DisplayDevice", "SurfaceFlinger"):
                    prior = policy_events.get((str(path), pid, tid))
                    if prior and number - prior["line"] <= 4:
                        if message.startswith("Previous:"):
                            prior["previous"] = message.split(":", 1)[1].strip()
                        elif message.startswith("Current:"):
                            prior["current"] = message.split(":", 1)[1].strip()
                            mode = re.search(r"defaultModeId=(\d+)", message)
                            fps = re.search(r"primaryRange=\[(\d+(?:\.\d+)?) Hz, (\d+(?:\.\d+)?) Hz\]", message)
                            if mode:
                                prior["frameworkModeId"] = int(mode[1])
                                prior["modeIdNamespace"] = "SurfaceFlinger DisplayModeId (policy default)"
                            if fps and fps[1] == fps[2]:
                                prior["fps"] = float(fps[1])
                        else:
                            count = re.fullmatch(r"(\d+) mode changes were performed under the previous policy", message)
                            if count:
                                prior["previousPolicyModeChanges"] = int(count[1])
                elif tag == "DisplayDeviceRepository" and message.startswith("Display device changed:"):
                    display = re.search(r'uniqueId="local:(\d+)"', message)
                    active = re.search(r"\bmodeId (\d+),", message)
                    if display and active:
                        mode_id = int(active[1])
                        hz = re.search(r"\{id=" + str(mode_id) + r",[^}]*?fps=([\d.]+)", message)
                        base.update(stage="ANDROID_DISPLAY_MODE_REPORTED", physicalDisplayId=display[1],
                                    frameworkModeId=mode_id, modeIdNamespace="Android Display.Mode.id",
                                    fps=float(hz[1]) if hz else None,
                                    outcome="framework report; not DRM or physical measurement")
                        events.append(base)
                elif tag == "DisplayModeDirector" and message.startswith("updateRefreshRateSettingLockedForX6:"):
                    base.update(stage="AYN_SETTINGS_OBSERVER_EXECUTED", message=message,
                                outcome="entry/readback logged; no proof of brightness-zero or restoration writes")
                    events.append(base)
                elif tag in ("sh", "auditd") and "avc:" in message and "bypass_ram" in message and "{ write }" in message:
                    base.update(stage="AYN_BYPASS_WRITE_AUDIT", message=message,
                                outcome="write attempted; audit permissive flag is not a successful DSI result")
                    events.append(base)
                continue
            trace = TRACE.match(line)
            if not trace:
                continue
            task, tid, tgid, timestamp, payload = trace.groups()
            stamp, tid = float(timestamp), int(tid)
            base.update(timestamp=timestamp, traceSeconds=stamp, clockDomain="atrace-device-clock",
                        tid=tid, pid=None if tgid.startswith("-") else int(tgid))
            key = (str(path), tid)
            sync = re.fullmatch(r"trace_event_clock_sync: realtime_ts=(\d+)", payload)
            if sync:
                anchors.append({"file": str(path), "line": number, "traceSeconds": stamp,
                                "realtimeEpochMs": int(sync[1]), "precision": "realtime marker is integer milliseconds"})
                continue
            fields = payload.split("|")
            if fields[0] == "B" and len(fields) >= 3:
                name = "|".join(fields[2:])
                scope = {"id": f"{number}:{tid}", "name": name, "start": stamp}
                base.update(function=name, scopeId=scope["id"],
                            ancestors=[s["name"] for s in stacks[key]])
                stage = FUNCTIONS.get(name)
                if name in ("HWDeviceDRM::AtomicCommit::", "DRMAtomicReq::Commit::", "presentAndGetReleaseFences"):
                    commit_scopes[name].append({"file": str(path), "line": number, "traceSeconds": stamp,
                                               "pid": base["pid"], "tid": tid})
                if stage:
                    base.update(stage=stage, outcome="function entry only; return status not traced")
                    events.append(base)
                    scope["event"] = base
                elif name.startswith("onComposerHalVsync("):
                    period = re.search(r"\((\d+)\)", name)
                    if period:
                        value = int(period[1])
                        track = (str(path), "onComposerHalVsync")
                        if periods.get(track) != value:
                            base.update(stage="VSYNC_PERIOD_REPORT", periodNs=value,
                                        outcome="unassigned to a physical display; not optical cadence")
                            events.append(base)
                            periods[track] = value
                stacks[key].append(scope)
            elif fields[0] == "E":
                if stacks[key]:
                    scope = stacks[key].pop()
                    if "event" in scope:
                        scope["event"].update(endTraceSeconds=stamp, durationUs=round((stamp - scope["start"]) * 1e6, 3))
            elif fields[0] == "C" and len(fields) == 4:
                name, value = fields[2:]
                target = TARGET.fullmatch(name)
                if target:
                    base.update(stage="SF_HWC_TARGET", physicalDisplayId=target[1], fps=float(value),
                                ancestors=[s["name"] for s in stacks[key]],
                                outcome="SF target marker; no HWC acceptance or physical measurement")
                    events.append(base)
                elif name == "VsyncPeriod" and periods.get((str(path), name)) != int(value):
                    base.update(stage="VSYNC_PERIOD_REPORT", periodNs=int(value),
                                outcome="vendor report without physical display ID; not optical cadence")
                    events.append(base)
                    periods[(str(path), name)] = int(value)
                if name == "VsyncPeriod":
                    vsync_samples[str(path)].append({"traceSeconds": stamp, "periodNs": int(value)})
                if name in ("PrevFramePending", "PrevFrameMissed", "PrevHwcFrameMissed", "PrevGpuFrameMissed"):
                    frame_counters[name].append({"file": str(path), "line": number,
                                                 "traceSeconds": stamp, "value": int(value)})

    # Clock conversion is scoped to each trace, never borrowed from another capture.
    by_trace = defaultdict(list)
    for anchor in anchors:
        by_trace[anchor["file"]].append(anchor)
    mappings = []
    for file, points in by_trace.items():
        deltas = [p["realtimeEpochMs"] / 1000 - p["traceSeconds"] for p in points]
        consistent = max(deltas) - min(deltas) <= .005
        mappings.append({"file": file, "anchors": points, "consistent": consistent,
                         "epochMinusTraceSeconds": deltas[0] if consistent else None})
        if consistent:
            for event in events:
                if event["file"] == file and event["clockDomain"] == "atrace-device-clock":
                    event["mappedUtc"] = datetime.fromtimestamp(event["traceSeconds"] + deltas[0], timezone.utc).isoformat(timespec="microseconds")
                    event["clockMappingEvidence"] = f"{file}:{points[0]['line']}"
    if year is not None and offset is not None:
        tz = utc_offset(offset)
        for event in events:
            if event["clockDomain"] == "logcat-wall-clock":
                wall = datetime.strptime(f"{year}-{event['timestamp']}", "%Y-%m-%d %H:%M:%S.%f").replace(tzinfo=tz)
                event["mappedUtc"] = wall.astimezone(timezone.utc).isoformat(timespec="microseconds")
                event["clockMappingEvidence"] = "explicit caller-provided logcat year and UTC offset"
    groups = defaultdict(list)
    for event in events:
        groups[event["physicalDisplayId"] or "UNASSIGNED"].append(event)
    for group in groups.values():
        group.sort(key=lambda e: (e.get("mappedUtc", ""), e["file"], e["line"]))
    return {"schema": "THOR_REFRESH_TIMELINE_V1", "sources": sources, "clockMappings": mappings,
            "logcatYear": year, "logcatUtcOffset": offset, "perDisplay": dict(groups),
            "automaticCrossClockPairing": False, "physicalCadenceMeasured": False,
            "chain": ["SF_REQUESTED", "SF_VALIDATED", "HWC_REQUESTED", "HWC_ACCEPTED",
                      "SDM_APPLIED", "DRM_ACTIVE", "PHYSICAL_CADENCE"],
            "globalPacing": {
                "vsyncPeriodSamples": {file: {"count": len(samples),
                    "reportedPeriodsNs": sorted({s["periodNs"] for s in samples}),
                    "medianArrivalGapMs": statistics.median([(b["traceSeconds"] - a["traceSeconds"]) * 1000
                        for a, b in zip(samples, samples[1:])]) if len(samples) > 1 else None}
                    for file, samples in vsync_samples.items()},
                "frameCounterSamples": {name: {"count": len(samples), "nonzeroCount": sum(s["value"] != 0 for s in samples)}
                                        for name, samples in frame_counters.items()},
                "commitFunctionEntries": {name: len(samples) for name, samples in commit_scopes.items()},
                "limits": "Global samples only. Arrival gaps may include lost/idle records. Zero misses do not exclude tearing. Commit scopes contain no return status, fence signal or panel ID."
            },
            "limits": "Each event supports its own boundary only. Invalid-mode text has no built-in BAD_VALUE semantics. No per-display HWC return, SDM success or physical cadence is inferred."}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", type=Path)
    parser.add_argument("--logcat-year", type=int)
    parser.add_argument("--logcat-utc-offset")
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    result = analyze(args.inputs, args.logcat_year, args.logcat_utc_offset)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(f"Timeline: {sum(map(len, result['perDisplay'].values()))} events; {len(result['clockMappings'])} trace clock mapping(s)")


if __name__ == "__main__":
    main()
