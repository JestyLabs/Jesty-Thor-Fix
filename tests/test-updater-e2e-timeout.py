#!/usr/bin/env python3
"""Unit checks for fail-path-only diagnostic redaction, no emulator needed."""
import importlib.util
from pathlib import Path
import unittest

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


if __name__ == '__main__':
    unittest.main()
