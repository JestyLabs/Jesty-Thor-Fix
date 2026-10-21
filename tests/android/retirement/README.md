# Installed retirement JNI fixture

Candidate: **1.7.6/code86**. Exact published predecessors: **1.7.1, 1.7.2, 1.7.3, 1.7.4 and 1.7.5**.
The frozen source hashes live under `tests/retirement/published/`; code76's missing
Q runtime field is preserved. Prepared sources are not an executed platform PASS.

## Host checks

From the repository root, run `tests/android/retirement/materialize.py` with
`--predecessor <version> --verify-only` for each origin, then
`python3 -B tests/android/retirement/check_sources_host.py` and
`python3 tests/test_process_binding_guard.py`.
Materialization records input/output SHA-256 and changes only the explicit
fixture namespace and JNI export prefix. Generated source copies are removed.
The host compile uses inert Android API shapes; no native method executes.

## Installed Android checks

The CI workflow provides JDK17, SDK platform34/build-tools35 and NDK27.2.12479018.
The runner installs no dependencies itself. On Linux:

```sh
python3 scripts/test-retirement-android.py --sdk "$ANDROID_HOME" --java "$JAVA_HOME" --build "$RUNNER_TEMP/retirement-android" --build-only
python3 scripts/test-retirement-android.py --sdk "$ANDROID_HOME" --build "$RUNNER_TEMP/retirement-android" --serial emulator-5554 --avd pr67-ci-api33
```

Use a new external build directory, a disposable signing key and package
**org.example.rfixture**. Never mix fixture sources/stubs into the production APK.
The test APK is non-debuggable. The already certified root supervisor writes
private acknowledgements atomically with verified app ownership; no run-as or
debuggable exception is needed.
Before mutation, the driver requires an emulator-shaped serial, the exact owned
AVD `pr67-ci-api33`, qemu=1, API33, x86_64 and absent PServerBinder. It recertifies
after root; every ADB command is serial-specific. Physical/foreign targets fail.

Twenty update cases cover native support and deliberately omitted support in
BOTH/TOP for all five origins. Four additional recovery cases use the current
real producer in BOTH/TOP with either native variant and a synthetic failed-start
phase. The `transport` route invokes actual LiveRetirement/JNI/fixed R:
app-opened descriptors, both I/Q rounds, kernel peer identity, retained process,
image guard, COMMIT, confirmed exit and fresh receipt remain product logic.
There is no caller-selected PID or numeric-PID signaling fallback.

Recovery shares that mechanism, with two authenticated I observations and its
separate same-version failed-start policy; it does not require a READY predecessor
or read Q for authorization. The fixture explicitly seeds prior activation, then
uses the actual durable preferences fence before COMMIT and clears it only after
the clean exit receipt. This synthetic setup is not a published-version upgrade
or real firmware failed-start acceptance. Exact-head retained results establish
which of the twenty cases actually passed.

The image lacks vendor Binder. An external process-owning supervisor replaces
only submission with two fixed operations: launch the validated R helper nonce,
then launch the held successor after independently waiting for old exit. This
is not full AutoService/vendor deployment. The separate `service` and `adapter`
instrumentation routes still require the approved vendor context.

An inert default D entry runs the actual frozen producer/server and singleton
lock. The native image guard still requires seven NUL-terminated prefix tokens:
`app_process`, `/`, `D`, `[01]`, `hold|run`, `[01]`, `[01]`. Do not loosen it for a
fixture. The root loader must find the extracted JNI library beside one installed
base APK; a host library, arbitrary path or Java native stub is not equivalent.

Candidate STARTING must remain ACTIVATION PENDING. Only after the synthetic READY
marker may authenticated I/Q yield ACTIVE. Kernel boot must remain unchanged.
Missing JNI must preserve the old peer, deny helper/successor and preserve only
the activation permission that existed before the trial.
Panels, CPU, Guard, watcher and boot readiness are synthetic; hardware-changing
requests throw. Generic Android does not prove vendor launch, firmware readiness
or physical display behavior. No firmware/framework changes belong here.

## Evidence and cleanup

`provenance.json` contains source/artifact hashes. Case runtime outputs and the
global `results.json` are retained by CI, excluding the disposable private key.
The global PASS is written only after all cases and cleanup succeed, with vendor
and physical exclusions explicit. Parent wait records independently confirm exit
before successor launch; a submitted command is not that proof.

The private STOP marker terminates only fixture entries; their 120-second expiry
is a cleanup bound, never retirement success. Cleanup removes only the fresh
owned emulator directory and disposable package. Uninstall must report Success
and package listing must confirm absence; failure prevents global PASS.

Stop on unexpected UID/image/process/boot, missing receipt, timeout, hardware
request, lost ordering or cleanup failure. Host, Android and physical evidence
remain separate. Exceptional reboot recovery is not normal no-reboot acceptance.
