# App observability and controlled energy investigation

Priority: **P2**. App diagnostics are implemented in this candidate. Device
validation is pending. Controlled energy measurements and any resulting runtime
fix remain **planned**.

## Structured app history and export

The dashboard's **APP DIAGNOSTICS** action previews a report and offers **Save
report** through Android's document picker. The owner chooses the destination;
the app does not upload or send the report. Cancellation exports nothing.

The app keeps at most 64 structured events in its private preferences: activity
creation, foreground/background transitions, activity destruction and changes
in existing foreground telemetry availability, AYN mode, CRTC flags, requested
display/lid choices, desired CPU choice, CPU property and boot action hold.
Desired/requested choices and the CPU property do not prove effective hardware
state or that the composer reloaded a property.
Identical telemetry samples cause no additional history writes. Only existing
authenticated `Q` samples are reused; no new request or command is introduced.
There is no diagnostics timer, sleep collector, wakelock or daemon change.
Asynchronous preference persistence can lose the final event if the process is
killed before persistence completes.

The last foreground sample is labelled with its age. It is neither a sleep
sample nor a current physical-panel measurement. A fresh Activity starts with
unknown sample freshness until telemetry arrives again.

On demand, Android 11/API 30 and later are queried for at most eight historical
process exits of this package. Exported fields are relative age, reason code and
label, exit/signal status and process importance. Unsupported, unavailable and
empty histories are distinguished. The query does not diagnose the separate
root daemon or other packages. Android's retained history can be incomplete;
no record does not prove that a process survived.

Activity destruction is **not** a process-death record. SIGKILL alone does not
identify the actor or establish low-memory pressure. An Android reason is an OS
classification, not proof of a particular vendor service causing the kill.
Reason-code semantics come from the
[Android ApplicationExitInfo API](https://developer.android.com/reference/android/app/ApplicationExitInfo).

Schema: `JESTY_THOR_DIAGNOSTICS_V1`, at most 24,000 characters. Export uses an
allowlist, including when loading persisted history. No raw daemon reply,
logcat, crash stack, ANR trace, exit description, process name, PID, UID, serial,
boot ID, session ID, absolute wall time, filesystem path or account data is
included. Private history timestamps become relative ages; clock rollback
produces unknown age. Invalid values remain unknown.

## Pending: sleep and lower-display power

In the owner-observed 2026-10-08 sleep case, both screens were visually off,
Android was Asleep, both logical displays OFF, TOP CRTC inactive and BOTTOM CRTC
marked active. The display fix was enabled, AYN mode BOTH, policy 60/60. The
existing reconciliation forces lower OFF in TOP mode; this BOTH sleep case is
outside that action. See the separate [refresh investigation PR](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37)
for provenance; raw captures and session identifiers stay owner-local.

Unknown: actual kernel suspend residency, lower-panel rails/scanning, retained
image versus black frames and additional power. Neither visible black nor a
CRTC flag establishes these. Do not infer an overlay, zero consumption or a
failure of the TOP-only fix from the observation.

### Measurement preparation

- Select the measurement method and document its resolution, sample interval,
  units and clock. The app report is not an energy meter. Battery discharge
  integrates total-device draw; charger-input power includes charging and
  conversion losses and cannot isolate panel draw.
- Record firmware/app candidate, mode/fix choices, battery range, temperature,
  charging/USB state, network state and workload. Raw data stays owner-local;
  publish only sanitized aggregates and limitations.
- Establish awake-idle and normal-sleep baselines. Verify suspend residency and
  wake-source evidence separately from Android Asleep, using supported read-only
  sources if available. Inaccessible data remains unknown.
- Close the dashboard during sleep trials: its existing visible-screen flag
  keeps the display awake. Leave no continuous ADB/telemetry polling running.
  Connected and disconnected instrumentation are separate conditions;
  USB/debugging can perturb results.
- Choose duration long enough for meter resolution and stabilization; repeat
  comparable trials and report variability rather than one instantaneous reading.
- Compare BOTH/TOP and fix enabled/disabled only in meaningful supervised cases,
  changing one factor at a time. Verify normal sleep/wake recovery after each.
- Measure diagnostics overhead with an otherwise identical baseline build and
  candidate. Separate idle foreground sampling, transition/history writes and
  deliberate report export. Do not claim a saving before measurement.
- Stop for unexpected wake/restart, panel state, temperature or measurement
  disturbance. Do not force panel/sysfs states to manufacture a comparison.

Use [the trial worksheet](ENERGY-TRIAL-WORKSHEET.csv) for sanitized summaries.
Missing observations must not become zero. Whole-device power differences alone
do not attribute consumption to the lower panel. No energy improvement, overhead
percentage or deep-sleep result is established by this candidate.

### Tools for all three fixes

`scripts/analyze-thor-energy.py` imports the worksheet as measured A/B trials.
It is an offline analyzer, not a hardware collector; it adds no on-device work.
For awake CPU/watcher overhead, the existing `scripts/measure-watcher-load.ps1`
remains available separately. Its ADB-connected foreground intervals must not
be presented as sleep measurements.

| Comparison | Controlled scenario | Required interpretation |
|---|---|---|
| Display fix OFF/ON | TOP, same content/brightness; BOTH as a separate control | Verify actual display state; total-device saving does not identify panel-only draw |
| CPU fix OFF/ON | Matched idle/game workload where the CPU condition is reproducible | Verify effective configuration and normal recovery; a desired switch/property alone does not prove composer state |
| Lid guard OFF/ON | Normal closed-lid sleep, then a separate supervised accidental-wake scenario | Normal sleep overhead and energy saved by preventing a wake are different metrics |
| All fixes OFF/ON | Same scenario and verified configuration | Measures the bundle; cannot attribute a gain to one fix |
| Baseline/candidate build | Identical fix choices and workload | Measures diagnostics/build overhead; do not mix with individual-fix attribution |

Use the existing supported UI to configure each case; the analyzer never changes
switches or restarts anything. Repeat at least three matched pairs where practical,
alternating order to reduce drift. Record effective-state checks and any restart
as owner-local evidence, then allow stabilization before timing. For combinations,
an optional eight-configuration matrix can explore interactions; do not sum
individual savings and assume the bundle will match.

Copy the worksheet outside Git and fill one row per arm (`baseline`/`candidate`),
using the same `pair_id` for a matched pair. `display_fix`, `cpu_fix`, `lid_guard`
are verified case choices (`0`/`1`); `mode` is `0` BOTH, `1` TOP or `2` BOTTOM.
`comparison` is `fix`, `bundle` or `overhead`; `scenario` is `awake_idle`,
`gameplay`, `normal_sleep` or `closed_lid_wake`. `method` is `battery_energy`,
`external_meter` or `charger_input` (reported as an input-power proxy).
Supply measured integrated `energy_wh`, duration seconds and meter energy
resolution in Wh. Battery percentage alone is not Wh; do not invent a conversion.
The `conditions` label identifies a separately recorded, matched firmware,
temperature, battery, brightness, network, charging and instrumentation setup.
Set `configuration_verified=yes`, `valid=yes`, `normal_wake_verified=yes` and
`disturbance=none` only with supporting evidence. Sleep scenarios also require
`suspend_verified=yes`; use `not_applicable` for awake trials.

```powershell
python scripts/analyze-thor-energy.py <owner-local-trials.csv> --output <owner-local-summary.json>
```

The analyzer rejects invalid/unverified trials, duplicates, mixed conditions,
sleep without verified suspend, configuration confounds and non-finite numbers.
Individual comparisons change exactly one fix OFF to ON on the same build;
bundle comparisons change multiple fixes and retain a separate label; overhead
comparisons keep all choices fixed across two builds. Outputs cannot be written
inside this repository or overwrite an existing file. Summary CSV row numbers
link results back to local input without copying owner labels.

Outputs include matched-pair count, mean watts, mean/median watt saving, percent
change, sample standard deviation and a limited quantization check. Positive
saving means lower candidate draw; negative means higher. Fewer than three pairs
are labelled descriptive only; repeats still do not establish statistical
significance or eliminate instrument uncertainty. No comparison is generated
when there are no valid pairs (exit code 2). There are no measured result rows
in the committed worksheet; test data is explicitly synthetic.

## Validation gates

2026-10-08 host validation passed: app diagnostic/privacy fixtures (21
assertions), four synthetic energy comparison tests, existing dashboard and
boot/lid/IPC suites, public-content and release-policy checks, and unsigned APK
compilation/alignment. Device export, Android exit-history behaviour and energy
measurements remain unvalidated; no candidate was installed for this PR.

- Host: retention, corrupt persistent input, arbitrary-field rejection, relative
  ages, unavailable samples, future reason codes, export bounds, app compilation,
  dashboard and boot/lid/IPC suites, publication privacy checks.
- Candidate device: preview; save/cancel/unavailable picker; background/recreate
  Activity while picker is open; readable sanitized output, persisted retention
  and stale-sample labels.
- Candidate device: known app-process termination followed by manual relaunch;
  inspect Android's retained reason without touching daemon/display state.
  Preparing this PR does not include deliberate crashes/kills or installation
  on the owner's device. Exercise unsupported/unavailable/empty paths in a
  suitable test setup.
- Energy validation remains separate and pending. Host tests and ordinary
  emulator lifecycle smoke do not validate export, exit reasons or energy.
