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
    parser.add_argument('--predecessor', choices=('1.7.1', '1.7.2', '1.7.3', '1.7.4', '1.7.5'))
    args = parser.parse_args()
    if not shutil.which("javac") or not shutil.which("java"):
        print("NOT RUN: existing JDK unavailable; no installation permitted")
        return 2
    for version in ((args.predecessor,) if args.predecessor else ('1.7.1', '1.7.2', '1.7.3', '1.7.4', '1.7.5')):
        with tempfile.TemporaryDirectory(prefix="retirement-host-") as tmp:
            root = Path(tmp)
            source = root / "src"
            source.mkdir()
            product = WORK / "src/com/thor/displaypowertest"
            sources = []
            for name in ("LiveRetirement", "LiveHandoverPolicy", "PreviousSecureDaemonIdentity",
                         "DaemonLaunchScript", "DaemonLaunchModel", "FailedStartRetirementPolicy", "RetirementNative"):
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
                mutants = [
                    ("second-Q-gate-removed", "LiveRetirement.java",
                     "requireUnchangedEligible(firstHealth, firstSnapshot, response,\n"
                     "                                    display, cpu, guard, uid, peerPid);",
                     "", "second-mode", "second-mode: unsafe stop admission"),
                    ("exit-proof-removed", "LiveRetirement.java",
                     "io.signalAndAwaitExit(processFd, control.fd, deadline.remaining(3000));",
                     "", "no-effect", "no-effect: unsafe stop admission"),
                    ("app-wait-undercuts-Q", "LiveRetirement.java",
                     "private static final int OBSERVATION_WAIT_MS = 6000;",
                     "private static final int OBSERVATION_WAIT_MS = 1000;",
                     "app-wait", "app wait preserves Q and identity-check budget"),
                    ("top-repair-gate-removed", "LiveHandoverPolicy.java",
                     '!confirmedTopSnapshot(snapshot)', 'false',
                     'top-pending', 'top-pending: unsafe stop admission'),
                    ("top-generation-check-removed", "LiveHandoverPolicy.java",
                     'generation.equals(field(second, "display_generation"))', 'true',
                     'top-aba', 'top-aba: unsafe stop admission'),
                    ("retained-mode-check-removed", "LiveHandoverPolicy.java",
                     'mode == null || !mode.equals(field(second, "mode"))', 'mode == null',
                     'both-to-top', 'both-to-top: unsafe stop admission'),
                    ("public-stop-top-widened", "PreviousSecureDaemonIdentity.java",
                     'return checkedPid(health, snapshot, false);',
                     'return checkedPid(health, snapshot, true);',
                     'top-public', 'public unbound retirement stays BOTH-only'),
                ]
                for name, filename, before, after, case, assertion in mutants:
                    path = source / filename
                    original = path.read_text()
                    if original.count(before) != 1:
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
