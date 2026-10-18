# S1 — supervised AYN Thor wake/black-screen evidence capture protocol

**Programme:** `JTF-QUALITY-2026-10-17 / S1`. **Scope:** read-only evidence plan for [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84); [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) is separate. **Status:** DOCUMENTATION ONLY / NOT AUTHORIZED / NO DEVICE SESSION RUN. **Baseline:** [integration #101](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/101) after host/emulator suite; proposed exact candidate must be pinned anew before any future session.

## Safety and authorization gate (all must be satisfied)

- [ ] Device owner separately approves a **specific** physical session and individual test scenarios. This document is **not** that approval.
- [ ] Owner is present and can operate hardware directly; normal reboot/recovery is available and explicitly agreed beforehand. If recovery cannot be confidently performed, **NO TEST**.
- [ ] Pin the **actual installed signed APK SHA-256**, signer certificate identity, versionName/versionCode, Git source commit and CI provenance. A branch HEAD or unsigned emulator APK is **not** a signed device candidate. Record production vs test build and whether same-kernel app update occurred.
- [ ] Capture exact firmware/build, Android security build, charging/USB condition, current display mode, selected refresh, desired Display Fix/CPU Fix/Guard toggles, dock/external display, relevant automation presence/configuration, brightness setting, active app and standby/sleep trigger. **Do not assume disabling an automation app undoes its earlier system configuration.**
- [ ] Review that all chosen commands are *read-only* and prepare a **private evidence destination outside Git**, with collection stop and data-redaction ownership. Do not publish raw `logcat`, device serial, boot ID, process lists, user activity, app names or dumpsys traces to a public PR.
- [ ] Note a monotonic correlation scheme: device `/proc/uptime` or `SystemClock.elapsedRealtime()` near the sample, local host time, device wall time and each command exit status. Cross-source ordering is **UNKNOWN** if correlation anchors are absent.

**Forbidden without a separate, explicit new decision:** unattended wake loops, forced panel ON/OFF, brightness writes, shell `input` commands, repeated black-screen induction, killing system services, binder experiments, `ctl.restart`, changing settings mid-capture, rooting/remounting, unapproved APK installation, key use, logging-severity increases or release publication.

## Preflight: healthy baseline first

Create a private case ID, immutable configuration sheet and signed APK checksum **before** any screen interaction. Take a read-only state snapshot while both displays are visibly healthy. Log:

| Required signal | Preferred passive source | Interpret carefully |
| --- | --- | --- |
| Device kernel boot / monotonic time / host anchor | Read-only `/proc/uptime`, optional device `date`, host collection timestamp | Kernel boot ID kept **PRIVATE**; host and device clocks can drift |
| Power / interactive state | `dumpsys power` where permitted | Does not alone prove pixels are visible |
| Actual Settings display mode | `settings get system dual_screen_display_mode` | May lag vendor panel changes |
| TOP/BOTTOM CRTC active state | Read-only DRM/debugfs if accessible | CRTC active ≠ illuminated image; inaccessible is `UNKNOWN` |
| Panel property and display services | Read-only `getprop display.power.state`, service/process diagnostics | Vendor property ≠ pixel state; avoid direct Binder calls |
| Watcher/repair generation and actions | Existing sanitized daemon Q and boot trace where safely available | Missing events after death / rotation cannot be reconstructed |
| Foreground app diagnostics | Existing *on-demand* diagnostics export only | Paused app cannot describe what happened while it was paused |
| Observable screen output | Owner's time-anchored note/video **if approved** | Keep any identifying media private; do not assume flash causality |
| Firmware, exact settings and external automation | Owner-provided configuration checklist, read-only package/property info | Automation state may persist after toggle or app disablement |

Existing [`scripts/collect-thor-boot.ps1`](../../scripts/collect-thor-boot.ps1) and [`scripts/measure-watcher-load.ps1`](../../scripts/measure-watcher-load.ps1) contain **read-oriented** helpers but are not implicitly approved to run: they require their own review for privacy, necessary fields, permissions, overhead and current device applicability. Avoid copying broad raw logs into a public issue. Collection failures must preserve exit codes and be marked `UNKNOWN`, not silently recovered by mutating the device.

## Supervised scenarios (separate approval for each)

| Scenario | Owner action | Evidence goal | Abort rule |
| --- | --- | --- | --- |
| **S1-P00 healthy BOTH** | No state change: normal foreground / known healthy BOTH | Fix baseline: mode, visible panels, current CRTC and correlated clocks | Any anomalous UI or unsupported ADB access |
| **S1-P01 ordinary TOP** | Owner manually switches to TOP once during normal use, no forced screen power | Confirm observation order around known mode edge and no stale repair | Unexpected sustained dark screen or repeated mode presses needed |
| **S1-P02 ordinary sleep/wake** | ONE naturally occurring wake after a normal TOP idle interval, only when owner chooses | Compare actual interactive/wake signal, mode, CRTC, repair generation, property, screen visibility | First loss of usable presentation, unexpected repeated panel action or owner discomfort |
| **S1-P03 prolonged TOP** | Optional, explicitly approved **one** ordinary prolonged idle/sleep/wake; no automated repetition | Observe whether #84-like persistence occurs; distinguish long sleep vs automation source | First black presentation or loss of recovery confidence: **STOP**, no repeated induction |
| **S1-P04 external automation comparison** | **Only if separately approved after safe P00–P03** and exact OS/config effects verified | Compare owner-confirmed automation configuration A/B with all other factors fixed | Any uncertain residual automation setting or new failure: **NOT RUN** |

BOTTOM-only, dock/external-display and Guard-ON positive tests belong to the later [S6 physical matrix](../ENGINEERING-QUALITY-ROADMAP-2026-10-17.md), not automatically to this S1 incident capture. Likewise the #94 privileged handover acceptance is **a separate S2/S6 requirement**, even if a healthy normal wake is captured here. Do not conflate a passing #84 observational session with validation of runtime handover.

## On first visual failure: stop, preserve, recover

1. **STOP actions on both screens immediately.** Record the last verified working state, observed screen visibility, last hardware-button action and approximate time. Do not retry mode presses, wake events or panel operations to “see if it clears.”
2. If the device and owner remain comfortable, gather **one short, already-approved read-only snapshot**, using existing session tools only. Mark any inaccessible source `UNKNOWN`; do not extend capture solely to fill missing fields.
3. Save evidence locally, hash all files, label exact command failures and privacy classification; no external posting or screenshots in public PRs.
4. Execute only the **pre-agreed owner-controlled recovery procedure** (normal hardware recovery/reboot if appropriate). Any inability to regain a visible, usable device is an escalation and terminates the session.
5. No further black-screen trial that day without a **new explicit risk review and approval**. An incident after reboot gives healthy **post-reboot** evidence, not the missing pre-reboot transition.

## Reporting format

| Field | Required content |
| --- | --- |
| Case / scenario / candidate | Local private case ID, exact signed APK SHA, source SHA, app version, firmware |
| Controls | Display mode, refresh, Fix/CPU/Guard, USB/battery, lid, dock, automation, foreground app |
| Owner observation | BOTH/TOP visible? time interval? hardware interaction? first symptom? |
| Anchors and raw evidence | Monotonic sample pair, host correlation, source filename/hash, command exit status |
| Classification | **PASS / FAIL / NOT RUN / UNKNOWN** — separately for event visibility, actual panel, safety and recovery |
| Inference | **OBSERVED** vs **POSSIBLE** vs **UNSUPPORTED**; explicit competing hypotheses |
| Residual risk | What was not observed and why (rotated buffer, paused dashboard, failure before durable write, inaccessible DRM) |
| Recovery / next step | Stop condition, recovery performed by owner, separately approved continuation required |

### Competing hypotheses / decisions after capture

Treat external automation sleep request, Android wake/interactive state, Settings-mode staleness, CRTC/vendor display power, SurfaceFlinger/compositor and real physical backlight/pixel state as **distinct candidates**. A `WATCH_BOTH` or `WAKE_REPAIR` log near a visual symptom is not proof of causation; synchronize observations before proposing behavior changes. Physical CRTC snapshots cannot prove visible presentation. Do not claim #84 fixed without attributable mechanism, failing reproduction or defensible regression, the minimal reviewed fix and exact-candidate acceptance; do not claim #65 flash fixed from #84 testing.

## Exit / integration gates

**Current:** HOST regression characterization developed; generic ANDROID EMULATOR CI does not emulate vendor panel. **THOR: NOT RUN**. Root cause #84: **UNKNOWN**. #65: **separate and OPEN**.

This plan becomes **eligible for an owner-supervised session** only after physical authorization, signed APK provenance and recovery preflight. No physical result, handover validation, signer use, `main` merge, release or change-of-scope is implied by merging this document. A successful S1 host-and-document review can close the **offline documentation task**, but **not** the incident or safety acceptance.
