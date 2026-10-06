# Qualcomm system-load check — Retroid comparison notes

Status: research handoff. This document records the current comparison only; it does not change
Jesty Thor Fix runtime behavior.

## Why this comparison was done

The Thor CPU Fix sets:

```text
vendor.display.disable_system_load_check=1
```

and restarts the Qualcomm display composer because the vendor `ResourceImpl` caches that
property during initialization. Retroid devices provide a useful independent Qualcomm comparison,
especially around multi-display products.

## Retroid Pocket Flip 2 physical observations

Test device:

```text
ro.board.platform = kona
ro.build.fingerprint =
qti/kona/kona:13/TKQ1.240621.001/eng.RPFlip.20250501.115911:user/release-keys

ro.vendor.build.fingerprint =
qti/kona/kona:11/RKQ1.230824.001/RPFlip205011233:user/release-keys
```

The stock device repeatedly returned an empty value for:

```text
getprop vendor.display.disable_system_load_check
```

Therefore the property is **UNSET** on this Flip 2 build.

A recursive text search over `/vendor`, `/odm`, `/product`, and `/system_ext` found the
property string in `libsdmextension.so`, but did not find a readable firmware config/script
setting it to `1`.

The device does not have the Duo-style dual-screen dashboard/control path, so it is not a direct
behavioral reproduction target for the Thor dashboard bug.

## Static binary comparison

The pulled Flip 2 binaries were:

```text
/vendor/lib64/libsdmextension.so
/vendor/lib64/libsdmcore.so
/vendor/bin/hw/vendor.qti.hardware.display.composer-service
```

The Flip 2 `libsdmextension.so` contains the same Qualcomm design relevant to the Thor:

```text
vendor.display.disable_system_load_check
        ↓
ResourceImpl::Init
        ↓
cached ResourceImpl flag
        ↓
ResourceImpl::CheckSystemLoad
        ↓
perf-hint display tracking
        ↓
ResourceImpl::PostPrepare
        ↓
CpuHints implementation
        ↓
libqti-perfd-client.so
        ↓
perf_lock_acq / perf_lock_rel
```

For this Flip 2 build, the cached flag defaults false and is changed when the property is read as
`1`. With the property unset at startup, the system-load check remains enabled.

This means a late `setprop ...=1` would have the same conceptual limitation as on the Thor:
changing the global property does not prove that the already-running `ResourceImpl` consumed it.

## Multi-display trigger

The matching Qualcomm logic only requests this CPU performance hint when there is more than one
active display and an application layer is GPU-composed.

Conceptually:

```text
active_displays.size() > 1
        +
application layer uses GPU composition
        ↓
display added to perf_hint_on_displays
        ↓
PostPrepare sets CPU perf hint
```

When the tracked set becomes empty, the hint is released.

This explains why the single-display Flip 2 can carry the same mechanism with the property unset
without normally reproducing the Thor condition.

## Relevance to the Thor

The Thor is dual-display. Stock TOP-only behavior can leave the lower display pipeline active even
though the panel appears black. The observed Thor CPU issue is therefore consistent with this
Qualcomm path:

```text
two displays considered active
        +
GPU-composed layer
        ↓
Qualcomm system-load check
        ↓
CPU performance hint stays active
```

The comparison strengthens the mechanism explanation, but does **not** by itself prove every runtime
edge in the Thor dashboard path. The Thor's physical traces remain the source of truth for the
device-specific behavior.

## Pocket Duo follow-up

The Retroid Pocket Duo is the more useful behavioral comparison because it is a native dual-display
product. It is also a candidate for future Jesty Thor Fix compatibility work, but **no support is
claimed yet**.

The safe order is:

1. detect the device and collect read-only display topology;
2. identify its physical/logical display IDs, CRTC mapping and hall/lid input;
3. compare Qualcomm system-load-check behavior with the Thor;
4. only then test individual features such as True Bottom Screen Off or wake repair.

Do not reuse Thor IDs or write display power on a Duo until its own topology has been measured.

Useful future artifacts include:

```text
OTA zip
payload.bin
vendor.img
super.img
QFIL package
/vendor dump
```

Priority files/strings:

```text
libsdmextension.so
vendor.display.disable_system_load_check
ResourceImpl::Init
CheckSystemLoad
active_displays
CpuHints
perf_lock_acq
```

The public Retroid Dual Screen Add-on repository is useful background for Retroid's SurfaceFlinger
customizations, but it is not the Pocket Duo firmware and should not be treated as proof of Duo
vendor-display behavior.


## Testers wanted

Pocket Duo owners interested in helping can provide firmware/build identifiers and read-only
SurfaceFlinger/DRM/vendor-display observations. The most useful early result is whether the Duo
shows the same family of dual-screen focus, power, wake or Qualcomm composition issues as the Thor.

Any test build must identify itself as experimental and keep unsupported write paths disabled until
the target device profile is physically validated.
