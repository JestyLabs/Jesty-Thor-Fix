# 1.4.0 physical validation — in progress, not release-ready

The signed candidate was installed in place on the AYN Thor for limited
physical checks. **Testing pre-release only; do not mark v1.4.0 stable or
Latest.** Cold-boot testing is in progress; the CPU Fix compositor restart and
lid guard still require the full physical matrix. A working APK and passing
unit tests are not stable-release validation.

On 2026-09-30, with both fixes enabled, a cold boot reached `BOOT READY` and
the user had not observed a green flash. Two distinct SurfaceFlinger aborts
were recorded. The first was at 11:50:33, about two seconds into that process,
with `no suitable EGLConfig found`; it happened **before** the app's boot
receiver ran, so it cannot be attributed to the app's boot-time action. The
second was at 11:51:14, during the intentional composer restart required by
the CPU Fix. It reported a dead HIDL composer object (`DEAD_OBJECT`) in
`getDisplayVsyncPeriod`. The Thor's init rules explicitly cascade the one
requested composer restart to SurfaceFlinger and then zygote, explaining the
Android UI/framework restart. The `SIGABRT` is still not a clean exit. Android
sent a second `BOOT_COMPLETED` and reconfigured USB.
The USB prompt and ADB disconnect looked like another reboot, but the kernel
boot ID remained `463538ff-c39b-43d3-b67d-2395133201e7` while uptime
continued to increase. This is **not yet** a successful safe-boot result, but
neither abort alone proves a hardware fault or that the app caused the early
boot abort.

The second `BOOT_COMPLETED` also made `AutoService` stop and relaunch the
daemon, adding another BOOT HOLD interval. The candidate source now avoids
that redundant stop. The rebuilt APK has been installed, but this boot-specific
change has not yet been validated by another cold boot. It will not fix the
SurfaceFlinger crash by itself.

Read-only health check after the incident: battery 91-92% and USB charging,
30.0 C, thermal status 0, compositor `running`, Android boot complete, daemon
`READY`, and device asleep with both CRTCs inactive. No additional kernel
reboot or display/composer restart was requested during this investigation.
The source audit found a no-op guard when the CPU property already matches
the preference, one intentional `ctl.restart` for a mismatch, and the
redundant daemon stop on the second boot broadcast noted above.

The rebuilt signed APK (SHA-256
`23651760668574A0298E7A17DE9B1587B64E6BFC71181CDB7EDCD56201951796`)
was installed in place on 2026-09-30 without clearing data or rebooting.
Kernel boot ID, SurfaceFlinger PID, system_server PID, and composer state
remained unchanged. With the lid physically open (`SW_LID=0`), daemon commands
`G`, `Q`, `H`, `Q` confirmed `lid=open`, guard ON then OFF, zero blocked wakes,
and no external display. This does not validate closed-lid behaviour.

An actual top-display screenshot of the installed signed candidate in
`BOTH SCREENS` mode was saved as
`docs/images/dashboard-both-v1.4.0.png` (SHA-256
`1010B28491B1B05C05387B4DADCC50AFB41DC64C4ABB8B7E49F2FAEE903DE042`).
It was visually reviewed. The device was returned to sleep afterward, with
the same boot ID and composer still running. README image references remain
unchanged until the other display states and final build are validated.

Controlled cold boot #1 with both fixes ON (user observed the lower screen):
no green flash or other visible artifact reported. The kernel boot ID changed
once to `10ff46ff-fce7-4826-8da8-7a0b7023a6cb`; the apparent second reboot
was the firmware's composer-to-SurfaceFlinger-to-zygote restart. The user saw
the USB-choice dialog for roughly one minute. The boot trace has one
`APPLY_CPU_FIX` at elapsed 46.2 s, one `WAIT_AFTER_COMPOSER` at 65.8 s, and
`BOOT_READY` at 72.0 s. Importantly, there was no second `WAIT_FOR_ANDROID`
or daemon stop/relaunch after the duplicate `BOOT_COMPLETED`, confirming the
idempotence change on this boot. The system-load property finished at 1,
daemon `READY`, composer running, and both CRTCs inactive while asleep.
Logcat still recorded an early `no suitable EGLConfig found` SurfaceFlinger
abort before the app acted; it did **not** record a second SurfaceFlinger
`SIGABRT` during the CPU Fix transition on this boot.

Three more controlled cold boots of that candidate with both fixes ON reached
`BOOT_READY` at 81.1, 72.0, and 71.8 s. The user reported no green on the
third and fourth; the second was probably clean but not observed confidently.
Each trace had one `APPLY_CPU_FIX` and no duplicate `WAIT_FOR_ANDROID` after
the compositor restart. The kernel boot ID changed once per cold boot.

An adaptive *post-compositor* relaunch was then built and signed (APK SHA-256
`C3B5D4927A131EF900F30ACBA250823D43A5952CF6D095B0E4866C8EAC939E14`,
same signing certificate). It retains the 10-second pre-apply boot grace and
the 18-second transition wake-lock coverage, but relaunches the daemon after
an eight-second minimum once composer, SurfaceFlinger, zygote, Android boot
state, and package manager respond. The post-restart boot gate still requires
stable CRTCs and five seconds before display actions. The signed build was
installed in place without reboot, then one user-observed controlled cold boot
was run. The user reported the lower screen clean, with no green. Its trace:
`WAIT_FOR_ANDROID` 34.445 s, `APPLY_CPU_FIX` 46.009 s,
`WAIT_AFTER_COMPOSER` 55.706 s, `RECONCILE_DISPLAY` 62.104 s,
`BOOT_READY` 62.112 s. This is roughly ten seconds earlier than the preceding
71.769-second boot, without moving the CPU Fix restart earlier. The kernel
boot ID changed to `e6d459de-e783-4e9c-8964-f8bd12983dbe`; Q returned
`READY`, CPU Fix property 1, BOTH mode and both CRTCs active. Logcat recorded
the same early `no suitable EGLConfig found` SurfaceFlinger abort, before the
app boot action, but no second SurfaceFlinger abort during the CPU Fix restart.
This is one clean visual boot of this exact APK, **not** a five-boot pass.

- [x] Identify `hall_switch` at `/dev/input/event2`; an open-lid snapshot gave
  `SW_LID=0`. Closed-lid value still needs physical confirmation.
- [x] Verify `input keyevent 223` sleeps the device after an open-lid ADB wake;
  physical lid-close/open wake behaviour remains to be tested.
- [ ] Test closed-lid false wake, three-attempt loop guard, and external display.
- [ ] Five cold boots with both fixes OFF.
- [ ] Five cold boots with only True Bottom Screen Off ON.
- [ ] Five cold boots with only AYN Dashboard CPU Fix ON.
- [ ] Five cold boots with both fixes ON.
- [ ] For every boot, record sanitized `ThorDisplayDaemon` phase/action trace,
  expected CRTC final state, composer restart count, and any visual green flash
  or other display artifact.
- [ ] Sleep/wake, top/both/bottom, unknown mode, and timeout recovery checks.
- [x] Capture and visually review the BOTH-mode dashboard from the installed
  signed candidate; TOP fake-off, TOP true-off, and guard-specific captures
  remain pending.
- [ ] If green occurs in CPU-only boots, stop release and revisit CPU auto-apply.
