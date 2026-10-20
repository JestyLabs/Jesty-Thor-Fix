# S2 native process-binding mechanism experiment

This fixture tests generic Android process descriptors and Unix peer credentials
using only children it creates. It accepts no external PID, never contacts the
application socket or vendor service, and changes no production implementation.
It is not a daemon, an update, or a test of the published predecessor protocol.

Build with pinned NDK 27.2.12479018 for x86_64/API33. The existing CI emulator job
runs it last, on the exact disposable `pr67-ci-api33` AVD. The controller rejects
physical serials before any ADB call; it also requires the expected AVD, qemu,
API, ABI and absent vendor bridge before root or filesystem writes. Only its
fresh fixture directory and known files are removed. Production signing is not
used. Unsupported native operations fail the gate rather than being skipped.

The native cases cover descriptor-targeted exit without affecting a sibling,
wrong server UID before command bytes, endpoint replacement between reads,
exit before acquisition, exit before signalling, unsupported acquisition,
submission without exit, and server rejection of a root client while admitting
the synthetic application UID. The positive route restores its effective root
UID after connecting as the application UID; this exercises generic kernel
credentials without broadening the server's UID admission. Two executable
fault controls remove peer admission or suppress the signal and must fail.

```sh
python3 tests/test_process_binding_guard.py
python3 scripts/test-process-binding-android.py --sdk "$ANDROID_HOME" \
  --build "$RUNNER_TEMP/process-binding" --build-only
python3 scripts/test-process-binding-android.py --sdk "$ANDROID_HOME" \
  --build "$RUNNER_TEMP/process-binding" --serial emulator-5554 --avd pr67-ci-api33
```

Results are sanitized fixed case names, source/binary hashes and tool versions
in the CI log; no new uploaded artifact, device trace or process identifier.
A PASS is **generic native Android fixture evidence only**. It does not prove
an installed application's SELinux permissions, vendor Binder operation, legacy
handover, successor READY or Thor compatibility. Numeric PID reuse is not
forced. The actual code76 compatibility correction remains a separate product
proposal, with authentication and physical acceptance gates preserved.
