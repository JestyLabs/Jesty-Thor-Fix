# S2 — v1.7.8 candidate identity and published v1.7.7 as a live-transfer origin

v1.7.7 was published as stable on 2026-11-03. The origin contract (`scripts/test-live-transfer-origins.py`) requires
every published release to be an admitted live-transfer origin of the next candidate. With the candidate identity at
1.7.8 and no other change, it reports nine missing sites for 1.7.7. The owner's requirement stands: an update never
requires a restart.

## Change

- **Candidate identity:** 1.7.8/code88 in the APK manifest, `DaemonIdentity.VERSION`, `release-source-intent.json`,
  the `build.ps1` artifact name and the retirement fixture manifest.
- **Exact admission:** the literal published identity 1.7.7 is added to the two predecessor gates
  (`LiveHandoverPolicy` and `PreviousSecureDaemonIdentity`).
- **Lower fault gate (new):**
  - **What a 1.7.7 daemon keeps in memory only:** its lower-panel fault latch (`LATCHED` after a slow or failed lower
    start, `POISONED_<reason>` after a kernel signal).
  - **Why that matters:** a successor's kernel reader starts at the end of the log, so after a live transfer it would
    start unlatched. It could then power on a lower panel whose DSI link is already poisoned, which starts the reset
    loop.
  - **The gate:** `LiveHandoverPolicy` now refuses any origin from 1.7.7 on unless its snapshot reports
    `lower_fault=0` exactly. A missing or duplicated field is refused too.
  - **Unchanged:** origins 1.7.1–1.7.6 never reported the field, and their gate is unchanged.
  - **Effect:** a refused boot keeps the existing restart fallback. That boot already needs a restart, because the
    fault notice asks for one.
- **Frozen producer:** the eight producer and server files, copied byte for byte from the v1.7.7 release commit
  `ab5126d0f21e6ac2220c8eb4fc581f1488a7fa9b`, with hashes in `tests/retirement/published/SOURCES.json`. Six are
  identical to 1.7.6. `DaemonIdentity` and `DaemonState` differ; `DaemonState` adds the `lower_fault` and
  `bypass_intercept` fields.
- **Matrices:** 1.7.7 joins:
  - the host retirement controllers;
  - reboot activation (BOTH handover, confirmed TOP handover, native support unavailable);
  - same-version recovery, where 1.7.7 is denied;
  - the fixture source compilation;
  - the Android emulator matrix, now 32 cases.

  The host reply probe gains the two read-only accessors that the 1.7.7 producer calls.

Nothing else changes in the gates, the native operations, the signal path, the timeouts, or the display or boot
actions. The public stop gate remains BOTH-only. TOP admission still requires the authenticated retained process and
the confirmed TOP state.

## Evidence

| Check | Result |
| --- | --- |
| Origin contract with the identity at 1.7.8 and no admission | FAIL, nine missing sites for 1.7.7 (expected) |
| Origin contract after the change | PASS; 13/13 negative controls rejected |
| Actual `LiveRetirement` with the frozen published 1.7.7 producer | PASS, 70 cases. Two new: the producer reporting `LATCHED` or `POISONED_KMSG_PRECURSOR` is refused with `LOWER_FAULT` |
| `LiveHandoverPolicyTest`: code87 eligible only with `lower_fault=0`; latched, poisoned, missing and duplicated refused; code86 unchanged | PASS |
| Reboot activation: code87 same-kernel handover; code87 with a lower fault (latched, poisoned, field absent) never submits the helper and keeps the restart fallback | PASS |

Host fixture evidence is not Android or Thor evidence. Physical acceptance (v1.7.8 C4) must install the signed
candidate over a running published 1.7.7 daemon with the interception armed, and observe a live transfer without a
restart and `BYPASS_INTERCEPT_REUSED`.
