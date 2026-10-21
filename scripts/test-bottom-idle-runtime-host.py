#!/usr/bin/env python3
"""Real Auto Bottom Off runtime with the production display coordinator; host only, no device access."""
from pathlib import Path
import argparse
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'src/com/thor/displaypowertest'
FIXTURE = ROOT / 'tests/bottom-idle'
DOUBLES = ROOT / 'tests/coordinator/CoordinatorHostDoubles.java'
PRODUCT = ('DisplayActionCoordinator.java', 'DisplayDecisionModel.java', 'DisplayGenerationModel.java',
           'ThorHardwareProfile.java', 'BottomIdleRuntime.java', 'BottomIdlePolicy.java',
           'BottomIdleSettings.java', 'BottomIdleRestoreQueue.java', 'BottomIdleTouchMonitor.java')
IDLE_DOUBLE = '/** Host-only test double: no touch observer and no physical display calls. */'
TEST = 'com.thor.displaypowertest.BottomIdleRuntimeHostTest'
# Each compiled control must fail at its own assertion, not at compilation.
CONTROLS = (
    ('no-rearm-after-restore', 'BottomIdleRuntime.java',
     'if (POLICY.completeRestore(lease, confirmed)) scheduleLocked(SystemClock.elapsedRealtime());',
     'POLICY.completeRestore(lease, confirmed);', 'RT-REARM'),
    ('native-restore-overrides-idle-off', 'DisplayActionCoordinator.java',
     '&& BottomIdleRuntime.isOffOrPending()) {', '&& false) {', 'RT-HOLD'),
    ('display-on-revokes-owned-off', 'BottomIdlePolicy.java',
     'if (!enabled || off || pending) return false;', 'if (!enabled) return false;', 'RT-OWNED'),
)


def replace_once(text, old, new):
    assert text.count(old) == 1, (old, text.count(old))
    return text.replace(old, new)


def build(work, control=None):
    sources = work / 'src'
    classes = work / 'classes'
    sources.mkdir(parents=True)
    classes.mkdir()
    inputs = []
    for name in PRODUCT:
        text = (SOURCE / name).read_text(encoding='utf-8')
        if name == 'DisplayActionCoordinator.java':
            # Only the Settings process read is replaced; every decision stays production code.
            text = replace_once(text, 'return DisplayModeReader.current();', 'return ScriptedMode.current;')
        if control and control[1] == name:
            text = replace_once(text, control[2], control[3])
        path = sources / name
        path.write_text(text, encoding='utf-8')
        inputs.append(path)
    doubles = DOUBLES.read_text(encoding='utf-8')
    # The shared coordinator doubles end with an idle-runtime replica; the real one is compiled here.
    assert doubles.count(IDLE_DOUBLE) == 1
    doubles = doubles[:doubles.index(IDLE_DOUBLE)]
    # The inert panel follows a successful scripted power command.
    doubles = replace_once(doubles, 'static long applyElapsed;',
                           'static long applyElapsed;\n    static volatile boolean panelFollows;')
    doubles = replace_once(doubles, 'return applySucceeds;',
                           'if (applySucceeds && panelFollows) Telemetry.bottom = on ? "1" : "0";'
                           ' return applySucceeds;')
    path = sources / 'CoordinatorHostDoubles.java'
    path.write_text(doubles, encoding='utf-8')
    inputs.append(path)
    inputs += sorted(FIXTURE.rglob('*.java'))
    compiled = subprocess.run(['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                               '-d', str(classes), *map(str, inputs)],
                              capture_output=True, text=True, timeout=120)
    assert compiled.returncode == 0, compiled.stdout + compiled.stderr
    return subprocess.run(['java', '-cp', str(classes), TEST],
                          capture_output=True, text=True, timeout=120)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--negative-controls', action='store_true')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='bottom-idle-runtime-') as temp:
        root = Path(temp)
        result = build(root / 'unchanged')
        print(result.stdout.strip())
        assert result.returncode == 0, result.stdout + result.stderr
        if args.negative_controls:
            for index, control in enumerate(CONTROLS):
                result = build(root / f'control-{index}', control)
                log = result.stdout + result.stderr
                assert result.returncode != 0 and control[4] in log, (control[0], log)
                print(f'NEGATIVE_CONTROL PASS: {control[0]} rejected at {control[4]}')
    print('HOST Auto Bottom Off runtime PASS; Android input routing, vendor panel and Thor NOT RUN')


if __name__ == '__main__':
    main()
