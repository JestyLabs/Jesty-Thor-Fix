# PR #37: resumed investigation, 2026-10-08

Follow-up: [exact kernel RAM path, sleep clarification and trace attempt](THOR-120HZ-KERNEL-RAM-20261008.md).
The capture below is an earlier snapshot, not a claim the Thor stayed awake.
The later sleep check and subsequent owner-authorized wake are recorded separately.

Scope: read-only device verification and integration of the existing exact-binary
analysis. No refresh-policy writes, installation, reboot or sysfs writes.

## Current device evidence

- Thor firmware reports `Thor_V1.0.0.377_20260206_165408_user`; the installed app
  reports version 1.7.0 / code 71.
- All 22 live display ELF SHA-256 values match the original baseline manifest.
  This links the existing disassembly to the files currently on the device;
  it does not authenticate the firmware image.
- System min/peak policy is 60/60. SF active IDs are TOP 0 and BOTTOM 1;
  both SDM dumps report cur60 and vsync period 16666666 ns. Both internal DRM
  CRTCs are active with their 60 Hz modes. These are software/DRM observations,
  not an optical measurement or a fresh visual confirmation.
- `bypass_ram` reads 0 at this 60/60 baseline. Brightness reads 764 and
  actual_brightness reads 0. These sysfs values alone do not establish actual
  luminance, a hardware fault, or which DSI commands last reached the controller.
  In particular, the framework threshold is not proof of the current RAM state.
- Collector captured all seven required sources; only `cmd display help`
  returned the already-known optional exit 255 with its help text saved.

Raw evidence stays outside Git in `C:\Temp\thor-refresh-pr37-resume-20261008`.
No device serial, boot ID, task/window dump or raw log is included here.

## Lower panel properties read from the current device tree

The CH13726A node contains DFPS `[120, 60]`, nominal timing 60 Hz and dynamic
clock values 1011000000 / 505000000. These are configured capabilities, not
measurements of current clock or optical refresh.

The current node also contains these encoded DSI packet payloads:

| Property | Payload sequence |
|---|---|
| `qcom,mdss-dsi-bypass-ram-command` | `28 00`, `f0 50`, `b9 00`, `29 00` |
| `qcom,mdss-dsi-ram-command` | `28 00`, `f0 50`, `b9 11`, `29 00` |
| Both command-state properties | `dsi_lp_mode` |

This upgrades the earlier public-driver clue to a current-device property
observation. It does not establish the exact kernel store-handler mapping,
command execution/ACK, controller RAM architecture, or frame repetition.
No command from this table was sent during this session. The literal pass
property is named `mdss-dsi-ram-command`, not `mdss-dsi-pass-ram-command`.

Local SHA-256 provenance:

| Raw file | SHA-256 |
|---|---|
| `03b-live-display-devicetree.txt` | `b6e9cba4ed40ae2b4f9d6e6c01447247e81a7dcf9359851b27a7c0001d6783e7` |
| `06-surfaceflinger.txt` | `3aec82b72817e9304462d127588f616d599f75c38f77ad524c6a6ee751c9bb9d` |
| `11-drm-state.txt` | `120dcd0b130b7522e5cbfa73defd445ea9484bb19afbd6897a01b7efd282c206` |

## Integration and tooling

Integrated the remote privacy changes and main `ff9b209`, preserving v1.7.0,
the #50 bridge/handoff diagnostics and the protected release workflows.
The PR adds no app runtime changes relative to that main commit. CI retains
both the refresh contract and release workflow policy checks.

The collector now requires an explicit serial, checks `get-state` before creating
output, defaults to a unique temporary directory and rejects output within its
repository. Mock tests cover offline refusal, repository-output refusal, successful
capture and serial pinning on every call; they never access hardware.

Validation: refresh model/timeline/capture/binary/collector suites, boot/lid/IPC
and dashboard host suites, release workflow policy, public content and staged
privacy checks. Host suites are not validation of a physical refresh fix.

## Remaining discriminating evidence

The exact ELF explains why a foreign TOP mode reaches BOTTOM HWC after logging
`invalid mode`. The current device tree confirms the RAM command properties.
Neither finding proves why tearing occurs or that BOTTOM produces 120 distinct
frames. Keep policy writes paused: the next investigation must distinguish
kernel/sysfs command semantics, actual per-display commit/timeline behavior and
optical frame cadence. Do not correct the observed baseline RAM value speculatively.

## 2026-10-09 supervised A → B → C follow-up (read-only)

**OBSERVED — context-associated, not causal.** The same game → Jesty Thor Fix app → game sequence was captured with BOTH selected and 120 requested through the stock Thor control. The TOP **SurfaceFlinger `displayManagerPolicy.primaryRange`** (not physical mode or a proven game request) was `[0,120] → [120,120] → [0,120]` Hz. The global system min/peak settings remained near 120 while DRM and TOP SF reported 60/60 in the game states and 120 in the app state. A 60-FPS content limit alone does not establish why DRM mode changes; an application surface/window frame-rate vote and a vendor game policy are separate **HYPOTHESES**, not measured causes.

| State | TOP SF local mode | BOTTOM SF local mode | TOP DRM | BOTTOM DRM | TOP SF primary policy |
|---|---|---|---|---|---|
| A — game | ID0 = 60 Hz | ID1 = 60 Hz | 60cmd | 60vid | [0,120] Hz |
| B — app | ID1 = 120 Hz | ID1 = 60 Hz | 120cmd | 120vid | [120,120] Hz |
| C — game | ID0 = 60 Hz | ID1 = 60 Hz | 60cmd | 60vid | [0,120] Hz |

**OBSERVED — namespaces differ.** TOP local ID0 is 60 Hz and ID1 is 120 Hz; BOTTOM local ID0 is 120 Hz and ID1 is 60 Hz in these SF tables. Never compare active mode IDs without the per-display table, physical mapping, HWC ID and clock provenance. At B, BOTTOM SF reports active ID1 = 60 Hz despite a BOTTOM DRM `1080x1240x120vid` mode: this is an **apparent cross-layer reporting discrepancy**, not proof of an applied optical 120 Hz mode, stale bookkeeping or a faulty commit. The BOTTOM primary policy in B is [120,120] even while SF activeModeId remains 1=60: this is an additional policy-vs-active observation. SF and DRM dumps were **sequential**, not synchronized; the long `app-120-requested` collector may include transition back to the game. The individually captured `app-after-120-selected-*` and `game-return-120-*` files are distinct temporal anchors.

**OBSERVED — CRTC state limitations.** In the supervised TOP/display-fix ON check, TOP CRTC remained active and BOTTOM inactive; turning that fix OFF through the app made BOTTOM active, and restoring ON made it inactive. In captured sleep states, `enable=1` can coexist with `active=0`; `active=0` does not establish fully powered-down pipeline, disconnected panel rails, zero energy or optical black. Preserve both distinct sleep observations: an **earlier** BOTH/closed sample showed BOTTOM CRTC active with Android logical displays OFF; on **2026-10-09**, a separately supervised BOTH/closed sample showed both CRTCs inactive, and TOP/closed did too. Different initial conditions, read times, repeatability and cause remain **UNKNOWN**. The owner reported normal recovery after opening in those recent single-cycle observations; there was no optical measurement.

**PROVEN (static binary), not observed runtime return.** The exact .377 vendor ELF's foreign-mode mismatch, HWC remap and default-only bookkeeping are documented in the binary reports. This static control flow does not establish the secondary HWC return, physical commit, vblank/fence timing, optical cadence or the tearing mechanism. Likewise, a 120 DRM mode and a software FPS readout do not establish 120 distinct optical frames.

**Remote evidence scope:** both ZIP manifests verified 124 and 529 entries, respectively; total archive file counts were 125 and 532. Historical sanitized PIDs prevent independently checking process continuity; local original observations of process continuity are not re-proven by this redacted package. Signed APK, signing evidence and owner photos were deliberately absent. Original captures stay owner-local.

**Next gate — no automatic physical test:** build a separate read-only, short SF→DRM→SF / DRM→SF→DRM collector with per-read host and device clock provenance. A stable bracket means only agreement among the samples, not continuous stability or an atomic snapshot. First validate its offline fixtures, then a supervised 60/60 pilot; only subsequently attempt repeated A→B→C. Window/Surface, DisplayModeDirector and game/vendor evidence belong to supplemental read-only captures. Neither the previous failed ftrace trace nor absent events prove a lack of vblank.
