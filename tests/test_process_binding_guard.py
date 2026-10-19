"""Admission tests for the actual Android fixture controller; no ADB calls."""
import importlib.util
from pathlib import Path
import unittest

SPEC = importlib.util.spec_from_file_location("process_binding", Path(__file__).resolve().parents[1] / "scripts/test-process-binding-android.py")
controller = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(controller)


class AdmissionTest(unittest.TestCase):
    def fake(self, change=None):
        calls = []
        answers = {("emu", "avd", "name"): "pr67-ci-api33\nOK\n",
                   ("shell", "getprop", "ro.kernel.qemu"): "1\n",
                   ("shell", "getprop", "ro.build.version.sdk"): "33\n",
                   ("shell", "getprop", "ro.product.cpu.abi"): "x86_64\n",
                   ("shell", "service", "check", "PServerBinder"): "Service PServerBinder: not found\n"}
        if change:
            answers[change[0]] = change[1]

        def call(command):
            calls.append(command)
            return answers[tuple(command)]
        return calls, call

    def test_physical_serial_rejected_before_any_call(self):
        calls, call = self.fake()
        for serial in [None, "", "synthetic-physical", "emulator-5554 shell"]:
            with self.assertRaisesRegex(controller.Failure, "NON_EMULATOR_SERIAL"):
                controller.certify(serial, "pr67-ci-api33", call)
        self.assertEqual([], calls)

    def test_unapproved_requested_avd_rejected_before_any_call(self):
        calls, call = self.fake()
        with self.assertRaisesRegex(controller.Failure, "UNAPPROVED_AVD_NAME"):
            controller.certify("emulator-5554", "personal-avd", call)
        self.assertEqual([], calls)

    def test_foreign_surface_fails_during_read_only_certification(self):
        variants = [(("emu", "avd", "name"), "other-avd\n"),
                    (("shell", "getprop", "ro.kernel.qemu"), "0\n"),
                    (("shell", "getprop", "ro.build.version.sdk"), "34\n"),
                    (("shell", "getprop", "ro.product.cpu.abi"), "arm64-v8a\n"),
                    (("shell", "service", "check", "PServerBinder"), "Service PServerBinder: found\n"),
                    (("shell", "service", "check", "PServerBinder"), "")]
        for variant in variants:
            calls, call = self.fake(variant)
            with self.subTest(variant=variant), self.assertRaises(controller.Failure):
                controller.certify("emulator-5554", "pr67-ci-api33", call)
            self.assertTrue(all(command[:2] in [["emu", "avd"], ["shell", "getprop"], ["shell", "service"]] for command in calls))

    def test_approved_emulator_requires_all_observations(self):
        calls, call = self.fake()
        controller.certify("emulator-5554", "pr67-ci-api33", call)
        self.assertEqual(5, len(calls))


if __name__ == "__main__":
    unittest.main()
