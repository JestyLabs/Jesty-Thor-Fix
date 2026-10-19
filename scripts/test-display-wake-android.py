#!/usr/bin/env python3
"""Real Android sleep/Battery Saver broadcasts; production coordinator, synthetic panels.

Only an explicitly named disposable emulator is accepted. No vendor Binder calls.
Build outputs, ephemeral keys and raw results must remain outside the checkout.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.jestylabs.thorfix.displaywakeharness'
COMPONENT = PACKAGE + '/com.thor.displaypowertest.'


def execute(command, env=None):
    done = subprocess.run([str(x) for x in command], stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                          errors='replace', timeout=60, env=env)
    if done.returncode:
        raise RuntimeError(f'{Path(str(command[0])).name}: {done.stdout}')
    return done.stdout.strip()


def build(args):
    out = args.build.resolve()
    if out.is_relative_to(ROOT) or out.exists():
        raise RuntimeError('Use a fresh output directory outside the checkout')
    out.mkdir(parents=True)
    classes = out / 'classes'; classes.mkdir()
    dex = out / 'dex'; dex.mkdir()
    exe = '.exe' if os.name == 'nt' else ''
    bat = '.bat' if os.name == 'nt' else ''
    tools = args.sdk / 'build-tools/35.0.0'
    jar = args.sdk / 'platforms/android-34/android.jar'
    source = ROOT / 'src/com/thor/displaypowertest'
    fixture = ROOT / 'tests/android/display-wake-harness'
    host = ROOT / 'tests/integration/display-wake/fixtures/com/thor/displaypowertest'
    sources = [args.coordinator_source or source / 'DisplayActionCoordinator.java',
               source / 'DisplayDecisionModel.java', source / 'DisplayGenerationModel.java',
               host / 'HostRuntimeStubs.java', host / 'TestDisplayState.java',
               *sorted(fixture.glob('*.java'))]
    env = dict(os.environ, JAVA_HOME=str(args.java))
    env['PATH'] = str(args.java / 'bin') + os.pathsep + env['PATH']
    execute([args.java / ('bin/javac' + exe), '-source', '8', '-target', '8',
             '-encoding', 'UTF-8', '-cp', jar, '-d', classes, *sources], env)
    execute([tools / ('d8' + bat), '--min-api', '28', '--lib', jar, '--output', dex,
             *sorted(classes.rglob('*.class'))], env)
    raw = out / 'raw.apk'
    execute([tools / ('aapt2' + exe), 'link', '-o', raw, '-I', jar,
             '--manifest', fixture / 'AndroidManifest.xml'], env)
    with zipfile.ZipFile(raw, 'a') as archive:
        archive.write(dex / 'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_STORED)
    execute([tools / ('zipalign' + exe), '-f', '4', raw, out / 'aligned.apk'], env)
    execute([args.java / ('bin/keytool' + exe), '-genkeypair', '-noprompt', '-alias', 'fixture',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '7', '-keystore', out / 'test.jks',
             '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=Disposable Display Test'], env)
    execute([tools / ('apksigner' + bat), 'sign', '--ks', out / 'test.jks',
             '--ks-key-alias', 'fixture', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
             '--out', out / 'harness.apk', out / 'aligned.apk'], env)
    execute([tools / ('apksigner' + bat), 'verify', out / 'harness.apk'], env)
    (out / 'source-manifest.json').write_text(json.dumps({
        str(p): hashlib.sha256(p.read_bytes()).hexdigest() for p in sources}, indent=2), encoding='utf-8')
    print('Built isolated Android display fixture', flush=True)


def run(args):
    if not re.fullmatch(r'emulator-\d+', args.serial or '') or not args.avd:
        raise RuntimeError('Explicit emulator serial and owned AVD name required')
    if not args.output or args.output.resolve().is_relative_to(ROOT):
        raise RuntimeError('Results must stay outside the checkout')
    args.output.mkdir(parents=True, exist_ok=True)
    adb = [args.sdk / ('platform-tools/adb.exe' if os.name == 'nt' else 'platform-tools/adb'),
           '-H', '127.0.0.1', '-P', str(args.adb_port), '-s', args.serial]
    def call(*parts):
        return execute([*adb, *parts])
    if call('emu', 'avd', 'name').splitlines()[0] != args.avd:
        raise RuntimeError('AVD name mismatch')
    if call('shell', 'getprop', 'ro.kernel.qemu') != '1':
        raise RuntimeError('Target is not an emulator')
    def shell(*parts):
        return call('shell', *parts)
    def state():
        return json.loads(shell('run-as', PACKAGE, 'cat', 'files/result.json'))
    def wait_for(predicate, label):
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            try:
                value = predicate()
                if value:
                    return value
            except (ValueError, RuntimeError):
                pass
            time.sleep(.15)
        raise AssertionError('Timed out: ' + label)
    def saver(enabled):
        shell('cmd', 'power', 'set-mode', str(int(enabled)))
        wait_for(lambda: shell('settings', 'get', 'global', 'low_power') == str(int(enabled)), 'low_power')
        wait_for(lambda: state()['events'][-1]['batterySaver'] == enabled, 'PowerManager saver broadcast')
    # This package is a disposable test fixture, with a different certificate per build.
    call('uninstall', PACKAGE) if PACKAGE in shell('pm', 'list', 'packages', PACKAGE) else None
    call('install', '-t', args.build / 'harness.apk')
    shell('dumpsys', 'battery', 'unplug')
    shell('settings', 'put', 'system', 'screen_off_timeout', '600000')
    shell('input', 'keyevent', '224')
    wait_for(lambda: 'mWakefulness=Awake' in shell('dumpsys', 'power'), 'awake before fixture launch')
    shell('wm', 'dismiss-keyguard')
    shell('am', 'start', '-W', '-n', COMPONENT + 'DisplayWakeActivity')
    wait_for(lambda: state()['events'], 'fixture ready')
    results = []
    try:
        for order in ('saver-off-control', 'restore-before-wake', 'restore-after-wake'):
            shell('input', 'keyevent', '224')
            wait_for(lambda: 'mWakefulness=Awake' in shell('dumpsys', 'power'), 'awake before arming')
            shell('am', 'broadcast', '--receiver-foreground', '-n', COMPONENT + 'DisplayWakeControl')
            wait_for(lambda: state()['events'][0]['interactive'] and not state()['offSeen'], 'fixture armed while awake')
            saver(order != 'saver-off-control')
            shell('input', 'keyevent', '223')
            wait_for(lambda: state()['offSeen'], 'real SCREEN_OFF')
            assert any(e['event'] == 'android.intent.action.SCREEN_OFF' and not e['interactive']
                       for e in state()['events'])
            asleep = shell('dumpsys', 'power')
            if 'mWakefulness=Asleep' not in asleep and 'mWakefulness=Dozing' not in asleep:
                raise AssertionError('Android did not enter asleep/dozing')
            time.sleep(1)
            if order == 'restore-before-wake':
                saver(False)
            shell('input', 'keyevent', '224')
            if order == 'restore-after-wake':
                saver(False)
            def settled():
                snapshot = state()
                return snapshot if snapshot['finished'] else None
            final = wait_for(settled, 'wake cancellation settled')
            assert final['onSeen'] and not final['pending']
            assert any(e['event'] == 'android.intent.action.SCREEN_ON' and e['interactive']
                       for e in final['events'])
            assert final['pendingProperty'] == args.expect_property, final
            assert final['property'] == args.expect_property and final['hardwareCalls'] == 0, final
            assert final['propertyWrites'] == (0 if args.expect_property == '1' else 1), final
            if order != 'saver-off-control':
                assert any(e['batterySaver'] for e in final['events'])
                assert any(not e['batterySaver'] for e in final['events'] if e['event'] != 'ARMED')
            results.append({'scenario': order, 'result': final})
            (args.output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
            print('PASS: ' + order + '; property=' + final['property'] + '; hardwareCalls=0', flush=True)
    finally:
        shell('cmd', 'power', 'set-mode', '0')
        shell('input', 'keyevent', '224')
        shell('dumpsys', 'battery', 'reset')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--java', type=Path, default=Path(os.environ.get('JAVA_HOME', '.')))
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--build-only', action='store_true')
    parser.add_argument('--run-only', action='store_true')
    parser.add_argument('--coordinator-source', type=Path)
    parser.add_argument('--expect-property', choices=('0', '1'), default='1')
    parser.add_argument('--serial')
    parser.add_argument('--avd')
    parser.add_argument('--adb-port', type=int, default=5037)
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.build_only and args.run_only:
        parser.error('Choose build-only or run-only')
    if not args.run_only:
        build(args)
    if not args.build_only:
        run(args)


if __name__ == '__main__':
    main()
