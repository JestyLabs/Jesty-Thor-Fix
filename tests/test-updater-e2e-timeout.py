#!/usr/bin/env python3
"""Unit checks for fail-path-only diagnostic redaction, no emulator needed."""
import importlib.util
from pathlib import Path
import unittest
import tempfile
from types import SimpleNamespace

SOURCE = Path(__file__).resolve().parents[1] / 'scripts/test-updater-android-e2e.py'
spec = importlib.util.spec_from_file_location('updater_e2e_fixture', SOURCE)
module = importlib.util.module_from_spec(spec)
spec.loader.exec_module(module)


class SettlementTimeoutEvidenceTest(unittest.TestCase):
    def test_reports_exact_unsettled_fields_and_stale_process(self):
        state = {'version': '1.0.1', 'pid': 111, 'session': 9,
                 'transfer': True, 'reserved': False, 'nativeSessions': 1,
                 'pending': 9, 'launches': 1, 'launchAttempts': 1,
                 'sensitive': 'SECRET-DONT-PRINT'}
        evidence = module.settlement_timeout_evidence(
            state, installed_version='1.0.1', running_pid=222,
            installer_focused=True)
        self.assertEqual(evidence['unsettled_fields'],
                         ['transfer', 'session', 'nativeSessions'])
        self.assertIs(evidence['running_process_matches_snapshot'], False)
        self.assertIs(evidence['system_installer_focused'], True)
        self.assertNotIn('sensitive', str(evidence))
        self.assertNotIn('SECRET-DONT-PRINT', str(evidence))
        self.assertNotIn('111', str(evidence))
        self.assertNotIn('222', str(evidence))

    def test_unavailable_and_unexpected_types_cannot_leak(self):
        evidence = module.settlement_timeout_evidence(
            None, installed_version=None, running_pid=None, installer_focused=None)
        self.assertFalse(evidence['snapshot_available'])
        self.assertNotIn('fixture_state', evidence)
        evidence = module.settlement_timeout_evidence({'pid': 'unsafe', 'other': 'nope'})
        self.assertIsNone(evidence['running_process_matches_snapshot'])
        self.assertNotIn('other', str(evidence))
        self.assertCountEqual(evidence['unsettled_fields'],
                              ['transfer', 'reserved', 'session', 'nativeSessions'])

    def test_malicious_content_inside_known_keys_is_not_emitted(self):
        state = {'version': 'SECRET-DONT-PRINT!', 'transfer': 'SECRET-DONT-PRINT!',
                 'reserved': False, 'session': {'secrets': 'SECRET-DONT-PRINT!'},
                 'nativeSessions': 0, 'pid': 'SECRET-DONT-PRINT!'}
        evidence = module.settlement_timeout_evidence(state,
                installed_version='SECRET-DONT-PRINT!', installer_focused='bad')
        self.assertNotIn('SECRET-DONT-PRINT!', str(evidence))
        self.assertIsNone(evidence['fixture_state']['version'])
        self.assertIsNone(evidence['fixture_state']['transfer'])
        self.assertIsNone(evidence['fixture_state']['session'])
        self.assertIsNone(evidence['installed_version'])
        self.assertIsNone(evidence['system_installer_focused'])

    def test_fully_settled_diagnostics_require_no_extra_operation(self):
        state = {'transfer': False, 'reserved': False, 'session': -1,
                 'nativeSessions': 0, 'pid': 10, 'version': '1.0.1'}
        evidence = module.settlement_timeout_evidence(state, running_pid=10)
        self.assertEqual(evidence['unsettled_fields'], [])
        self.assertTrue(evidence['running_process_matches_snapshot'])


class DiagnosticAssertionEvidenceTest(unittest.TestCase):
    def test_fixed_schema_classifies_without_raw_data(self):
        logs = ('TEST_ERROR SNAPSHOT_ERROR FATAL EXCEPTION: secret-process-123\n'
                'java.io.FileNotFoundException: SECRET-DO-NOT-PRINT https://private.invalid\n'
                ' at private.Class.method(UpdaterTestActivity.java:40)\n'
                'java.lang.SecretTokenException: should not be allowlisted\n')
        evidence = module.diagnostic_failure_evidence(logs)
        self.assertEqual(evidence['markers'],
                         {'TEST_ERROR': True, 'SNAPSHOT_ERROR': True, 'FATAL EXCEPTION': True})
        self.assertEqual(evidence['exception_types'], ['java.io.FileNotFoundException'])
        self.assertEqual(evidence['fixture_frames'], ['UpdaterTestActivity.java:40'])
        for private in ('SECRET', 'https:', 'private.', 'secret-process', 'SecretToken'):
            self.assertNotIn(private, str(evidence))

    def test_unknown_types_and_unbounded_frames_cannot_leak(self):
        self.assertFalse(any(module.diagnostic_failure_evidence(None)['markers'].values()))
        log = ('(PrivateSecret.java:42) (AppUpdater.java:123456789)\n'
               + ' '.join('(AppUpdater.java:' + str(n) + ')' for n in range(20))
               + ' evil.java.io.IOException.Secret java.io.IOExceptionSuffix')
        evidence = module.diagnostic_failure_evidence(log)
        self.assertEqual(len(evidence['fixture_frames']), 8)
        self.assertEqual(evidence['exception_types'], [])
        self.assertNotIn('PrivateSecret', str(evidence))
        self.assertNotIn('123456789', str(evidence))

    def test_actual_case_retains_assertion_and_emits_sanitized_message(self):
        with tempfile.TemporaryDirectory() as folder:
            suite = object.__new__(module.Suite)
            suite.args = SimpleNamespace(case=None)
            suite.results = []
            suite.output = Path(folder)
            suite.call = lambda *args: ('SNAPSHOT_ERROR java.io.IOException: SECRET\n'
                                        'at x(UpdaterTestActivity.java:40)')
            with self.assertRaises(AssertionError) as caught:
                suite.case('synthetic-case', lambda: None)
            message = str(caught.exception)
            self.assertIn('native-updater-diagnostic-assertion', message)
            self.assertIn('java.io.IOException', message)
            self.assertIn('UpdaterTestActivity.java:40', message)
            self.assertNotIn('SECRET', message)
            self.assertEqual(suite.results[0]['result'], 'FAIL')
            # Existing raw synthetic fixture-log behavior remains local; it is
            # not printed or added to public CI by this evidence function.
            self.assertTrue((Path(folder) / 'synthetic-case.log').exists())


if __name__ == '__main__':
    unittest.main()
