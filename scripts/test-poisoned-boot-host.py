#!/usr/bin/env python3
"""Poisoned-boot detection with the real coordinator and synthetic platform boundaries.

No device access. Runs PoisonedBootTest against production sources, then against mutants
that remove one detection or containment rule each; every mutant must be rejected by its
named check.
"""
from pathlib import Path
import argparse
import json
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
PKG = 'com.thor.displaypowertest.'
CORE = ['DisplayActionCoordinator.java', 'DisplayHardware.java', 'DisplayDecisionModel.java',
        'DisplayGenerationModel.java', 'ThorHardwareProfile.java', 'KernelSignalModel.java']
# (id, file, before, after, check that must reject it)
MUTANTS = [
    ('normal-store-is-precursor', 'KernelSignalModel.java',
     '|| text.contains("failed to send DSI_CMD_SET_VID_BYPASS_RAM")) return PRECURSOR;',
     '|| text.contains("failed to send DSI_CMD_SET_VID_BYPASS_RAM")\n                || text.contains("bypass_ram_store")) return PRECURSOR;',
     'PB-02'),
    ('any-controller-timeout', 'KernelSignalModel.java',
     'text.contains("dsi-ctrl-0: Command transfer failed")',
     'text.contains("Command transfer failed")', 'PB-02'),
    ('slow-threshold-at-stall', 'DisplayActionCoordinator.java',
     'private static final long SLOW_LOWER_ON_MS = 1000L;',
     'private static final long SLOW_LOWER_ON_MS = 5000L;', 'PB-08'),
    ('slow-latches-despite-kernel-signals', 'DisplayActionCoordinator.java',
     'if (!kernelSignalsAvailable) latchLowerPoisonedLocked("SLOW_ON");',
     'latchLowerPoisonedLocked("SLOW_ON");', 'PB-09'),
    ('reconcile-clears-poisoned', 'DisplayActionCoordinator.java',
     '        if (lowerPoisonedReason != null) {\n            Log.d(TAG, "LOWER_FAILURE_LATCH kept',
     '        if (false) {\n            Log.d(TAG, "LOWER_FAILURE_LATCH kept', 'PB-07'),
    ('reconcile-powers-poisoned', 'DisplayActionCoordinator.java',
     'if (targetOn && lowerPoisonedReason != null && !"1".equals(bottom))',
     'if (false)', 'PB-06'),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--skip-mutants', action='store_true')
    args = parser.parse_args()
    output = args.output or Path(tempfile.mkdtemp(prefix='poisoned-boot-'))
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
        inputs.append(fixture / 'PoisonedBootTest.java')
        compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '-d', str(work), *map(str, inputs)],
                                  capture_output=True, text=True, timeout=120)
        log = compiled.stdout + compiled.stderr
        assert compiled.returncode == 0, log
        proc = subprocess.run(['java', '-cp', str(work), PKG + 'PoisonedBootTest'],
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
    print('poisoned-boot host: %d runs PASS' % len(results))


if __name__ == '__main__':
    main()
