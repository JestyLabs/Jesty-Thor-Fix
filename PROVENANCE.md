<!-- SPDX-FileCopyrightText: 2026 Jesty Labs contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Research provenance

This file records where important technical claims and implementation ideas in Jesty Thor Fix came from. It is a verification aid, not a claim that generic Android, Qualcomm, AYN or Retroid mechanisms were invented by this project.

See [docs/RESEARCH-WORKFLOW.md](docs/RESEARCH-WORKFLOW.md) for the standing workflow used before publishing substantial new reverse-engineering or cross-project findings.

## Scope and attribution rule

When project work materially depends on external code, research or prior art, record the upstream project and an exact URL or commit where practical. When a claim comes from this project, keep the commit, PR, device evidence and tested artifact tied together so later refactors do not erase the original history.

Downstream use remains governed by GPL-3.0. The project asks that non-trivial research and implementation provenance be preserved when reused.

## Public prior art and platform mechanisms

The AYN/Retroid `PServerBinder` privileged bridge predates Jesty Thor Fix and is used by other public projects. Examples include:

- OdinTools at commit `0eaf49392e263bf1de4d6c7eb37aeb03dadb0ccc`
- ClusterTune at commit `4920c5a82925772f5765dbee1f4679242a72cada`
- GameNative at commit `7d06fc32f119bb35ae2740827ca2a47ce9034cd4`

Those projects are reference points for the vendor bridge itself. This project does not claim authorship of that interface or of the generic idea of launching a privileged helper through it.

## Project research records

### JTF-RR-20260926-INITIAL

Initial public release: `462b8e9f62d747ace608f73d8f35f32b2c6c4677`

This establishes the public starting point for the Thor display/CPU utility and its original root-bridge integration.

### JTF-RR-20261006-PRECOMPOSER

Commit: `75dcc4e23b5651bc9203e2f7e3a897802b93b4fc`

Primary record: [docs/PRECOMPOSER-CPU-FIX-PROOF.md](docs/PRECOMPOSER-CPU-FIX-PROOF.md)

This work records the Qualcomm display-stack investigation around `vendor.display.disable_system_load_check`, the first-composer timing constraint, the Thor init-tree audit, and static analysis of stock `pservice` including its early `/data/boot_start.sh` execution path.

### JTF-RR-20261007-EARLYCPU-P33

Physical-test provenance: [PR #33](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/33)

Tested source head: `cb7d7b19e9227674d316459458277b911f331cd4`

Tested CI merge candidate: `9ec3ad26f03fdf4dcc9da1dd9eb0ff0323212c36`

Signed test APK SHA-256: `CB18BC9A0EB77F0256D23E856658D160E9B838912A6437ED38D9C86DB0646A73`

Physical cold-boot record:
- boot ID `00000000-0000-4000-8000-000000000005`
- imported phase `RESTART_REQUESTED`
- baseline composer PID `1221`
- successor composer PID `2314`
- attempt reached `APPLIED`
- one-shot gate was consumed
- managed boot hook and ownership sidecar were removed
- final state reached `BOOT_READY`
- no later second CPU-fix restart was required

This record is intentionally tied to the exact tested candidate even if the implementation is later consolidated or refactored before release.

### JTF-RR-20261007-V160-STABLE

Production integration:
[PR #34](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/34)

Stable release:
[v1.6.0](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.6.0)

Release target commit:
`e4542adb9fe47cccb5bc8ee5b3cffdeeec702513`

Release APK SHA-256:
`2cf04fc692898a2a582781838f3026555bc7cc4e233226bfad677840e329cf00`

The release promotes the physically validated early CPU-restart design while keeping the exact prototype provenance in closed PR #33. The remaining one composer replacement is a consequence of the vendor state being cached by the running composer; no safe app-controlled pre-composer stock-firmware path was proven.

### JTF-RR-20261007-WATCHER-COST

Status: **OBSERVED CODE PATH / PERFORMANCE IMPACT UNTESTED**

Current v1.6.0 code establishes these background-work bounds:

- Settings-provider mode sample every 20 ms in `DaemonWatchThread.smali`;
- steady physical checks throttled to at most every 250 ms by `DisplayActionCoordinator` when no urgent repair state exists;
- the coordinator currently obtains TOP and BOTTOM through separate CRTC reads, so the steady path can perform up to eight individual DRM debugfs opens per second;
- the visible dashboard adds a separate once-per-second telemetry query only while the Activity is open.

No user-facing micro-stutter, thermal regression or gameplay-performance problem has been demonstrated from this watcher. The active workstream is to instrument and measure actual daemon CPU/I/O cost first, then evaluate an adaptive event-driven + safety-poll design only if the measured benefit justifies changing a physically stable watcher.

### JTF-RR-20261007-WATCHER-POLLING

Research branch:
`research/thor-watcher-drm-polling`

Initial research head:
`760146ae3cdfbb31f88bd190ca67dd75883172b0`

Status: **UNTESTED ON HARDWARE / MEASUREMENT CANDIDATE**

The v1.6.0 source establishes a fixed 20 ms Settings-provider watcher cadence and a steady physical-check throttle of 250 ms. This research path tests whether the same correctness can be retained with:

- a 500 ms idle Settings safety poll;
- immediate wake-up from existing DisplayManager callbacks;
- a short 20 ms transition burst;
- a 1 s DRM safety interval;
- one paired TOP/BOTTOM CRTC snapshot instead of two independent debugfs opens.

This is an efficiency investigation, not a response to a demonstrated gameplay defect. No micro-stutter, thermal regression or frame-time problem has been attributed to the v1.6.0 watcher.

Promotion requires measured A/B benefit plus physical BOTH/TOP and wake-repair equivalence. If the measured benefit is negligible, the research path should be closed without changing stable behavior.

### JTF-RR-20261008-WATCHER-VALIDATION

Continuation: [PR #41](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/41).

**PROVEN by code/host regression tests:** a callback arriving after a sample
starts but before its monitor wait can lose the notification while retaining
its DRM dirty generation. Capturing generation before Settings I/O and skipping
the wait on a changed generation closes that latency window, without changing
the 500/20/1600/1000 ms intervals or display/CPU fail-safe policy.

**OBSERVED on AYN Thor firmware .377 / Android 13:** the Windows collector's
CRLF shell script fails on Android, and SettingsProvider runs in system_server.
The collector now normalizes LF, requires an explicit device serial, resolves
the actual provider host, uses monotonic windows and retains raw endpoint data.
Provider-host CPU includes unrelated system work. `/proc/PID/io` is unavailable
to the shell, so exact DRM opens and Settings calls are not inferred from it.

Host success and lower theoretical request rates do not establish device A/B
benefit, physical regression safety or battery-life improvement. Raw hardware
identifiers and session evidence stay in the owner's local evidence archive.

## Evidence discipline

Technical claims should be labelled internally as one of:

- **PROVEN** — directly supported by code, static analysis, controlled device evidence or a reproducible artifact.
- **OBSERVED** — seen on a named device/build but not yet generalized.
- **HYPOTHESIS** — plausible explanation awaiting direct evidence.
- **UNTESTED** — designed or inferred but not physically validated.

Raw logs and binaries are not required to live in the public repository, but published claims should retain enough hashes, SHAs and test conditions to be independently traced.

## Licensing metadata

Maintained source, tests and scripts are covered by the repository's GPL-3.0 terms. Central SPDX annotations are in [REUSE.toml](REUSE.toml). Branding and artwork remain governed separately by [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
