"""Generic API33 process-descriptor experiment; never targets a physical device."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import subprocess

ROOT = Path(__file__).resolve().parent.parent
NDK_VERSION = "27.2.12479018"
CASES = ["descriptor_targets_endpoint_not_sibling",
         "wrong_server_uid_rejected_before_command",
         "replacement_between_reads_rejected", "exit_before_handle_rejected",
         "exit_before_signal_preserves_replacement", "unsupported_primitive_rejected",
         "submission_without_exit_is_not_success", "server_preserves_app_uid_admission"]


class Failure(Exception):
    pass


def certify(serial, avd, call):
    if not re.fullmatch(r"emulator-[0-9]+", serial or ""):
        raise Failure("NON_EMULATOR_SERIAL")
    if avd != "pr67-ci-api33":
        raise Failure("UNAPPROVED_AVD_NAME")
    name = call(["emu", "avd", "name"]).strip().splitlines()
    if not name or name[0] != avd:
        raise Failure("FOREIGN_AVD")
    for prop, expected in [("ro.kernel.qemu", "1"), ("ro.build.version.sdk", "33"),
                           ("ro.product.cpu.abi", "x86_64")]:
        if call(["shell", "getprop", prop]).strip() != expected:
            raise Failure("UNAPPROVED_EMULATOR_CONFIGURATION")
    if call(["shell", "service", "check", "PServerBinder"]).strip() != "Service PServerBinder: not found":
        raise Failure("VENDOR_SERVICE_PRESENT_OR_UNKNOWN")


def execute(command, timeout=90):
    return subprocess.run([str(value) for value in command], capture_output=True,
                          text=True, encoding="utf-8", errors="replace", timeout=timeout)


def build(sdk, output):
    output = output.resolve()
    if output.is_relative_to(ROOT) or (output.exists() and any(output.iterdir())):
        raise Failure("BUILD_REQUIRES_FRESH_EXTERNAL_DIRECTORY")
    output.mkdir(parents=True, exist_ok=True)
    compiler = sdk / "ndk" / NDK_VERSION / "toolchains/llvm/prebuilt/linux-x86_64/bin/x86_64-linux-android33-clang"
    if not compiler.is_file():
        raise Failure("PINNED_ANDROID_COMPILER_MISSING")
    source = ROOT / "tests/android/process-binding/process_binding.c"
    binary = output / "probe"
    result = execute([compiler, "-std=c11", "-Wall", "-Wextra", "-Werror", "-O2",
                      "-fPIE", "-pie", "-Wl,-z,relro,-z,now", source, "-o", binary])
    if result.returncode:
        print(result.stderr)
        raise Failure("NATIVE_FIXTURE_COMPILATION")
    manifest = {"source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
                "binary_sha256": hashlib.sha256(binary.read_bytes()).hexdigest(),
                "ndk": NDK_VERSION, "api": 33, "abi": "x86_64"}
    (output / "provenance.json").write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print("PROCESS_BINDING_BUILD: pinned Android native fixture compiled")


def run(sdk, output, serial, avd):
    output = output.resolve()
    if output.is_relative_to(ROOT):
        raise Failure("RESULTS_REQUIRE_EXTERNAL_DIRECTORY")
    # A failed rerun must never leave a previous PASS as current evidence.
    (output / "results.json").unlink(missing_ok=True)
    adb = sdk / "platform-tools/adb"

    def command(arguments, checked=True):
        result = execute([adb, "-s", serial, *arguments])
        if checked and result.returncode:
            raise Failure("EMULATOR_OPERATION_FAILED")
        return result

    def call(arguments):
        return command(arguments).stdout

    # Serial syntax is checked before even the first ADB call. All calls remain
    # pinned to that serial; certification precedes root, push, writes or exec.
    certify(serial, avd, call)
    command(["root"])
    command(["wait-for-device"])
    certify(serial, avd, call)
    if call(["shell", "id", "-u"]).strip() != "0":
        raise Failure("ROOT_UNAVAILABLE_ON_APPROVED_EMULATOR")
    manifest = json.loads((output / "provenance.json").read_text(encoding="utf-8"))
    binary = output / "probe"
    if hashlib.sha256(binary.read_bytes()).hexdigest() != manifest["binary_sha256"]:
        raise Failure("FIXTURE_BINARY_HASH_CHANGED")
    remote = "/data/local/tmp/jtf-process-binding-" + secrets.token_hex(6)
    if not re.fullmatch(r"/data/local/tmp/jtf-process-binding-[0-9a-f]{12}", remote):
        raise Failure("UNSAFE_FIXTURE_PATH")
    created = False
    result = None
    try:
        command(["shell", "mkdir", remote])
        created = True
        command(["push", binary, remote + "/probe"])
        command(["shell", "chmod", "0700", remote + "/probe"])
        # Only this validated disposable directory is used as the fixture cwd.
        baseline = command(["shell", "cd " + remote + " && ./probe plain"], checked=False)
        observed = re.findall(r"^PROCESS_BINDING_PASS:([a-z_]+)\r?$", baseline.stdout, re.MULTILINE)
        if baseline.returncode or observed != CASES or "PROCESS_BINDING_COMPLETE:8" not in baseline.stdout:
            codes = re.findall(r"PROCESS_BINDING_FAIL:([A-Z_]+)", baseline.stdout + baseline.stderr)
            raise Failure("NATIVE_BASELINE_" + (codes[0] if codes else "INCOMPLETE"))
        controls = []
        for mode, expected in [("ignore-peer", "EXPECTED_UID_DENIAL"), ("no-signal", "EXPECTED_EXIT")]:
            negative = command(["shell", "cd " + remote + " && ./probe " + mode], checked=False)
            if negative.returncode == 0 or "PROCESS_BINDING_FAIL:" + expected not in negative.stdout + negative.stderr:
                raise Failure("NEGATIVE_CONTROL_NOT_DETECTED_" + mode)
            controls.append(mode)
        result = {"status": "PASS", "tier": "GENERIC_ANDROID_API33_NATIVE_FIXTURE",
                  "cases": CASES, "negative_controls_detected": controls, "provenance": manifest,
                  "source_head": os.environ.get("SOURCE_HEAD", "UNKNOWN"),
                  "workflow_revision": os.environ.get("GITHUB_SHA", "UNKNOWN"),
                  "limits": ["Not production Java or published predecessor protocol",
                             "No forced numeric PID recycling", "No installed-app SELinux proof",
                             "No vendor Binder, successor READY or Thor acceptance"]}
    finally:
        if created:
            # No recursive delete: only three known fixture files, then its
            # exact empty directory. A cleanup failure prevents a PASS report.
            command(["shell", "rm", "-f", remote + "/probe", remote + "/endpoint.sock", remote + "/sibling.sock"])
            command(["shell", "rmdir", remote])
    (output / "results.json").write_text(json.dumps(result, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(result, sort_keys=True))


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--sdk", type=Path, required=True)
    parser.add_argument("--build", type=Path, required=True)
    parser.add_argument("--build-only", action="store_true")
    parser.add_argument("--serial")
    parser.add_argument("--avd")
    args = parser.parse_args()
    if args.build_only:
        build(args.sdk, args.build)
    else:
        run(args.sdk, args.build, args.serial, args.avd)


if __name__ == "__main__":
    try:
        main()
    except (Failure, subprocess.TimeoutExpired) as error:
        print("PROCESS_BINDING_GATE_FAILED:" + (str(error) if isinstance(error, Failure) else "COMMAND_TIMEOUT"))
        raise SystemExit(1)
