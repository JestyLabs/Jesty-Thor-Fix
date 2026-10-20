# S3 — native channel contracts

Programme **JTF-QUALITY-2026-10-17 / S3**, tracker [#95](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95). Baseline `d03529f5b111bbe30c1bbf70c8597c75da6a6b0f`. Test-only; no production change or new security-fix claim.

The existing real transport/client HOST suites use inert Android endpoints; their `Os.open` fixture deliberately cannot establish native exclusive ownership. The existing installed JNI retirement suite exercises a different transport. This increment fills that concrete gap by executing unchanged `SecureChannel`, `IpcPeerPolicy` and `DaemonLaunchModel` on the owned generic Android AVD. It recovers an earlier unvalidated test draft rather than adding duplicate policy tests.

## Scope and proof

Four native contracts: root peer connects; a separate process denied the held lock cannot alter lock contents or live socket inode; a retained stale inode is recovered after holder exit; a real wrong-UID peer is rejected before command bytes. Two compiled controls deliberately move the stamp before lock ownership or bypass peer admission. Both must fail the targeted contract; build failure is not accepted evidence.

The fixture has no production command handler, daemon runtime, vendor bridge, CPU or display operation. Exactly one path literal is adapted only in the temporary compilation. Native process/UID/filesystem methods remain real. See [fixture procedure and boundaries](../../tests/android/secure-channel-native/README.md).

Controller review found two flaws in the unvalidated draft: weak admission/provenance prerequisites and a cleanup exception that could leave a PASS result. HOST regression executes the actual old main path and detects the latter; the corrected main writes overall PASS only after cleanup. Ten HOST tests cover target admission, changed input rejection before ADB, missing stale precondition, preservation of existing evidence and truthful result/cleanup reporting. Source/fixture/JAR checks happen before root/writes and are rechecked at execution. No host metadata is reported as native evidence.

| Tier | Gate |
| --- | --- |
| HOST | PASS: controller guards; preserved draft fails the intended cleanup-report regression. Existing transport/client/IPC suites remain required. |
| BUILD ONLY | PASS: actual SDK34/JDK17 compilation and D8 for baseline plus both mutations. No device contact. |
| ANDROID | Exact-head full CI must execute all four native contracts and both compiled controls, retain the result and confirm cleanup. PR checks/result artifact record the observed outcome. |
| THOR / SELinux / vendor | NOT RUN / UNKNOWN. Generic emulator root/app-process behavior is not private app SELinux or vendor/firmware acceptance. |

## Delivery boundaries

CI gains one HOST guard step, a disposable build and a final native step in its existing API33 job. It retains only the sanitized result in the existing evidence artifact. Existing tests, timeouts, protected signing and publication policy remain unchanged. No `src/`, APK identity/version/permission, trust policy, CPU/boot timing, hardware action or repository protection setting changes.

Admission observes the pinned emulator/AVD, qemu/API/ABI and absent vendor service before root/writes, then recertifies after root. Owned holders self-exit and cleanup is part of acceptance. Source/workflow revisions, JDK and payload digests distinguish what ran. A green native result does not certify the whole privileged boundary or establish a higher independent quality score.

Independent review and S6 retain the surrounding process-incarnation, vendor Binder availability, private-app SELinux and physical upgrade/presentation limits. Do not intentionally provoke a permanent display stall, bypass a denied peer, use numeric-PID fallback in this fixture or infer successor READY from transport connection.
