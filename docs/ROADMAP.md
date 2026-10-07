# Current roadmap

Last updated: 2026-10-07.

Jesty Thor Fix v1.6.0 is the current stable release. The cleaner CPU Fix boot path
is complete: the required Qualcomm composer recovery now happens during normal
startup, with strict restart provenance and fail-safe handling preserved.

The project keeps a Thor-first rule: measure the real device, separate evidence
from hypotheses, make the smallest isolated change, then validate on hardware.
Stable release behavior stays unchanged until a change is physically validated.

## Completed: cleaner CPU Fix startup

v1.6.0 replaces the old late second boot-like interruption with an early
stock-`pservice` boot hook. The production path was physically validated on the
Thor without the manual prototype marker.

The property is cached by Qualcomm `ResourceImpl::Init()`. A late write needs
a new composer; same-process TOP/BOTH recreation does not reload it, and no
supported same-process reload path was found on the tested firmware. The stock
`pservice` hook runs after the first composer, so it cannot prove a zero-restart
boot. The inspected init tree offers no safe app-controlled pre-composer hook.
The app does not spoof hardware subtype, patch `/vendor`, or install speculative
system modifications to eliminate the remaining restart.

The previous recovery-splash, boot-animation suppression and pre-composer-hook
experiments are historical research, not active work. They remain useful as
evidence for rejected approaches, but they are no longer release goals.

See [stock pservice early CPU restart](PSERVICE-EARLY-CPU-RESTART.md).
Historical splash research is in [recovery-splash research](archive/RECOVERY-SPLASH-RESEARCH.md).

## Active: 120 Hz / mixed-refresh investigation

The upper Thor panel is 120 Hz-capable while the lower panel is physically a
different panel. Android and Thor community projects sometimes report the lower
logical display as 120 Hz, while other code treats that panel as 60 Hz.

The active question is therefore not merely "does Android say 120?":

> Does the lower panel physically scan at the rate Android reports, or is there
> a logical/render-rate layer that can claim 120 Hz over a 60 Hz scanout?

Work is read-only first:

1. capture Android `Display.Mode` / DisplayModeDirector state for both displays;
2. capture SurfaceFlinger physical display modes and scheduler/vsync state;
3. capture DRM connector/CRTC state and any kernel-exposed mode timing;
4. pull the relevant Qualcomm display binaries for local static analysis;
5. compare the Thor behavior with AOSP multi-display scheduling and public
   Qualcomm SDM implementations;
6. only after the mismatch layer is proven, design a minimal experiment.

No refresh-rate setting, HWC command, DRM write, display power write or reboot
belongs in the first evidence pass.

See [Thor 120 Hz investigation](THOR-120HZ-INVESTIGATION.md).

## Short term

### TOP-mode focus / input

Reproduce the reported case where a game or app appears to lose controller/input
focus to the second logical display. Compare stock TOP with True Bottom Screen Off
and record focused display/task/input routing. A fix requires a reproducible A/B;
do not infer causality from the display merely being black or powered off.
Powering off the lower physical panel does not necessarily remove Android's
lower logical display; the focus issue is not claimed fixed.

### BOTTOM ONLY and dock validation

Close two remaining support gaps on physical hardware:

- BOTTOM ONLY display behavior;
- dock/external-display behavior with True Bottom Screen Off and Wake Guard.

External displays must never be treated as the Thor lower internal panel.

### Updater end-to-end install

The updater already verifies version, package, size, SHA-256 and signing
certificate. Exercise one real newer-version install through the updater itself
when a suitable release exists.

### Dashboard wording / state cleanup

Keep technically distinct UNKNOWN, PENDING, MISMATCH and ERROR states, but make
their user-facing text easier to understand where evidence shows ambiguity.

### PServerBinder disappearance

Investigate separately why `PServerBinder` was once absent from ServiceManager
while stock `pservice` remained alive. An isolated `pservice` restart restored
the Binder without changing composer or SurfaceFlinger. Do not hide this with
aggressive retries until the failure mode is understood.

## Parallel active research

### Watcher / DRM polling cost (#41)

The current watcher works; gameplay stutter or thermal impact has not been
demonstrated. Code-path counts, rather than measured cost, are known:

- the Settings-provider watcher samples `dual_screen_display_mode` every 20 ms
  (up to about 50 calls/s);
- with True Bottom Screen Off enabled in a stable mode, the coordinator checks
  CRTCs at most every 250 ms, reading TOP and BOTTOM separately (up to eight
  DRM debugfs opens/s);
- the visible dashboard adds its own once-per-second telemetry query;
- urgent wake repair bypasses the steady-state throttle.

Measure daemon CPU/wakeups and DRM opens with the dashboard closed, then compare
fixed idle and gaming windows. If the cost is meaningful, prototype a short
fast burst after display/wake events and a 500–1000 ms stable-state safety
poll. Preserve lost-callback recovery, physical CRTC verification, and
BOTH↔TOP / sleep-wake repair latency. Keep the current watcher if its cost is
negligible or the replacement misses transitions.

This work is tracked separately in draft PR #41 and must remain measurement-led:
v1.6.0 is the baseline, and lower polling counts alone are not a promotion
criterion.

## Medium term

### Hardware profiles

Continue moving measured Thor-specific IDs and capabilities behind explicit
profiles. Do not generalize write paths until a second device has been measured.

### Retroid Pocket Duo research

The Pocket Duo is the strongest candidate for a second dual-screen profile.
Start with read-only topology: logical/physical display IDs, CRTCs, Hall/lid
input and Qualcomm SDM behavior. Compare the system-load-check path before
testing True Bottom Screen Off or wake repair. Never reuse Thor IDs.

See [Retroid SDM comparison](RETROID-SDM-COMPARISON.md).

### Lid / dock / external-display edge cases

Expand Wake Guard validation around legitimate external-display use and closed-lid
states so a guard intended for false wakes cannot suppress an intentional session.

### Bottom-screen Android UI quirks

Track navigation-bar, task-placement and lower-display UI behavior only when it
is reproducible. Keep this separate from physical panel power and refresh-rate
research.

## Long term

### More dual-screen devices

If the Pocket Duo work succeeds, evolve the architecture toward explicit
capability profiles rather than a collection of model checks.

### Cross-firmware evidence

Collect the same small evidence set from multiple Thor firmware revisions and
hardware variants. Focus on mixed refresh, focus/input, Hall behavior, dock use
and Qualcomm composition.

### Architectural simplification

Remove old hardening or compatibility branches only when current hardware
evidence proves they are obsolete. Historical complexity is not a reason by
itself to weaken a safety guarantee.

### Optional device-specific recovery work

Ideas such as Wi-Fi recovery stay out of the app unless there is a reproducible
Thor defect, a narrow mechanism and a separate safety case. Jesty Thor Fix is
not intended to become a generic tweak pack.

## Working rule

**Measure -> prove -> change -> validate.**

Keep findings labelled **PROVEN**, **OBSERVED**, or **HYPOTHESIS**. Prefer static
analysis, CI and read-only collectors. A physical reboot or state-changing probe
must answer a concrete question that cannot be closed safely another way.
