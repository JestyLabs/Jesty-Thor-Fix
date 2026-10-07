# Stock pservice early CPU restart — model only

Status: **research / host-only preparation**

Branch:

`work/thor-pservice-early-cpu-restart-model`

This branch does **not** install `/data/boot_start.sh`, set the Qualcomm
property, restart composer, change display routing, or request a device reboot.

## Goal

If the true pre-composer zero-restart path is unavailable on stock firmware,
move the single required CPU-fix composer restart from ~30-40 seconds into the
first ~4-5 seconds of boot, while Android is still naturally in its boot
sequence.

This is a different goal from the pre-composer work:

```text
zero restart:
property=1 -> first ResourceImpl::Init

this workstream:
first composer starts -> pservice boot_start -> property=1
-> exactly one composer restart -> successor consumes 1
```

## Thor firmware evidence

Measured on one real boot:

- qti_display_boot: 3.654992 s
- first display composer: 4.038636 s
- pservice: 4.171782 s

The tested Thor reports `platform_subtype_id=0`, so it does not enter the
Qualcomm Kalama/HHG branch that sets
`vendor.display.disable_system_load_check=1` before composer.

## pservice binary evidence

Tested binary:

`/system/bin/pservice`

SHA-256:

`8a0b75b44f0139843f2608f1ac7946ed1184cb126ed2777ee2bc2fb509357be4`

The ELF is a 32-bit ARM PIE for Android 33. Its embedded mini-debug section
exposes enough symbols to reconstruct the startup sequence.

Static control-flow inspection shows `main()` always executes:

```text
sh /data/boot_start.sh &
```

The call happens before the cpu_init thread, device-manager startup and the
main Binder block. The trailing `&` makes the script asynchronous.

Therefore this stock hook is potentially suitable for a **very early one-shot
restart**, but not for proving that the first composer consumed the property.

## Safety architecture

A future implementation must reuse `CpuBootAttemptModel`; it must not create
an independent weaker provenance scheme.

Proposed early hook sequence:

```text
pservice starts
  -> /data/boot_start.sh
  -> verify app-owned opt-in identity
  -> if /dev early-attempt already exists: exit (same-boot loop guard)
  -> wait boundedly for one valid composer PID
  -> capture boot_id + previous property + baseline composer PID
  -> write PREPARED record in /dev
  -> set vendor.display.disable_system_load_check=1
  -> verify exact readback
  -> write PROPERTY_VERIFIED
  -> write RESTART_REQUESTED BEFORE ctl.restart
  -> restart vendor.qti.hardware.display.composer exactly once
  -> optionally observe successor and write APPLIED
```

The record should use the same logical fields/phase names as
`CpuBootAttemptModel.Attempt`.

Later, the normal daemon imports the same-boot early attempt and hands it to the
existing `CpuBootAttemptModel` state machine.

### No-repeat invariant

If `pservice` itself restarts in the same kernel boot, the hook must see the
existing `/dev` attempt and must never issue a second compositor restart.

`/dev` is chosen because the loop guard disappears on a true kernel reboot.

### Proven early failure

A normal late restart may be used as fallback only when an early FAILED record
proves both:

- the original baseline composer is still the current composer; and
- the global property was restored exactly to the recorded previous value.

Any ambiguous `RESTART_REQUESTED` outcome remains fail-safe: no second
automatic restart.

## Global /data/boot_start.sh ownership

This path is firmware-global and must never be treated as app-private.

Runtime integration is NO-GO until installation identity is solved. Minimum
rules:

- install only when the path is absent;
- if an unknown file/symlink already occupies it, refuse early mode;
- update/remove only an exact app-owned file with validated type, owner, mode
  and content/version identity;
- disabling CPU Fix removes only the exact owned hook;
- uninstall/stale-install state must make the hook no-op and eventually
  removable without trusting an app uninstall callback;
- app update path changes must fail open to the current normal restart path.

A device-protected app-owned identity is preferable to a generic persistent
root marker, because it can naturally disappear with app data. This still needs
SELinux/readability proof before runtime use.

## Explicitly rejected

- modifying /vendor or /system;
- spoofing platform_subtype_id;
- overwriting a pre-existing /data/boot_start.sh;
- restart loops based only on getprop equality;
- using bootanimation/splash work to hide failures;
- display/lid actions from the early hook;
- a physical reboot before hook ownership and proof import are implemented and
  host-tested.

## Success criterion for a later physical test

Only one supervised cold boot should be needed.

Success means:

- one early restart only;
- no later ~30-40 s compositor restart;
- first/second boot-animation transition is materially less intrusive than the
  current path;
- CPU attempt reaches APPLIED using existing provenance semantics;
- the normal display safety gate still runs unchanged;
- no persistent property/hook residue after opt-out.
