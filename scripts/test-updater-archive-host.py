#!/usr/bin/env python3
"""Real updater archive gate with inert Android metadata; no network/device/installation."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
PKG = Path('src/com/thor/displaypowertest')

def execute(command, *, cwd=None, expected=None):
    p = subprocess.run(list(map(str, command)), cwd=cwd, capture_output=True,
                       text=True, encoding='utf-8', errors='replace', timeout=60)
    output = p.stdout + p.stderr
    if expected:
        assert p.returncode != 0 and expected in output, output
    else:
        assert p.returncode == 0, output
    return output

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, default=os.environ.get('ANDROID_SDK_ROOT') or os.environ.get('ANDROID_HOME'))
    parser.add_argument('--java', type=Path, default=os.environ.get('JAVA_HOME'))
    parser.add_argument('--production-root', type=Path, default=ROOT)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--negative-controls', action='store_true')
    args = parser.parse_args()
    if args.sdk is None or args.java is None:
        parser.error('Existing SDK34 and JDK required; no dependencies installed')
    jar = args.sdk / 'platforms/android-34/android.jar'
    suffix = '.exe' if os.name == 'nt' else ''
    javac = args.java / ('bin/javac' + suffix)
    java = args.java / ('bin/java' + suffix)
    for path in (jar, javac, java):
        assert path.is_file(), 'Missing existing prerequisite: ' + path.name
    spec = importlib.util.spec_from_file_location('archive_source_list', ROOT / 'scripts/test-updater-android-e2e.py')
    native = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(native)
    names = list(native.SOURCES)
    # Supports the recorded unchanged pre-extraction source without invented replicas.
    names = [n for n in names if n != 'UpdateArchiveVerifier'
             or (args.production_root / PKG / (n + '.java')).is_file()]
    assert 'AppUpdater' in names and 'UpdateVersion' in names
    sources = {n: (args.production_root / PKG / (n + '.java')).read_text(encoding='utf-8') for n in names}
    target = 'UpdateArchiveVerifier' if 'UpdateArchiveVerifier' in sources else 'AppUpdater'
    fixtures = ROOT / 'tests/integration/updater-archive'
    shared = ROOT / 'tests/integration/updater-lifecycle/fixtures'
    adapters = [p for p in shared.rglob('*.java')
                if p.relative_to(shared).as_posix() != 'android/content/pm/PackageManager.java']
    adapters += sorted((fixtures / 'fixtures').rglob('*.java'))
    results = []
    with tempfile.TemporaryDirectory(prefix='jtf-archive-gate-') as tmp:
        work = Path(tmp)
        def trial(name, change=None, expected=None):
            directory = work / name
            production = directory / 'production'; production.mkdir(parents=True)
            fakes = directory / 'fakes'; fakes.mkdir()
            copied = directory / 'src'; copied.mkdir()
            inputs = []
            for class_name, original in sources.items():
                content = original
                if class_name == target and change:
                    before, after = change
                    assert content.count(before) == 1, 'Mutation site drift: ' + name
                    content = content.replace(before, after, 1)
                source = copied / (class_name + '.java')
                source.write_text(content, encoding='utf-8')
                inputs.append(source)
            compile_log = execute([javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                                   '-classpath', jar, '-d', production, *inputs])
            compile_log += execute([javac, '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                                    '-classpath', str(production) + os.pathsep + str(jar),
                                    '-d', fakes, *adapters, fixtures / 'UpdaterArchiveHostTest.java'])
            log = execute([java, '-cp', os.pathsep.join(map(str, (fakes, production, jar))),
                           'com.thor.displaypowertest.UpdaterArchiveHostTest'], cwd=directory, expected=expected)
            print(log if not expected else 'NEGATIVE_CONTROL PASS: ' + name + '; detected ' + expected, flush=True)
            results.append({'id': name, 'result': 'PASS', 'expectedRejection': expected})
            if args.output:
                (args.output / (name + '.log')).write_text(compile_log + log, encoding='utf-8')
            return log
        if args.output:
            assert not args.output.resolve().is_relative_to(ROOT) and not args.output.exists(), 'Fresh external output required'
            args.output.mkdir(parents=True)
        trial('production')
        if args.negative_controls:
            trial('omit-version-code', ('if (archive.getLongVersionCode() <= installed.getLongVersionCode()) {',
                                       'if (false) {'), 'ARCHIVE lower-code expected rejection')
            trial('omit-signer-rejection', ('if (!trusted) throw new IOException(',
                                          'if (false) throw new IOException('), 'ARCHIVE foreign-signer expected rejection')
            trial('candidate-contents-instead-of-lineage', (': signing.getSigningCertificateHistory()),',
                                                          ': signing.getApkContentsSigners()),'),
                  'ARCHIVE rotation-lineage expected acceptance')
            trial('installed-history-instead-of-current', ('encoded(current.getApkContentsSigners())',
                                                         'encoded(current.getSigningCertificateHistory())'),
                  'ARCHIVE valid-current-signer expected acceptance')
            trial('archive-signatures-flag-omitted', ('PackageManager.GET_SIGNING_CERTIFICATES);\n        if (archive == null)',
                                                   '0);\n        if (archive == null)'),
                  'ARCHIVE valid-current-signer metadata order/flags')
    if args.output:
        (args.output / 'results.json').write_text(json.dumps({
            'tier': 'HOST_REAL_APPUPDATER_METADATA_ADAPTED', 'target': target,
            'productionSourcesSha256': {n: hashlib.sha256((args.production_root / PKG / (n + '.java')).read_bytes()).hexdigest()
                                        for n in names},
            'trials': results, 'nativeAndroidParsing': 'NOT_RUN', 'physical': 'NOT_RUN'}, indent=2) + '\n', encoding='utf-8')

if __name__ == '__main__':
    main()
