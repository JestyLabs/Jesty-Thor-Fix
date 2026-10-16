#!/usr/bin/env python3
"""Measure the real dashboard only on an explicitly owned disposable API 33 AVD.

Fresh test key, synthetic cached metadata, no update download or hardware toggles.
Raw captures and keys stay outside the checkout; print sanitized case results only.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.thor.displaypowertest'
HARNESS = 'org.jestylabs.thorfix.uiaudit'


def execute(args, env=None):
    done = subprocess.run([str(a) for a in args], env=env, stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                          errors='replace', timeout=120)
    if done.returncode:
        raise RuntimeError(f'{Path(str(args[0])).name} failed: {done.stdout}')
    return done.stdout


def external(path):
    path = path.resolve()
    if path.is_relative_to(ROOT):
        raise RuntimeError('Test keys, builds and raw evidence must stay outside the checkout')
    return path


def build(args):
    out = external(args.build)
    if out.exists() and any(out.iterdir()):
        raise RuntimeError('Use a fresh disposable build directory')
    out.mkdir(parents=True, exist_ok=True)
    java = args.java.resolve(); sdk = args.sdk.resolve()
    env = dict(os.environ, JAVA_HOME=str(java))
    env['PATH'] = str(java / 'bin') + os.pathsep + env['PATH']
    exe = '.exe' if os.name == 'nt' else ''
    bat = '.bat' if os.name == 'nt' else ''
    tools = sdk / 'build-tools/35.0.0'; jar = sdk / 'platforms/android-34/android.jar'
    classes = out / 'classes'; classes.mkdir()
    dex = out / 'dex'; dex.mkdir()
    source = ROOT / 'tests/android/dashboard-ui/UiAuditInstrumentation.java'
    execute([java / ('bin/javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
             '-classpath', jar, '-d', classes, source], env)
    execute([tools / ('d8' + bat), '--min-api', '26', '--lib', jar, '--output', dex,
             *sorted(classes.rglob('*.class'))], env)
    key = out / 'ui-fixture.jks'
    execute([java / ('bin/keytool' + exe), '-genkeypair', '-noprompt', '-alias', 'fixture',
             '-keyalg', 'RSA', '-keysize', '2048', '-validity', '7', '-keystore', key,
             '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=Disposable UI Test'], env)
    manifest = out / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{HARNESS}">
      <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="33"/>
      <application android:label="Disposable Dashboard UI Test"/>
      <instrumentation android:name="{HARNESS}.UiAuditInstrumentation" android:targetPackage="{PACKAGE}" android:functionalTest="true"/>
    </manifest>''', encoding='utf-8')
    raw = out / 'harness-raw.apk'
    execute([tools / ('aapt2' + exe), 'link', '-o', raw, '-I', jar, '--manifest', manifest], env)
    with zipfile.ZipFile(raw, 'a') as archive:
        archive.write(dex / 'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_STORED)
    aligned = out / 'harness-aligned.apk'
    execute([tools / ('zipalign' + exe), '-f', '4', raw, aligned], env)
    for inputfile, name in [(aligned, 'ui-audit.apk'), (args.unsigned_apk, 'product-test.apk')]:
        execute([tools / ('apksigner' + bat), 'sign', '--ks', key, '--ks-key-alias', 'fixture',
                 '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', out / name, inputfile], env)
        execute([tools / ('apksigner' + bat), 'verify', out / name], env)
    (out / 'provenance.json').write_text(json.dumps({
        'unsignedSha256': hashlib.sha256(args.unsigned_apk.read_bytes()).hexdigest(),
        'instrumentationSha256': hashlib.sha256(source.read_bytes()).hexdigest(),
        'productionSigning': False}, indent=2), encoding='utf-8')


def nodes(node):
    yield node
    for child in node.get('children', []):
        yield from nodes(child)


def assess(data):
    captures = {c['name']: c for c in data['captures']}
    base = captures['dashboard']; density = base['density']
    tabs = [n['focus'] for n in data['navigation'] if n['key'] == 'TAB']
    expected_stops = 4 if data['activationGatePresent'] else 7
    navigation = (data['tabStops'] == expected_stops and len(tabs) == expected_stops + 1
                  and len(set(tabs)) == expected_stops and tabs[0] == tabs[-1])
    navigation &= all(any(n.get('text') == title for n in nodes(captures[name]['accessibility']))
                      for name, title in [('enter-diagnostics', 'App diagnostics'),
                                          ('settings-keyboard', 'Updates'), ('update-dialog', 'Update available')])
    navigation &= any(n['key'] == 'SPACE_RESULT' and n['openedDiagnostics'] for n in data['navigation'])
    actions = [v for v in base['views'] if v.get('clickable')]
    touch = len(actions) == 7 and all(min(v['width'], v['height']) / density >= 47.5 for v in actions)
    text = all(not v.get('verticalTextClipped') and not v.get('ellipsisCharacters')
               and v.get('maxLineWidth', 0) <= v.get('availableWidth', 0) + 1
               for c in captures.values() for v in c.get('views', []))
    dialog_tree = captures['enter-diagnostics']['accessibility']
    window = list(map(int, re.findall(r'-?\d+', dialog_tree.get('bounds', ''))))
    buttons = {str(n.get('text', '')).lower(): n for n in nodes(dialog_tree)
               if n.get('class') == 'android.widget.Button'}
    diagnostics = len(window) == 4
    for label in ('close', 'save report', 'sleep trial'):
        node = buttons.get(label, {})
        bounds = list(map(int, re.findall(r'-?\d+', node.get('bounds', ''))))
        diagnostics &= bool(node.get('visible')) and len(bounds) == 4
        if len(bounds) == 4:
            left, top, right, bottom = bounds
            diagnostics &= bottom - top >= 47.5 * density
            if len(window) == 4:
                diagnostics &= left >= window[0] and top >= window[1]
                diagnostics &= right <= window[2] and bottom <= window[3]
    diagnostics &= any(n['key'] == 'SLEEP_ENTRY_RESULT' and n['openedSleepEntry'] for n in data['navigation'])
    checks = {'navigation': bool(navigation), 'touch-targets': bool(touch),
              'dashboard-text': bool(text), 'diagnostics-actions': bool(diagnostics)}
    if data['activationGatePresent']:
        switches = [v for v in base['views'] if v.get('class') == 'Switch']
        checks['activation-pending-controls'] = (len(switches) == 3
            and all(not v['enabled'] for v in switches)
            and any(v.get('text') == 'RESTART REQUIRED' for v in base['views']))
    return checks


def run(args):
    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise RuntimeError('Refusing a non-emulator serial')
    output = external(args.output); output.mkdir(parents=True, exist_ok=True)
    adb = args.sdk / ('platform-tools/adb.exe' if os.name == 'nt' else 'platform-tools/adb')
    def call(*cmd):
        return execute([adb, '-P', args.adb_port, '-s', args.serial, *cmd])
    if call('emu', 'avd', 'name').splitlines()[0].strip() != args.avd:
        raise RuntimeError('AVD identity mismatch')
    if call('shell', 'getprop', 'ro.build.version.sdk').strip() != '33':
        raise RuntimeError('API 33 required')
    if 'not found' not in call('shell', 'service', 'check', 'PServerBinder'):
        raise RuntimeError('Refusing an emulator with a vendor bridge')
    call('shell', 'svc', 'wifi', 'disable'); call('shell', 'svc', 'data', 'disable')
    call('shell', 'settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
    for package in (HARNESS, PACKAGE):
        try: call('uninstall', package)
        except RuntimeError: pass
    for name in ('product-test.apk', 'ui-audit.apk'):
        call('install', '--no-incremental', args.build / name)
    results = []
    try:
        for scale in (args.font_scale or (100, 150, 200)):
            call('shell', 'settings', 'put', 'system', 'font_scale', str(scale / 100))
            call('shell', 'am', 'force-stop', PACKAGE)
            log = call('shell', 'am', 'instrument', '-w', '-e', 'prefix', f'scale{scale}',
                       HARNESS + '/' + HARNESS + '.UiAuditInstrumentation')
            (output / f'scale{scale}-instrumentation.log').write_text(log, encoding='utf-8')
            if 'result=PASS' not in log:
                raise AssertionError('UI collector did not finish: ' + '\n'.join(
                    line for line in log.splitlines() if 'INSTRUMENTATION_RESULT:' in line))
            remote = f'/sdcard/Android/data/{PACKAGE}/files/scale{scale}'
            path = output / f'scale{scale}-results.json'
            call('pull', remote + '-results.json', path)
            data = json.loads(path.read_text(encoding='utf-8'))
            if abs(data['captures'][0]['fontScale'] - scale / 100) > .01:
                raise AssertionError('Requested font scale was not applied to the Activity')
            for capture in data['captures']:
                call('pull', remote + '-' + capture['name'] + '.png', output / f"scale{scale}-{capture['name']}.png")
            checks = assess(data)
            for name, passed in checks.items():
                results.append({'fontScale': scale / 100, 'case': name, 'result': 'PASS' if passed else 'FAIL'})
                print(('PASS' if passed else 'FAIL') + f': dashboard {name} at {scale}%', flush=True)
    finally:
        call('shell', 'settings', 'put', 'system', 'font_scale', '1.0')
        call('shell', 'am', 'force-stop', PACKAGE)
        (output / 'results.json').write_text(json.dumps(results, indent=2), encoding='utf-8')
    expected_cases = 5 if data['activationGatePresent'] else 4
    if len(results) != expected_cases * len(args.font_scale or (100, 150, 200)) or any(r['result'] != 'PASS' for r in results):
        raise AssertionError('Dashboard native UI acceptance checks failed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--java', type=Path)
    parser.add_argument('--unsigned-apk', type=Path)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--build-only', action='store_true')
    parser.add_argument('--serial'); parser.add_argument('--avd')
    parser.add_argument('--adb-port', type=int, default=5037)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--font-scale', type=int, choices=(100, 150, 200), action='append',
                        help='Select a focused scale; default is all three')
    args = parser.parse_args()
    if args.java:
        if not args.unsigned_apk: parser.error('Building requires --unsigned-apk')
        build(args)
    if not args.build_only:
        if not args.serial or not args.avd or not args.output:
            parser.error('Running requires an explicitly owned serial, AVD and output directory')
        run(args)


if __name__ == '__main__': main()
