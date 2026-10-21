#!/usr/bin/env python3
"""Actual Android transport contracts, confined to an owned disposable AVD."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import time
import uuid
import zipfile

sys.dont_write_bytecode = True
ROOT = Path(__file__).resolve().parents[1]
SENTINEL = 'FIXTURE_EXCLUSIVE_OWNER'
PACKAGE = 'com.thor.displaypowertest'
PRODUCTION = ('SecureChannel', 'IpcPeerPolicy', 'DaemonLaunchModel')
VARIANTS = ('plain', 'prelock', 'peer')
APPROVED_AVD = 'pr67-ci-api33'


class Failure(Exception):
    pass


def execute(argv, timeout=60):
    return subprocess.run([str(x) for x in argv], capture_output=True, text=True,
                          encoding='utf-8', errors='replace', timeout=timeout)


def external(path):
    path = path.resolve()
    if path.is_relative_to(ROOT):
        raise Failure('BUILD_OR_EVIDENCE_INSIDE_CHECKOUT')
    return path


def certify(serial, avd, call):
    """Only read-only checks; no root or write may precede their success."""
    if not isinstance(serial, str) or not re.fullmatch(r'emulator-[0-9]+', serial):
        raise Failure('NON_EMULATOR_SERIAL')
    if avd != APPROVED_AVD:
        raise Failure('UNAPPROVED_AVD_NAME')
    name = call(['emu', 'avd', 'name']).strip().splitlines()
    if not name or name[0].strip() != avd:
        raise Failure('FOREIGN_AVD')
    if call(['shell', 'getprop', 'ro.kernel.qemu']).strip() != '1':
        raise Failure('NOT_QEMU')
    if call(['shell', 'getprop', 'ro.build.version.sdk']).strip() != '33':
        raise Failure('WRONG_API')
    if call(['shell', 'getprop', 'ro.product.cpu.abi']).strip() != 'x86_64':
        raise Failure('WRONG_ABI')
    service = call(['shell', 'service', 'check', 'PServerBinder']).strip()
    if service != 'Service PServerBinder: not found':
        raise Failure('VENDOR_SERVICE_PRESENT_OR_UNKNOWN')
    return {'serial': serial, 'avd': avd, 'api': 33, 'abi': 'x86_64',
            'qemu': True, 'vendor_service': 'ABSENT'}


def build(args):
    out = external(args.build)
    if out.exists() and any(out.iterdir()):
        raise Failure('BUILD_NOT_FRESH')
    out.mkdir(parents=True, exist_ok=True)
    sdk = args.sdk.resolve()
    java = args.java.resolve()
    suffix = '.exe' if os.name == 'nt' else ''
    tools = sdk / 'build-tools/35.0.0'
    jar = sdk / 'platforms/android-34/android.jar'
    device_root = '/data/local/tmp/jtf-ipc-' + uuid.uuid4().hex[:8]
    production = {name: (ROOT / 'src/com/thor/displaypowertest' / (name + '.java'))
                  for name in PRODUCTION}
    fixture = ROOT / 'tests/android/secure-channel-native/NativeSecureChannelFixture.java'
    compiler = execute([java / ('bin/javac' + suffix), '-version'])
    compiler_version = (compiler.stdout + compiler.stderr).strip()
    if compiler.returncode or not re.fullmatch(r'javac 17(?:[.0-9+_-]*)(?:-LTS)?', compiler_version):
        raise Failure('JDK17_REQUIRED')
    revision = execute(['git', '-C', ROOT, 'rev-parse', 'HEAD'])
    if revision.returncode or not re.fullmatch(r'[0-9a-f]{40}', revision.stdout.strip()):
        raise Failure('BUILD_REVISION_UNAVAILABLE')
    original = production['SecureChannel'].read_text(encoding='utf-8')
    path_token = '"/data/user/0/com.thor.displaypowertest"'
    if original.count(path_token) != 1:
        raise Failure('PATH_ADAPTATION_TARGET')
    plain = original.replace(path_token, json.dumps(device_root + '/app'))
    lock_block = ('lock = lockStream.getChannel().tryLock();\n'
                  '            if (lock == null) throw new IOException("Daemon instance already locked");\n'
                  '            stampBootId(lockStream);')
    peer_check = 'if (peer == null || !IpcPeerPolicy.trustedDaemon(peer.getUid()))'
    if plain.count(lock_block) != 1 or plain.count(peer_check) != 1:
        raise Failure('MUTATION_TARGET_CHANGED')
    variants = {
        'plain': plain,
        'prelock': plain.replace(lock_block, 'stampBootId(lockStream);\n            '
                                 + lock_block.replace('\n            stampBootId(lockStream);', '')),
        'peer': plain.replace(peer_check, 'if (false)'),
    }
    manifest = {'device_root': device_root, 'production': {}, 'jars': {},
                'fixture_sha256': hashlib.sha256(fixture.read_bytes()).hexdigest(),
                'javac': compiler_version, 'workflow_revision': revision.stdout.strip(),
                'adaptation': 'one DATA_DIR literal in temporary copy only',
                'tier': 'native generic API33; not Thor/vendor/private-app SELinux'}
    for name, path in production.items():
        manifest['production'][name] = hashlib.sha256(path.read_bytes()).hexdigest()
    for variant, source in variants.items():
        work = out / variant
        work.mkdir()
        classes = work / 'classes'
        classes.mkdir()
        dex = work / 'dex'
        dex.mkdir()
        temporary = work / 'SecureChannel.java'
        temporary.write_text(source, encoding='utf-8')
        compiled = execute([java / ('bin/javac' + suffix), '-encoding', 'UTF-8',
                            '-source', '8', '-target', '8', '-cp', jar, '-d', classes,
                            temporary, production['IpcPeerPolicy'],
                            production['DaemonLaunchModel'], fixture])
        (work / 'compiler.txt').write_text(compiled.stdout + compiled.stderr, encoding='utf-8')
        if compiled.returncode:
            labels = re.findall(r'([A-Za-z]+\.java):([0-9]+): error: ([^\r\n]+)', compiled.stderr)
            print(json.dumps({'compiler_errors': labels[:8]}))
            raise Failure('NATIVE_COMPILATION_' + variant)
        d8 = tools / ('d8.bat' if os.name == 'nt' else 'd8')
        converted = execute([d8, '--min-api', '33', '--lib', jar, '--output', dex,
                             *sorted(classes.rglob('*.class'))])
        if converted.returncode:
            raise Failure('NATIVE_DEX_' + variant)
        target = out / (variant + '.jar')
        with zipfile.ZipFile(target, 'w', zipfile.ZIP_STORED) as archive:
            archive.write(dex / 'classes.dex', 'classes.dex')
        manifest['jars'][variant] = hashlib.sha256(target.read_bytes()).hexdigest()
    (out / 'provenance.json').write_text(json.dumps(manifest, indent=2), encoding='utf-8')
    print('NATIVE_IPC_BUILD: actual sources and both compiled controls built')


class NativeRun:
    def __init__(self, args):
        self.args = args
        self.adb = args.sdk / ('platform-tools/adb.exe' if os.name == 'nt' else 'platform-tools/adb')
        args.build = external(args.build)
        self.manifest = json.loads((args.build / 'provenance.json').read_text(encoding='utf-8'))
        self.path = self.manifest['device_root']
        if not isinstance(self.path, str) or not re.fullmatch(r'/data/local/tmp/jtf-ipc-[0-9a-f]{8}', self.path):
            raise Failure('UNSAFE_FIXTURE_PATH')
        self.validate_inputs()
        self.created = False
        self.holders = []
        self.results = []
        self.admission = []

    def validate_inputs(self):
        if (set(self.manifest.get('production', {})) != set(PRODUCTION)
                or set(self.manifest.get('jars', {})) != set(VARIANTS)):
            raise Failure('PROVENANCE_SCHEMA')
        for name in PRODUCTION:
            source = ROOT / 'src/com/thor/displaypowertest' / (name + '.java')
            if hashlib.sha256(source.read_bytes()).hexdigest() != self.manifest['production'][name]:
                raise Failure('PRODUCTION_SOURCE_DRIFT')
        source = ROOT / 'tests/android/secure-channel-native/NativeSecureChannelFixture.java'
        if hashlib.sha256(source.read_bytes()).hexdigest() != self.manifest['fixture_sha256']:
            raise Failure('FIXTURE_SOURCE_DRIFT')
        for variant in VARIANTS:
            jar = self.args.build / (variant + '.jar')
            if (jar.resolve().parent != self.args.build
                    or hashlib.sha256(jar.read_bytes()).hexdigest() != self.manifest['jars'][variant]):
                raise Failure('DEX_INPUT_DRIFT')

    def raw(self, argv, timeout=20):
        return execute([self.adb, '-s', self.args.serial, *argv], timeout)

    def call(self, argv):
        result = self.raw(argv)
        if result.returncode:
            raise Failure('ADB_READ_OR_FIXTURE_COMMAND')
        return result.stdout

    def shell(self, *argv):
        return self.call(['shell', shlex.join([str(x) for x in argv])])

    def command(self, variant, mode):
        return ['shell', shlex.join(['env', 'CLASSPATH=' + self.path + '/' + variant + '.jar',
                                    'app_process', '/', PACKAGE + '.NativeSecureChannelFixture',
                                    mode, str(self.uid)])]

    def fixture(self, variant, mode, marker):
        result = self.raw(self.command(variant, mode))
        if result.returncode or marker not in result.stdout:
            raise Failure('NATIVE_FIXTURE_' + mode)

    def wait_file(self, name, expected):
        deadline = time.monotonic() + 8
        while time.monotonic() < deadline:
            value = self.raw(['shell', shlex.join(['cat', self.path + '/' + name])])
            if value.returncode == 0 and value.stdout.strip() == expected:
                return
            time.sleep(0.1)
        raise Failure('NATIVE_MARKER_' + name)

    def holder(self, mode):
        self.shell('rm', '-f', self.path + '/ready', self.path + '/stop', self.path + '/bytes')
        process = subprocess.Popen([str(self.adb), '-s', self.args.serial,
                                    *self.command('plain', mode)], stdout=subprocess.PIPE,
                                   stderr=subprocess.PIPE)
        self.holders.append(process)
        self.wait_file('ready', 'APP_HOLDER' if mode == 'hold-app' else 'ROOT_HOLDER')
        return process

    def stop(self, process):
        self.shell('touch', self.path + '/stop')
        try:
            process.communicate(timeout=50)
        except subprocess.TimeoutExpired:
            raise Failure('FIXTURE_DID_NOT_SELF_EXIT')
        if process.returncode:
            raise Failure('FIXTURE_HOLDER_FAILED')

    def contender(self, variant, inode):
        self.fixture(variant, 'contend', 'LOCK_DENIED')
        lock = self.path + '/app/files/jesty-thor-control-v2.lock'
        if self.shell('cat', lock).strip() != SENTINEL:
            raise Failure('EXCLUSIVITY_CONTENT_MUTATION')
        if self.shell('stat', '-c', '%i', self.path + '/app/files/jesty-thor-control-v2.sock').strip() != inode:
            raise Failure('LIVE_SOCKET_REPLACED')

    def passed(self, case):
        self.results.append({'case': case, 'status': 'PASS'})
        print('NATIVE_IPC_CASE ' + json.dumps(self.results[-1]))

    def run(self):
        self.validate_inputs()
        self.admission.append(certify(self.args.serial, self.args.avd, self.call))
        # The certified AVD is the only target; no device enumeration or vendor call.
        if self.raw(['root']).returncode:
            raise Failure('EMULATOR_ROOT_UNAVAILABLE')
        self.call(['wait-for-device'])
        self.admission.append(certify(self.args.serial, self.args.avd, self.call))
        if self.shell('id', '-u').strip() != '0':
            raise Failure('EMULATOR_NOT_ROOT')
        packages = self.shell('cmd', 'package', 'list', 'packages', '-U', PACKAGE)
        match = re.search(r'^package:com\.thor\.displaypowertest uid:([0-9]+)\s*$', packages, re.M)
        if not match or int(match[1]) < 10000:
            raise Failure('DISPOSABLE_APP_UID_UNAVAILABLE')
        self.uid = int(match[1])
        existence = self.raw(['shell', 'test -e ' + shlex.quote(self.path)]).returncode
        if existence == 0:
            raise Failure('FIXTURE_PATH_ALREADY_EXISTS')
        if existence != 1:
            raise Failure('FIXTURE_PATH_STATE_UNKNOWN')
        self.shell('mkdir', '-p', self.path + '/app/files')
        self.created = True
        for path in (self.path, self.path + '/app', self.path + '/app/files'):
            self.shell('chown', str(self.uid) + ':' + str(self.uid), path)
            self.shell('chmod', '0700', path)
        for variant in VARIANTS:
            jar = self.args.build / (variant + '.jar')
            self.call(['push', str(jar), self.path + '/' + variant + '.jar'])
            self.shell('chmod', '0644', self.path + '/' + variant + '.jar')
        root = self.holder('hold-root')
        self.fixture('plain', 'connect-root', 'ROOT_PEER_ACCEPTED')
        self.passed('actual-root-peer-positive')
        inode = self.shell('stat', '-c', '%i', self.path + '/app/files/jesty-thor-control-v2.sock').strip()
        self.contender('plain', inode)
        self.passed('native-held-lock-preserves-content-and-socket')
        try:
            self.contender('prelock', inode)
        except Failure as error:
            if str(error) != 'EXCLUSIVITY_CONTENT_MUTATION':
                raise
            self.passed('compiled-prelock-write-control-detected')
        else:
            raise Failure('PRELOCK_CONTROL_NOT_DETECTED')
        self.stop(root)
        self.shell('test', '-S', self.path + '/app/files/jesty-thor-control-v2.sock')
        if self.shell('stat', '-c', '%i', self.path + '/app/files/jesty-thor-control-v2.sock').strip() != inode:
            raise Failure('STALE_SOCKET_PRECONDITION_MISSING')
        self.fixture('plain', 'recover', 'STALE_RECOVERED')
        self.passed('native-process-exit-and-stale-recovery')
        app = self.holder('hold-app')
        self.fixture('plain', 'reject-app', 'UNTRUSTED_PEER_REJECTED')
        self.wait_file('bytes', '0')
        self.passed('actual-wrong-uid-peer-denied-before-command')
        mutant = self.raw(self.command('peer', 'reject-app'))
        if mutant.returncode == 0 or 'CONTRACT|UNTRUSTED_PEER_ACCEPTED' not in mutant.stdout:
            raise Failure('PEER_CONTROL_NOT_DETECTED')
        self.passed('compiled-peer-bypass-control-detected')
        self.stop(app)

    def cleanup(self):
        for holder in self.holders:
            if holder.poll() is None:
                self.stop(holder)
        if self.created:
            # Unique directory was absent before this certified emulator run.
            self.shell('rm', '-rf', self.path)


def run_and_record(runner, output):
    """A completed case is not an overall PASS until owned cleanup succeeds."""
    failure = cleanup_failure = None
    try:
        runner.run()
    except BaseException as error:
        failure = error
    finally:
        try:
            runner.cleanup()
        except BaseException as error:
            cleanup_failure = error
        errors = []
        for phase, error in (('run', failure), ('cleanup', cleanup_failure)):
            if error is not None:
                code = str(error) if isinstance(error, Failure) else type(error).__name__
                if not re.fullmatch(r'[A-Za-z0-9_]+', code):
                    code = 'UNCLASSIFIED'
                errors.append({'phase': phase, 'category': code})
        (output / 'results.json').write_text(json.dumps({
            'result': 'FAIL' if errors else 'PASS', 'cases': runner.results,
            'errors': errors, 'cleanup': 'FAIL' if cleanup_failure else 'PASS',
            'production': runner.manifest['production'],
            'fixture_sha256': runner.manifest['fixture_sha256'],
            'jars': runner.manifest['jars'],
            'admission': getattr(runner, 'admission', []),
            'source_head': safe_revision(os.environ.get('SOURCE_HEAD', '')),
            'workflow_revision': safe_revision(runner.manifest.get('workflow_revision', '')),
            'javac': (runner.manifest.get('javac') if re.fullmatch(
                r'javac 17[.0-9+_-]*(?:-LTS)?', str(runner.manifest.get('javac', ''))) else 'UNKNOWN'),
            'compile_api': 34, 'build_tools': '35.0.0',
            'tier': 'native generic Android API33, fixture filesystem path; Thor NOT RUN',
            'productionChanges': False, 'signing': False}, indent=2), encoding='utf-8')
    if failure is not None:
        raise failure
    if cleanup_failure is not None:
        raise cleanup_failure
    print('NATIVE_IPC: 4 native contracts and 2 compiled controls PASS; cleanup PASS; Thor NOT RUN')


def safe_revision(value):
    return value if isinstance(value, str) and re.fullmatch(r'[0-9a-f]{40}', value) else 'UNKNOWN'


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', required=True, type=Path)
    parser.add_argument('--java', type=Path, default=Path(os.environ.get('JAVA_HOME', '.')))
    parser.add_argument('--build', required=True, type=Path)
    parser.add_argument('--build-only', action='store_true')
    parser.add_argument('--serial')
    parser.add_argument('--avd')
    parser.add_argument('--output', type=Path)
    args = parser.parse_args()
    if args.build_only:
        build(args)
        return
    if not args.serial or not args.avd or not args.output:
        raise Failure('EXPLICIT_AVD_AND_OUTPUT_REQUIRED')
    output = external(args.output)
    if output.exists():
        raise Failure('EVIDENCE_NOT_FRESH')
    runner = NativeRun(args)
    output.mkdir(parents=True)
    run_and_record(runner, output)


if __name__ == '__main__':
    try:
        main()
    except Failure as error:
        print('NATIVE_IPC_FAIL ' + str(error))
        raise SystemExit(1)
    except Exception as error:
        print('NATIVE_IPC_FAIL ' + type(error).__name__)
        raise SystemExit(1)
