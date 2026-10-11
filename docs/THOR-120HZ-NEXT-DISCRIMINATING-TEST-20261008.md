# Thor mixed-refresh — discriminating test plan (2026-10-08)

**Status: research only.** This note refines PR #37 from the exact-binary reports and the already collected, locally retained evidence. **No production refresh fix has been implemented or physically validated.** This document authorizes no new device action. Do not publish raw captures, proprietary binaries or disassembly, device/session identifiers, personal paths, application/window inventories, or unsanitized logs.

Read this together with [exact SurfaceFlinger/framework analysis](THOR-120HZ-EXACT-BINARY-OFFLINE-20261008.md), [loaded kernel RAM analysis](THOR-120HZ-KERNEL-RAM-20261008.md), [latest baseline](THOR-120HZ-PR37-RESUME-20261008.md), and [event/mode provenance](THOR-120HZ-EVENT-ORDER-AND-MODE-PROVENANCE.md). Those exact-firmware reports take precedence over older AOSP-only interpretations.

## Decision

**Next experiment: establish an instrumented, passive BOTH/60/60 baseline with verified per-CRTC events and reliable trace-producer health. Do not run another 120 Hz switch yet.** The present evidence explains a vendor `invalid mode` path and reveals weak RAM-switch acknowledgement, **but neither explains nor fixes the visually reported tearing**. Optical frame cadence and the type of visible artifact (spatial tear vs repeated/late frames) have not been measured.

A useful result must separate six boundaries: Android-advertised mode -> SurfaceFlinger request -> HWC/SDM acceptance/application -> DRM scanout timing -> DSI/controller behavior -> *distinct optical frames*.

## Evidence ledger (not interchangeable)

| Classification | Finding | Limit |
|---|---|---|
| **PROVEN / exact firmware, static** | Vendor SurfaceFlinger reuses the active display's desired mode for the height-identified secondary; physical identity mismatch logs `invalid mode` yet proceeds through a config-ID remap; the secondary call result is not checked as the default call is. Completion updates the default's bookkeeping. | Static routing and accounting do not establish secondary HWC success, scanout, fences or optical tearing. |
| **PROVEN / exact firmware, static** | `bypass_ram_show` reads a software shadow; `store` can update that value and return input length despite a switch failure; secondary panel preparation replays the shadow while ignoring the transfer result. The command sets map 1 to BYPASS/`B9 00` and 0 to PASS/`B9 11`. | Shadow readback or a successful sysfs write is **not** a DSI acknowledgement or physical controller-state measurement. The electrical meaning of B9/TE/RAM is not proven. |
| **OBSERVED / saved device evidence** | A supervised transition reached HWC-related scopes and a global 8.33 ms vsync period, with two visually reported black blinks. Other sustained policy requests did not produce a confirmed DRM mode switch. | Anonymous scopes lack per-display config, return and commit provenance. Neither observation measures 120 distinct lower-panel frames nor proves the cause of tearing or both blinks. |
| **OBSERVED / last passive attempt** | The 3 s Perfetto capture contained 889 bytes but **zero ftrace events** according to Trace Processor. Producer logs include repeated resets and a `f2fs_truncate_partial_nodes.nid` translation error. | Invalid as a baseline. This exact error has been associated publicly with Android 13 Perfetto collector failure; causation on this unit is **strongly suggested, not isolated by a controlled test**. |
| **OBSERVED / saved configuration** | The lower path uses DSI video mode with DFPS settings and nominal/dynamic clock values; the upper path is command mode. | DT clock values are configured options, not observed clocks or optical frames. |
| **EXTERNAL / public source, not stock firmware** | Upstream Linux has a device-specific CH13726A driver advertising both 120 and 60 Hz. A July 2026 discussion of a 120 Hz colour/timing problem led to a withdrawn change. | A different Linux driver and an unresolved colour report do not establish physical 120 distinct-frame cadence or explain Android tearing. The owner's 60 Hz panel-spec information is not independently authenticated by this source. |

### Public, independently attributable sources

- [Linux CH13726A panel driver](https://github.com/torvalds/linux/blob/master/drivers/gpu/drm/panel/panel-chipwealth-ch13726a.c): panel/video-mode configuration and published 60/120 mode definitions. **Not** the Thor .377 proprietary kernel driver.
- [July 2026 timing/colour patch discussion](https://lists.openwall.net/linux-kernel/2026/07/28/89) and [author's withdrawal](https://lists.openwall.net/linux-kernel/2026/07/28/1810): useful lead on DSI-clock/blanking sensitivity only; **do not port the proposed timing changes**.
- [Linux mailing-list Android 13 ftrace translation discussion](https://lists.openwall.net/linux-kernel/2023/08/14/960) and [upstream Perfetto issue #646](https://github.com/google/perfetto/issues/646): an independently reported matching producer failure. No kernel rollback or patch is recommended.

## Hypotheses, ordered by test value rather than asserted probability

| Mechanism | Diagnostic observation that strengthens it | Observation that weakens it |
|---|---|---|
| **Secondary SF/HWC mode state and timeline** | Secondary request/return/pending/applied state diverges from its actual DRM commit/vblank or optical symptom, while TOP remains coherent. | Both independently attributed paths remain coherent during a reproduced optical defect. |
| **Lower DSI/PASS-BYPASS/controller or TE timing** | Lower transport-clock/porch or verified DSI-command transition coincides with the optical break even when commits/fences are coherent. | A confirmed tear occurs during stable DSI mode and no relevant controller state transition; still check continuous scanout. |
| **Buffer lifetime / release-retire fences / atomic commits** | A frame crosses scanout during an incomplete update, premature fence, wrong buffer or inconsistent atomic presentation; optically matching event. | Reliable buffer provenance and fence ordering remain correct while the defect occurs. |
| **Pacing, judder or frame repetition** | Optical frame IDs show whole-frame duplicates/skips/irregular cadence without a spatially split frame. | A high-speed recording identifies two different frame versions in one physical refresh. |

A zero global missed-vsync counter does not rule out secondary tearing, and a DRM vblank rate does not prove distinct optical frames.

## Test A — passive, supervised 60/60 baseline (prepare first; execute only with separate owner approval)

1. **Preflight:** re-read live state; require Awake, BOTH, policy min/peak 60/60, both CRTCs active at 60, and stable display/compositor identities. Prior post-wake snapshots are **not** assumed current. Record whether the test display scene is static or animated.
2. **Trace access, not device manipulation:** verify in advance the exact privilege context and ability to create/delete one isolated ftrace instance, identify a trace clock and event formats, and capture exit status + output proof. Shell write denial must not be silently bypassed. `Binder transact=true` is insufficient evidence of successful execution. Do not restart services, modify kernel/firmware, change display sysfs, inject DSI commands, or install APKs.
3. **Producer health:** do not reuse the previous 889-byte artifact as a measurement. If Perfetto is used, check that the ftrace producer starts without resets/errors, then prove non-empty event records with Trace Processor. The public Android 13 F2FS issue makes parser incompatibility a priority to investigate, **not** proof that all other capture/access issues are absent. If producer health is not verifiable, stop and plan an explicitly authorized isolated ftrace alternative.
4. **Minimal event set:** use the locally enumerated `drm/drm_vblank_event` (fields `crtc,seq,time,high_prec`), available queued/delivered vblank events, and `dma_fence/dma_fence_signaled` (`driver,timeline,context,seqno`). Include commit/pageflip/atomic and wait traces **only where present and with verified semantics**. Capture matching DRM/SDM/SF snapshots and, if exposed read-only, effective clock/DSI state.
5. **Attribution:** tracepoint `crtc` is a DRM pipe/index, **not the DRM object ID**. Saved evidence maps lower `crtc-1` and upper `crtc-0`, but verify the mapping against fresh `drm_state` and object/connector identities at both ends. HWC handle, SF config ID, Android mode ID and DRM object ID are different namespaces. Fence `context/seqno` alone is **not** a display label.
6. **Timing and coverage:** align trace-clock, monotonic uptime and logcat with an explicit calibration, preserving uncertainty. Inspect actual event counts, sequences, gaps, dropped/overwritten records, and whether either CRTC is observable. **Do not require 60 trace records/sec** from events that may only be emitted when requested/queued; establish the semantics of each event before estimating physical software cadence. Distinguish vblank counters from delivered userspace vblank events.
7. **Acceptance gate:** start with a ~3 s minimal capture. Accept only if trace producer is healthy, trace contains genuine attributable events, both relevant paths have adequate coverage for the intended claim, the capture and its cleanup succeeded, and unchanged 60/60 state is independently verified. Otherwise report which gate failed; **no 120 test**. A valid 5–10 s extended passive sample may follow after the same approval/guardrails.

Stop on missing privilege/cleanup guarantees, producer reset, empty/unattributable events, unexpectedly changing mode/identity, or device instability. Never infer no vblank simply from absent vblank-event trace records.

## Test B — tightly bounded 60 -> 120 -> 60 (NOT YET AUTHORIZED)

Prerequisites: Test A is accepted; a reversible stock setting path is identified; rollback state and abort conditions are agreed; high-speed optical recording with visible frame IDs / motion reference is ready for **both** displays.

- Baseline: record short steady 60/60 software timing and optical evidence.
- Switch: use only an already understood **stock** refresh-policy path; correlate SF request, per-display HWC response, SDM application, DRM mode/commits, lower DSI state and optical recording. **No manual `bypass_ram` writes, driver patch, kernel/module change or speculative compositor restart.**
- Exposure: short, supervised 120 phase (order of ~5 s), immediately aborting on persistent black screen, unexpected flicker, crash, thermal anomaly or loss of observability.
- Rollback: restore stock 60/60 and **verify** Android BOTH + min/peak 60, SF config IDs, per-display HWC/SDM current/pending state, both DRM CRTCs active at 60, and normal visual output. The `bypass_ram` shadow is contextual data, not a verified hardware rollback check.
- Readout: distinguish split optical frames (tearing), skipped/repeated whole frames (judder), lower-only pipeline divergence, and transitions in panel timing/DSI commands. A global vsync period by itself cannot determine this.

## Focused reverse if Test A does not distinguish the layers

Use symbols/offsets and byte provenance already documented in [exact-binary analysis](THOR-120HZ-EXACT-BINARY-OFFLINE-20261008.md), not generic string-search:

- Composer `SetActiveConfigWithConstraints` / `ProcessActiveConfigChange`: per-display return, pending-config consumption and applied time.
- SDM `DisplayBase::SetActiveConfig` and DAL `SetDisplaySwitchMode`: when a requested mode becomes an applied video-mode/clock/porch change.
- DAL `AtomicCommit`, fences and buffer ownership: association between CRTC, plane/framebuffer, commit and signaled fence.
- Loaded kernel `sde_crtc_vblank_cb`, DSI mode validation/application and RAM command transmission: distinguish CRTC callback, transfer submission, any observable response and controller semantics.

Do not reverse engineer more solely to explain the already-accounted-for `invalid mode` messages.

## Application feasibility

- **Feasible / low-risk:** diagnostic UI, read-only state collection, explicit provenance/unknown-state reporting, possible user opt-in warning or conservative 60/60 mitigation after real optical reproduction and validation.
- **Unproven:** independently enforcing TOP120/BOTTOM60 through standard Android settings; this firmware couples the mode request at SurfaceFlinger. No promises of independent physical refresh or a universal tearing fix.
- **Likely outside the APK:** modifying vendor SurfaceFlinger mode/timeline bookkeeping, composer/SDM fence and atomic behavior, or CH13726A DSI/controller timings. These need firmware/privileged-component work and hardware validation.

**PR policy:** keep #37 draft/research-only; do not change main, release, runtime APK/build, panel state or installation as part of this documentation update. Only a fully observed, reproducible defect and a separately reviewed, reversible mitigation would justify implementation work.

## Publication boundary

Git commits and PR comments contain **only** conclusions, high-level mappings, non-sensitive function names and public URLs. Original device traces, private logcat, host/device paths, dump files, disassembly, binary artifacts, boot/session IDs, device identifiers, emails, and unsanitized command outputs remain offline. No source's presence in the private research ZIP authorizes its public upload.
