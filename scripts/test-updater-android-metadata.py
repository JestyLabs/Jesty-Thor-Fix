#!/usr/bin/env python3
"""Characterize real Android release JSON/cache on an explicitly owned disposable AVD."""
import argparse
import importlib.util
import json
from pathlib import Path
import sys

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location('updater_native', Path(__file__).with_name('test-updater-android-e2e.py'))
native = importlib.util.module_from_spec(spec)
spec.loader.exec_module(native)

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--avd', required=True)
    parser.add_argument('--adb-port', type=int, default=5037)
    parser.add_argument('--output', type=Path, required=True)
    parser.add_argument('--implementation', choices=['baseline', 'catalog'], required=True)
    args = parser.parse_args()
    suite = native.Suite(args)
    # Suite validates emulator identity, AVD name, API and owner-local output first.
    suite.call('shell', 'am', 'force-stop', 'com.android.packageinstaller')
    try: suite.call('uninstall', native.PACKAGE)
    except RuntimeError: pass
    suite.call('install', args.build / 'base.apk')
    suite.call('logcat', '-c')
    suite.command('metadata_tests')
    try:
        suite.wait(lambda: 'METADATA_COMPLETE' in suite.call('logcat', '-d', '-s', 'UpdaterMetadata:I', '*:S'))
    finally:
        logs = suite.call('logcat', '-d', '-s', 'UpdaterMetadata:V', 'UpdaterE2E:V', 'AndroidRuntime:E', '*:S')
        (suite.output / 'android.log').write_text(logs, encoding='utf-8')
    assert 'TEST_ERROR' not in logs and 'FATAL EXCEPTION' not in logs
    result = json.loads(suite.call('shell', 'cat', suite.files + 'metadata-results.json'))
    expected = 'AppUpdater' if args.implementation == 'baseline' else 'UpdateReleaseCatalog'
    assert result['implementation'] == 'com.thor.displaypowertest.' + expected
    assert len(result['cases']) == 36 and all(c['result'] == 'PASS' for c in result['cases'])
    (suite.output / 'results.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    for case in result['cases']: print('PASS: ' + case['case'], flush=True)
    print(f"Native metadata: {len(result['cases'])} cases / {result['assertions']} assertions PASS", flush=True)

if __name__ == '__main__': main()
