# Owner-only Android retirement fixture — SOURCE PREPARED / PLATFORM NOT RUN

No Android command, APK build, native/root execution, install, device operation,
network request or dependency installation was performed to prepare this fixture.
It is not release/device approval. The host activation fixture remains the
executed regression; this directory supplies a separate owner-side integration
recipe and driver, not a new claim of native acceptance.

## Exact-source materialization (Python only, no build)

From `work/`, with the supplied bundle layout intact:

```
python3 -B tests/android/retirement/materialize.py --predecessor 1.7.1
python3 -B tests/android/retirement/materialize.py --predecessor 1.7.2
```

Add `--verify-only` to check all source hashes/namespace-only changes and the
exact missing/present Q runtime field, then remove the generated source copies.
That is an offline source-integrity check, not Android compilation or execution.

The separate JDK17 source-only check is:

```
python3 -B tests/android/retirement/check_sources_host.py
```

It compiles both actual frozen producers/servers and their default `D` entry,
plus the actual candidate Java retirement route/JNI declarations/default `R`
entry and new owner driver sources. It reuses existing inert Android interfaces,
adding only missing compile-time SDK shapes. The four UI construction/settings
types (`DashboardLayout`, `DashboardViews`, `DashboardSettingsController`,
`DashboardStyle`) are inert API shapes in this HOST check; actual renderer/models
are compiled. **No** LiveRetirement/PServer/native Java adapter is replaced.
No fixture class/main, platform operation or native method executes.
The materialized owner Android sources retain all four real UI classes.
Compilation files stay beneath `work/` and are removed before overall PASS.
This is not an Android SDK/UI build, APK, native/kernel or device acceptance.

Each invocation creates a NEW source directory beneath `work/`. Do not
materialize over a checkout or modify `reference/`. `SOURCE-MAP.json` records
input/output SHA-256 for every copied file. Review it before building. Generated
source directories are disposable owner artifacts, not return-package binaries.

The predecessor compilation contains the frozen version's **actual**
`DaemonState`, `DaemonCommandHandler`, `DaemonIdentity`, `DisplayDecisionModel`,
`DaemonIpcServer`, `SecureChannel`, `IpcPeerPolicy` and `DaemonLaunchModel`.
The default-package inert `D` entry sets BOTH/confirmed state and invokes that
actual server with the actual kernel PID. The original code76 Q producer is not patched to
add `runtime_version`. The code80 producer remains unchanged too.

The candidate compilation contains the **actual** AutoService/PServer,
LiveRetirement Java app/root controllers, RetirementNative JNI declarations and
loader, default-package R entry, policy, SocketClient/SecureChannel, launch
script, identity, producers/server and dashboard classes. **Never include**
`tests/integration/reboot-activation/fixtures`, parent HOST stubs or a replacement
LiveRetirement/PServer/RetirementNative in this Android compilation.

Both source sets are isolated as `org.example.rfixture`; no original application
package/data is targeted. The only product edits in the generated copies are:

1. `com.thor.displaypowertest` to the shorter disposable `org.example.rfixture`,
   including Java imports, fixed `pm path` command and app-data path literals.
2. Native `Java_com_thor_displaypowertest_RetirementNative_` JNI export prefix to
   `Java_org_example_rfixture_RetirementNative_`.

The materializer makes these substitutions explicitly and records both hashes.
Package version/code stay 1.7.3/83 for candidate app/runtime agreement; predecessor
wire identities stay their exact frozen versions. No version allowlist,
peer/UID check, lock, policy, framing, byte request, timeout or receipt is changed.

## Owner build/materialization contract — NOT RUN

Use separately authorized existing Android/JDK17 tooling on the owner PC. No
download/install is implied. Source-build the materialized native C for each
supported ABI with the reference NDK **27.2.12479018** and the candidate build's
current native compile/link flags; record tool/source hashes. Package the resulting
`libretirement.so` under the corresponding APK `lib/<abi>/` entry and preserve
`extractNativeLibs=true` from the fixture manifest. The real root loader requires
the extracted library beside a **single installed base APK**, not a host library,
ad-hoc absolute load path, multi-APK CLASSPATH or Java native stub.

Compile/dex/package `candidate/src` with `candidate/AndroidManifest.xml` into ONE
disposable package. Compile/dex `predecessor/src` into a separate dex-containing
JAR/APK for the old root process; **do not combine the two source sets or put the
candidate artifact on the predecessor CLASSPATH**. Otherwise its duplicate
producer/identity classes could silently resolve to the wrong version.
`DashboardLayout` uses ordinary Android widgets and resource lookup; absent
decorative resources do not validate the product's visual assets. The fixture
minimum API is 26 for `java.nio.file`; Android 13 is the intended generic owner
fixture tier. This does not broaden product compatibility.

The real PServer uses `PServerBinder`. The owner must separately provide
the existing approved bridge in the disposable environment and verify its actual
root helper context. A missing bridge is a blocked native integration, **not**
permission to add an arbitrary command service, `su` fallback, bypass SSO/UIDs,
or change the fixed product command. Stop on wrong package/UID/path/ABI/provenance.

## Owner invocation sequence — NOT RUN; no command is executed by the materializer

Repeat in a fresh disposable fixture state for each exact predecessor and route.
Keep original application/data untouched. These are instrumentation arguments
and Java entrypoint specifications, not authorization to run platform commands.

1. Instrumentation:
   `org.example.rfixture.RetirementFixtureInstrumentation`, arguments
   `stage=prepare`, `predecessor=1.7.1` (or `1.7.2`),
   `owner_approved_disposable=true`.
   It refuses existing preferences/markers and persists a same-kernel baseline
   through real RebootActivation. It must report `PREPARED_NOT_ACTIVATED`.
2. Through the owner's authorized, process-owned fixture supervisor, start only
   the separate frozen producer artifact's **default-package `D` entry** with
   the four normal arguments **`1 run 0 0`**.
   It refuses a non-root/non-disposable path and a mismatched preparation marker,
   then uses actual SecureChannel singleton locking/app-UID admission and
   DaemonIpcServer one-byte I/Q framing. This is the fixture `D` wrapper, **not**
   the actual hardware DaemonRuntime. Its OS command-line image must preserve
   `app_process / D 1 run 0 0`; a renamed fully-qualified entry is not equivalent.
   Keep the native `^app_process / D [01] (hold|run) [01] [01]` root image guard
   in addition to UID/PID and retained-descriptor checks. Do not loosen the guard
   to fit a fixture. The candidate wrapper similarly receives `1 hold 0 0`
   through the actual PServer launch.
   Verify the actual `/proc/<authenticated-kernel-PID>/cmdline` bytes, not a
   flattened display name: the old fixture's seven NUL-terminated prefix tokens
   are `app_process`, `/`, `D`, `1`, `run`, `0`, `0`; candidate uses `hold`.
   The native check follows descriptor acquisition, checks liveness before/after
   the bounded read, repeats on retained-process checks and before signaling.
   No process scan, caller-supplied PID, numeric signal or image-check bypass
   belongs in this fixture. HOST synthetic image acceptance is not that proof.
   Begin the activation stage promptly: the disposable old process self-expires
   after 120 seconds; an expiry is not successful product retirement evidence.
3. Run the same instrumentation with `stage=activate`, the same `predecessor`,
   `route=service`, `expect=success`, `owner_approved_disposable=true`.
   It drives actual AutoService; retirement is actual JNI + fixed R helper +
   four app-opened transferred descriptors + retained process + product receipt.
   It checks old kernel PID/I identity and actual code76/code80 format, then
   requires an authenticated new held STARTING peer and durable live permission.
   The real renderer must remain ACTIVATION PENDING. Only then does the driver
   release the synthetic READY marker and require actual new I/Q READY and ACTIVE.
   Re-rendering old telemetry must never become ACTIVE.
4. In a separate fresh trial, `route=adapter` calls actual
   PServer.stopLivePredecessorIfSafe and asserts its positive returned receipt
   matches the authenticated old peer BEFORE invoking actual held launch. It
   persists real RebootActivation permission only after authenticated STARTING.
   This exposes the receipt return directly; it complements, not replaces,
   the full `route=service` test.
5. In a separate disposable candidate variant **omit** the native library and
   use `expect=unavailable`, preferably `route=service`. The driver first verifies
   that the actual loader fails, then requires the same old root peer to remain
   present, no live permission and no ACTIVE state. This is not a successful
   retirement or successor launch.

The fixture `D` is an explicitly inert successor entry with real
candidate producers/server. CPU, Guard, panels and watcher are synthetic;
BootSafety is deliberately held until the app-private READY marker, **not the
real Android/vendor boot coordinator**. Hardware-changing commands throw.
RootLogFiles uses `/dev/null`, and Direct-Boot CPU opt-in is disabled. Thus
generic Android transport/receipt/activation evidence cannot prove physical
display, CPU, firmware, vendor boot readiness or deployment safety.

## Evidence, cleanup and STOP

Retain exact restored source/build/ABI hashes, old producer I/Q, kernel peers,
helper/control request identity and descriptor exit-before-launch evidence from
the owner's independent process-owned supervisor. The Java driver trusts the
actual product receipt gate; it is **not** a second independent pidfd observer.
A Binder ACK, missing listener or numeric PID check is never exit proof.

The old fixture only self-expires after 120 seconds; it does not implement a
cooperative retirement command. The candidate fixture self-exits on its own
private STOP marker (or the same 120-second expiry), never kills another process.
The driver checks listener absence before reporting its successful READY
function, but explicitly leaves independent process exit/cleanup confirmation
to the owner supervisor. A `PASS_TO_READY_NOT_PHYSICAL_ACCEPTANCE` result is not
overall acceptance; cleanup/exit observer failure must make the trial FAIL.
Unavailable-native trials preserve the old process until controlled owner
cleanup/self-expiry; do not purge sockets or stop a guessed PID.

STOP on UID/PID/path change, stale markers, missing native support, no-effect
signal, malformed/missing receipt, duplicate identity, unexpected hardware
request, lost ordering or cleanup failure. Keep reboot fallback. Receipt
corruption/cancellation/process-interference probes remain separately required
owner/native tests; the executed HOST suite covers their Java control flow,
not SELinux, kernel socket ownership, pidfd or platform signal behavior.
