# Jesty Thor Fix — Early CPU restart, phase 1

Status: model + secure persistent store + CpuFixController runtime provenance integration implemented. The restart still sits behind the existing full display gate; moving it earlier is the next phase.

## Goal

Separate the CPU Fix compositor restart from the display readiness gate without weakening the
existing display/sleep safety invariants.

Current path:

```text
daemon
  -> full display gate
  -> 3 stable CRTC samples
  -> 10 s grace
  -> CPU property write
  -> composer restart
```

Target path:

```text
daemon
  -> CPU-only prerequisites
  -> persistent boot-scoped attempt marker
  -> CPU property write + readback
  -> mark RESTART_REQUESTED
  -> exactly one composer restart
  -> successor proves composer PID changed
  -> mark APPLIED
  -> existing full display gate, unchanged
  -> display reconciliation / Wake Guard
```

## Why a persistent attempt marker is required

`vendor.display.disable_system_load_check` is cached by the vendor compositor's
`ResourceImpl`. A successful `setprop` does not prove that the running compositor consumed
the new value.

Failure window:

```text
setprop succeeds
  -> daemon dies before composer restart
  -> next daemon sees getprop == desired
  -> false "restart not needed"
```

The marker records provenance across daemon replacement and prevents a false ACTIVE state.

## Model

`CpuBootAttemptModel` is deliberately Android-free so it can run in the existing host-test
suite.

Phases:

- `PREPARED`
- `PROPERTY_VERIFIED`
- `RESTART_REQUESTED`
- `APPLIED`
- `FAILED`

Persisted fields:

- kernel `boot_id`
- desired property value (`0`/`1`)
- previous property state (`0`/`1`/`UNSET`)
- baseline compositor PID
- phase
- phase timestamp on the boot clock

Critical invariant:

> Once `RESTART_REQUESTED` is persisted, no automatic path may issue another composer restart
> in the same attempt.

A successor only treats the change as applied when:

```text
same boot_id
property == desired
current composer PID != baseline composer PID
```

## Host cases covered in phase 1

- clean cold-boot enable from `UNSET`
- enable/disable mismatch starts one attempt
- `UNSET` is not silently treated as confirmed OFF
- crash before `setprop`
- crash after `setprop` but before the marker advances
- property verified + unchanged PID -> one restart may be requested
- property verified + changed PID -> compositor already recreated, mark applied
- `RESTART_REQUESTED` + old PID -> wait, never retry
- bounded timeout -> fail safe
- new PID -> mark applied
- APPLIED cannot point at the baseline compositor
- stale boot marker -> discard
- FAILED suppresses retries
- strict marker serialization/parser

## Secure store

`CpuBootAttemptStore` now persists the attempt under the app-private `files/` directory:

```text
/data/user/0/com.thor.displaypowertest/files/jesty-thor-cpu-boot-attempt-v1
```

The store deliberately fails closed and enforces:

- app-owned trusted data/files directories;
- final-component `O_NOFOLLOW` opens;
- regular-file + single-link checks;
- owner limited to root or the app UID, normalized to the app UID on write;
- mode `0600`;
- strict 512-byte maximum;
- strict canonical model parsing;
- `O_EXCL` temp-file creation;
- file `fsync` before atomic `rename`;
- parent-directory `fsync` after replace/delete;
- unexpected target/temp objects are never followed or silently removed;
- corrupt current markers are returned as `CORRUPT`, never as `ABSENT`.

The marker is not stored in `/data/local/tmp` or `/data/adb`, so uninstall/data-clear removes
the state with the application instead of leaving a persistent root hook.

## Runtime integration now in place

`CpuFixController` now reads the durable marker before trusting a matching global property and
persists the transition sequence around the actual property write/restart path:

```text
PREPARED
  -> setprop + readback
PROPERTY_VERIFIED
  -> durable boundary before any restart thread
RESTART_REQUESTED
  -> ctl.restart
successor composer PID != baseline PID
APPLIED
```

A non-boot-hold daemon resumes a current in-flight marker after a daemon crash. Once
`RESTART_REQUESTED` exists, the state model only waits for a new composer PID or fails safe; it
never issues a second automatic restart for that attempt. The post-restart boot coordinator also
calls the controller when the global property already matches so the successor can persist
`APPLIED` from PID provenance instead of trusting `getprop` alone.

## Next implementation step

Move the **initial** CPU reconcile ahead of `BootGateModel` using CPU-only prerequisites while
leaving the existing display readiness gate, stable CRTC samples, grace period, display
reconciliation and Wake Guard ordering unchanged.

This phase deliberately does **not** move the restart earlier yet, so physical behavior/timing
should remain on the existing v1.5.20-style gate until that next change.
