#!/usr/bin/env python3
"""Panel-RAM bypass interception with the real coordinator and synthetic kernel boundaries.

No device access. Runs BypassRamInterceptorTest against production sources, then against
mutants that remove one safety rule each; every mutant must be rejected by its named check.
"""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PKG = 'com.thor.displaypowertest.'
CORE = ['DisplayActionCoordinator.java', 'DisplayHardware.java', 'DisplayDecisionModel.java',
        'DisplayGenerationModel.java', 'ThorHardwareProfile.java', 'BypassRamPolicy.java',
        'BypassRamInterceptor.java']
# (id, file, before, after, check that must reject it)
MUTANTS = [
    ('write-with-lower-off', 'BypassRamPolicy.java',
     '        if (!"On".equals(lowerDpms)) return "Off".equals(lowerDpms) ? "LOWER_OFF" : "LOWER_UNKNOWN";\n',
     # The settle rule also holds the store; the deferral reason is what changes.
     '', 'BR-03c'),
    ('write-when-non-interactive', 'BypassRamPolicy.java',
     '        if (!"1".equals(interactive)) return "0".equals(interactive) ? "NON_INTERACTIVE" : "INTERACTIVE_UNKNOWN";\n',
     '', 'BR-07'),
    ('accept-any-value', 'BypassRamPolicy.java',
     'if ("1".equals(value) || "0x1".equals(value)) return "1";',
     'if (!value.isEmpty()) return "1";', 'BR-08'),
    ('stale-power-read', 'BypassRamInterceptor.java',
     'String dpms = io.read(ThorHardwareProfile.BOTTOM_DPMS_PATH);',
     'String dpms = "On";', 'BR-03c'),
    ('bind-without-sysfs', 'BypassRamInterceptor.java',
     'if (!sysfs && !io.run(MOUNT, "-t", "sysfs", "sysfs", SYSFS)) {\n            unavailable("SYSFS");\n            return;\n        }',
     'io.run(MOUNT, "-t", "sysfs", "sysfs", SYSFS);', 'BR-14'),
    ('bind-without-watch', 'BypassRamInterceptor.java',
     'if (!startWatch()) {\n            unavailable("WATCH");\n            return;\n        }',
     'startWatch();', 'BR-16'),
    ('drop-waiting-request-on-fix-off', 'BypassRamInterceptor.java',
     '            if (pending != null) {\n                state = State.DRAINING;',
     '            if (false) {\n                state = State.DRAINING;', 'BR-13'),
    ('no-lower-on-hook', 'DisplayHardware.java',
     'if (on && listener != null) {', 'if (false) {', 'BR-04'),
    ('raw-value-to-kernel', 'BypassRamInterceptor.java',
     'io.write(REAL, BypassRamPolicy.kernelForm(wanted))', 'io.write(REAL, wanted)', 'BR-02'),
    ('unbounded-watcher-retries', 'BypassRamInterceptor.java',
     'if (writeFailures >= MAX_WRITE_FAILURES) return;', '', 'BR-22'),
    ('request-earns-no-retry', 'BypassRamInterceptor.java',
     'if (!"WATCHER".equals(source) && !"DRAIN".equals(source)) writeFailures = 0;', '', 'BR-22f'),
    ('lower-on-coalesced', 'BypassRamInterceptor.java',
     'if (pending != null) worker.execute(() -> attempt("LOWER_ON"));',
     'if (pending != null && writeFailures < MAX_WRITE_FAILURES) worker.execute(() -> attempt("LOWER_ON"));', 'BR-22d'),
    ('platform-start-not-awaited', 'BypassRamPolicy.java',
     'if (!lowerSettled) return "LOWER_SETTLING";', '', 'BR-06b'),
    ('first-sighting-counts-as-settled', 'BypassRamInterceptor.java',
     'lowerOnSince = SystemClock.elapsedRealtime();',
     'lowerOnSince = SystemClock.elapsedRealtime() - SETTLE_MS;', 'BR-06b'),
    ('top-on-counts-as-settled', 'BypassRamInterceptor.java',
     'if ("1".equals(mode) || "0".equals(bottomCrtc)) {', 'if ("0".equals(bottomCrtc)) {', 'BR-23e'),
    ('own-lower-on-not-trusted', 'BypassRamInterceptor.java',
     'lowerOnSince = SystemClock.elapsedRealtime() - SETTLE_MS;', '', 'BR-04b'),
    ('drain-start-counts-as-settled', 'BypassRamInterceptor.java',
     'else if ("DRAIN".equals(source) && lowerOnSince < 0L) lowerOnSince = now;',
     'else if ("DRAIN".equals(source) && lowerOnSince < 0L) lowerOnSince = now - SETTLE_MS;', 'BR-13f'),
    ('off-keeps-settle-time', 'BypassRamInterceptor.java',
     'if (!"On".equals(dpms)) lowerOnSince = -1L;', 'if (!"On".equals(dpms)) { }', 'BR-13g'),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--skip-mutants', action='store_true')
    args = parser.parse_args()
    output = args.output or Path(tempfile.mkdtemp(prefix='bypass-ram-'))
    output.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'src/com/thor/displaypowertest'
    fixture = ROOT / 'tests/integration/display-wake'
    core = {name: (source / name).read_text(encoding='utf-8') for name in CORE}
    results = []

    def run(name, mutant=None):
        work = output / name
        work.mkdir()
        inputs = []
        for file, content in core.items():
            if mutant and file == mutant[1]:
                assert content.count(mutant[2]) == 1, (name, content.count(mutant[2]))
                content = content.replace(mutant[2], mutant[3])
            path = work / file
            path.write_text(content, encoding='utf-8')
            inputs.append(path)
        inputs += sorted((fixture / 'fixtures').rglob('*.java'))
        inputs.append(fixture / 'BypassRamInterceptorTest.java')
        compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), *map(str, inputs)],
                                  capture_output=True, text=True, timeout=120)
        log = compiled.stdout + compiled.stderr
        assert compiled.returncode == 0, log
        proc = subprocess.run(['java', '-cp', str(work), PKG + 'BypassRamInterceptorTest'],
                              capture_output=True, text=True, timeout=60)
        log += proc.stdout + proc.stderr
        (work / 'output.txt').write_text(log, encoding='utf-8')
        if mutant:
            assert proc.returncode != 0 and mutant[4] in log, name + ' was not rejected by ' + mutant[4] + '\n' + log
        else:
            assert proc.returncode == 0, log
        results.append({'id': name, 'result': 'PASS', 'expectedRejection': mutant[4] if mutant else None})
        print(name + ': PASS' + (' (mutant rejected by ' + mutant[4] + ')' if mutant else
                                 ' ' + proc.stdout.strip().splitlines()[-1]), flush=True)

    run('production')
    if not args.skip_mutants:
        for mutant in MUTANTS:
            run('mutant-' + mutant[0], mutant)
    (output / 'results.json').write_text(json.dumps(results, indent=1), encoding='utf-8')
    print('bypass-ram host: %d runs PASS' % len(results))


if __name__ == '__main__':
    main()
