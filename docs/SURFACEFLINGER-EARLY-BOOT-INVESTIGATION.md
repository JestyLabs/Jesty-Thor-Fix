# Early SurfaceFlinger abort: read-only investigation

**Status (2026-10-04):** one additional supervised v1.5.17 BOTH reboot with
read-only capture recorded the unfiltered early graphics log. The owner observed
both visual phases normal. Firmware init rules and exact installed binaries
were read or copied to the PC; no graphics property or service was manually
changed, and no APK or app data was changed. Raw logs and firmware binaries
remain outside Git under
`C:\Temp\jesty-thor-1517-evidence` and `C:\Temp\jesty-thor-sf-re-20261004`.
The earlier boots are linked from
[the validation diary](VALIDATION-1.5.17-PENDING.md).

## Clock conversion

The v1.5.16 early watcher read the same `boot_progress_start` event twice:
the first dump printed `3.965 ... boot_progress_start: 3965`, while the next
dump printed `7.511 ... boot_progress_start: 3965`. The event payload did not
change. The later dump moved the displayed time by +3.546 s. The first
`sf_stop_bootanim` payload is 17.899 s, although a later dump's column reads
21.444 s. In both v1.5.17 boots, early event columns match their payloads.

AOSP's event-tag definition explicitly stores uptime values because log-entry
timestamps use wall clock. Its `liblog` formatter constructs the `monotonic`
column by converting the stored timestamp with clock/dmesg offsets when the
log is printed. That mechanism can reformat an old entry differently on a
later read. It explains the *kind* of error observed here; a wall-clock step
or a changed conversion marker is plausible, but the saved data does not
identify which one happened on this Thor.

- [AOSP boot event tag definitions](https://android.googlesource.com/platform/system/core/+/af3dd88a36489f00b6a24ecf2831df0e6214bbda/logcat/event.logtags)
- [AOSP liblog monotonic conversion](https://android.googlesource.com/platform/system/logging/+/refs/heads/master/liblog/logprint.cpp)

The incident report therefore uses event payloads and `ro.boottime` for the
first phase. The fatal-signal time of ~4.42 s is an approximate conversion
from the first phase's displaced crash-buffer column, not a native uptime
timestamp. The original ZIP remains sealed; its corrected-report companion
is identified in the validation diary.

## Repeated boot sequence

Times below are seconds after kernel boot. The first baseline abort time is
converted approximately; the v1.5.17 abort columns agree with event payloads.

| Boot | First SurfaceFlinger start (`ro.boottime`) | Fatal SIGABRT | First zygote boot event | Next zygote boot event | First visual finish |
| --- | ---: | ---: | ---: | ---: | ---: |
| v1.5.16 BOTH | 4.095 | ~4.419 | 3.965 | 4.790 | 17.899 |
| v1.5.17 BOTH | 4.051 | 4.391 | 3.954 | 4.722 | 17.896 |
| v1.5.17 TOP | 4.108 | 4.446 | 4.322 | 4.797 | 17.909 |
| v1.5.17 BOTH, graphics capture | 3.945 | 4.325 | 3.830 | 4.576 | 17.743 |

All four captured crash buffers name `/system/bin/surfaceflinger`, report
`no suitable EGLConfig found, giving up`, and show
`SkiaGLRenderEngine::chooseEglConfig` with BuildId
`a4e0851419d45662b0fd5cd067b585bf`. These aborts occur before the
first Jesty `DAEMON_MAIN`; the first system_server run is later still. The
CPU Fix property is unknown at daemon start and is written only later.

In comparable AOSP `SkiaGLRenderEngine` source, that fatal message follows
failure to select ES3, ES2 and simplified EGL configurations. The earlier
filtered logs could not show *why* EGL failed. The new unfiltered capture does;
see below.
[AOSP render-engine source](https://android.googlesource.com/platform/frameworks/native/+/e1d797219097be512bb4a9d844eb7beb2fff7926/libs/renderengine/skia/SkiaGLRenderEngine.cpp)

The Thor's own `/system/etc/init/surfaceflinger.rc` declares
`onrestart restart --only-if-running zygote`. This supplies the missing
firmware mechanism for the new zygote after the early SurfaceFlinger abort.

## Exact failure chain from the new capture

The new boot ID is `00000000-0000-4000-8000-000000000008` and the
installed app is still v1.5.17. Uptime seconds are from the early
`logcat -v monotonic` capture; first-phase event payloads agree on this boot.

| Uptime | Observation |
| ---: | --- |
| 3.744 | `vendor_qti_graphics_boot` one-shot starts, from `ro.boottime` |
| 3.945 | first SurfaceFlinger process starts, from `ro.boottime` |
| 4.284 | SurfaceFlinger PID 1362 logs its startup |
| 4.310–4.319 | EGL loads vendor `libEGL_angle.so` and ANGLE GLES libraries |
| 4.324 | `initializeAnglePlatform failed to get valid ANGLE library filename suffix!` |
| 4.325 | `eglInitialize` returns `EGL_NOT_INITIALIZED`, then RenderEngine reports no EGLConfig and aborts |
| 4.470 | init attempts `ro.opengles.version=196610`, after the crash, in the `gpu_rendering=true` action |
| 5.082 | restarted zygote PID 1853 loads vendor `libEGL_adreno.so` |
| 34.881 | first Jesty `DAEMON_MAIN` |

The installed `/vendor/etc/init/init.qti.graphics.rc` starts
`vendor_qti_graphics_boot` at `early-boot`. Its script
`/vendor/bin/init.qti.graphics.sh` reads the SoC subset bits and then sets
`vendor.display.gpu_rendering`. Only the **property trigger** for `true`
sets `ro.hardware.egl=adreno`; the `false` branch sets it to `angle`.
The captured failure log and AOSP's matching error path indicate that
`ro.hardware.egl` was empty to this first EGL process at 4.324 s. Once
the vendor trigger completed, the device reported `ro.hardware.egl=adreno`
and `vendor.display.gpu_rendering=true`. The first SurfaceFlinger evidently
started before that driver-selection property became usable. The exact
property-set timestamp was not logged, so its ordering is inferred from the
error and vendor init sequence rather than directly measured.

The AOSP EGL loader tries the property-selected driver before unsuffixed and
wildcard fallback. With the driver selection unavailable, the installed ANGLE
library was loaded, then failed to initialize. This is a firmware startup
ordering failure, not a Jesty display action: the app started about 30.6 s
after the abort. The `vulkan.kalama.so` lookup failure in the same millisecond
is recorded but not established as causal.

- [AOSP EGL loader fallback](https://android.googlesource.com/platform/frameworks/native/+/cdb6b16dec3a541b455be99d075004cb2f0a0cd7/opengl/libs/EGL/Loader.cpp)
- [AOSP ANGLE suffix error path](https://android.googlesource.com/platform/frameworks/native/+/b51a9cc926a8f49db6c2c65a47ec4ea7d72b510b/opengl/libs/EGL/egl_angle_platform.cpp)

The pulled `/system/bin/surfaceflinger` has SHA-256
`44C3BED3B6FEF384A29C2F5C9ED66813F70617C3832B1C63C1EA13F580C50FDD`
and GNU BuildId `a4e0851419d45662b0fd5cd067b585bf`, exactly the crash
BuildId. Static AArch64 cross-reference analysis located the fatal string
at virtual address `0x574fb`, its reference at `0x5a68dc`, and the following
call to `__android_log_assert` at `0x5a68e4`, the tombstone PC
(`chooseEglConfig+504`). The actual installed binary also calls the same
EGL config selector three times before that fatal branch. This identifies
the native `SkiaGLRenderEngine` path; there is no Java class at the crash site.
The pulled `libEGL.so` BuildId is `54089dcd91ad6053d54a000e04ee7888`;
its strings include the exact ANGLE suffix failure. All pulled files were
hashed against their on-device copies. No proprietary binary is in Git.

## Remaining uncertainty and safe next step

The direct property-set timestamp and the reason the first EGL loader falls
through to ANGLE are not logged. AOSP loader source is comparable, not a
source-level reconstruction of Qualcomm's exact build. The evidence is strong
for the startup-order explanation but does not prove which vendor action
was late or whether the same sequence occurs on every boot. The shell cannot
read `/data/tombstones`; the full crash-buffer backtrace is retained instead.

Do not change `ro.hardware.egl`, restart SurfaceFlinger, alter init scripts,
or add boots solely to investigate this. The actionable report for AYN is:
make Adreno driver selection available before the `core animation`
SurfaceFlinger starts, or prevent EGL's fallback from selecting ANGLE with
an unset driver property. Any proposed firmware change needs AYN validation.
No hardware-damage conclusion follows from this software abort.
