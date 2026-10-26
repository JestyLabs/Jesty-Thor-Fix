#!/usr/bin/env python3
"""Characterize current real containment classes on HOST; never accesses Android/devices."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
CORE = ('DisplayActionCoordinator.java', 'DisplayHardware.java', 'DisplayDecisionModel.java',
        'DisplayGenerationModel.java', 'ThorHardwareProfile.java', 'WatcherSupervisor.java')
CASES = ('no-total-cap', 'fast-errors', 'threshold-one-over', 'reset-top', 'reset-bottom',
         'unknown-mode', 'unknown-power', 'missed-power-cycle', 'sampled-power-cycle',
         'transient-crtc', 'no-app-call-trigger', 'blocked-return', 'blocked-error',
         'concurrent-property-rollback')


def replace_once(value, before, after):
    if value.count(before) != 1:
        raise AssertionError('Fixture/source boundary drift: ' + before)
    return value.replace(before, after, 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--negative-controls', action='store_true')
    args = parser.parse_args()
    output = args.output or Path(tempfile.mkdtemp(prefix='containment-boundaries-'))
    output.mkdir(parents=True, exist_ok=True)
    fixture = ROOT / 'tests/integration/display-wake'
    source = ROOT / 'src/com/thor/displaypowertest'
    original = {name: (source / name).read_text(encoding='utf-8') for name in CORE}
    hashes = {str((source / name).relative_to(ROOT)).replace('\\', '/'):
              hashlib.sha256((source / name).read_bytes()).hexdigest() for name in CORE}
    hashes.update({str(path.relative_to(ROOT)).replace('\\', '/'):
                   hashlib.sha256(path.read_bytes()).hexdigest()
                   for path in sorted((fixture / 'fixtures').rglob('*.java'))})
    test = fixture / 'ContainmentBoundaryHostTest.java'
    hashes[str(test.relative_to(ROOT)).replace('\\', '/')] = hashlib.sha256(test.read_bytes()).hexdigest()
    results = []

    def compile_sources(label, mutation=None):
        work = output / label
        work.mkdir()
        inputs = []
        for name, value in original.items():
            if mutation and name == mutation[0]:
                value = replace_once(value, mutation[1], mutation[2])
            path = work / name
            path.write_text(value, encoding='utf-8')
            inputs.append(path)
        for path in sorted((fixture / 'fixtures').rglob('*.java')):
            value = path.read_text(encoding='utf-8')
            # Adapt existing test doubles only. Production classes above are unchanged.
            if path.name == 'HostRuntimeStubs.java':
                value = replace_once(value,
                    'final class WatcherSupervisor { static String health() { return "RUNNING"; } }', '')
                value = replace_once(value, 'final class WatcherCadence {',
                    'final class WatcherCadence {\n    static void noteSample() {}')
                value += '\nfinal class DaemonWatchThread extends Thread {\n'
                value += '    public void run() { throw new AssertionError("No watcher loop in HOST fixture"); }\n}\n'
            if path.name == 'Log.java':
                value = replace_once(value, 'new java.util.ArrayList<String>()',
                    'java.util.Collections.synchronizedList(new java.util.ArrayList<String>())')
                value = replace_once(value, 'public final class Log {',
                    'public final class Log {\n    public static int e(String tag, String message) { return 0; }')
            if path.name == 'SystemClock.java':
                value = replace_once(value, 'public static long now =', 'public static volatile long now =')
            target = work / path.relative_to(fixture / 'fixtures')
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(value, encoding='utf-8')
            inputs.append(target)
        inputs.append(test)
        process = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work),
                                  *map(str, inputs)], capture_output=True, text=True, timeout=60)
        (work / 'compile.log').write_text(process.stdout + process.stderr, encoding='utf-8')
        if process.returncode:
            raise AssertionError('Compilation failed, never an accepted negative control: ' + process.stderr)
        return work

    def run(work, case, expected=None):
        process = subprocess.run(['java', '-cp', str(work),
                                  'com.thor.displaypowertest.ContainmentBoundaryHostTest', case],
                                 capture_output=True, text=True, timeout=15)
        text = process.stdout + process.stderr
        (work / (case + '.log')).write_text(text, encoding='utf-8')
        if expected:
            if process.returncode == 0 or ('AssertionError: ' + expected) not in text:
                raise AssertionError('Negative control missed intended assertion: ' + text)
            checks = None
        else:
            match = re.search(r'^PASS ' + re.escape(case) + r': ([0-9]+) assertions;', text, re.M)
            if process.returncode or not match:
                raise AssertionError(text)
            checks = int(match.group(1))
        results.append({'run': work.name, 'case': case, 'result': 'PASS',
                        'expectedRejection': expected, 'assertions': checks,
                        'observations': [line for line in text.splitlines() if line.startswith('OBSERVATION:')]})
        print(work.name + '/' + case + ': PASS' + (' (compiled control rejected at ' + expected + ')' if expected else ''), flush=True)

    production = compile_sources('production')
    for case in CASES:
        run(production, case)
    if args.negative_controls:
        controls = (
            ('unknown-clears', 'DisplayActionCoordinator.java',
             'if ("1".equals(mode) || "2".equals(mode)) clearLowerOnBackoffLocked',
             'if ("?".equals(mode) || "1".equals(mode) || "2".equals(mode)) clearLowerOnBackoffLocked',
             'unknown-mode', 'UNKNOWN-01'),
            ('bottom-no-clear', 'DisplayActionCoordinator.java',
             'if ("1".equals(mode) || "2".equals(mode)) clearLowerOnBackoffLocked',
             'if ("1".equals(mode)) clearLowerOnBackoffLocked', 'reset-bottom', 'RESET-01'),
            ('cancel-without-monitor', 'DisplayActionCoordinator.java',
             'synchronized (DisplayHardware.class) { generations.invalidate(); cancelRepairLocked(); }',
             'generations.invalidate(); cancelRepairLocked();', 'blocked-return', 'BLOCK-03'),
            ('latch-cleared-with-mode', 'DisplayActionCoordinator.java',
             'lowerOnBackoff = false;\n        sawNonInteractive = false;',
             'lowerOnBackoff = false; lowerFailureLatched = false;\n        sawNonInteractive = false;',
             'reset-top', 'RESET-03'),
            ('premature-completion-log', 'DisplayHardware.java',
             'powerMethod.invoke(null, token, on ? 2 : 0);',
             'Log.d(TAG, "HW_CALL_OBSERVED reason=SYNTHETIC_PRE_RETURN");\n            powerMethod.invoke(null, token, on ? 2 : 0);',
             'blocked-return', 'BLOCK-05'),
        )
        for name, filename, before, after, case, expected in controls:
            run(compile_sources(name, (filename, before, after)), case, expected)
    total = sum(item['assertions'] or 0 for item in results)
    document = {'tier': 'HOST_REAL_CLASSES_SYNTHETIC_PLATFORM', 'result': 'PASS',
                'productionCases': len(CASES), 'assertions': total,
                'negativeControls': len(results) - len(CASES), 'sourceSha256': hashes,
                'fixtureAdaptations': ['real WatcherSupervisor replaces health constant; no watcher loop runs',
                    'inert cadence noteSample and Log.e adapters', 'synchronized synthetic log list', 'volatile synthetic clock'],
                'physicalBlackoutReproduced': False, 'androidExecuted': False, 'results': results}
    (output / 'results.json').write_text(json.dumps(document, indent=2) + '\n', encoding='utf-8')
    print('PASS: ' + str(len(CASES)) + ' characterization cases, ' + str(total)
          + ' assertions; vendor causality and physical recovery UNKNOWN')


if __name__ == '__main__':
    main()
