"""Release contract: every earlier published release is an admitted live-transfer origin.

An update from any published release from 1.7.1 on must transfer the background service live; a
mandatory restart is not an acceptable update path (owner requirement, 2026-11-02, after the first
1.7.7 candidate refused the published 1.7.6 pre-release with PREVIOUS_VERSION). The published
releases are the docs/RELEASE-NOTES-X.Y.Z.md files below the candidate's DaemonIdentity.VERSION
(release.yml refuses to publish without them); --published-tags adds the tags actually published.

Each required origin must be an exact literal in both predecessor gates, have a complete frozen
published producer, and be in every retirement matrix that exercises the real producer. The
candidate itself is never an origin. --negative-controls breaks one site at a time and requires
the contract to fail. Static source contract; the behavioral proof is the retirement suites.
"""
import argparse
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
FLOOR = (1, 7, 1)
PRODUCER_FILES = 8
IDENTITY = "src/com/thor/displaypowertest/DaemonIdentity.java"
LIVE = "src/com/thor/displaypowertest/LiveHandoverPolicy.java"
PREVIOUS = "src/com/thor/displaypowertest/PreviousSecureDaemonIdentity.java"
SOURCES = "tests/retirement/published/SOURCES.json"
NOTES = "docs/RELEASE-NOTES-*.md"
# Exact literal admitting one origin, per file.
SITES = {
    LIVE: '"{v}".equals(version)',
    PREVIOUS: 'version.equals("{v}")',
    "tests/android/retirement/src/D.java": '"{v}".equals(DaemonIdentity.RUNTIME_ID)',
    "tests/android/retirement/src/com/thor/displaypowertest/RetirementFixtureHardware.java": '"{v}".equals(armed)',
    "tests/android/retirement/src/com/thor/displaypowertest/RetirementFixtureInstrumentation.java":
        '"{v}".equals(version)',
}
# Origin lists that run the real producers: file -> (list pattern, expected number of lists).
LISTS = {
    "tests/android/retirement/check_sources_host.py": (r'for version in \(("1\.7\.1"[^)]*)\)', 1),
    "tests/android/retirement/materialize.py": (r'"--predecessor", required=True, choices=\(([^)]*)\)', 1),
    "scripts/test-retirement-host.py": (r"(?:choices=|else )\(('1\.7\.1'[^)]*)\)", 2),
    "scripts/test-retirement-android.py": (r"^VERSIONS = \(([^)]*)\)", 1),
    "tests/integration/reboot-activation/RebootActivationHostTest.java":
        (r'for\(String old:new String\[\]\{("1\.7\.1"[^}]*)\}\)', 2),
}


def parse(text):
    return tuple(int(part) for part in text.split("."))


def name(version):
    return ".".join(map(str, version))


def load():
    paths = [IDENTITY, SOURCES, *SITES, *LISTS]
    files = {path: (ROOT / path).read_text(encoding="utf-8") for path in paths}
    files[NOTES] = "\n".join(sorted(p.name for p in ROOT.glob(NOTES)))
    return files


def candidate(files):
    match = re.search(r'VERSION = "(\d+\.\d+\.\d+)";', files[IDENTITY])
    if not match:
        raise ValueError("DaemonIdentity.VERSION is not an exact X.Y.Z release")
    return parse(match.group(1))


def check(files, tags=()):
    """Returns (required origins, errors); no errors means the contract holds."""
    current = candidate(files)
    noted = {tuple(map(int, m)) for m in re.findall(r"^RELEASE-NOTES-(\d+)\.(\d+)\.(\d+)\.md$", files[NOTES], re.M)}
    tagged = {tuple(map(int, m.groups())) for m in (re.fullmatch(r"v(\d+)\.(\d+)\.(\d+)", t) for t in tags) if m}
    origins = sorted(v for v in noted | tagged if FLOOR <= v < current)
    errors = ["published %s has no release notes" % name(v) for v in sorted(tagged - noted) if v in origins]
    if not origins:
        errors.append("no published origin below the candidate")
    frozen = json.loads(files[SOURCES])["files"]
    for v in map(name, origins):
        for path, form in SITES.items():
            if form.format(v=v) not in files[path]:
                errors.append("%s does not admit published %s" % (path, v))
        entries = [e for e in frozen if e["version"] == v]
        if len(entries) != PRODUCER_FILES or not all(
                (ROOT / "tests/retirement/published" / v / e["source_path"]).is_file() for e in entries):
            errors.append("%s has no complete frozen producer for %s" % (SOURCES, v))
        for path, (pattern, count) in LISTS.items():
            lists = re.findall(pattern, files[path], re.M)
            if len(lists) != count:
                errors.append("%s: expected %d origin list(s), found %d" % (path, count, len(lists)))
            elif not all(re.search(r"['\"]%s['\"]" % re.escape(v), found) for found in lists):
                errors.append("%s does not exercise published %s" % (path, v))
    for path in (LIVE, PREVIOUS):
        if SITES[path].format(v=name(current)) in files[path]:
            errors.append("%s admits the candidate %s as its own predecessor" % (path, name(current)))
    return origins, errors


def controls(files, latest):
    """label -> (file, old, new, extra tags); each must break the contract."""
    out = {}
    for path, form in SITES.items():
        out["%s drops %s" % (Path(path).stem, latest)] = (path, form.format(v=latest), form.format(v="0.0.0"), [])
    for path, (pattern, _) in LISTS.items():
        found = list(re.finditer(pattern, files[path], re.M))[-1]
        dropped = re.sub(r",\s*['\"]%s['\"]" % re.escape(latest), "", found.group(1))
        out["%s list drops %s" % (Path(path).stem, latest)] = (
            path, found.group(0), found.group(0).replace(found.group(1), dropped), [])
    own = name(candidate(files))
    out["LiveHandoverPolicy admits the candidate"] = (
        LIVE, SITES[LIVE].format(v=latest), SITES[LIVE].format(v=latest) + ' || "%s".equals(version)' % own, [])
    out["frozen producer for %s missing" % latest] = (SOURCES, '"version": "%s"' % latest, '"version": "0.0.0"', [])
    out["published tag %s without notes" % latest] = (
        NOTES, "RELEASE-NOTES-%s.md" % latest, "RELEASE-NOTES-%s-withdrawn.md" % latest, ["v" + latest])
    return out


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--published-tags", default="", help="comma-separated published release tags (release.yml)")
    parser.add_argument("--negative-controls", action="store_true")
    args = parser.parse_args()
    tags = [t.strip() for t in args.published_tags.split(",") if t.strip()]
    files = load()
    origins, errors = check(files, tags)
    if errors:
        print("FAIL live-transfer origin contract:")
        for error in errors:
            print("  - " + error)
        return 1
    print("PASS live-transfer origins for candidate %s: %s" % (name(candidate(files)), ", ".join(map(name, origins))))
    if not args.negative_controls:
        return 0
    cases = controls(files, name(origins[-1]))
    for label, (path, old, new, extra) in cases.items():
        if old not in files[path]:
            print("NEGATIVE_CONTROL NOT_APPLIED " + label)
            return 1
        mutated = dict(files, **{path: files[path].replace(old, new, 1)})
        _, failed = check(mutated, tags + extra)
        if not failed:
            print("NEGATIVE_CONTROL FAIL accepted: " + label)
            return 1
        print("NEGATIVE_CONTROL PASS %s -> %s" % (label, failed[0]))
    print("PASS %d/%d negative controls rejected" % (len(cases), len(cases)))
    return 0


if __name__ == "__main__":
    sys.exit(main())
