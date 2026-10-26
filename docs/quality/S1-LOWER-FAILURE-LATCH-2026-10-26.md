# S1 — boot-scoped lower-failure latch (draft, 2026-10-26)

Programme: JTF-QUALITY-2026-10-17 / S1. Status: **draft; host evidence only; no signing, release or device test.**

## Problem

1.7.4 added a volatile lower-ON back-off after a display power call blocked for more than five seconds (see
[BLACKOUT-CONTAINMENT-1.7.4.md](../BLACKOUT-CONTAINMENT-1.7.4.md)). The back-off is cleared by a mode change to TOP/BOTTOM,
an admitted TOP wake, a sampled non-interactive → interactive edge, or confirmed manual reconciliation. That document
already lists the limit this PR addresses: *"Mode changes/manual retry/sleep-wake can expose the underlying failure again."*

The 2026-10-20 incident timeline, as reconstructed in an independent offline analysis of the retained logs, shows:

- after the first failed lower-panel start, **every** later lower start in the same boot failed, until a reboot;
- the vendor reset loop ran several times. Each run ended when a lower-panel OFF completed;
- **twice, in BOTH, the app's own automatic lower ON re-entered the loop about one second after the app's OFF** (code82;
  1.7.4/1.7.5 would have suppressed this until the back-off was cleared, but not afterwards).

The 1.7.5 coordinator still re-powers the lower panel automatically after the back-off clears. The replay tests below
reproduce this against a frozen copy of the published coordinator.

## Change (production: `DisplayActionCoordinator.java` only)

- A stalled call (>5 s, same threshold and classification as the back-off) also sets `lowerFailureLatched`. It is logged
  once per daemon process as `LOWER_FAILURE_LATCH set reason=…;call_ms=…`.
- While the latch is set, automatic BOTH reconciliation never powers the lower panel ON (same suppression and status as
  the back-off), and an observed active lower panel in BOTH gets the existing degrade OFF, **even after the back-off has
  been cleared**.
- Mode change, TOP wake and the sleep/wake edge still clear the back-off, but **not** the latch. Confirmed manual
  reconciliation (`requestFix`) clears both. It is the only in-process escape hatch, for example after a false positive.
  A new daemon process (normally a reboot) starts unlatched.
- Unchanged: the threshold, admission interval, readback/confirmation, boot hold, disabled fix, TOP repair/OFF paths,
  the power query (still only while the back-off is set), Guard, CPU, updater, IPC, UI, and the vendor/firmware interfaces.
  The lower OFF path is never blocked.

User-visible effect: after a stalled call, BOTH can keep the lower screen OFF until reboot or until manual reconciliation
in the app. The status remains `BOTH_ON_BACKOFF`/`BOTH_DEGRADED`; no new UI is added here.

## Evidence (host only)

| Check | Result |
|---|---|
| New/changed `BothBackoffTest` cases against the **unmodified** 1.7.5 coordinator | FAIL as expected: CL-02, PC-03, RS-01, LA-00b, LA-03, LA-04b (the re-arm is reproduced). Controls CT/ND pass |
| Same cases on this branch | `BothBackoffTest` 84 assertions PASS |
| `scripts/test-display-backoff-host.py` | PASS. Includes 3 new frozen-1.7.5 rejections (`v175-rearm-*`) and 5 new latch mutants (no latch, cleared with back-off, no suppression, no degrade, survives manual reconcile), all rejected. Existing mutant markers updated where the source lines changed |
| Coordinator S1 host classes (`DisplayActionCoordinatorHostTest`, `CoordinatorReadbackHostTest`, `CoordinatorFailureHostTest`) | PASS (140/126/39 checks) |
| Android emulator display-wake (`scripts/test-display-wake-android.py`) | **NOT RUN locally**; expectations updated for the latch (power-cycle cases: back-off cleared, latch held, no new hardware call) |
| Physical AYN Thor | **NOT RUN** |

## Limits and residual risk

- Five seconds is an elapsed-time heuristic (see 1.7.4). A slow call for another reason now costs the lower screen in BOTH
  until reboot or manual reconcile, instead of until the next wake.
- The latch is per daemon process. A daemon restart in the same boot (for example an in-place update) starts unlatched;
  the next stall latches again.
- The platform still powers the lower panel on every wake from Asleep. The latch cannot prevent that or the loop it starts;
  it only stops the app from adding its own ON and keeps the degrade OFF available. The panel state itself is only known to
  reset on reboot.
- Not covered here (separate scope decisions): detecting the precursor before the first failure, a user-facing reboot
  prompt, a safe in-app refresh toggle, and any change to vendor components.

## Physical acceptance required before release

Owner-supervised, with a recovery plan, and never by forcing a blackout: a normal BOTH/TOP/sleep regression, plus confirmation
on a naturally occurring stall that the lower panel stays OFF across a wake until reboot or manual reconcile.
