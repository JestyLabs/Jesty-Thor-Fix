#!/usr/bin/env python3
"""Real TOP successor coordinators; inert OS, stores and monotonic input only."""
from pathlib import Path
import argparse
import hashlib
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
NAMES = ('BootCoordinator', 'CpuFixController', 'CpuAttemptResumer', 'BootSession', 'BootSafety',
         'BootGateModel', 'EarlyCpuGateModel', 'BootTraceFieldPolicy', 'CpuBootAttemptModel',
         'EarlyCpuAttemptImportModel', 'PropertyState', 'HandoffRecoveryModel', 'DaemonLaunchModel',
         'DisplayActionCoordinator', 'DisplayDecisionModel', 'DisplayGenerationModel')

def run(argv, expected=None):
    result = subprocess.run(argv, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, timeout=90)
    print(result.stdout, end='')
    if expected is None:
        if result.returncode:
            raise RuntimeError('HOST command failed')
    elif result.returncode == 0 or expected not in result.stdout:
        raise RuntimeError('Compiled boot control did not fail the required power assertion')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--negative-controls', action='store_true')
    args = parser.parse_args()
    if not shutil.which('javac') or not shutil.which('java'):
        raise RuntimeError('Existing JDK required; no installation attempted')
    with tempfile.TemporaryDirectory(prefix='jtf-top-boot-') as tmp:
        scratch = Path(tmp)
        sources = []
        for name in NAMES:
            original = ROOT / 'src/com/thor/displaypowertest' / (name + '.java')
            destination = scratch / original.name
            destination.write_bytes(original.read_bytes())
            sources.append(str(destination))
            if name in ('BootCoordinator', 'CpuFixController', 'DisplayActionCoordinator'):
                print('REAL_SOURCE ' + name + ' sha256=' + hashlib.sha256(original.read_bytes()).hexdigest())
        sources += [str(path) for path in sorted((ROOT / 'tests/top-handover').rglob('*.java'))]
        compiler = ['javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8', '-d', str(scratch / 'classes'), *sources]
        run(compiler)
        for case in ('absent', 'applied'):
            run(['java', '-cp', str(scratch / 'classes'), 'com.thor.displaypowertest.TopBootHostTest', case])
        if args.negative_controls:
            path = scratch / 'BootCoordinator.java'
            original = path.read_text(encoding='utf-8')
            target = 'boolean bottomShouldBeOn = !DaemonState.isEnabled() || !"1".equals(mode);'
            if original.count(target) != 1:
                raise RuntimeError('Boot target calculation changed; review the control')
            path.write_text(original.replace(target, 'boolean bottomShouldBeOn = true;'), encoding='utf-8')
            run(compiler)
            run(['java', '-cp', str(scratch / 'classes'), 'com.thor.displaypowertest.TopBootHostTest', 'absent'],
                'TOP boot must not issue a power command')
            print('NEGATIVE_CONTROL PASS: caller lower-ON mutation detected by actual boot path')
    print('HOST TOP boot PASS: actual CPU/display/boot controllers; native/vendor/Thor NOT RUN')

if __name__ == '__main__':
    main()
