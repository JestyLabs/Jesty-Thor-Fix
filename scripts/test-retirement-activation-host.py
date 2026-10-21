#!/usr/bin/env python3
"""Offline JDK17 integration of the real activation and retirement controllers.

Only the native/Android/bridge boundaries are inert. As in the original PS
fixture, legacy TCP probes are replaced and AutoService's poll sleep is removed
in a disposable source copy. No admission/receipt/policy code is substituted.
The actual default-package R entry is compiled, not invoked as a root process.
Scratch sources/classes stay under work/ and are removed even after failure.
No APK, platform binary, native root, device, network or dependency installation.
"""
from pathlib import Path
import argparse
import hashlib
import platform
import re
import shutil
import subprocess
import sys
import uuid


ROOT = Path(__file__).resolve().parents[1]
PACKAGE = Path("com/thor/displaypowertest")
PRODUCT = (
    "AutoService", "PServer", "LiveRetirement", "RebootActivation",
    "RebootActivationPolicy", "PreviousSecureDaemonIdentity", "LiveHandoverPolicy",
    "DaemonLaunchModel", "FailedStartRetirementPolicy", "DashboardRenderer", "DashboardStateModel",
    "CpuWarningModel", "TelemetryValues",
)
TESTS = (
    "com.thor.displaypowertest.RebootActivationHostTest",
    "com.thor.displaypowertest.RebootDashboardHostTest",
    "RebootActivationPolicyTest",
    "LiveHandoverPolicyTest",
    "com.thor.displaypowertest.PredecessorRecheckHostTest",
    "com.thor.displaypowertest.RecoveryRetirementHostTest",
    "com.thor.displaypowertest.RecoveryFenceHostTest",
)
MUTANTS = (
    ("same-kernel-authorized", "RebootActivationPolicy.java",
     "!boot.equalsIgnoreCase(savedBoot)", "true",
     "RebootActivationPolicyTest", (), "same kernel cannot authorize"),
    ("activation-authorization-bypassed", "RebootActivation.java",
     "Evidence result = evidence();\n"
     "            return result == Evidence.BOOT_VERIFIED || result == Evidence.LIVE_VERIFIED;", "return true;",
     TESTS[0], (), "first-open-prepares-only: launches"),
    ("retained-second-snapshot-guard-removed", "LiveRetirement.java",
     "requireUnchangedEligible(firstHealth, firstSnapshot, response,\n"
     "                                    display, cpu, guard, uid, peerPid);",
     "/* deliberate compiled negative control: second snapshot guard removed */",
     TESTS[4], ("mode",), "S2-recheck-mode stop admission"),
    ("invented-code76-runtime-field", "FixtureState.java",
     '"1.7.1".equals(version) ? "" : ";runtime_version=" + version',
     '";runtime_version=" + version',
     TESTS[4], ("raw-code76",), "raw-code76 must not fabricate runtime identity"),
    ("recovery-exit-proof-removed", "LiveRetirement.java",
     "io.signalAndAwaitExit(processFd, control.fd, deadline.remaining(3000));", "",
     TESTS[5], ("no-effect",), "no-effect: recovery result requires confirmed exit"),
    ("recovery-second-phase-guard-removed", "FailedStartRetirementPolicy.java",
     'a.get("boot_phase").equals(b.get("boot_phase"))', 'true',
     TESTS[5], (), "changed-failed-phase: recovery result requires confirmed exit"),
    ("recovery-durable-fence-removed", "RebootActivation.java",
     'boot.equalsIgnoreCase(unconfirmedRecoveryBoot)\n'
     '                            || boot.equalsIgnoreCase(pending)', 'false',
     TESTS[6], (), "same boot pending blocks subsequent ticket"),
)


def sanitize(text):
    text = text.replace(str(ROOT.parent), "[BUNDLE_ROOT]")
    text = re.sub(r"\.host-retirement-activation-[0-9a-f]{32}", "[HOST_TEMP]", text)
    return re.sub(r"(?:[A-Za-z]:[\\/]Users[\\/]|/Users/|/home/|/private/var/folders/)[^\s'\";]+",
                  "[HOST_TEMP]", text)


def run(command, *, expected=None):
    print("COMMAND " + sanitize(" ".join(str(part) for part in command)), flush=True)
    result = subprocess.run(command, cwd=ROOT, text=True, stdout=subprocess.PIPE,
                            stderr=subprocess.STDOUT, timeout=120)
    if result.stdout:
        print(sanitize(result.stdout), end="" if result.stdout.endswith("\n") else "\n", flush=True)
    print("EXIT " + str(result.returncode), flush=True)
    if expected is not None:
        if result.returncode == 0 or expected not in result.stdout:
            raise RuntimeError("negative control did not fail the intended assertion: " + expected)
    elif result.returncode:
        raise RuntimeError("host command failed (not an accepted negative control)")
    return result.stdout


def replace_body(source, signature, replacement):
    start = source.index(signature)
    opened = source.index("{", start)
    depth, end = 1, opened + 1
    while depth:
        depth += (source[end] == "{") - (source[end] == "}")
        end += 1
    return source[:opened] + "{" + replacement + "}" + source[end:]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--run-negative-controls", action="store_true")
    args = parser.parse_args()
    if not shutil.which("java") or not shutil.which("javac"):
        print("NOT RUN: existing java/javac unavailable; no installation attempted")
        return 2
    print("HOST ONLY: real AutoService/PServer/LiveRetirement app+root/RebootActivation/"
          "Dashboard; native effects, credentials and historical replies MODELLED")
    print("PATH_REDACTION: bundle=[BUNDLE_ROOT]; disposable compilation directory=[HOST_TEMP]")
    print("Python " + platform.python_version())
    run(["java", "-version"])
    compiler = run(["javac", "-version"])
    if not compiler.startswith("javac 17."):
        print("NOT RUN: this portable fixture requires the existing JDK17")
        return 2
    scratch = ROOT / (".host-retirement-activation-" + uuid.uuid4().hex)
    scratch.mkdir()
    try:
        src, classes = scratch / "src", scratch / "classes"
        fixture = ROOT / "tests/integration/reboot-activation"
        shutil.copytree(fixture / "fixtures", src)
        classes.mkdir()
        for name in PRODUCT:
            product_path = ROOT / "src" / PACKAGE / (name + ".java")
            product_bytes = product_path.read_bytes()
            print("PRODUCT_SOURCE " + product_path.relative_to(ROOT).as_posix() + " sha256="
                  + hashlib.sha256(product_bytes).hexdigest(), flush=True)
            source = product_bytes.decode("utf-8")
            if name == "AutoService":
                if "Thread.sleep(200L);" not in source:
                    raise RuntimeError("fixture acceleration target missing")
                source = source.replace("Thread.sleep(200L);", "Thread.sleep(0L);")
                if "stopPreviousSecureDaemonIfSafe" in source or "stopLegacyDaemon" in source:
                    raise RuntimeError("reboot activation must not stop an earlier/legacy daemon")
            if name == "PServer":
                source = replace_body(source, "public static boolean legacyTcpOpen()",
                                      " return FixtureState.legacyPresent; ")
            (src / PACKAGE / (name + ".java")).write_text(source, encoding="utf-8")
        entry = ROOT / "src/R.java"
        entry_bytes = entry.read_bytes()
        print("PRODUCT_SOURCE src/R.java sha256="
              + hashlib.sha256(entry_bytes).hexdigest() + " (compiled only)", flush=True)
        (src / "R.java").write_bytes(entry_bytes)
        for test in fixture.glob("*.java"):
            shutil.copyfile(test, src / test.name)
        for name in ("RebootActivationPolicyTest", "LiveHandoverPolicyTest"):
            shutil.copyfile(ROOT / "tests" / (name + ".java"), src / (name + ".java"))
        sources = sorted(src.rglob("*.java"))
        argfile = scratch / "sources.txt"
        argfile.write_text("".join('"' + p.relative_to(ROOT).as_posix() + '"\n'
                                   for p in sources), encoding="utf-8")
        compile_command = [
            "javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
            "-d", classes.relative_to(ROOT).as_posix(),
            "@" + argfile.relative_to(ROOT).as_posix(),
        ]
        run(compile_command)
        for test in TESTS:
            run(["java", "-cp", classes.relative_to(ROOT).as_posix(), test])
        if args.run_negative_controls:
            for name, file, before, after, test, extra, expected in MUTANTS:
                path = src / PACKAGE / file
                original = path.read_text(encoding="utf-8")
                if original.count(before) != 1:
                    raise RuntimeError("negative control needs exactly one target: " + name)
                try:
                    path.write_text(original.replace(before, after), encoding="utf-8")
                    # Compile success is mandatory; compilation failure is FAIL.
                    run(compile_command)
                    run(["java", "-cp", classes.relative_to(ROOT).as_posix(), test, *extra],
                        expected=expected)
                    print("NEGATIVE_CONTROL PASS: " + name + " compiled and rejected by " + expected,
                          flush=True)
                finally:
                    path.write_text(original, encoding="utf-8")
    finally:
        if scratch.parent != ROOT or scratch.is_symlink():
            raise RuntimeError("refusing unexpected host fixture cleanup target")
        shutil.rmtree(scratch)
    print("PASS activation-product-host: actual product controllers, inert lower boundaries; "
          "compilation files removed")
    print("NOT RUN: Android/JNI/ABI/SELinux/published native producer/device/"
          "root execution; no installations")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (RuntimeError, subprocess.TimeoutExpired, OSError) as error:
        print("FAIL: " + sanitize(str(error)), file=sys.stderr)
        raise SystemExit(1)
