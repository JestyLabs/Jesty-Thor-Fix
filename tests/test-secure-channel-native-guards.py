"""Controller admission and evidence regressions; never calls ADB or Android."""
import contextlib
import hashlib
import importlib.util
import io
import json
from pathlib import Path
from types import SimpleNamespace
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('native_ipc', ROOT / 'scripts/test-secure-channel-native.py')
controller = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(controller)


class NativeControllerGuards(unittest.TestCase):
    def fake_reads(self, change=None):
        calls = []
        answers = {('emu', 'avd', 'name'): 'pr67-ci-api33\nOK\n',
                   ('shell', 'getprop', 'ro.kernel.qemu'): '1\n',
                   ('shell', 'getprop', 'ro.build.version.sdk'): '33\n',
                   ('shell', 'getprop', 'ro.product.cpu.abi'): 'x86_64\n',
                   ('shell', 'service', 'check', 'PServerBinder'): 'Service PServerBinder: not found\n'}
        if change:
            answers[change[0]] = change[1]
        def call(command):
            calls.append(tuple(command))
            return answers[tuple(command)]
        return calls, call

    def test_unapproved_target_before_any_command(self):
        calls, call = self.fake_reads()
        for serial, avd in [(None, 'pr67-ci-api33'), ('synthetic-physical', 'pr67-ci-api33'),
                            ('emulator-5554 shell', 'pr67-ci-api33'), ('emulator-5554', 'personal-avd')]:
            with self.subTest(serial=serial, avd=avd), self.assertRaises(controller.Failure):
                controller.certify(serial, avd, call)
        self.assertEqual([], calls)

    def test_foreign_surface_stops_at_read_only_boundary(self):
        variants = [(('emu', 'avd', 'name'), 'another-avd\n'),
                    (('shell', 'getprop', 'ro.kernel.qemu'), '0\n'),
                    (('shell', 'getprop', 'ro.build.version.sdk'), '34\n'),
                    (('shell', 'getprop', 'ro.product.cpu.abi'), 'arm64-v8a\n'),
                    (('shell', 'service', 'check', 'PServerBinder'), 'Service PServerBinder: found\n'),
                    (('shell', 'service', 'check', 'PServerBinder'), '')]
        for variant in variants:
            calls, call = self.fake_reads(variant)
            with self.subTest(variant=variant), self.assertRaises(controller.Failure):
                controller.certify('emulator-5554', 'pr67-ci-api33', call)
            self.assertTrue(all(c[:2] in (('emu', 'avd'), ('shell', 'getprop'), ('shell', 'service')) for c in calls))

    def test_all_positive_observations_required(self):
        calls, call = self.fake_reads()
        controller.certify('emulator-5554', 'pr67-ci-api33', call)
        self.assertEqual(5, len(calls))

    def manifest(self, build):
        jars = {}
        for variant in ('plain', 'prelock', 'peer'):
            path = build / (variant + '.jar')
            path.write_bytes(b'host-only-inert-data')
            jars[variant] = hashlib.sha256(path.read_bytes()).hexdigest()
        return {'device_root': '/data/local/tmp/jtf-ipc-1234abcd', 'jars': jars,
                'fixture_sha256': hashlib.sha256((ROOT / 'tests/android/secure-channel-native/NativeSecureChannelFixture.java').read_bytes()).hexdigest(),
                'production': {name: hashlib.sha256((ROOT / 'src/com/thor/displaypowertest' / (name + '.java')).read_bytes()).hexdigest()
                               for name in ('SecureChannel', 'IpcPeerPolicy', 'DaemonLaunchModel')}}

    def test_drift_or_manifest_escape_before_any_adb_command(self):
        for kind in ('production', 'fixture', 'jar-bytes', 'jar-name', 'device-path'):
            with self.subTest(kind=kind), tempfile.TemporaryDirectory() as temp:
                build = Path(temp)
                data = self.manifest(build)
                if kind == 'production': data['production']['SecureChannel'] = '0' * 64
                if kind == 'fixture': data['fixture_sha256'] = '0' * 64
                if kind == 'jar-bytes': (build / 'plain.jar').write_bytes(b'drift')
                if kind == 'jar-name': data['jars']['../escape'] = data['jars'].pop('plain')
                if kind == 'device-path': data['device_root'] = '/data/local/tmp'
                (build / 'provenance.json').write_text(json.dumps(data), encoding='utf-8')
                args = SimpleNamespace(build=build, sdk=build)
                with patch.object(controller, 'execute', side_effect=AssertionError('No ADB admitted')):
                    with self.assertRaises(controller.Failure):
                        controller.NativeRun(args)

    def test_inputs_rechecked_before_run(self):
        with tempfile.TemporaryDirectory() as temp:
            build = Path(temp)
            (build / 'provenance.json').write_text(json.dumps(self.manifest(build)), encoding='utf-8')
            runner = controller.NativeRun(SimpleNamespace(build=build, sdk=build))
            (build / 'plain.jar').write_bytes(b'changed-after-construction')
            with patch.object(runner, 'raw', side_effect=AssertionError('No ADB admitted')):
                with self.assertRaisesRegex(controller.Failure, 'DEX_INPUT_DRIFT'):
                    runner.run()

    def test_missing_stale_inode_cannot_claim_recovery(self):
        with tempfile.TemporaryDirectory() as temp:
            build = Path(temp)
            (build / 'provenance.json').write_text(json.dumps(self.manifest(build)), encoding='utf-8')
            args = SimpleNamespace(build=build, sdk=build, serial='emulator-5554', avd='pr67-ci-api33')
            runner = controller.NativeRun(args)
            invocations = []
            def shell(*command):
                if command[:2] == ('id', '-u'): return '0'
                if command[:3] == ('cmd', 'package', 'list'):
                    return 'package:com.thor.displaypowertest uid:10001\n'
                if command[:2] == ('stat', '-c'): return '123'
                if command[:2] == ('test', '-S'):
                    raise controller.Failure('SYNTHETIC_STALE_PATH_MISSING')
                return ''
            def contender(variant, inode):
                if variant == 'prelock': raise controller.Failure('EXCLUSIVITY_CONTENT_MUTATION')
            with patch.object(controller, 'certify', return_value={}), \
                    patch.object(runner, 'raw', return_value=SimpleNamespace(returncode=1, stdout='')), \
                    patch.object(runner, 'call', return_value=''), \
                    patch.object(runner, 'shell', side_effect=shell), \
                    patch.object(runner, 'holder', return_value=object()), \
                    patch.object(runner, 'stop'), \
                    patch.object(runner, 'contender', side_effect=contender), \
                    patch.object(runner, 'fixture', side_effect=lambda *a: invocations.append(a)), \
                    contextlib.redirect_stdout(io.StringIO()):
                # Root succeeds, path-absence probe returns1, other raw calls are unused here.
                def raw(command):
                    return SimpleNamespace(returncode=0 if command == ['root'] else 1, stdout='')
                runner.raw.side_effect = raw
                with self.assertRaisesRegex(controller.Failure, 'SYNTHETIC_STALE_PATH_MISSING'):
                    runner.run()
            self.assertFalse(any(call[1] == 'recover' for call in invocations))
            self.assertFalse(any(r['case'] == 'native-process-exit-and-stale-recovery' for r in runner.results))

    def evidence(self, run_error=None, cleanup_error=None):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp) / 'result'
            calls = []
            def run():
                calls.append('run')
                if run_error: raise controller.Failure(run_error)
            def cleanup():
                calls.append('cleanup')
                if cleanup_error: raise controller.Failure(cleanup_error)
            fake = SimpleNamespace(run=run, cleanup=cleanup, results=[],
                                   manifest={'production': {}, 'jars': {}, 'fixture_sha256': 'synthetic'})
            args = SimpleNamespace(build_only=False, serial='emulator-5554', avd='pr67-ci-api33', output=output)
            with patch.object(controller.argparse.ArgumentParser, 'parse_args', return_value=args), \
                    patch.object(controller, 'NativeRun', return_value=fake), contextlib.redirect_stdout(io.StringIO()):
                if run_error or cleanup_error:
                    with self.assertRaisesRegex(controller.Failure, run_error or cleanup_error):
                        controller.main()
                else:
                    controller.main()
            self.assertEqual(['run', 'cleanup'], calls)
            return json.loads((output / 'results.json').read_text(encoding='utf-8'))

    def test_cleanup_failure_never_records_pass(self):
        report = self.evidence(cleanup_error='SYNTHETIC_CLEANUP_FAILURE')
        self.assertEqual('FAIL', report['result'], 'cleanup failure must never preserve PASS')
        self.assertEqual('FAIL', report['cleanup'])

    def test_run_and_cleanup_failure_both_retained(self):
        report = self.evidence(run_error='SYNTHETIC_RUN_FAILURE', cleanup_error='SYNTHETIC_CLEANUP_FAILURE')
        self.assertEqual('FAIL', report['result'])
        self.assertEqual(['run', 'cleanup'], [e['phase'] for e in report['errors']])

    def test_pass_only_after_cleanup(self):
        report = self.evidence()
        self.assertEqual('PASS', report['result'])
        self.assertEqual('PASS', report['cleanup'])
        self.assertEqual([], report['errors'])

    def test_existing_evidence_not_overwritten(self):
        with tempfile.TemporaryDirectory() as temp:
            output = Path(temp)
            marker = output / 'results.json'; marker.write_text('preserve', encoding='utf-8')
            args = SimpleNamespace(build_only=False, serial='emulator-5554', avd='pr67-ci-api33', output=output)
            with patch.object(controller.argparse.ArgumentParser, 'parse_args', return_value=args), \
                    patch.object(controller, 'NativeRun', side_effect=AssertionError('No native run')):
                with self.assertRaisesRegex(controller.Failure, 'EVIDENCE_NOT_FRESH'):
                    controller.main()
            self.assertEqual('preserve', marker.read_text(encoding='utf-8'))


if __name__ == '__main__':
    unittest.main()
