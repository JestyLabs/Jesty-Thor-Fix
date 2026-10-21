# Native channel fixture — disposable API33 AVD only

This executes actual `SecureChannel`, `IpcPeerPolicy` and `DaemonLaunchModel` against Android `LocalSocket`, `Os` and cross-process file locks. No daemon command handler, Binder/vendor, CPU or panel control is compiled. Production sources are unchanged; the builder replaces exactly one `DATA_DIR` literal in a temporary copy with a fresh fixture directory. The original source and fixture/Dex hashes are recorded.

Prerequisites are the existing JDK17, Android API34 compilation SDK/build-tools35 and the CI-owned `pr67-ci-api33` API33 x86_64 default AVD. Build-only does not call ADB:

```text
python tests/test-secure-channel-native-guards.py
python scripts/test-secure-channel-native.py --sdk <existing-sdk> --java <existing-jdk17> --build <fresh-external-build> --build-only
```

The CI runs the native step last on its disposable emulator. Runtime requires explicit `--serial emulator-5554 --avd pr67-ci-api33 --output <fresh-external-output>`. The controller rejects physical/malformed targets and other requested AVDs before any command; actual AVD/qemu/API/ABI/absent-vendor observations must pass before emulator root or fixture writes and again after root. It validates source/fixture/JAR hashes before those operations. Never aim it at a Thor or another emulator.

Four contracts run in separate fixture processes:

1. A root-authenticated endpoint connects successfully.
2. A contender denied the exclusive lock preserves the holder's lock sentinel and live socket inode. The holder uses its existing locked descriptor, avoiding an extra close on its lock inode.
3. After the holder exits, the old socket must still exist with its captured inode before the actual transport recovers the stale endpoint. A successful clean bind without that precondition cannot count as stale recovery.
4. An actual non-root peer is rejected before any command byte is sent.

Two compiled mutations must be detected: writing the boot stamp before exclusive acquisition changes the sentinel, and bypassing daemon peer admission accepts the wrong-UID peer. Compilation failure is not a detected behavioral mutation.

All fixture processes have a self-exit deadline and a stop-file path. Only the unique, previously absent fixture directory is removed. Overall PASS is written only after cleanup succeeds; run and cleanup errors remain recorded separately. Existing evidence cannot be overwritten. HOST tests prove these controller/reporting guards without ADB; they do not prove any native contract.

The retained result contains sanitized emulator certification, source/workflow revisions, JDK and payload hashes; raw compiler details and temporary paths stay outside Git. No boot ID, actual app UID/PID or production key is emitted. This qualifies generic Android transport only: private-app SELinux, Thor firmware, vendor process lifetime, physical display and real update acceptance remain separate.
