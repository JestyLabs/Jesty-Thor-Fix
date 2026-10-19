# PR67 — scoped physical validation, 15–16 October 2026

**Supervised session complete: 15 PASS / 1 historical FAIL in T00–T15, with A1 recorded separately as PASS.** This is scoped evidence for `1.7.2-rc1 / code78` at source `8d2935736080567c6c538035d8925519a083ac41`, not universal acceptance or release authorization. Private captures and per-device identifiers remain outside Git.

## Execution ledger

| Cases | Result | Tested scope |
| --- | --- | --- |
| T00–T05 | PASS | Stable baseline awake/identity/observed preferences; recovery79 native create/write/abandon; three-window CPU baselines in BOTH and TOP |
| T06 | PASS | Signed RC78 installed in place, without uninstall or data clear |
| T07 | PASS, bounded | Immediate normal reboot and candidate activation, with the early-boot crash limitation below |
| T08 | PASS | Normal BOTH → TOP → BOTH and scoped authenticated health |
| T09 | **FAIL, preserved** | Original RC78 BOTH CPU series exceeded the ceiling |
| T10 | PASS | RC78 TOP CPU, three valid windows below the TOP ceiling |
| T11 /T12 | PASS | One normal short lid cycle each, Guard OFF in BOTH /TOP, followed by normal restoration |
| T13 | PASS | One normal short BOTH lid cycle with effective Guard ON; fresh post-wake health and Guard OFF restoration |
| T14 | PASS | UI/diagnostics, five observed preferences retained, uniquely named report exported |
| T15 | PASS | Selected final state, fresh health, physical confirmation and session finalization |

Independent BOTH acceptance **A1 PASS** is additional evidence, not a replacement for T09. Finalization does not turn the ledger into all-PASS. Recovery installation, long TOP sleep, active blocking and excluded variant/distribution cases remain unexecuted.

## CPU reconciliation

All figures are daemon CPU as a percentage of **one core**. Each ceiling is the corresponding stable76 mode median plus **0.10 percentage points**. All three valid samples contribute to each three-window result; D1 is one diagnostic window.

| Series | Windows | Median or diagnostic sample | Ceiling | Result |
| --- | --- | --- | --- | --- |
| Stable76 BOTH | 3 | 0.088455954% | Reference | Baseline |
| RC78 T09 BOTH | 3 | **0.285730014%** | 0.188455954% | **Historical FAIL** |
| RC78 D1 BOTH | 1 | 0.082918500% | 0.188455954% | Valid diagnostic below ceiling |
| RC78 A1 BOTH | 3 | **0.088457127%** | 0.188455954% | **Separate acceptance PASS** |
| Stable76 TOP | 3 | 0.324110373% | Reference | Baseline |
| RC78 T10 TOP | 3 | **0.324422937%** | 0.424110373% | **PASS** |

Recalculate the [sanitized window data](evidence/PR67-CPU-WINDOWS-2026-10-16.csv) as `100 * ticks / CLK_TCK / elapsed_seconds`, with `CLK_TCK=100`. Do not add watcher-thread CPU to the process aggregate.

The same boot, daemon and watcher instance continued through T09, D1, A1 and T10. A1 and T10 demonstrate repeated compliance under their recorded conditions. **The cause of T09 versus D1/A1 variability is unknown.** Recents, time, intervening sleep/USB changes, workload and clocks/thermal state were not isolated. A task in recents does not establish background polling: Activity telemetry stops in `onPause()`. No causal explanation, statistical equivalence, universal regression clearance or immediate post-transition CPU result is claimed.

## Wake, health and diagnostic limits

Each T11/T12/T13 lid closure was owner-timed at approximately ten seconds. Framework sleep/wake endpoints do not establish exact closed-lid or kernel-suspend duration. No commands or collection stream were issued while the lid was closed. T12 observed `OFF_OK` wake repair and a running watcher, corroborated by normal TOP state. This does not resolve the separate prolonged-sleep investigation.

Guard was effectively ON before/after T13 and restored OFF through normal UI. Its blocked counter stayed zero; no deliberate closed-lid wake was provoked. **Active blocking remains NOT RUN / not demonstrated.**

Health used fresh acceptance by the app's authenticated validator, dashboard observations and runtime continuity. The complete literal wire identity reply was **not captured**. The T07 SurfaceFlinger EGL abort matched a previously documented early-boot signature and preceded daemon startup; that does not establish its cause or a crash-free boot. Bounded crash-buffer snapshots gained no entries in the final blocks.

T14 compared two fix switches, Guard, automatic update checking, pre-release inclusion, UID and first-install identity with prior observations; all matched. It did not exhaustively compare private files. Normal export used a new filename and preserved three existing matching reports. Foreground samples, bounded app exit history and CRTC flags are not sleep measurements, complete root-daemon death history or physical panel-energy proof. Logical120 Hz is not optical refresh proof.

Final selected state: RC78 installed, BOTH/120 Hz logical, both fixes ON, Guard OFF, external sleep automation OFF and Home with the AYN panel closed. Both screens were physically confirmed normal. Collection ended; no monitor or automatic resumption remains.

Recovery79 was staged/abandoned, **not installed**. Prolonged TOP sleep/PR82, active blocking, BOTTOM/dock/external variants, fault injection, live predecessor handover, calibrated energy, optical refresh and public updater discovery-to-install remain outside acceptance. See [integration and release gates](PR67-INTEGRATION-GATES-2026-10-16.md).
