#!/usr/bin/env python3
"""Production display back-off with synthetic Android boundaries; no device access."""
from pathlib import Path
import argparse
import json
import hashlib
import re
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
    frozen = fixture / 'baseline'
    for item in json.loads((frozen / 'manifest.json').read_text(encoding='utf-8')):
        assert hashlib.sha256((frozen / item['file']).read_bytes()).hexdigest() == item['sha256']
    tests = ['DisplayWakeCoordinatorTest', 'BottomWakeCharacterizationTest',
             'ResumeStallObservationTest', 'BothBackoffTest']
    results = []

    def run(name, changed=None, case=None, expected=None, baseline=None, test_class=None):
        work = output / name
        work.mkdir()
        inputs = []
        for file, content in core.items():
            if baseline == 'original' and file in ('DisplayActionCoordinator.java', 'DisplayHardware.java'):
                content = (frozen / ('original-coordinator.java.txt' if file == 'DisplayActionCoordinator.java'
                                     else 'original-hardware.java.txt')).read_text(encoding='utf-8')
            elif baseline == 'v174' and file == 'DisplayActionCoordinator.java':
                content = (frozen / 'v174-coordinator.java.txt').read_text(encoding='utf-8')
            elif baseline == 'backoff' and file == 'DisplayActionCoordinator.java':
                content = (frozen / 'backoff-coordinator.java.txt').read_text(encoding='utf-8')
            elif changed and file == 'DisplayActionCoordinator.java':
                before, after = changed
                assert content.count(before) == 1, (name, content.count(before))
                content = content.replace(before, after)
            path = work / file
            path.write_text(content, encoding='utf-8')
            inputs.append(path)
        inputs += sorted((fixture / 'fixtures').rglob('*.java'))
        inputs += [fixture / (test + '.java') for test in tests]
        if test_class or not case:
            inputs.append(fixture / 'ContainmentStatusRegression.java')
        compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work),
                                   *map(str, inputs)], capture_output=True, text=True, timeout=60)
        log = compiled.stdout + compiled.stderr
        assert compiled.returncode == 0, log
        selected = [(test_class or 'BothBackoffTest', case)] if case else (
            [(test, None) for test in tests] + [('ContainmentStatusRegression', value)
                                              for value in ('failure', 'unconfirmed', 'mode-changed')])
        for test, selected_case in selected:
            proc = subprocess.run(['java', '-cp', str(work), PKG + test] + ([selected_case] if selected_case else []),
                                  capture_output=True, text=True, timeout=30)
            text = proc.stdout + proc.stderr
            log += text
            (work / 'output.txt').write_text(log, encoding='utf-8')
            assert (proc.returncode != 0 and expected in text) if expected else proc.returncode == 0, log
        results.append({'id': name, 'result': 'PASS', 'expectedRejection': expected})
        print(name + ': PASS' + (' (compiled mutant/baseline rejected: ' + expected + ')' if expected else ''), flush=True)
        return log

    production_log = run('production')
    for value in ('failure', 'unconfirmed', 'mode-changed'):
        before_log = run('v174-status-' + value, case=value, expected='REG-04',
                         baseline='v174', test_class='ContainmentStatusRegression')
        pattern = r'case=' + value + r';bottom=1;status=[A-Z_]+;hardware_calls=(\d+);property_writes=(\d+)'
        before = re.search(pattern, before_log)
        after = re.search(pattern, production_log)
        assert before and after and before.groups() == after.groups(), 'Command effects changed: ' + value
    run('status-retain-old-success',
        ('bothDegraded = attempt(false, "BOTH_DEGRADE", mode);',
         'if (attempt(false, "BOTH_DEGRADE", mode)) bothDegraded = true;'),
        case='failure', expected='REG-04', test_class='ContainmentStatusRegression')
    run('status-false-waiting-label',
        ('status = "BOTH_ON_BACKOFF";\n                bothDegraded = attempt',
         'status = "BOTH_DEGRADED";\n                bothDegraded = attempt'),
        case='failure', expected='REG-07', test_class='ContainmentStatusRegression')
    run('baseline', case='trace', expected='BO-02', baseline='original')
    run('baseline-threaded-replay', case='replay', expected='REPLAY-04', baseline='original')
    run('backoff-before-degrade', case='degrade', expected='DG-01', baseline='backoff')
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
        ('no-degrade', 'if (lowerOnBackoff && "0".equals(mode) && "1".equals(bottom)) {', 'if (false) {', 'degrade', 'DG-01'),
        ('degrade-without-stall', 'if (lowerOnBackoff && "0".equals(mode) && "1".equals(bottom)) {', 'if ("0".equals(mode) && "1".equals(bottom)) {', 'nodegrade', 'ND-01'),
        ('no-degrade-clear', 'bothDegraded = false;', '/* mutant: retain previous degraded label */', 'restore', 'RS-02'),
        ('bypass-boot-hold', 'if (!BootSafety.knownMode(mode) || BootSafety.isHeld()) {', 'if (!BootSafety.knownMode(mode)) {', 'gates', 'SAFE-01'),
        ('bypass-disabled-fix', 'if (!DaemonState.isEnabled()) {', 'if (false) {', 'gates', 'SAFE-01'),
    ]
    for name, before, after, case, expected in mutations:
        run(name, (before, after), case, expected)
    (output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    print('Host contracts complete; physical blackout NOT reproduced. Results: ' + str(output))


if __name__ == '__main__':
    main()
