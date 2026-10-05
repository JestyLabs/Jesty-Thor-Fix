# Why the second visual boot starts late

**Status (2026-10-05):** the v1.5.17 analysis below now has one supervised
v1.5.20 TOP comparison. Raw v1.5.17 evidence stays outside Git in
`C:\Temp\jesty-thor-1517-evidence\20261004-043420-sf-repro-one-reboot-early`
and `C:\Temp\jesty-thor-1517-evidence\20261004-043813-sf-repro-postboot`.

## v1.5.20 TOP comparison

The signed v1.5.20 TOP cold boot retained the 10-second and five-second
graces. Its first `sf_stop_bootanim` event carried uptime 18.210 s, while the
second carried 57.844 s: **39.634 s between visible finishes**. The first
phase's `-v monotonic` column was 2.656 s later than the event's own uptime;
these figures use the event payloads. The app's first daemon started at
33.656 s, requested the composer restart at 45.228 s, and the successor
reached `BOOT_READY` at 60.539 s. The helper found the new services and
launched one successor. The owner observed the expected two visual phases
without flash or an extra cycle.

This boot validates the current safe sequence and the short stale-socket
launch wait (`74 ms`); it does **not** validate an earlier restart or a
pre-composer property write. The property was unknown at daemon start,
written at 44.897 s and applied only after the compositor restart. Avoiding
that restart remains a separate opt-in investigation. Moving the existing
restart earlier would need a CPU-only readiness gate while all display/sleep
actions stay held, plus new host and physical tests. No grace was shortened.

The reference boot is BOTH, kernel boot ID
`599c48a0-4d31-4d90-91b0-287eb5c8577d`. Times below use the early
`03-graphics-monotonic.txt` and the daemon trace. The first-phase event
payloads agree with the monotonic column in this boot. Earlier v1.5.16 logs
have a clock-conversion offset; see the SurfaceFlinger investigation.

| Uptime | Event | Interval |
| ---: | --- | ---: |
| 17.743 s | First `sf_stop_bootanim` | - |
| 17.958 s | First system_server posts `BOOT_COMPLETED` for user 0 | 0.215 s after visual finish |
| ~26.269 s | `BOOT_COMPLETED` dispatch starts, derived from Android's `dispatchLatency:8257` at 36.112 s | ~8.3 s after posting |
| 34.675 s | Jesty `BootReceiver.onReceive` | ~8.4 s into dispatch |
| 34.881 s | Jesty daemon main | 0.206 s after receiver |
| 34.904 / 35.403 / 35.903 s | Three stable CRTC samples | ~1.0 s |
| 35.903-45.940 s | Boot safety grace | 10.037 s |
| 45.965 s | CPU Fix starts | - |
| 46.407 s | Composer restart requested | 28.664 s after first visual finish |
| 59.106 s | Second `sf_stop_bootanim` | 12.699 s after restart request |
| 61.499 s | `BOOT_READY` | 2.393 s after second visual finish |

The log reports 88 `BOOT_COMPLETED` receivers, `dispatchLatency:8257`, and
`completeLatency:18100`. The preceding `LOCKED_BOOT_COMPLETED` broadcast
finished at 26.269 s after taking 8.502 s to complete. These measurements
explain why a broadcast posted just after the first animation reached the app
much later. They do not identify which individual receiver or service caused
the 8.4 s within the `BOOT_COMPLETED` dispatch. The app process had already
started at 23.549 s, so its process creation is not that delay.

**Correction to earlier analysis:** a saved `dumpsys activity broadcasts
history` was captured after the app's compositor restart. Its receiver #82
and 11.351 s completion belong to the *second* system_server, so they cannot
be used to measure first-boot delivery. The pre-restart log above is the
first-boot evidence.

## Why the older build appeared faster

The v1.3.0 `AutoService` also waited for `BOOT_COMPLETED`, but then launched
the daemon and sent the CPU Fix command immediately. The daemon scheduled
`ctl.restart` after 300 ms. There was no three-sample, 10-second boot gate.
This is consistent with a short gap **after its receiver ran**, but there is
no saved timestamped old boot with which to prove the remembered ~5-second
visual gap. The current 10-second gate was introduced after display races and
green flashes; reverting to the old sequence would discard that protection.

The known early SurfaceFlinger EGL/ANGLE abort occurs at ~4.325 s in this
boot, before the app or even the first system_server. It is a separate firmware
problem, not the late app restart request.

## Options and required proof

1. **Measure first-boot receiver delivery more precisely.** On the next
   *already planned* supervised boot, save `dumpsys activity broadcasts
   history` before the composer restart, with an uptime/clock pair. This is
   read-only. Keep the early logcat and trace. Do not schedule another boot
   solely for this capture. The history from after the restart cannot answer
   which first-phase receivers ran before Jesty.
2. **Move only CPU property/restart earlier, keep display actions held.** A
   separate CPU gate could wait for a running composer and a known property,
   then restart; the existing CRTC/mode gate would run *after* the restart
   before any display or sleep action. This could recover roughly the current
   11.7 s receiver-to-restart interval, but changes boot behavior. It needs
   host model tests and supervised BOTH and TOP boots, with a stop on a new
   crash, flash, wrong panel or restart loop. It must not treat an unknown
   property as applied or infer display state from mode `?`.
3. **Start before `BOOT_COMPLETED`.** A manifest receiver cannot receive
   `USER_UNLOCKED` on Android; that action is for dynamically registered
   receivers. `LOCKED_BOOT_COMPLETED` requires a direct-boot-aware receiver
   and device-protected storage. The current preferences and authenticated
   Unix socket/instance lock live in credential-protected `/data/user/0`, so
   simply adding that action is unsafe. A design would need explicit storage
   migration, idempotence across both boot broadcasts, and proof that the
   privileged bridge is usable then. This is larger than a timing tweak.
   See the [Android `USER_UNLOCKED` reference](https://developer.android.com/reference/android/content/Intent#ACTION_USER_UNLOCKED)
   and [Direct Boot guidance](https://developer.android.com/privacy-and-security/direct-boot).
4. **Avoid the restart at boot (option D).** If the vendor property can be set
   before the composer first starts and cannot be overwritten, the second
   visual boot may disappear. This needs the existing read-only init/script
   investigation, then a separate opt-in design with boot-loop escape and
   uninstall cleanup. The historical runtime experiments and the proposed
   toggle semantics are in [CPU Fix restart options](CPU-FIX-RESTART-OPTIONS.md).
   No persistent system change is authorized by this analysis.

Raising the priority of the app's ordered `BOOT_COMPLETED` receiver could also
move it earlier in the queue, but an earlier compositor restart might
interrupt other apps' boot receivers. The log does not establish a safe
priority. We should not use that as a shortcut.

The v1.5.20 release still uses the existing restart path. Its TOP boot had a
39.634 s interval between the two visual finishes, with 12.616 s from restart
request to the second finish. These are observations from that boot, not a
prediction for a changed gate. The next priority is the read-only
vendor-state reload investigation in [CPU Fix restart options](CPU-FIX-RESTART-OPTIONS.md),
followed by a separate CPU-only early-gate candidate if no safe runtime update
exists. Neither result justifies shortening the display safety grace.
