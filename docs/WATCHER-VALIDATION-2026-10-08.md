# Watcher validation — 2026-10-08

Research PR: [#41](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/41).
Starting head: `4939a7e70332a66336a2ccf7e7bbb2292eae39ca`.
Reference stable source/release: `v1.6.0`, commit `e4542adb9fe47cccb5bc8ee5b3cffdeeec702513`.

## Identity and conditions

AYN Thor, Android 13, firmware `Thor_V1.0.0.377_20260206_165408_user`.
Fingerprint: `qti/kalama/kalama:13/TKQ1.231222.001/eng.Thor.20260206.163241:user/release-keys`.
USB powered, battery 100%, initial battery temperature 30.0 C. Screen timeout
was 60 seconds; the owner maintained awake windows. No settings were changed
by the collector. Dashboard was closed by the owner. Endpoint checks cannot
exclude every transient state change or interaction within a window.

Installed package: versionName 1.6.0 / versionCode 70, runtime identity 1.6.0.
Installed APK SHA-256:
`eb13d6e307b477b863c032e966fbeb257dbd6f71e96024086d90d62bef872414`.
Published APK SHA-256:
`2cf04fc692898a2a582781838f3026555bc7cc4e233226bfad677840e329cf00`.
The complete ZIP entry/hash comparison found **no different entries**, including
classes.dex, AndroidManifest.xml, resources and META-INF. Disassembled smali trees
also had no differences. Both APKs verify with certificate SHA-256
`727d4850779bed1e51018108e13bc399d4da38cfc68f4f7504120ad5e2dad6fc`.
Thus installed code matches published stable; the APK containers are not byte
identical. Do not claim installation of the exact published asset in this run.

Daemon PID 6911, SettingsProvider host PID 2185, composer PID 2305,
SurfaceFlinger PID 2319 at preflight. The provider dump maps authority `settings`
to `com.android.providers.settings/.SettingsProvider`, hosted in `system`
(system_server); it is not a standalone process named after its package.
CPU Fix property was 1. Existing boot trace reaches CPU_ATTEMPT_APPLIED and
BOOT_READY for the current boot. This is historical trace evidence, not a
new boot or CPU Fix transition test.

## Source audit and supported corrections

- Idle Settings wait remains 500 ms; callbacks, mode edges and repair scheduling
  open a 1600 ms burst with a 20 ms timeout. A callback can end that timeout
  earlier. There is no hard 50/s cap or storm limiter. Duplicate events coalesce
  only when several arrive before the same DRM snapshot.
- Capturing a DRM generation before I/O and consuming only that generation
  preserves later callbacks. Host tests cover multiple queued callbacks,
  callbacks from another thread during simulated I/O, and older completions.
- **Fixed:** a callback after sampling began but before `wait()` could lose its
  monitor notification. Its dirty generation survived, but processing waited
  for a burst timeout (normally up to 20 ms; a long operation outlasting the
  burst could leave the 500 ms idle wait). The watcher now captures generation
  before the Settings Binder read and skips sleep if that generation changed.
  The zero-delay case returns; it never calls `Object.wait(0)`.
- The 1 s DRM rule is a safety interval, not a global maximum read rate. Stable
  TOP only forces reads when `display.power.state=1`, indicating possible wake;
  normal confirmed TOP has property 0 and uses the safety/event gate. A due
  repair forces reads; merely pending repair does not force every sample.
- Paired CRTC reads replace two independent opens in watcher and Q telemetry.
  Other action/verification paths still perform their own reads. Counters named
  watcher_drm_reads count attempted paired snapshots, including unknown results,
  not all debugfs opens or successful hardware confirmations.
- A failed/partial DRM read returns unknown values and cannot authorize a write
  through DisplayDecisionModel. The event is nevertheless consumed and the
  timestamp advances; recovery without another event can wait until the next
  safety read. No new retry policy or speculative interval change was added.
- Without callbacks, mode discovery can wait 500 ms rather than stable's 20 ms;
  DRM observation can wait about 1 s plus scheduling/I/O. These are real latency
  tradeoffs, not proof of equivalent responsiveness. Supervisor's 5 s stale
  threshold and bounded watcher restart behavior are preserved. Binder stalls
  and framework replacement still require device validation.
- Lock order stays DisplayHardware -> cadence; callbacks release cadence before
  LidGuard/coordinator work. Cadence never takes the hardware lock. New counter
  strings are built on Q/minute logging; paired reads still allocate readers,
  strings and an array. No allocation or battery benchmark was performed.
- Cadence changes retain boot gates and physical write authority. The separate
  in-place provenance defect described below required a narrow CPU import fix.
  No ContentObserver or new hidden Binder dependency was added.

## Collector defects found on hardware

The original collector failed before its first timing window because Windows
CRLF in the remote thread script caused Android sh `unexpected do`.
`Invoke-AdbText` now normalizes remote line endings. Every ADB invocation uses
the mandatory explicit `-Serial`; this matters with two handhelds connected.

SettingsProvider is resolved from its running provider record at both endpoints,
with PID/starttime checks. Provider CPU is the **whole host process**, including
unrelated system work; it cannot be called provider-only CPU. Process, provider
and thread windows use their respective monotonic timestamps. Optional JSON
retains raw endpoint counters, clock frequency, boot ID and physical conditions.
`/proc/PID/io` is denied to adb shell on this firmware; no root change or tracing
was used to manufacture I/O results. Logcat's latest cumulative metric remains
context only, never a measured window delta.

## Results and remaining gates

Hardware A/B used three awake windows per mode and implementation. CPU and
context-switch reductions below are measured, rather than inferred from cadence.
Exact stable Settings call and DRM-open counts require additional suitable
instrumentation; procfs CPU/context-switch counters do not establish those counts.
USB-powered windows cannot establish battery-life savings.

Stable awake baseline (three windows each; CPU percentage of one core, not the
whole eight-core SoC):

| Scenario | Daemon CPU % | Watcher voluntary switches/s | Watcher involuntary switches/s | system_server CPU % |
|---|---|---|---|---|
| BOTH | 0.886 / 0.888 / 0.871 | 98.082 / 98.001 / 97.980 | 0.131 / 0.164 / 0.066 | 1.31 / 1.26 / 1.25 |
| TOP, CRTC 1/0 | 3.244 / 3.153 / 3.212 | 79.55 / 82.24 / 81.59 | 17.95 / 14.72 / 15.08 | 2.58 / 2.53 / 2.50 |

Raw windows are approximately 60.8–62.0 s, including collection endpoint
overhead. Every accepted awake window was Awake at both endpoints, with the
expected mode/property/CRTC pair. An earlier valid procfs window was Asleep at
both endpoints (2.747% daemon CPU) and is **excluded** from awake comparisons.
The first CRLF-failed attempt is not a measurement. Another early window lacked
physical-condition snapshots and provider metrics, so it is also excluded.

| Mode | Stable daemon CPU % | Exp2 daemon CPU % | CPU reduction | Stable watcher voluntary switches/s | Exp2 switches/s | Reduction |
|---|---:|---:|---:|---:|---:|---:|
| BOTH | 0.882 | 0.093 | 89.41% | 98.022 | 3.997 | 95.92% |
| TOP, CRTC 1/0 | 3.203 | 0.350 | 89.06% | 81.129 | 3.990 | 95.08% |

Exp2 individual CPU windows were 0.099 / 0.082 / 0.099% in BOTH and
0.339 / 0.324 / 0.388% in TOP. Watcher involuntary switches/s fell from
0.120 to 0.011 in BOTH and 15.917 to 0.022 in TOP. Whole system_server CPU
averaged 1.276 -> 0.596% and 2.541 -> 1.336%, respectively; this includes
unrelated services and is not a provider-only attribution.

Timestamped candidate minute-counter deltas show approximately 1.997 Settings
samples/s and 0.999 attempted paired DRM snapshots/s in BOTH; 1.991 and 0.995
in TOP. These are watcher-path counters, not all process debugfs opens.
Stable has no matching counters and procfs I/O is denied. Therefore the requested
**actual Settings/DRM count reduction percentages remain unmeasured**. Source
estimates of 96% fewer Settings samples and 75% fewer DRM cycles are not hardware
A/B count results. No battery or gameplay benefit follows from these CPU results.

The physical switch into TOP increased mode_changes without increasing
display_events. Callback registration succeeded, but this AYN transition was
handled by the safety poll. Callback acceleration is not established on this
firmware; fallback latency remains part of the acceptance decision.

The owner performed stable TOP -> BOTH -> TOP -> sleep/wake -> BOTH and reported
normal images without flash, failure or abnormal delay. A read-only debugfs
timeline captured the expected CRTC states; its observer interval was roughly
0.12–0.18 s including command cost. Stable logcat wake -> WAKE_REPAIR OFF took
1.240 s; first observed top-active -> bottom-inactive took 0.750 s. These have
different start definitions and are one sample, not latency distributions or
direct evidence of every internal OFF_OK field. Logcat monotonic and procfs
uptime differ by suspend time; their absolute timestamps must not be mixed.

Exp2: the owner performed TOP -> BOTH -> TOP and two power-button sleep/wake
cycles with the dashboard closed, reporting normal images without flash, failure
or abnormal delay. Debugfs captured 1/0 -> 1/1 -> 1/0 and both sleep/wake
recoveries ending 1/0. Wake -> WAKE_REPAIR OFF was 1.219 and 1.262 s,
versus stable's one 1.240 s sample. First observed top-active -> bottom-inactive
was 0.760 and 1.000 s, versus stable's 0.750 s. The second wake had a different
observed order (top active before bottom became active); this and observer
granularity prevent a claim of identical latency distributions. A callback-driven
EVENT ON CONFIRMED appeared on the second wake. Thus callbacks do arrive on
some wake paths, although the earlier physical mode edge did not increment them.
No direct authenticated Q/OFF_OK field was captured; successful repair logs and
final CRTC state provide narrower confirmation. No lid-close/open cycle was
measured; power-button sleep/wake must not be relabeled as lid testing.

Long TOP idle: **PASS at the observed sampling resolution**. After explicit owner
authorization, screen timeout changed from 60 s to 660 s; the owner woke the
device and left it untouched in TOP, dashboard closed. All 113 read-only snapshots
across 602.64 s device uptime (602.61 s host elapsed; collector ended at 607.97 s)
were Awake, mode 1, CRTC 1/0, CPU property 1, same boot/composer/SurfaceFlinger
and single daemon. Sampling was roughly every 5.4 s; sub-interval transients
cannot be excluded. Ten consecutive minute-counter intervals spanned 602.860 s
with 1200 samples and 600 paired DRM attempts (1.991 / 0.995 per second), no
new display callbacks/mode edges/repair pulses, and no additional burst waits.
This demonstrates continued watcher progress, not a direct Q health-field read.
No watcher failure was logged. The interrupted earlier natural-rest attempt is
excluded. No deep-sleep/battery claim is made with USB/ADB observation.

## In-place CPU provenance blocker and exp2

The owner authorized installation/open of signed exp1 in awake BOTH. Installed
hash matched the candidate. The standard prior-daemon grace took about 30 s,
followed by guarded replacement and BOOT_READY. Boot ID, composer and
SurfaceFlinger PIDs and CPU property 1 remained unchanged. No CPU transition
or kernel reboot was performed.

However, the new daemon logged EARLY_CPU_ATTEMPT_REJECTED and
BOOT_CPU_FIX_NOT_APPLIED; the dashboard showed CPU FIX NOT CONFIRMED. Further
physical testing stopped. Both imported records were VALID. Existing trace
showed the predecessor importing RESTART_REQUESTED and progressing to APPLIED.
The import model in this branch is identical to published v1.6.0: it accepts
only byte-identical current-boot records and rejects legitimate durable progress
when a later daemon re-reads the original early record. A host regression
reproduced this failure before correction. This is an inherited handover defect,
not evidence that changing watcher intervals disables the hardware CPU Fix.

Exp2 keeps a completed durable record only when the early phase is
RESTART_REQUESTED, durable phase APPLIED, boot/desired/previous/baseline composer
all match, and completion time is not earlier than request time. It neither
overwrites the durable proof nor replays the early phase. Existing live property
and successor-composer validation still applies. Conflict, corruption, earlier
completion, saved OFF and FAILED cases remain rejected. Narrow prior-daemon
allowance accepts exp1 only under the existing authenticated BOTH/1/1 gate.

Exp2 source commit: `b2387eb`; runtime identity `1.6.0-watcher-exp2`.
Signed APK SHA-256:
`4eacdc032978b27f5cc38f7045cd4c00bdfb1c99ec6ce99a0a9b9700056bc535`.
The owner separately authorized this installation; physical confirmation and
subsequent A/B are recorded separately from the host regression tests.

On hardware, exp2 logged EARLY_CPU_ATTEMPT_PROGRESS_KEPT (APPLIED),
CPU_GATE_COMPLETE with composer_restart=not_needed, then BOOT_READY. One daemon
remained active. Existing CPU property, boot ID, composer and SurfaceFlinger
identities stayed unchanged. This validates completed-proof preservation during
this handover; it does not replace a fresh CPU Fix activation or cold-boot test.

## Host validation

Windows host: PowerShell 7.6.5, Temurin JDK 17.0.18+8 and apktool 3.0.3.
`scripts/test-boot-lid.ps1` ran the collector fixtures and real cadence bridge,
then BootLidModel, CpuBootAttemptModel, PreComposerCpuProofModel, early CPU
boot-hook model/script/import/gate, WatcherCadenceModel, BootLatency,
DaemonLaunchModel/script, PropertyState, DisplayDecisionModel,
DisplayGenerationModel, IpcPeerPolicy, LegacyDaemonIdentity,
PreviousSecureDaemonIdentity, HallNodeModel, HandoffRecoveryModel and DaemonArgs
tests. `scripts/test-dashboard.ps1` ran DashboardStateModel,
CpuWarningModel and UpdateVersion tests. All passed on exp2.

- Boot/lid/IPC/CPU provenance/model suite: PASS.
- Watcher cadence model: PASS, 232 assertions (previously 22).
- Real cadence bridge with host clock/log fixtures: PASS, monitor notification
  and concurrent DRM generation handling. This does not emulate Android Binder.
- PowerShell collector fixtures: PASS, serial, LF normalization, actual provider
  mapping, missing provider, recycled PID and unknown counters; no ADB calls.
- Dashboard, CPU warning and updater model suite: PASS.
- Unsigned and signed APK build, DEX merge and alignment: PASS.
- Signed candidate v1/v2/v3 and established certificate: PASS.
- Research signed APK SHA-256:
  `d2a192e36bf19a0ca969ad18dfeac7ceb65a3f4118b9c2a13ae5cffbc2fbe999`.
- Whitespace diff check: PASS. Build and CodeQL checks on `b2387eb`: PASS.
  A trial merge-tree with fetched main
  succeeded without conflicts; no actual merge or main modification was made.

## Raw evidence

Local evidence directory: `C:\Temp\jesty-thor-pr41-evidence-20261008`.
Raw APKs, logs and decoded APK trees remain outside Git. See the local SHA-256
manifest for immutable references to measurements and test logs. The report
contains aggregate results only; local raw diagnostics may include device state.

Key files: `baseline-{both,top}-awake-{1,2,3}.json` and
`experimental-{both,top}-awake-{1,2,3}.json` retain the twelve accepted windows;
`summary.csv`, `comparison.csv` and `candidate-counter-rates.csv` retain the
derived tables. `baseline-physical-*` and `experimental-physical-*` retain
timelines, edge tables and logcat. `cpu-import-regression-before-fix.txt` records
the failing regression; `exp2-install-postflight.txt` records actual recovery.
`host-boot-lid-exp2.txt`, `host-dashboard-exp2.txt` and `build-signed-exp2.txt`
record host/build verification. Analysis helper scripts also stay locally with
the archive. The committed [SHA-256 index](WATCHER-VALIDATION-2026-10-08-SHA256.txt) identifies the evidence files without
publishing their raw contents.

## Recommendation and remaining work

**GO to prepare and test v1.7.0; publication remains pending final validation.**
The initial INCONCLUSIVE recommendation applied the full exploratory evidence
list as a promotion gate. After discussion, the owner accepted candidate
preparation based on measured idle CPU/context-switch benefit and scoped
display/wake correctness. Gameplay and battery studies can follow later and are
not required for this change. Missing exact baseline request-count percentages
limit that claim, but do not invalidate measured CPU benefit; those count targets
are no longer a blocker to candidate preparation.

Remaining publication gates are the exact production-identity signed candidate,
its guarded handover/CPU proof, repeated BOTH/TOP and wake checks with direct
OFF_OK/health evidence, and separately authorized supervised startup validation.
See [v1.7.0 checklist](VALIDATION-1.7.0-PENDING.md). No fresh boot was performed
during the A/B investigation. Repeated lid cycles, larger latency distributions,
game/load callback rates and framework replacement remain unmeasured; this run
does not establish gameplay benefit or universal callback-storm resilience.

Do not tune the intervals, add a ContentObserver or add an event rate limiter
solely from these results. Investigate missing mode-edge callback acceleration
only if repeated latency testing shows an unacceptable user-visible difference.
The inherited CPU import fix deserves its own focused review because it affects
proof acceptance independently of watcher scheduling.

At completion of the A/B phase the device ran signed `1.6.0-watcher-exp2`
(package 1.6.0/70). The subsequent authorized v1.7.0 installation and its final
checks are recorded in [the candidate checklist](VALIDATION-1.7.0-PENDING.md).
Published v1.6.0 remains the recommendation for normal
use. Rollback means installing the verified published asset with owner approval;
the published daemon does not recognize exp2 for in-place prior-daemon handover,
so a supervised normal reboot may be required. Do not kill services, edit proof
markers, clear app data or reboot automatically. This run changed no display/CPU
setting; later, at the owner's explicit request, screen_off_timeout changed from
60000 to 660000 ms for the long awake test. It remains 11 minutes unless the owner
requests restoration. No release, merge or main-branch change was performed.
