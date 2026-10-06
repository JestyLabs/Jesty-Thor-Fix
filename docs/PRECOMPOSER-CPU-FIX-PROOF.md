# Pre-composer CPU Fix proof

Status: **research / host-only preparation**

Branch:

`work/thor-precomposer-cpu-proof`

No boot hook is installed by this branch. No device property, init service,
display state or release behavior is changed.

## Product goal

When CPU Fix is saved ON before a cold boot, make the *first* Qualcomm display
composer consume:

`vendor.display.disable_system_load_check=1`

before its first `ResourceImpl::Init()`.

If this can be proven, the automatic second composer/framework restart is not
needed on that cold boot.

Runtime ON/OFF transitions remain separate: the currently running composer
caches the value, so changing the toggle while Android is already running still
requires the existing controlled restart on this firmware.

## Already proven -- do not repeat

The same-process reload investigation is closed for the tested Thor firmware:

- `ResourceImpl::Init()` reads the property and caches the bool;
- `CheckSystemLoad()` consumes the cached value;
- a late `setprop` does not update the effective state;
- TOP/BOTH recreation with the same composer PID does not update it;
- a replacement composer with property `1` does apply it;
- no supported same-process `ResourceImpl` recreation path was found.

The boot-scoped restart provenance is also closed and must not be weakened:

`PREPARED -> PROPERTY_VERIFIED -> RESTART_REQUESTED -> APPLIED/FAILED`

The current early-restart work is already merged and physically validated.
This project is not another attempt to move that same restart a few seconds
earlier. Its goal is to avoid the cold-boot restart entirely.

## Historical timing

One retained boot showed approximately:

- vendor `qti_display_boot` work at ~3.701 s;
- first vendor composer start at ~4.074 s.

That gives only ~0.37 s in that observed boot. It is evidence that ordering is
tight, not a timing guarantee.

The vendor display boot script was previously observed to enable the target
property for a different `subtype_id`; the tested Thor reports the path that
leaves this property unset. Changing hardware subtype is not an acceptable
solution.

## Why matching getprop is insufficient

A later app process cannot conclude:

`getprop == 1 -> first composer consumed 1`

because the write may have happened after `ResourceImpl::Init()`.

Therefore a zero-restart cold boot requires an independent boot-scoped proof
that the property was written before the first composer start.

## Proof model

`PreComposerCpuProofModel` accepts a proof only when all are true:

1. proof boot ID equals the current kernel boot ID;
2. desired state is ON -- this early path is intentionally enable-only;
3. property readback was verified;
4. composer was absent immediately before the write;
5. composer was still absent immediately after the verified write;
6. the proof write timestamp is strictly before the first composer start;
7. the later observed property is still `1`.

Any missing/corrupt/stale/late proof is not success.

The model deliberately performs no I/O and has no authority to:

- call `setprop`;
- start/restart a service;
- change a display;
- change Wake Guard;
- change CPU restart provenance.

## Ordering evidence

Android init exposes a boot-time property for services when they first enter
the running state:

`ro.boottime.<service-name>`

A future runtime implementation should use this (when present on the Thor)
and/or the current composer's `/proc/<pid>/stat` start time to compare the
first composer start against the early property-write timestamp.

A plain `pidof` absence check alone is not sufficient proof because it has a
race window.

## Candidate proof transport

A future early hook should write its boot-scoped proof into `/dev`, not
persistent app/root storage.

Reason:

- `/dev` is recreated on a real kernel boot;
- a stale proof cannot survive into the next boot;
- the later privileged daemon can validate file type/owner/mode/content;
- uninstall cleanup is not needed for the proof itself.

This does **not** solve cleanup for the mechanism that launches the early hook.
Any persistent hook still needs a separately proven install/disarm/remove
lifecycle.

## Existing Thor root bridge

The stock Thor exposes `PServerBinder` / `pservice`, and the project already
uses that bridge for privileged commands after Android starts.

That fact alone does **not** prove it can solve pre-composer execution.

Before using it here we must establish:

1. exact init service declaration;
2. first-start time relative to the vendor composer;
3. whether it has any vendor-supported startup/config mechanism;
4. whether such a mechanism can run a tiny command before composer without an
   app/framework process invoking Binder;
5. cleanup/removal semantics.

Do not turn ordinary Binder command execution into a persistent boot hook by
assumption.

## Read-only collector

`scripts/inspect-thor-precomposer.ps1`

collects, without writes or reboots:

- relevant `init.svc.*` and `ro.boottime.*` properties;
- composer and pservice process/domain information;
- PServerBinder service visibility;
- init declarations for composer, pservice/PServer and qti display boot;
- likely vendor display boot scripts;
- occurrences of the CPU property in text scripts;
- composer `/proc/<pid>/stat` and clock tick rate;
- mount metadata and candidate hook-directory metadata.

CI guards reject mutation commands in this collector.

## Go/no-go for a real hook

A real prototype is **NO-GO** until a launch mechanism satisfies all of these:

- runs before first composer, not merely before BOOT_COMPLETED;
- opt-in and enable-only;
- no modification of immutable system/vendor partitions;
- records same-boot ordering proof;
- verifies property readback;
- has bounded behavior and no restart authority;
- can be disarmed before runtime OFF;
- can be removed independently of app uninstall callbacks;
- if it fails, the normal one-restart path remains authoritative.

If no existing firmware mechanism satisfies this, do not install a speculative
`/data/adb` hook just to get an earlier timestamp.

## Future runtime integration

Only after the early launch mechanism is identified:

1. write property `1` and verified proof before first composer;
2. later app starts normally at BOOT_COMPLETED;
3. app validates proof against this boot and first composer start;
4. **PROVEN** -> skip automatic cold-boot composer restart;
5. anything else -> use the existing one-restart provenance path;
6. full normal display safety gate still runs before display/lid reconciliation.

No display grace or Wake Guard safety rule needs to be removed to achieve the
zero-restart cold-boot path.
