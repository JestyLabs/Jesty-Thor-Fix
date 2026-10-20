#!/usr/bin/env python3
"""Production display back-off with synthetic Android boundaries; no device access."""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PKG = 'com.thor.displaypowertest.'
CORE = ['DisplayActionCoordinator.java', 'DisplayHardware.java', 'DisplayDecisionModel.java',
        'DisplayGenerationModel.java', 'ThorHardwareProfile.java']


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    output = args.output or Path(tempfile.mkdtemp(prefix='display-backoff-'))
    output.mkdir(parents=True, exist_ok=True)
    source = ROOT / 'src/com/thor/displaypowertest'
    fixture = ROOT / 'tests/integration/display-wake'
    core = {name: (source / name).read_text(encoding='utf-8') for name in CORE}
    tests = ['DisplayWakeCoordinatorTest', 'BottomWakeCharacterizationTest',
             'ResumeStallObservationTest', 'BothBackoffTest']
    results = []

    def run(name, changed=None, case=None, expected=None, baseline=False):
        work = output / name
        work.mkdir()
        inputs = []
        for file, content in core.items():
            if baseline:
                content = subprocess.check_output(['git', 'show',
                    '705fb76e12c0f0a404e5e87dc46d94ca36481e20:src/com/thor/displaypowertest/' + file],
                    cwd=ROOT, encoding='utf-8')
            elif changed and file == 'DisplayActionCoordinator.java':
                before, after = changed
                assert content.count(before) == 1, (name, content.count(before))
                content = content.replace(before, after)
            path = work / file
            path.write_text(content, encoding='utf-8')
            inputs.append(path)
        inputs += sorted((fixture / 'fixtures').rglob('*.java'))
        inputs += [fixture / (test + '.java') for test in tests]
        compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work),
                                   *map(str, inputs)], capture_output=True, text=True, timeout=60)
        log = compiled.stdout + compiled.stderr
        assert compiled.returncode == 0, log
        for test in (['BothBackoffTest'] if case else tests):
            proc = subprocess.run(['java', '-cp', str(work), PKG + test] + ([case] if case else []),
                                  capture_output=True, text=True, timeout=30)
            text = proc.stdout + proc.stderr
            log += text
            (work / 'output.txt').write_text(log, encoding='utf-8')
            assert (proc.returncode != 0 and expected in text) if expected else proc.returncode == 0, log
        results.append({'id': name, 'result': 'PASS', 'expectedRejection': expected})
        print(name + ': PASS' + (' (compiled mutant/baseline rejected: ' + expected + ')' if expected else ''), flush=True)

    run('production')
    run('baseline', case='trace', expected='BO-02', baseline=True)
    mutations = [
        ('no-arm', 'if (blockedMs <= STALLED_CALL_MS || lowerOnBackoff) return;', 'if (true) return;', 'trace', 'BO-02'),
        ('no-suppression', 'action == DisplayDecisionModel.Action.ON && lowerOnBackoff', 'action == DisplayDecisionModel.Action.ON && false', 'trace', 'BO-02'),
        ('clear-entering-both', 'if ("1".equals(mode) || "2".equals(mode)) clearLowerOnBackoffLocked', 'if ("0".equals(mode) || "1".equals(mode) || "2".equals(mode)) clearLowerOnBackoffLocked', 'trace', 'BO-02'),
        ('threshold', 'blockedMs <= STALLED_CALL_MS', 'blockedMs < STALLED_CALL_MS', 'threshold', 'TH-02'),
        ('block-off', 'if (action == DisplayDecisionModel.Action.OFF) attempt', 'if (action == DisplayDecisionModel.Action.OFF && !lowerOnBackoff) attempt', 'topoff', 'TO-02'),
        ('no-user-clear', 'clearLowerOnBackoffLocked("USER_RECONCILE");', '/* mutant: no user clear */', 'clear', 'CL-05'),
        ('no-failed-call-arm', 'noteHardwareCallLocked(reason, SystemClock.elapsedRealtime() - hardwareStartedAt);\n            status = "APPLY_FAILED";', 'status = "APPLY_FAILED";', 'failure', 'FA-02'),
        ('no-power-clear', 'clearLowerOnBackoffLocked("POWER_CYCLE");', '/* mutant: no cycle clear */ {}', 'powercycle', 'PC-02'),
        ('unconditional-power-clear', '"1".equals(interactive) && sawNonInteractive', '"1".equals(interactive)', 'trace', 'BO-02'),
        ('no-query-rate-limit', 'queryAt - lastInteractiveQueryAt >= INTERACTIVE_QUERY_MS', 'true', 'unknown', 'UN-02'),
        ('query-without-backoff', 'if (lowerOnBackoff) {', 'if (true) {', 'control', 'CT-03'),
    ]
    for name, before, after, case, expected in mutations:
        run(name, (before, after), case, expected)
    (output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    print('Host contracts complete; physical blackout NOT reproduced. Results: ' + str(output))


if __name__ == '__main__':
    main()
