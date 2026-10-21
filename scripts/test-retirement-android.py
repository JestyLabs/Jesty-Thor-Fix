"""Installed-app JNI retirement on an owned API33 emulator; no vendor/Thor claim.

Two fixed supervisor operations replace only unavailable vendor submission.
The real app/controller/native helper, frozen producers/server and activation
record/renderer execute. Synthetic panel/CPU/boot boundaries never touch hardware.
Parent wait on owned children independently confirms exit before successor launch.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import re
import secrets
import shlex
import shutil
import subprocess
import time
import zipfile

ROOT = Path(__file__).resolve().parents[1]
PACKAGE = 'org.example.rfixture'
AVD = 'pr67-ci-api33'
VERSIONS = ('1.7.1', '1.7.2', '1.7.3', '1.7.4')
RECOVERY_ORIGIN = '1.7.4'
EXPECTED_CASE_COUNT = 20

def expected_cases():
    return {(purpose, version, support, mode)
            for version in VERSIONS for support in ('native', 'unavailable') for mode in ('both', 'top')
            for purpose in (('update', 'recovery') if version == RECOVERY_ORIGIN else ('update',))}

def validate_origins(records):
    origins = [record['predecessor'] for record in records]
    if len(origins) != len(VERSIONS) or set(origins) != set(VERSIONS):
        raise ValueError('Frozen provenance must contain every expected origin exactly once')

def validate_results(results):
    expected = expected_cases()
    actual = [(case['purpose'], case['fixture_origin'], case['support'], case['mode']) for case in results]
    if (len(expected) != EXPECTED_CASE_COUNT or len(actual) != EXPECTED_CASE_COUNT
            or len(set(actual)) != len(actual) or set(actual) != expected
            or any(case['result'] != 'PASS' for case in results)):
        raise ValueError('Android PASS requires the complete unique expected retirement matrix')

def execute(command, timeout=120, check=True):
    p = subprocess.run(list(map(str, command)), stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                       text=True, encoding='utf-8', errors='replace', timeout=timeout)
    if check and p.returncode:
        raise RuntimeError(Path(str(command[0])).name + ': ' + p.stdout)
    return p

def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def remove_disposable_package(adb_call):
    result = adb_call('uninstall', PACKAGE)
    if result.stdout.strip() != 'Success':
        raise RuntimeError('Disposable package uninstall was not confirmed')
    remaining = adb_call('shell', 'pm', 'list', 'packages', PACKAGE)
    if remaining.stdout.strip():
        raise RuntimeError('Disposable package absence was not confirmed')

def build(args):
    out = args.build.resolve()
    if out.is_relative_to(ROOT) or (out.exists() and any(out.iterdir())):
        raise ValueError('A fresh external build directory is required')
    out.mkdir(parents=True)
    sdk, java = args.sdk.resolve(), args.java.resolve()
    tools = sdk / 'build-tools/35.0.0'
    android = sdk / 'platforms/android-34/android.jar'
    compiler = sdk / 'ndk/27.2.12479018/toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android33-clang'
    if not compiler.is_file():
        raise ValueError('Pinned Linux NDK missing; no installation performed by this runner')
    key = out / 'disposable.jks'
    execute([java / 'bin/keytool', '-genkeypair', '-noprompt', '-alias', 'fixture', '-keyalg', 'RSA',
             '-keysize', '2048', '-validity', '7', '-keystore', key, '-storepass', 'android',
             '-keypass', 'android', '-dname', 'CN=Disposable Retirement Test'])
    spec = importlib.util.spec_from_file_location('retirement_materializer', ROOT / 'tests/android/retirement/materialize.py')
    materializer = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(materializer)
    records = []
    for version in VERSIONS:
        trial = out / version
        trial.mkdir()
        # Materializer owns a new temporary source directory under the checkout;
        # move the generated copies outside before compiling or retaining artifacts.
        generated = ROOT / ('.owner-retirement-source-' + secrets.token_hex(8))
        try:
            materializer.materialize(version, generated)
            shutil.copytree(generated, trial / 'source')
        finally:
            if generated.exists():
                if generated.parent != ROOT or not generated.name.startswith('.owner-retirement-source-'):
                    raise ValueError('Unexpected temporary source path')
                shutil.rmtree(generated)
        source = trial / 'source'
        for kind in ('predecessor', 'candidate'):
            classes = trial / (kind + '-classes')
            classes.mkdir()
            sources = sorted((source / kind / 'src').rglob('*.java'))
            argfile = trial / (kind + '.args')
            argfile.write_text(''.join('"' + p.as_posix() + '"\n' for p in sources), encoding='utf-8')
            execute([java / 'bin/javac', '-encoding', 'UTF-8', '-source', '8', '-target', '8',
                     '-classpath', android, '-d', classes, '@' + str(argfile)])
            jar = trial / (kind + '.jar')
            execute([java / 'bin/jar', 'cf', jar, '-C', classes, '.'])
            dex = trial / (kind + '-dex')
            dex.mkdir()
            execute([tools / 'd8', '--min-api', '26', '--lib', android, '--output', dex, jar])
        old = trial / 'predecessor-dex.jar'
        with zipfile.ZipFile(old, 'w') as z:
            z.write(trial / 'predecessor-dex/classes.dex', 'classes.dex')
        native = trial / 'libretirement.so'
        execute([compiler, '-std=c11', '-Wall', '-Wextra', '-Werror', '-O2', '-fPIC', '-shared',
                 '-pthread', '-fvisibility=hidden', '-Wl,--no-undefined', '-Wl,-z,relro,-z,now',
                 source / 'candidate/native/retirement.c', '-o', native])
        raw = trial / 'raw.apk'
        execute([tools / 'aapt2', 'link', '-o', raw, '-I', android,
                 '--manifest', source / 'candidate/AndroidManifest.xml'])
        for support in ('native', 'unavailable'):
            apk = trial / (support + '-raw.apk')
            shutil.copyfile(raw, apk)
            with zipfile.ZipFile(apk, 'a') as z:
                z.write(trial / 'candidate-dex/classes.dex', 'classes.dex', compress_type=zipfile.ZIP_STORED)
                if support == 'native':
                    z.write(native, 'lib/x86_64/libretirement.so', compress_type=zipfile.ZIP_STORED)
            aligned = trial / (support + '-aligned.apk')
            execute([tools / 'zipalign', '-f', '4', apk, aligned])
            signed = trial / (support + '.apk')
            execute([tools / 'apksigner', 'sign', '--ks', key, '--ks-key-alias', 'fixture',
                     '--ks-pass', 'pass:android', '--key-pass', 'pass:android', '--out', signed, aligned])
            execute([tools / 'apksigner', 'verify', signed])
        records.append({'predecessor': version, 'old_dex_sha256': digest(old),
                        'native_source_sha256': digest(source / 'candidate/native/retirement.c'),
                        'native_binary_sha256': digest(native), 'native_apk_sha256': digest(trial / 'native.apk'),
                        'unavailable_apk_sha256': digest(trial / 'unavailable.apk')})
    (out / 'provenance.json').write_text(json.dumps({'ndk': '27.2.12479018', 'api': 33, 'abi': 'x86_64',
         'production_signing': False, 'fixtures': records}, indent=2) + '\n', encoding='utf-8')
    print(f'RETIREMENT_ANDROID_BUILD: {len(VERSIONS)} frozen producers + actual JNI + disposable APKs built')

def run(args):
    if not re.fullmatch(r'emulator-[0-9]+', args.serial or '') or args.avd != AVD:
        raise ValueError('Only the explicit owned API33 emulator is permitted')
    out = args.build.resolve()
    if out.is_relative_to(ROOT):
        raise ValueError('Evidence must stay outside the checkout')
    (out / 'results.json').unlink(missing_ok=True)
    adb = args.sdk.resolve() / 'platform-tools/adb'
    def adb_call(*values, check=True):
        return execute([adb, '-s', args.serial, *values], check=check)
    def shell(command, check=True):
        return adb_call('shell', command, check=check).stdout.strip()
    def certify():
        if adb_call('emu', 'avd', 'name').stdout.strip().splitlines()[0] != AVD:
            raise ValueError('Foreign AVD')
        for prop, expected in [('ro.kernel.qemu', '1'), ('ro.build.version.sdk', '33'),
                               ('ro.product.cpu.abi', 'x86_64')]:
            if shell('getprop ' + prop) != expected:
                raise ValueError('Unapproved emulator configuration')
        if shell('service check PServerBinder') != 'Service PServerBinder: not found':
            raise ValueError('Vendor service must be absent in this generic fixture')
    certify()
    adb_call('root')
    adb_call('wait-for-device')
    certify()
    if shell('id -u') != '0':
        raise ValueError('Emulator root unavailable')
    boot = shell('cat /proc/sys/kernel/random/boot_id')
    provenance = json.loads((out / 'provenance.json').read_text(encoding='utf-8'))
    validate_origins(provenance['fixtures'])
    results = []
    def instrument(version, stage, support, mode, purpose):
        return [adb, '-s', args.serial, 'shell', 'am', 'instrument', '-w', '-r',
                '-e', 'owner_approved_disposable', 'true', '-e', 'predecessor', version,
                '-e', 'stage', stage, '-e', 'mode', mode, '-e', 'purpose', purpose,
                '-e', 'route', 'transport', '-e', 'expect',
                'unavailable' if support == 'unavailable' else 'success',
                PACKAGE + '/.RetirementFixtureInstrumentation']
    def wait_text(path, deadline=8):
        end = time.monotonic() + deadline
        while time.monotonic() < end:
            text = shell('cat ' + shlex.quote(path), check=False)
            if re.fullmatch(r'EXIT [0-9]+ [0-9]+', text):
                return text
            time.sleep(.05)
        raise RuntimeError('Owned child exit not observed')
    def owned_launch(remote, label, classpath, main, arguments):
        # Every argument comes from a fixed role or a validated hex nonce. No
        # untrusted command, PID or path from the application is executed.
        command = 'CLASSPATH=' + shlex.quote(classpath) + ' app_process / ' + main + ' ' + arguments
        script = command + ' >' + shlex.quote(remote + '/' + label + '.log') + ' 2>&1 & child=$!; '
        script += 'printf "%s" "$child" >' + shlex.quote(remote + '/' + label + '.pid') + '; '
        script += 'wait "$child"; code=$?; printf "EXIT %s %s" "$child" "$code" >' + shlex.quote(remote + '/' + label + '.exit')
        return subprocess.Popen([str(adb), '-s', args.serial, 'shell', 'sh', '-c', shlex.quote(script)],
                                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    for record in provenance['fixtures']:
        version = record['predecessor']
        purposes = ('update', 'recovery') if version == RECOVERY_ORIGIN else ('update',)
        for support, mode, purpose in ((support, mode, purpose) for support in ('native', 'unavailable')
                                       for mode in ('both', 'top') for purpose in purposes):
            trial = out / version
            apk = trial / (support + '.apk')
            if digest(apk) != record[support + '_apk_sha256'] or digest(trial / 'predecessor-dex.jar') != record['old_dex_sha256']:
                raise ValueError('Frozen fixture artifact changed')
            remote = '/data/local/tmp/retirement-' + secrets.token_hex(6)
            files = '/data/user/0/' + PACKAGE + '/files'
            children = []
            active = None
            try:
                adb_call('uninstall', PACKAGE, check=False)  # Only our certified disposable package.
                if 'Success' not in adb_call('install', str(apk)).stdout:
                    raise RuntimeError('Disposable fixture installation failed')
                prepared = execute(instrument(version, 'prepare', support, mode, purpose)).stdout
                if 'PREPARED_NOT_ACTIVATED' not in prepared or 'outcome=FAIL' in prepared:
                    raise RuntimeError('Fixture preparation failed: ' + prepared)
                app_owner = shell('stat -c %u:%g ' + shlex.quote(files))
                owner_match = re.fullmatch(r'([0-9]+):([0-9]+)', app_owner)
                if not owner_match or int(owner_match[1]) < 10000 or owner_match[1] != owner_match[2]:
                    raise RuntimeError('Disposable app directory ownership was not confirmed')
                shell('mkdir ' + shlex.quote(remote))
                adb_call('push', str(trial / 'predecessor-dex.jar'), remote + '/predecessor.jar')
                pm = shell('pm path ' + PACKAGE)
                if not re.fullmatch(r'package:/data/app/[^\r\n:]+/base.apk', pm):
                    raise RuntimeError('Expected one installed disposable base APK')
                installed = pm.removeprefix('package:')
                old_classpath = installed if purpose == 'recovery' else remote + '/predecessor.jar'
                old_args = '1 hold 0 0' if purpose == 'recovery' else '1 run 0 0'
                children.append(owned_launch(remote, 'old', old_classpath, 'D', old_args))
                time.sleep(.4)
                active = subprocess.Popen(list(map(str, instrument(version, 'activate', support, mode, purpose))),
                                          stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
                handled = set()
                until = time.monotonic() + 60
                while active.poll() is None and time.monotonic() < until:
                    for number in (1, 2):
                        if number in handled:
                            continue
                        request = shell('cat ' + shlex.quote(files + '/owner-request-' + str(number)), check=False)
                        operation = 'RECOVERY' if purpose == 'recovery' else 'HELPER'
                        match = re.fullmatch(operation + r' ([0-9a-f]{32})', request)
                        if number == 1 and match:
                            helper_args = match[1] + ' 100' + (' recovery' if purpose == 'recovery' else '')
                            children.append(owned_launch(remote, 'helper', installed, 'R', helper_args))
                        elif number == 2 and request == 'SUCCESSOR':
                            exited = wait_text(remote + '/old.exit')
                            if not exited.endswith(' 143'):
                                raise RuntimeError('Old child did not terminate by the product SIGTERM: ' + exited)
                            if not wait_text(remote + '/helper.exit').endswith(' 0'):
                                raise RuntimeError('Helper did not exit cleanly')
                            children.append(owned_launch(remote, 'successor', installed, 'D', '1 hold 0 0'))
                        else:
                            continue
                        # App-owned ack; a launch acknowledgement is not an exit receipt.
                        ack = files + '/owner-ack-' + str(number)
                        pending_ack = ack + '.pending'
                        shell('umask 077; printf "%s\\n" ' + shlex.quote(request) + ' >' + shlex.quote(pending_ack)
                              + ' && chown ' + app_owner + ' ' + shlex.quote(pending_ack)
                              + ' && mv ' + shlex.quote(pending_ack) + ' ' + shlex.quote(ack))
                        handled.add(number)
                    time.sleep(.02)
                output = active.communicate(timeout=5)[0]
                (trial / (support + '-' + mode + '-' + purpose + '-runtime.txt')).write_text(output, encoding='utf-8')
                expected = 'PASS_CLOSED_NATIVE_UNAVAILABLE' if support == 'unavailable' else 'PASS_TO_READY_NOT_PHYSICAL_ACCEPTANCE'
                if expected not in output or 'outcome=FAIL' in output:
                    logs = shell('cat ' + shlex.quote(remote) + '/*.log', check=False)
                    raise RuntimeError('Installed JNI trial failed: ' + output + '\n' + logs)
                if support == 'native':
                    if handled != {1, 2} or not wait_text(remote + '/successor.exit').endswith(' 0'):
                        raise RuntimeError('Successor cleanup or ordered launch not proved')
                else:
                    if handled:
                        raise RuntimeError('Unavailable native must not submit helper or successor')
                if shell('cat /proc/sys/kernel/random/boot_id') != boot:
                    raise RuntimeError('Kernel boot changed during no-reboot trial')
                results.append({'predecessor': version if purpose == 'update' else 'SAME_VERSION',
                                'fixture_origin': version, 'purpose': purpose,
                                'support': support, 'mode': mode, 'result': 'PASS',
                                'same_boot': True, 'vendor_bridge': 'NOT_TESTED',
                                'hardware_boot': 'SYNTHETIC', 'owned_exit_before_launch': support == 'native'})
                print('RETIREMENT_ANDROID_CASE: ' + purpose + ' ' + version + ' ' + support + ' ' + mode + ' PASS', flush=True)
            finally:
                shell('touch ' + shlex.quote(files + '/retirement-fixture-stop'), check=False)
                if active is not None and active.poll() is None:
                    active.communicate(timeout=10)
                for child in children:
                    child.wait(timeout=12)
                # Verify parent wait records before deleting owned fixture files.
                if children:
                    wait_text(remote + '/old.exit')
                shell('rm -rf ' + shlex.quote(remote))  # Fresh, regex-shaped task-owned emulator path only.
                remove_disposable_package(adb_call)
    validate_results(results)
    report = {'result': 'PASS', 'tier': 'ANDROID_API33_X86_64_INSTALLED_APP_REAL_JNI',
              'cases': results, 'vendor_binder': 'ABSENT_NOT_TESTED', 'physical': 'NOT_RUN',
              'cleanup': 'OWNED_CHILDREN_WAITED_AND_DISPOSABLE_PACKAGE_REMOVED', 'provenance': provenance}
    (out / 'results.json').write_text(json.dumps(report, indent=2) + '\n', encoding='utf-8')
    print(f'RETIREMENT_ANDROID: {len(results)} cases PASS; real JNI + frozen producer + exit + READY; vendor/physical NOT RUN')

def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--java', type=Path)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--build-only', action='store_true')
    parser.add_argument('--serial')
    parser.add_argument('--avd')
    args = parser.parse_args()
    if args.build_only:
        if args.java is None:
            parser.error('--java required for build')
        build(args)
    else:
        run(args)

if __name__ == '__main__':
    main()
