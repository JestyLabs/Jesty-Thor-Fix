# S2 — published 1.7.6 as a live-transfer origin, and the origin contract

The first signed 1.7.7 candidate did not admit the published 1.7.6 pre-release as a predecessor. Every 1.7.6 user would
have needed a restart to finish the update: an app opening would have held the transfer with
`PREVIOUS_VERSION;value=1.7.6`. The owner's requirement (2026-11-02) is that an update never requires a restart, now
or for any future release.

## Change

- **Exact admission.** The literal published identity 1.7.6 is added to the two existing predecessor gates
  (`LiveHandoverPolicy` and `PreviousSecureDaemonIdentity`). Nothing else changes in either gate, in the native
  operations, the signal path, the timeouts, the display or boot actions, or the same-version recovery policy. The
  public stop gate remains BOTH-only. TOP admission still requires the authenticated retained process and the
  confirmed TOP state.
- **Frozen producer.** The eight producer and server files are copied byte for byte from the v1.7.6 release commit
  `dd6d773e6c9e97a4379d75abf83a18db3ff2be46`, and their hashes are recorded in `tests/retirement/published/SOURCES.json`.
  Seven are identical to the 1.7.5 producer; only `DaemonIdentity` differs.
- **Matrices.** 1.7.6 joins every retirement matrix that runs the real producers:
  - the host retirement controllers;
  - reboot activation, in BOTH, in TOP, and with native support unavailable;
  - same-version recovery (1.7.6 is denied there);
  - the fixture source compilation;
  - the Android emulator matrix, now 28 cases.

## Contract for future releases

`scripts/test-live-transfer-origins.py` makes the rule mechanical. It derives the published releases from the
`docs/RELEASE-NOTES-X.Y.Z.md` files below the candidate's `DaemonIdentity.VERSION`; `release.yml` refuses to publish a
release without its notes. For each such release from 1.7.1 on, the script requires:
- the exact literal in both gates;
- a complete frozen producer;
- presence in every matrix listed above.

The candidate itself must not be admitted. On main before this change the script reported eleven missing sites for
1.7.6. Thirteen negative controls each break one site, and all thirteen are rejected. CI runs it with the retirement
suites. `release.yml` runs it again before publication, with the tags actually published (stable and pre-release).

## Evidence

| Check | Result |
| --- | --- |
| Actual `LiveRetirement` with the frozen published 1.7.6 producer, before the change | Refused (base policy rejection) |
| The same, after the change | PASS, 68 cases; 42/42 compiled negative controls across six origins |
| Reboot activation and recovery suites | PASS |
| Origin contract | PASS; 13/13 controls rejected |

Host fixture evidence is not Android or Thor evidence. Physical acceptance must install the exact signed candidate over
a running published 1.7.6 daemon and observe the live transfer without a restart.
