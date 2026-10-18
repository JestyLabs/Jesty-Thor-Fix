#!/usr/bin/env python3
"""S1: contract-check the production watcher CALL GRAPH without Android or Thor.

A structural tripwire, not proof of runtime scheduling, physical presentation,
or a black-screen root cause. Never imports or executes privileged product code.
"""
from pathlib import Path
import re
import shutil
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[1]
WATCH = "apk/smali/com/thor/displaypowertest/DaemonWatchThread.smali"
EVENT = "apk/smali/com/thor/displaypowertest/DisplayEventCallback.smali"
SUPERVISOR = "src/com/thor/displaypowertest/WatcherSupervisor.java"
RUNTIME = "src/com/thor/displaypowertest/DaemonRuntime.java"
SECURE = "src/com/thor/displaypowertest/SecureChannel.java"
DEPENDENCIES = (WATCH, EVENT, SUPERVISOR, RUNTIME, SECURE)

JAVA_CALL = re.compile(r"\bDisplayActionCoordinator\s*\.\s*onWatcherSample\s*\(")
SMALI_CALL = re.compile(
    r"Lcom/thor/displaypowertest/DisplayActionCoordinator;->"
    r"onWatcherSample\(Ljava/lang/String;\)V"
)


def check(root: Path):
    """Return a compact, no-device result; raise on changes to caller safety."""
    locations = []
    for folder, suffix, expression in (
        ("src", ".java", JAVA_CALL),
        ("apk/smali", ".smali", SMALI_CALL),
    ):
        for path in sorted((root / folder).rglob("*" + suffix)):
            data = path.read_text(encoding="utf-8")
            for match in expression.finditer(data):
                locations.append(path.relative_to(root).as_posix())
    if locations != [WATCH]:
        raise AssertionError("S1 watcher call-site drift: " + repr(locations))

    watcher = (root / WATCH).read_text(encoding="utf-8")
    if watcher.count(".method public run()V") != 1 or watcher.count(":watch_loop") != 2:
        # Label definition + target reference must both be present.
        raise AssertionError("S1 watcher main-loop layout changed")
    # Use literal call tokens, rather than regular-expression source strings.
    required = (
        "WatcherCadence;->beginSample()V",
        "IContentProvider;->call(",
        "DisplayActionCoordinator;->onWatcherSample(Ljava/lang/String;)V",
        "WatcherSupervisor;->noteSample()V",
        "WatcherCadence;->awaitNextSample()V",
        "goto :watch_loop",
    )
    positions = [watcher.find(token) for token in required]
    if any(i < 0 for i in positions) or positions != sorted(positions):
        raise AssertionError("S1 Settings read -> coordinator -> wait serial order changed")

    supervisor = (root / SUPERVISOR).read_text(encoding="utf-8")
    members = ("new DaemonWatchThread()", "watcher.start();", "watcher.join();")
    positions = [supervisor.find(item) for item in members]
    if any(i < 0 for i in positions) or positions != sorted(positions):
        raise AssertionError("S1 supervisor must join one watcher before restart")
    if supervisor.count("new DaemonWatchThread()") != 1:
        raise AssertionError("S1 unexpected watcher launch in supervisor")

    runtime = (root / RUNTIME).read_text(encoding="utf-8")
    if not runtime.count("WatcherSupervisor.start();") == 1:
        raise AssertionError("S1 watcher supervisor start multiplicity changed")
    lock = runtime.find("SecureChannel.listen();")
    start = runtime.find("WatcherSupervisor.start();")
    if lock < 0 or start < lock:
        raise AssertionError("S1 watcher started before exclusive daemon socket/lock")
    secure = (root / SECURE).read_text(encoding="utf-8")
    lock = secure.find("tryLock()")
    bind = secure.find("bound.bind(address())")
    if lock < 0 or bind < lock:
        raise AssertionError("S1 daemon exclusive lock no longer precedes socket bind")

    callback = (root / EVENT).read_text(encoding="utf-8")
    if "DisplayActionCoordinator;->noteDisplayOn()V" not in callback:
        raise AssertionError("S1 display callback no longer signals display-on observation")
    for forbidden in ("DisplayHardware;->apply(", "DisplayActionCoordinator;->requestFix("):
        if forbidden in callback:
            raise AssertionError("S1 display callback unexpectedly has a direct power writer")
    return {
        "mode_sample_call_sites": 1,
        "sample_producer": WATCH,
        "watcher_loop_order": "Settings read -> coordinator -> await",
        "supervisor": "one watcher start, joined before restart",
        "daemon_lock": "acquired before watcher start and socket bind",
        "display_callback": "passive acceleration and display-on notification",
        "tier": "static production-source host audit ONLY",
    }


class WatcherProducerContract(unittest.TestCase):
    def test_real_sources(self):
        evidence = check(ROOT)
        self.assertEqual(evidence["mode_sample_call_sites"], 1)

    def copy_sample(self, destination):
        root = Path(destination)
        for relative in DEPENDENCIES:
            target = root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(ROOT / relative, target)
        return root

    def test_negative_unexpected_java_producer(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.copy_sample(folder)
            extra = root / "src/com/thor/displaypowertest/SecondProducer.java"
            extra.write_text(
                "class SecondProducer { void run() { "
                'DisplayActionCoordinator.onWatcherSample("1"); } }',
                encoding="utf-8",
            )
            with self.assertRaisesRegex(AssertionError, "call-site drift"):
                check(root)

    def test_negative_missing_join(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.copy_sample(folder)
            p = root / SUPERVISOR
            p.write_text(p.read_text(encoding="utf-8").replace(
                "watcher.join();", "watcher.waitForOtherWork();"), encoding="utf-8")
            with self.assertRaisesRegex(AssertionError, "must join"):
                check(root)

    def test_negative_unlocked_start(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.copy_sample(folder)
            p = root / RUNTIME
            p.write_text(p.read_text(encoding="utf-8").replace(
                "SecureChannel.listen();", "SecureChannel.skipLock();"), encoding="utf-8")
            with self.assertRaisesRegex(AssertionError, "exclusive daemon"):
                check(root)

    def test_negative_callback_panel_writer(self):
        with tempfile.TemporaryDirectory() as folder:
            root = self.copy_sample(folder)
            p = root / EVENT
            p.write_text(p.read_text(encoding="utf-8")
                         + "\ninvoke-static {}, Lcom/thor/displaypowertest/"
                           "DisplayHardware;->apply(ZLjava/lang/String;)Z\n",
                         encoding="utf-8")
            with self.assertRaisesRegex(AssertionError, "direct power writer"):
                check(root)


if __name__ == "__main__":
    unittest.main(verbosity=2)
