# Thor / Flip 2 PServer compatibility research

Status: research only; public summary, 2026-10-08.

## Result

The acquired stock server binaries are different, with a closely shared Binder
core. Confidence is high for the two locally verified images; this is not a claim
about every AYN or Retroid firmware.

Detailed binary analysis, security observations, device/session identifiers and
raw captures are retained locally outside Git. This document contains only the
engineering conclusions needed to guide the two projects.

## Portable engineering conclusions

- The current client transport is compatible across the acquired images.
- Keep the released payload convention; no convention change is justified.
- Transport acceptance and command success must remain separate concepts.
- Commands and replies should remain small, fixed and explicitly validated.
- Process lifetime must use boot identity, PID and kernel process start time.
- A client lock can order one application's operations; it cannot order all clients.
- Startup hooks, CPU management, SELinux policy and OEM cleaners are device-specific.

The Thor boot hook must not be assumed available on Flip 2. Root-launch viability
does not establish survival after cleaner, Force Stop, deep sleep or package update.

## Concrete follow-up work

| Work | Result required | Gate |
|---|---|---|
| Passive lifecycle diagnostics | Paired process identity and Binder-visibility samples, explicit read failures | Read-only research |
| Client failure taxonomy | PR #50 delivered lookup-null, rejected transact and sanitized exception categories; Binder death/root cause still requires evidence | Host-tested; normal-operation Thor smoke passed |
| Helper ownership model | Exact boot/PID/starttime plus UID/cmdline and authenticated peer identity where available | No kill by name or socket occupancy |
| Flip 2 survival experiment | Harmless leased sentinel, exact identity and continuing heartbeat through Clear All | Separation OFF, owner supervised |
| Future transport design | Typed operations, bounded waits and no blind replay of ambiguous writes | Only after a concrete failure warrants it |

## Thor lifecycle assessment

The server comparison strengthens the need to distinguish initial registration,
later registry loss, ServiceManager generation change, policy visibility and
client lookup failure. It does not select the historical root cause.
If a natural incident occurs, capture evidence before recovery. Do not provoke it.

## Retroid resilience assessment

Android remains the charging-policy owner. A possible future helper is restore-only
and must pass its own physical gates. Normal close, individual Recents removal,
Clear All, standby cleaning, USB-free sleep, Force Stop and package update are
separate experiments. Parent metadata does not prove detached child survival.

## Recommendation

COMMON PSERVER MODEL: partial, with high confidence for the acquired pair.
SAFE TO SHARE: transport lessons, identity methods and bounded-helper principles.
THOR SHOULD ADOPT: exact process identity, group/cgroup diagnostics and research leases.
RETROID SHOULD ADOPT: authenticated owner identity and narrowly verified replacement.
DO NOT CROSS-APPLY: hardware controls, startup hooks, cleaner or policy behavior.
NEXT PHYSICAL TEST: harmless leased Flip 2 sentinel through Clear All, separation OFF.
PRODUCTION CHANGE JUSTIFIED: no; failure cause and physical safety remain unresolved.

See [lifecycle investigation](PSERVERBINDER-LIFECYCLE-INVESTIGATION.md) and the
[publication policy](../SECURITY-PUBLICATION.md).
