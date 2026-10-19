"""Real published producer + product controller; existing JDK only, no Android.

Run from the supplied bundle. Native/OS operations are inert Io boundaries.
Compiled deliberate mutants must fail a specified behavioural assertion.
"""
from pathlib import Path
import argparse
import shutil
import subprocess
import tempfile

WORK = Path(__file__).resolve().parents[1]
BUNDLE = WORK.parent


def execute(command, expected=None):
    result = subprocess.run(command, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
    print(result.stdout, end="")
    if expected is None:
        if result.returncode:
            raise RuntimeError("HOST command failed: " + command[0])
    elif result.returncode == 0 or expected not in result.stdout:
        raise RuntimeError("Compiled negative control did not fail expected assertion: " + expected)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--negative-controls", action="store_true")
    args = parser.parse_args()
    if not shutil.which("javac") or not shutil.which("java"):
        print("NOT RUN: existing JDK unavailable; no installation permitted")
        return 2
    for version in ("1.7.1", "1.7.2", "1.7.3"):
        with tempfile.TemporaryDirectory(prefix="retirement-host-") as tmp:
            root = Path(tmp)
            source = root / "src"
            source.mkdir()
            product = WORK / "src/com/thor/displaypowertest"
            sources = []
            for name in ("LiveRetirement", "LiveHandoverPolicy", "PreviousSecureDaemonIdentity",
                         "DaemonLaunchScript", "RetirementNative"):
                dest = source / (name + ".java")
                shutil.copyfile(product / dest.name, dest)
                sources.append(str(dest))
            producer = WORK / "tests/retirement/published" / version / "src/com/thor/displaypowertest"
            for name in ("DaemonState", "DaemonCommandHandler", "DaemonIdentity", "DisplayDecisionModel"):
                sources.append(str(producer / (name + ".java")))
            fixture = WORK / "tests/retirement/published-probe"
            # This is an inert OS interface, not a modified wire producer.
            text = (fixture / "PublishedReplyProbe.java").read_text()
            text = text.replace("static final int PROTOCOL = 2;",
                                'static final int PROTOCOL = 2; static String path() { return "/private/files/control.sock"; }')
            (source / "PublishedReplyProbe.java").write_text(text)
            sources.extend([str(source / "PublishedReplyProbe.java"),
                            str(fixture / "SystemClock.java"),
                            str(WORK / "tests/retirement/RetirementHostTest.java"),
                            str(WORK / "tests/LiveHandoverPolicyTest.java"),
                            str(WORK / "tests/PreviousSecureDaemonIdentityTest.java")])
            classes = root / "classes"
            compile_command = ["javac", "-encoding", "UTF-8", "-source", "8", "-target", "8",
                               "-d", str(classes), *sources]
            execute(compile_command)
            print("REAL_PUBLISHED_PRODUCER " + version)
            execute(["java", "-cp", str(classes), "com.thor.displaypowertest.PublishedReplyProbe"])
            execute(["java", "-cp", str(classes), "LiveHandoverPolicyTest"])
            execute(["java", "-cp", str(classes), "PreviousSecureDaemonIdentityTest"])
            execute(["java", "-cp", str(classes), "com.thor.displaypowertest.RetirementHostTest"])
            if args.negative_controls:
                path = source / "LiveRetirement.java"
                original = path.read_text()
                mutants = [
                    ("second-Q-gate-removed",
                     "requireEligible(firstHealth, response, display, cpu, guard, uid, peerPid);",
                     "", "second-mode", "second-mode: unsafe stop admission"),
                    ("exit-proof-removed",
                     "io.signalAndAwaitExit(processFd, control.fd, deadline.remaining(3000));",
                     "", "no-effect", "no-effect: unsafe stop admission"),
                    ("app-wait-undercuts-Q",
                     "private static final int OBSERVATION_WAIT_MS = 6000;",
                     "private static final int OBSERVATION_WAIT_MS = 1000;",
                     "app-wait", "app wait preserves Q and identity-check budget"),
                ]
                for name, before, after, case, assertion in mutants:
                    if before not in original:
                        raise RuntimeError("Missing mutation target: " + name)
                    path.write_text(original.replace(before, after))
                    execute(compile_command)
                    execute(["java", "-cp", str(classes), "com.thor.displaypowertest.RetirementHostTest",
                             case], assertion)
                    print("NEGATIVE_CONTROL PASS compiled " + name + " rejected by " + assertion)
                    path.write_text(original)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
