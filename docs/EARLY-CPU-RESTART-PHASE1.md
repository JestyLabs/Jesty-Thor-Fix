# Jesty Thor Fix — Early CPU restart, phase 1

Status: design/model only. No APK runtime change yet.

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

## Next implementation step

Add a small production `CpuBootAttemptStore` under the app-private `files/` directory.

Required storage properties:

- final component opened with `O_NOFOLLOW`
- regular file, single link
- mode `0600`
- owner root or app UID, normalize to app UID
- strict size bound
- strict parser
- durable write before `ctl.restart`
- atomic temp-file + rename update preferred
- corrupt current marker => fail safe, never treat as "absent"

Then integrate it into `CpuFixController` and move the initial CPU reconcile before
`BootGateModel` in `BootCoordinator`.

The existing display gate timings are not changed in this phase.
