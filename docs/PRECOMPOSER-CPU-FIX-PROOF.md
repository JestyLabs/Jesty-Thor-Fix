# Pre-composer CPU Fix proof

Status: **closed historical research for the tested stock Thor firmware**

This document records the investigation that asked whether the CPU property could be set before the **first** Qualcomm composer and thereby avoid any cold-boot composer recovery.

Current conclusion (v1.6.0): no safe app-controlled pre-composer execution path was found in the inspected stock Thor init/vendor configuration. The shipped solution therefore uses the later stock `pservice -> /data/boot_start.sh` path to move the one required composer recovery into the natural startup window. Removing that restart entirely would require vendor/firmware/init support, modification of immutable vendor content, or genuinely new evidence of an earlier trusted privileged hook.

This is not an active release gap. Keep the evidence below for provenance; do not interpret the old future-work sections as the current roadmap.

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

The early-restart path is merged, physically validated and shipped in v1.6.0. The zero-restart question documented here is closed for the currently inspected stock-firmware/app-only routes unless new platform evidence appears.

## Thor firmware evidence

Read-only inspection of the tested Thor exposed exact init boot times:

- `ro.boottime.qti_display_boot = 3654991769` -> 3.654992 s;
- `ro.boottime.vendor.qti.hardware.display.composer = 4038635987` -> 4.038636 s;
- `ro.boottime.pservice = 4171782341` -> 4.171782 s.

Therefore the stock `qti_display_boot` service starts about **383.644 ms
before** the first vendor composer process, while `pservice` starts about
**133.146 ms after** that composer process. These are measurements from one
real boot, not timing guarantees.

The firmware declaration is:

```text
service qti_display_boot /vendor/bin/init.qti.display_boot.sh
   class main
   user system
   group system
   disabled
   oneshot

on post-fs-data
   start qti_display_boot
```

The vendor script reads `ro.board.platform`, SoC ID and
`platform_subtype_id`. In its Kalama branch, for the supported Kalama SoC IDs,
it explicitly does:

```text
if [ "$subtype_id" -eq 1 ]; then
    setprop vendor.display.disable_system_load_check 1
fi
```

This is direct firmware evidence that Qualcomm intentionally supports the
target property being established **before the first display composer** on at
least one hardware subtype. The zero-restart concept is therefore compatible
with the vendor initialization model; the unresolved problem is how to opt in
on the Thor without falsifying hardware identity or modifying immutable vendor
files.

Changing `platform_subtype_id`, patching `/vendor`, or replacing the vendor
script is explicitly out of scope.


## Offline init-tree audit

A complete read-only pull of the Thor init trees was audited locally:

- `/system/etc/init`;
- `/system_ext/etc/init`;
- `/product/etc/init`;
- `/vendor/etc/init`.

The current boot reports:

- `ro.board.platform=kalama`;
- `ro.vendor.qti.soc_id=603`;
- `ro.product.model=AYN Thor`.

The stock init sequence triggers `post-fs-data` before `early-boot` and
`boot`. The Qualcomm display boot service is started from `post-fs-data`.
The standard binderized HAL class, which contains
`vendor.qti.hardware.display.composer`, is started later by
`class_start hal` in the `on boot` action.

No general writable-data execution hook suitable for this project was found in
the pulled init tree:

- no `import /data/...`;
- no init service whose executable path is a writable `/data` script;
- no `exec /data/...` pre-composer path;
- no stock `post-fs-data.d` or `service.d` import;
- no property trigger that maps a project-controlled persistent property to
  `vendor.display.disable_system_load_check`;
- no reference to `/data/boot_start.sh` in init rc files.

The only occurrence of
`vendor.display.disable_system_load_check` in the pulled firmware scripts is
the Qualcomm `init.qti.display_boot.sh` Kalama/HHG branch itself.

There are services which consume data files (for example perfetto configs), but
none provide an acceptable arbitrary early command path for setting this
property.

This means a zero-restart implementation cannot currently be built as a normal
app-only feature on the inspected stock init configuration without one of:

1. an already-installed early-root framework/hook that runs before `boot`;
2. modification/overlay of immutable init/vendor content;
3. new evidence that an existing stock service executes a writable hook early
   enough.

Options 2 is out of scope. Option 1 must be detected explicitly rather than
assumed. Option 3 remains research-only.

## Stock pservice binary result

The tested `/system/bin/pservice` was statically inspected.

- ELF: ARM32 PIE, Android 33;
- SHA-256:
  `8a0b75b44f0139843f2608f1ac7946ed1184cb126ed2777ee2bc2fb509357be4`;
- embedded mini-debug symbols expose `main`, `cpu_init`,
  `device_manager_start`, `BinderMainBlock` and related functions.

Its `main()` invokes:

```text
sh /data/boot_start.sh &
```

unconditionally. Control-flow inspection shows this happens before the
`cpu_init` thread, device-manager startup and the main Binder block. The
trailing `&` means the script itself is asynchronous.

This is useful for an **early restart** design, but it is not accepted as
pre-composer proof: the measured pservice process start (4.171782 s) is already
after the measured first composer process start (4.038636 s). No direct
timestamp for the first `ResourceImpl::Init()` was recovered, so this is
classified as **not proven early enough**, not as a mathematical impossibility.

Accordingly, `/data/boot_start.sh` must not be used by this zero-restart
workstream to claim that the first composer consumed the property. A separate
research branch may evaluate it as a way to move the one required restart into
the first ~4-5 seconds of boot.

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
uses that bridge for privileged runtime commands.

Its init declaration is stock firmware:

```text
on boot
    start pservice

service pservice /system/bin/pservice
    class core
    disabled
    user root
    group root
    seclabel u:r:pservice:s0
```

Measured first-start timing places pservice at 4.171782 s, after the first
composer start at 4.038636 s. Therefore PServer is **not an accepted
pre-composer launch mechanism** based on current evidence.

The firmware also probes `/data/boot_start.sh`, but an action launched by
pservice inherits this late start and cannot be treated as pre-composer proof.
Do not use `/data/boot_start.sh` for the zero-restart path unless new evidence
shows execution before the first `ResourceImpl::Init()`.

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
