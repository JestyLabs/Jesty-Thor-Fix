# Jesty Thor Fix — Early CPU restart, phase 1

Status: phase 1 provenance is merged; phase 2 moves the CPU reconcile ahead of the display gate while preserving the existing display/CRTC/grace rules.

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

## Phase 2 boot ordering

The boot coordinator now treats CPU and display readiness as independent phases:

```text
boot hold
  -> sys.boot_completed
  -> composer running + valid PID
  -> watcher RUNNING
  -> valid boot_id + CPU property observation
  -> CpuFixController provenance reconcile
       -> no restart needed: continue
       -> restart required: hand off immediately
  -> existing BootGateModel
       -> known AYN mode
       -> valid top/bottom CRTC
       -> 3 stable samples
       -> unchanged 10 s initial / 5 s post-restart grace
  -> display reconciliation
  -> Wake Guard enable
  -> BOOT_READY
```

The CPU-only gate deliberately does not read mode/CRTC state or issue display/lid actions.
`BootSafety` remains held throughout both phases. A current `APPLIED` marker can also prove
post-restart compositor provenance if a replacement daemon loses the explicit `post` argv token,
so the post-restart display gate retains its 5-second semantics.

The expected physical effect on a normal cold boot with the CPU fix enabled is to move the
composer restart from roughly the end of the 10-second display grace to immediately after the
CPU-only prerequisites become ready. The display gate itself is not shortened.
