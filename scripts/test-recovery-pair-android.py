#!/usr/bin/env python3
"""Exercise the frozen unsigned pair using one disposable key and a fresh API33 AVD."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'com.thor.displaypowertest'
HARNESS = 'org.jestylabs.thorfix.recoveryaudit'
EXPECTED = {
    'candidate': 'c12a26ab7d4993c4dfbf85b9cb0b416b01280ffe4428f0c4a60811d7bb9f10df',
    'recovery': '5d1a6657f816d6edacf69a1927ff411cf71bb98ed2f4ddfd73c2203c164bd386',
}

def execute(command, env=None, allowed=(0,)):
    result = subprocess.run([str(x) for x in command], env=env, capture_output=True,
                            text=True, encoding='utf-8', errors='replace', timeout=120)
    if result.returncode not in allowed:
        raise RuntimeError(f'{Path(str(command[0])).name}: {result.stdout[:1000]} {result.stderr[:1000]}')
    return result.stdout + result.stderr

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def payload(path):
    with zipfile.ZipFile(path) as z:
        assert z.testzip() is None
        return {n: hashlib.sha256(z.read(n)).hexdigest() for n in z.namelist()
                if not n.endswith('/') and not re.fullmatch(r'META-INF/(MANIFEST\.MF|.*\.(SF|RSA|DSA|EC))', n, re.I)}

def inputs(args):
    for role in EXPECTED:
        assert digest(getattr(args, role)) == EXPECTED[role], f'Wrong frozen CI input: {role}'

def tools(args):
    exe, bat = ('.exe', '.bat') if os.name == 'nt' else ('', '')
    return args.sdk / 'build-tools/35.0.0', args.java / 'bin', exe, bat

def build(args):
    inputs(args)
    out = args.work.resolve()
    if out.is_relative_to(ROOT) or (out.exists() and any(out.iterdir())):
        raise RuntimeError('Use a fresh private work directory outside the checkout')
    out.mkdir(parents=True)
    sdk, java, exe, bat = tools(args)
    env = dict(os.environ, JAVA_HOME=str(args.java), RECOVERY_PAIR_TEST_PASS=secrets.token_hex(24))
    env['PATH'] = str(java) + os.pathsep + env['PATH']
    key = out / 'disposable-pair.p12'
    classes, dex = out / 'classes', out / 'dex'
    classes.mkdir(); dex.mkdir()
    jar = args.sdk / 'platforms/android-34/android.jar'
    source = ROOT / 'tests/android/recovery-pair/RecoveryRetentionInstrumentation.java'
    execute([java / ('javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
             '-classpath', jar, '-d', classes, source], env)
    execute([sdk / ('d8' + bat), '--min-api', '26', '--lib', jar, '--output', dex,
             *sorted(classes.rglob('*.class'))], env)
    manifest = out / 'AndroidManifest.xml'
    manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{HARNESS}" android:versionCode="1" android:versionName="1">
      <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="33"/>
      <application android:label="Disposable recovery audit" android:debuggable="true"/>
      <instrumentation android:name="{HARNESS}.RecoveryRetentionInstrumentation" android:targetPackage="{PACKAGE}"/>
    </manifest>''', encoding='utf-8')
    base, aligned = out / 'audit-base.apk', out / 'audit-aligned.apk'
    execute([sdk / ('aapt2' + exe), 'link', '-o', base, '--manifest', manifest, '-I', jar], env)
    with zipfile.ZipFile(base, 'a') as z:
        z.write(dex / 'classes.dex', 'classes.dex')
    execute([sdk / ('zipalign' + exe), '-f', '4', base, aligned], env)
    signers, checks = [], {}
    try:
        execute([java / ('keytool' + exe), '-genkeypair', '-noprompt', '-alias', 'fixture',
                 '-keyalg', 'RSA', '-keysize', '2048', '-validity', '7', '-storetype', 'PKCS12',
                 '-keystore', key, '-storepass:env', 'RECOVERY_PAIR_TEST_PASS',
                 '-keypass:env', 'RECOVERY_PAIR_TEST_PASS', '-dname', 'CN=Disposable Recovery Pair Test'], env)
        for role, original in [('candidate', args.candidate), ('recovery', args.recovery), ('audit', aligned)]:
            signed = out / (role + '-test.apk')
            execute([sdk / ('apksigner' + bat), 'sign', '--ks', key, '--ks-key-alias', 'fixture',
                     '--ks-pass', 'env:RECOVERY_PAIR_TEST_PASS', '--key-pass', 'env:RECOVERY_PAIR_TEST_PASS',
                     '--out', signed, original], env)
            output = execute([sdk / ('apksigner' + bat), 'verify', '--verbose', '--print-certs', signed], env)
            match = re.search(r'Signer #1 certificate SHA-256 digest: ([0-9a-fA-F]+)', output)
            assert match, 'Missing verified test signer'
            signers.append(match.group(1).lower())
            assert payload(original) == payload(signed), f'Payload changed by test signing: {role}'
            checks[role] = {'signedPayloadUnchanged': True, 'signatureVerified': True}
        assert len(set(signers)) == 1, 'Test certificates differ'
    finally:
        if key.exists():
            key.unlink()
        env.pop('RECOVERY_PAIR_TEST_PASS', None)
    inputs(args)
    (out / 'build-summary.json').write_text(json.dumps({
        'frozenUnsignedHashes': EXPECTED, 'originalsPreserved': True,
        'sameDisposableSigner': True, 'privateKeyRemoved': not key.exists(), 'apkChecks': checks,
        'productionSigning': False, 'instrumentationSourceSha256': digest(source)}, indent=2), encoding='utf-8')
    print('PASS coherent disposable signing; originals and payloads unchanged; private key removed')

def run(args):
    inputs(args)
    if not re.fullmatch(r'emulator-\d+', args.serial):
        raise RuntimeError('Refusing non-emulator serial')
    adb = args.sdk / ('platform-tools/adb.exe' if os.name == 'nt' else 'platform-tools/adb')
    def call(*command, allowed=(0,)):
        return execute([adb, '-s', args.serial, *command], allowed=allowed)
    assert call('emu', 'avd', 'name').splitlines()[0].strip() == args.avd, 'Wrong disposable AVD'
    assert call('shell', 'getprop', 'ro.build.version.sdk').strip() == '33', 'API33 required'
    assert call('shell', 'getprop', 'ro.product.cpu.abi').strip() == 'x86_64', 'Generic x86_64 required'
    assert 'not found' in call('shell', 'service', 'check', 'PServerBinder'), 'Vendor bridge must be absent'
    assert not call('shell', 'pm', 'path', PACKAGE, allowed=(0, 1)).strip(), 'Target must be absent on fresh AVD; never clear/uninstall it'
    assert not call('shell', 'pm', 'path', HARNESS, allowed=(0, 1)).strip(), 'Harness already installed'
    call('shell', 'svc', 'wifi', 'disable'); call('shell', 'svc', 'data', 'disable')
    call('shell', 'settings', 'put', 'secure', 'immersive_mode_confirmations', 'confirmed')
    nonce, cases = secrets.token_hex(16), []
    def instrument(mode, version='1.7.2-recovery', code=79, strict=False, expected_error=None):
        call('shell', 'am', 'force-stop', PACKAGE)
        output = call('shell', 'am', 'instrument', '-w', '-e', 'mode', mode,
                      '-e', 'nonce', nonce, '-e', 'version', version, '-e', 'code', str(code),
                      '-e', 'strictXml', str(strict).lower(), HARNESS + '/' + HARNESS + '.RecoveryRetentionInstrumentation')
        private = args.work / ('instrument-' + str(len(cases)) + '-' + mode + '.txt')
        private.write_text(output, encoding='utf-8')
        if expected_error:
            assert re.search(r'^INSTRUMENTATION_RESULT: result=FAIL\r?$', output, re.M) and expected_error in output, 'Negative control failed to detect its specific mutation'
        else:
            assert re.search(r'^INSTRUMENTATION_RESULT: result=PASS\r?$', output, re.M), f'Instrumentation {mode} failed: {output[:1500]}'
        cases.append({'mode': mode, 'expectedFailureObserved': bool(expected_error), 'status': 'PASS'})
        print('PASS ' + mode + (' negative oracle' if expected_error else ''))
    def snapshot(mode, name):
        path = args.work / name
        call('pull', '/sdcard/Android/data/' + PACKAGE + '/files/recovery-pair-' + mode + '.json', path)
        return json.loads(path.read_text(encoding='utf-8'))
    assert 'Success' in call('install', '--no-incremental', args.work / 'candidate-test.apk')
    assert 'Success' in call('install', '--no-incremental', args.work / 'audit-test.apk')
    instrument('seed', '1.7.2-rc1', 78)
    before = snapshot('seed', 'before.json')
    # Independent Android installer transport, with replacement and no uninstall/data clear.
    call('shell', 'am', 'force-stop', PACKAGE)
    assert 'Success' in call('install', '--no-incremental', '-r', args.work / 'recovery-test.apk')
    instrument('verify', strict=True)
    after = snapshot('verify', 'after-install.json')
    assert before['preferences'] == after['preferences'] and before['privateDataSha256'] == after['privateDataSha256']
    assert after['code'] == 79 and after['version'] == '1.7.2-recovery'
    instrument('open')
    reopened = snapshot('open', 'after-reopen.json')
    assert reopened['preferences'] == before['preferences'] and reopened['privateDataSha256'] == before['privateDataSha256']
    instrument('tamper_pref'); instrument('verify', expected_error='PREF:updates.auto_check')
    instrument('restore_pref'); instrument('verify')
    instrument('tamper_data'); instrument('verify', expected_error='DATA:file hash')
    instrument('restore_data'); instrument('open')
    inputs(args)
    result = {'status': 'PASS', 'candidateCode': 78, 'recoveryCode': 79,
              'frozenUnsignedHashes': EXPECTED, 'originalsPreserved': True, 'sameDisposableSigner': True,
              'actualPreferenceCount': 7, 'actualPreferenceValues': after['preferences'],
              'preferenceValuesAndTypesRetained': True, 'preferenceXmlUnchangedDuringInstall': True,
              'privateFileRetained': True, 'uidPreserved': after['uidPreserved'],
              'firstInstallTimePreserved': after['firstInstallTimePreserved'], 'dataDirectoryPreserved': after['dataDirectoryPreserved'],
              'actualRecoveryActivityAndSettingsOpened': reopened['realActivityReopened'] and reopened['realUpdaterSettingsRead'],
              'independentInstaller': 'adb install -r on guarded disposable emulator',
              'uninstallOrClearTargetData': False, 'negativePreferenceOracle': True, 'negativeDataOracle': True,
              'stages': cases, 'vendorBridge': 'ABSENT', 'productionSigning': False, 'Thor': 'NOT RUN'}
    (args.work / 'RESULTS.json').write_text(json.dumps(result, indent=2), encoding='utf-8')
    print('RECOVERY_PAIR_RESULT ' + json.dumps(result, separators=(',', ':')))

if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ('sdk', 'java', 'candidate', 'recovery', 'work'):
        parser.add_argument('--' + name, type=Path, required=True)
    parser.add_argument('--serial', default='emulator-5554')
    parser.add_argument('--avd', default='pr67-recovery-pair-api33')
    action = parser.add_mutually_exclusive_group(required=True)
    action.add_argument('--build-only', action='store_true'); action.add_argument('--run-only', action='store_true')
    args = parser.parse_args()
    for name in ('sdk', 'java', 'candidate', 'recovery', 'work'):
        setattr(args, name, getattr(args, name).resolve())
    build(args) if args.build_only else run(args)
