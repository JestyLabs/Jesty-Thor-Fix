#!/usr/bin/env python3
"""Build/run updater Android integration on an explicitly owned disposable AVD.

No production APK, certificate, vendor service or physical-device access. The
production updater sources are compiled unchanged; HTTPS transport is a fixture
adapter. Native PackageInstaller and system consent UI remain real.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET
import zipfile

PACKAGE = 'org.jestylabs.thorfix.updatere2e'
ACTIVITY = PACKAGE + '/com.thor.displaypowertest.UpdaterTestActivity'
SOURCES = ['AppUpdater', 'UpdateReleaseCatalog', 'UpdateInstallReceiver', 'UpdateVersion', 'UpdateCommitGate',
           'UpdateConfirmationStore', 'UpdateSessionOwnership', 'ReplacementSessionPolicy', 'UpdateTaskDispatch']
ROOT = Path(__file__).resolve().parents[1]


def execute(args, *, env=None, timeout=90):
    done = subprocess.run([str(a) for a in args], stdout=subprocess.PIPE,
                          stderr=subprocess.STDOUT, text=True, encoding='utf-8',
                          errors='replace', env=env, timeout=timeout)
    if done.returncode:
        raise RuntimeError(f'{Path(str(args[0])).name} failed ({done.returncode}): {done.stdout}')
    return done.stdout


def build(out, sdk, java):
    if out.is_relative_to(ROOT):
        raise RuntimeError('Disposable APKs, keys and build evidence must stay outside the checkout')
    out.mkdir(parents=True, exist_ok=True)
    env = dict(os.environ, JAVA_HOME=str(java))
    env['PATH'] = str(java / 'bin') + os.pathsep + env['PATH']
    windows = os.name == 'nt'
    exe = '.exe' if windows else ''
    bat = '.bat' if windows else ''
    tools = sdk / 'build-tools/35.0.0'
    jar = sdk / 'platforms/android-34/android.jar'
    classes = out / 'classes'
    dex = out / 'dex'
    classes.mkdir(exist_ok=True); dex.mkdir(exist_ok=True)
    src = [ROOT / f'src/com/thor/displaypowertest/{s}.java' for s in SOURCES]
    src.append(ROOT / 'tests/android/updater-e2e/UpdaterTestActivity.java')
    src.append(ROOT / 'tests/android/updater-e2e/UpdaterTestControlReceiver.java')
    src.append(ROOT / 'tests/android/updater-e2e/LocalTransport.java')
    src.append(ROOT / 'tests/android/updater-e2e/UpdateMetadataCharacterization.java')
    execute([java / ('bin/javac' + exe), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
             '-classpath', jar, '-d', classes, *src], env=env)
    execute([tools / ('d8' + bat), '--min-api', '23', '--lib', jar, '--output', dex,
             *sorted(classes.rglob('*.class'))], env=env)
    # Both certificates are freshly generated disposable fixtures, outside Git.
    for key in ('test', 'other'):
        keyfile = out / (key + '.jks')
        if keyfile.exists():
            raise RuntimeError('Use a fresh build output directory; never reuse an existing key')
        execute([java / ('bin/keytool' + exe), '-genkeypair', '-noprompt', '-alias', 'fixture',
                 '-keyalg', 'RSA', '-keysize', '2048', '-validity', '7', '-keystore', keyfile,
                 '-storepass', 'android', '-keypass', 'android', '-dname', 'CN=Disposable Updater Test'], env=env)
    for name, code, version, minimum, key in (
            ('base', 1, '1.0.0', 23, 'test'), ('candidate', 2, '1.0.1', 23, 'test'),
            ('incompatible', 2, '1.0.1', 100, 'test'), ('test-only', 2, '1.0.1', 23, 'test'),
            ('wrong-signer', 2, '1.0.1', 23, 'other')):
        manifest = out / (name + '.xml')
        manifest.write_text(f'''<manifest xmlns:android="http://schemas.android.com/apk/res/android"
            package="{PACKAGE}" android:versionCode="{code}" android:versionName="{version}">
          <uses-sdk android:minSdkVersion="{minimum}" android:targetSdkVersion="25"/>
          <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
          <uses-permission android:name="android.permission.INTERNET"/>
          <application android:label="Updater E2E Fixture" android:debuggable="true" android:allowBackup="false"
                       {'android:testOnly="true"' if name == 'test-only' else ''}
                       android:theme="@android:style/Theme.Material.Light">
            <activity android:name="com.thor.displaypowertest.UpdaterTestActivity" android:exported="true"
                      android:launchMode="singleTask"/>
            <receiver android:name="com.thor.displaypowertest.UpdateInstallReceiver" android:exported="false"/>
            <receiver android:name="com.thor.displaypowertest.UpdaterTestControlReceiver" android:exported="true"/>
          </application></manifest>''', encoding='utf-8')
        raw = out / (name + '-raw.apk')
        execute([tools / ('aapt2' + exe), 'link', '-o', raw, '-I', jar, '--manifest', manifest], env=env)
        with zipfile.ZipFile(raw, 'a') as archive:
            archive.write(dex / 'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_STORED)
        aligned = out / (name + '-aligned.apk')
        execute([tools / ('zipalign' + exe), '-f', '4', raw, aligned], env=env)
        execute([tools / ('apksigner' + bat), 'sign', '--ks', out / (key + '.jks'),
                 '--ks-key-alias', 'fixture', '--ks-pass', 'pass:android', '--key-pass', 'pass:android',
                 '--out', out / (name + '.apk'), aligned], env=env)
        execute([tools / ('apksigner' + bat), 'verify', out / (name + '.apk')], env=env)
    (out / 'source-manifest.json').write_text(json.dumps({
        str(p.relative_to(ROOT)): hashlib.sha256(p.read_bytes()).hexdigest() for p in src}, indent=2), encoding='utf-8')
    print('Built disposable fixture APKs using unchanged production updater sources', flush=True)


class Suite:
    def __init__(self, args):
        if not re.fullmatch(r'emulator-\d+', args.serial):
            raise RuntimeError('Refusing a non-emulator serial')
        self.args = args
        self.adb = args.sdk / ('platform-tools/adb.exe' if os.name == 'nt' else 'platform-tools/adb')
        self.output = args.output; self.output.mkdir(parents=True, exist_ok=True)
        if self.output.resolve().is_relative_to(ROOT):
            raise RuntimeError('Raw emulator evidence must stay outside the checkout')
        self.results = []
        name = self.call('emu', 'avd', 'name').splitlines()[0].strip()
        if name != args.avd:
            raise RuntimeError('AVD identity does not match the explicitly owned disposable AVD')
        if self.call('shell', 'getprop', 'ro.build.version.sdk').strip() != '33':
            raise RuntimeError('This suite requires Android 13/API 33')
        self.files = '/sdcard/Android/data/' + PACKAGE + '/files/'

    def call(self, *args):
        return execute([self.adb, '-P', str(self.args.adb_port), '-s', self.args.serial, *args])

    def command(self, mode, **extras):
        cmd = ['shell', 'am', 'start', '-W', '-n', ACTIVITY, '--es', 'mode', mode]
        for k, v in extras.items():
            cmd += ['--ei' if isinstance(v, int) else '--es', k, str(v)]
        self.call(*cmd)

    def state(self):
        return json.loads(self.call('shell', 'cat', self.files + 'state.json'))

    def replay_pending(self, session=-1):
        self.call('shell', 'am', 'broadcast', '-n', PACKAGE + '/com.thor.displaypowertest.UpdaterTestControlReceiver',
                  '--ei', 'session', str(session))

    def wait(self, condition, timeout=30):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            value = condition()
            if value: return value
            time.sleep(.3)
        raise AssertionError('Timed out waiting for Android state')

    def ui(self):
        self.call('shell', 'uiautomator', 'dump', '/sdcard/updater-e2e-ui.xml')
        xml = self.call('shell', 'cat', '/sdcard/updater-e2e-ui.xml')
        return ET.fromstring(xml[xml.index('<?xml'):])

    def click(self, text, package=None):
        def find():
            tree = self.ui()
            for node in tree.iter('node'):
                if node.get('text', '').lower() == text.lower() and (package is None or node.get('package') == package):
                    return node.get('bounds')
            return None
        bounds = self.wait(find)
        numbers = list(map(int, re.findall(r'\d+', bounds)))
        self.call('shell', 'input', 'tap', str((numbers[0]+numbers[2])//2), str((numbers[1]+numbers[3])//2))

    def fresh(self, fixture='candidate', scenario='normal', transport=None):
        # Reinstall base on this dedicated AVD only; no existing production app.
        # A previous failed test can leave the system installer UI above a removed
        # package. Clear that test UI before reinstalling with a different UID.
        self.call('shell', 'am', 'force-stop', 'com.android.packageinstaller')
        try: self.call('uninstall', PACKAGE)
        except RuntimeError: pass
        self.call('install', self.args.build / 'base.apk')
        if transport is not None:
            self.call('shell', 'mkdir', '-p', self.files)
            self.call('push', transport.certificate, self.files + 'transport-ca.pem')
            self.command('init', proxy_port=transport.port)
        else:
            self.command('init')
        self.call('shell', 'appops', 'set', PACKAGE, 'REQUEST_INSTALL_PACKAGES', 'allow')
        self.call('push', self.args.build / (fixture + '.apk'), self.files + 'candidate.apk')
        self.call('logcat', '-c')
        self.command('update', scenario=scenario)
        self.click('Update', PACKAGE); self.click('Download', PACKAGE)
        self.wait(lambda: self.state()['reservations'] >= 1)

    def installer(self):
        self.wait(lambda: 'com.android.packageinstaller' in self.call('shell', 'dumpsys', 'window') and
                  any(n.get('package') == 'com.android.packageinstaller' and n.get('text', '').lower() in ('cancel', 'update')
                      for n in self.ui().iter('node')))

    def settled(self, timeout=30):
        def check():
            state = self.state()
            return state if not state['transfer'] and not state['reserved'] and state['session'] == -1 and state['nativeSessions'] == 0 else False
        return self.wait(check, timeout=timeout)

    def case(self, name, action):
        if self.args.case and name not in self.args.case:
            return
        try:
            action()
            diagnostics = self.call('logcat', '-d', '-s', 'UpdaterE2E:V', 'AndroidRuntime:E', '*:S')
            assert 'TEST_ERROR' not in diagnostics and 'SNAPSHOT_ERROR' not in diagnostics and 'FATAL EXCEPTION' not in diagnostics
            self.results.append({'case': name, 'result': 'PASS'})
            print('PASS: ' + name, flush=True)
        except Exception as error:
            self.results.append({'case': name, 'result': 'FAIL', 'error': str(error)})
            raise
        finally:
            logs = self.call('logcat', '-d', '-v', 'threadtime', '-s', 'UpdaterE2E:I', 'ThorDisplayUpdate:V', 'AndroidRuntime:E', '*:S')
            (self.output / (name + '.log')).write_text(logs, encoding='utf-8')
            (self.output / 'results.json').write_text(json.dumps(self.results, indent=2), encoding='utf-8')

    def run(self):
        self.call('shell', 'settings', 'put', 'global', 'window_animation_scale', '0')
        self.call('shell', 'settings', 'put', 'global', 'transition_animation_scale', '0')
        self.call('shell', 'settings', 'put', 'global', 'animator_duration_scale', '0')
        def deny():
            self.fresh(); self.installer()
            self.click('Cancel', 'com.android.packageinstaller')
            state = self.settled(); assert state['version'] == '1.0.0'
        self.case('native-consent-denied', deny)
        def accept():
            self.fresh(); self.installer()
            old_pid = self.state()['pid']
            self.click('Update', 'com.android.packageinstaller')
            self.wait(lambda: "versionName=1.0.1" in self.call('shell', 'dumpsys', 'package', PACKAGE))
            self.command('init')
            state = self.settled(); assert state['version'] == '1.0.1' and state['pid'] != old_pid
        self.case('native-consent-accepted', accept)
        def cancel():
            self.fresh(scenario='slow'); self.click('Cancel', PACKAGE)
            state = self.settled(); assert state['version'] == '1.0.0' and state['launches'] == 0
        self.case('download-cancel-before-commit', cancel)
        for name, fixture, scenario, message in (
                ('checksum-failure', 'candidate', 'hash', 'checksum does not match'),
                ('http-failure', 'candidate', 'http_failure', 'HTTP 503'),
                ('signer-failure', 'wrong-signer', 'normal', 'not signed with this app'),
                ('archive-incompatible-failure', 'incompatible', 'normal', 'not a valid app'),
                ('native-test-only-failure', 'test-only', 'normal', 'update install failed')):
            def failure(fixture=fixture, scenario=scenario, message=message):
                self.fresh(fixture, scenario)
                if fixture == 'test-only':
                    self.installer(); self.click('Update', 'com.android.packageinstaller')
                state = self.settled(); assert state['version'] == '1.0.0'
                assert state['launches'] == (1 if fixture == 'test-only' else 0)
                assert message in self.call('logcat', '-d', '-s', 'ThorDisplayUpdate:V', '*:S')
            self.case(name, failure)
        def duplicate_terminal():
            self.fresh(); self.installer()
            # Observe owned session without bringing the test Activity to the foreground.
            # Android owns the terminal callback, then injected replays must stay inert.
            old_session = self.state()['session']; assert old_session >= 0
            self.click('Cancel', 'com.android.packageinstaller'); state = self.settled()
            self.command('sentinel')
            self.command('duplicate_terminal', session=old_session)
            self.command('unknown_terminal', session=123456789)
            self.wait(lambda: self.state()['replays'] == 4)
            state = self.state(); assert state['sentinel'] and state['session'] == -1
        self.case('terminal-and-unknown-replays-inert', duplicate_terminal)
        def stale_during_transfer():
            self.fresh(); self.installer()
            old_session = self.state()['session']
            self.click('Cancel', 'com.android.packageinstaller'); self.settled()
            self.command('update', scenario='slow')
            self.click('Update', PACKAGE); self.click('Download', PACKAGE)
            self.wait(lambda: self.state()['reservations'] == 2 and self.state()['transfer'])
            self.command('sentinel'); self.command('duplicate_terminal', session=old_session)
            self.wait(lambda: self.state()['replays'] == 2)
            state = self.state()
            assert state['transfer'] and state['reserved'] and state['sentinel']
            self.click('Cancel', PACKAGE); self.settled()
        self.case('old-terminal-preserves-new-transfer', stale_during_transfer)
        def duplicate_pending():
            self.fresh(); self.installer()
            initial = self.state(); assert initial['launches'] == 1
            self.replay_pending()
            self.wait(lambda: self.state()['replays'] == 1)
            state = self.state()
            assert state['launches'] == 1 and state['pending'] == -1
            assert state['session'] == initial['session'] and state['nativeSessions'] == 1
            self.installer(); self.click('Cancel', 'com.android.packageinstaller'); self.settled()
        self.case('pending-replay-after-launch-deduplicated', duplicate_pending)
        def foreground_replay():
            self.fresh(scenario='foreground_replay'); self.installer()
            state = self.state(); assert state['launchAttempts'] == 1 and state['launches'] == 1
            self.click('Cancel', 'com.android.packageinstaller'); self.settled()
        self.case('pending-replay-during-launch-deduplicated', foreground_replay)
        def background_confirmation():
            self.fresh(scenario='delayed')
            self.call('shell', 'input', 'keyevent', 'KEYCODE_HOME')
            self.wait(lambda: self.state()['pending'] >= 0)
            before = self.state(); assert before['launches'] == 0 and before['nativeSessions'] == 1
            self.replay_pending(); self.replay_pending()
            self.wait(lambda: self.state()['replays'] == 2)
            assert self.state()['pending'] == before['pending'] and self.state()['launches'] == 0
            self.command('init'); self.installer()
            assert self.state()['launches'] == 1
            self.click('Cancel', 'com.android.packageinstaller'); self.settled()
        self.case('background-confirmation-deferred-until-resume', background_confirmation)
        def failed_launch_retry():
            self.fresh(scenario='launch_failure')
            failed = self.settled()
            assert failed['launchAttempts'] == 1 and failed['launches'] == 0 and failed['lastConfirmationSession'] >= 0
            self.replay_pending(failed['lastConfirmationSession'])
            self.wait(lambda: self.state()['replays'] == 1)
            assert self.state()['launchAttempts'] == 1 and self.state()['pending'] == -1
            self.command('update', scenario='normal')
            self.click('Update', PACKAGE); self.click('Download', PACKAGE); self.installer()
            retry = self.state()
            assert retry['launches'] == 1 and retry['session'] != failed['lastConfirmationSession']
            self.click('Cancel', 'com.android.packageinstaller'); self.settled()
        self.case('confirmation-launch-failure-new-session-retry', failed_launch_retry)
        def activity_recreation():
            self.fresh(scenario='slow')
            before = self.state()
            self.command('recreate')
            self.settled()
            self.command('update', scenario='normal')
            self.click('Update', PACKAGE); self.click('Download', PACKAGE)
            self.installer(); assert self.state()['pid'] == before['pid']
            self.click('Cancel', 'com.android.packageinstaller'); self.settled()
        self.case('activity-recreation-cancel-and-retry', activity_recreation)
        def process_recreation():
            self.fresh(); self.installer()
            old = self.call('shell', 'pidof', PACKAGE).strip(); assert old
            # The native confirmation is a dialog: its underlying Activity is
            # still visible. Go Home so am kill can reclaim a cached process.
            self.call('shell', 'input', 'keyevent', 'KEYCODE_HOME')
            self.wait(lambda: 'com.android.launcher3' in next(
                line for line in self.call('shell', 'dumpsys', 'window').splitlines() if 'mCurrentFocus=' in line))
            self.wait(lambda: re.search(r'Proc #.*cch.*' + re.escape(PACKAGE),
                                       self.call('shell', 'dumpsys', 'activity', 'processes')))
            # Kill only the background test process; no force-stop or data clear.
            self.call('shell', 'am', 'kill', PACKAGE)
            self.wait(lambda: old not in self.call('shell', 'ps', '-A').split())
            self.command('init')
            state = self.settled(); assert str(state['pid']) != old and state['version'] == '1.0.0'
            # Existing policy abandons stale native sessions and permits a fresh retry.
            self.command('update', scenario='normal')
            self.click('Update', PACKAGE); self.click('Download', PACKAGE)
            self.installer(); self.click('Cancel', 'com.android.packageinstaller'); self.settled()
        self.case('process-recreation-abandon-and-retry', process_recreation)
        if not self.results or (self.args.case and set(self.args.case) != {r['case'] for r in self.results}):
            raise AssertionError('Requested cases were not executed')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk', type=Path, required=True)
    parser.add_argument('--java', type=Path)
    parser.add_argument('--build', type=Path, required=True)
    parser.add_argument('--build-only', action='store_true')
    parser.add_argument('--serial')
    parser.add_argument('--avd')
    parser.add_argument('--adb-port', type=int, default=5037)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--case', action='append', help='Run only a named case; may be repeated')
    args = parser.parse_args()
    if args.java: build(args.build.resolve(), args.sdk.resolve(), args.java.resolve())
    if not args.build_only:
        if not args.serial or not args.avd or not args.output:
            parser.error('Running requires explicit --serial, --avd, --output')
        Suite(args).run()


if __name__ == '__main__': main()
