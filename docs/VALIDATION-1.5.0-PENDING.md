# 1.5.x validation ledger — candidates, not a release

The exact signed 1.5.0 candidate (versionCode 49, SHA-256
`627A174FDF18030A012A79C05E5793BDAAF2DDD6DA12AAB116F66DEBA4E274FB`)
was installed once over 1.4.2 on 2026-09-30. The migration **failed closed**:
the old TCP listener remained, so the new daemon was not launched, no display
action was attempted, and no reboot occurred. The old root daemon was still
running afterward. The suspected cause is an over-strict process-environment
check in the migration filter; access to another process's environment is not
guaranteed in this vendor context. This is a hypothesis, not a proven SELinux
denial. The candidate is not published. The corrected candidate uses
versionCode 50 / versionName 1.5.1 and must repeat the affected migration and
installed-update tests. Both screens were ON before installation; the device
later slept normally.

The signed 1.5.1 candidate (versionCode 50) was installed next without a
reboot. It uniquely identified and stopped the old daemon, removed the TCP
listener, and the new daemon reached `BOOT READY` in BOTH with bottom CRTC=1.
However, the app-side authenticated health check did not succeed; a second
launch was submitted but the existing socket prevented a duplicate daemon.
The UI showed no telemetry. The shell UID was denied access to the private
socket, as intended. **No physical mode or CPU Fix tests follow until the
legitimate app connection is diagnosed.** A 1.5.2 diagnostic candidate adds
one bounded failure log and refuses to launch over an existing secure socket;
it is not a release candidate yet.

The signed 1.5.2 diagnostic APK (versionCode 51) was installed without a
reboot. Its log showed the exact transport failure:
`LocalSocket.connect(address, timeoutMs)` throws
`UnsupportedOperationException` on this Thor. The app never established a
connection; this is not evidence of a peer-UID or SELinux rejection. The
1.5.3 candidate (versionCode 52) uses the supported one-argument local
connect and keeps the read timeout. A socket inode left by a dead daemon can
be replaced only after the new root server checks its type, owner and live
peer; a reachable but unhealthy daemon remains a hard stop. The 1.5.3 APK
must be installed and validated before any display-mode test or release.

The signed 1.5.3 APK (versionCode 52) restored the authenticated channel:
one root daemon, a healthy app handshake, and no TCP listener. Both Android
displays remained ON and no reboot was requested. The boot trace then showed
`BOOT_CPU_FIX_NOT_APPLIED`: the vendor CPU property had become absent (`?`),
despite the saved preference being ON. The daemon correctly did not restart
the composer, but incorrectly held the *display* in `BOOT SAFETY TIMEOUT`.
The 1.5.4 candidate (versionCode 53) keeps CPU state unconfirmed, skips any
automatic CPU restart on `?`, and allows an independently stable display to
finish reconciliation. Its UI explicitly marks the CPU state unknown. This
candidate is not yet installed or published.

The 1.5.4 APK (versionCode 53) was installed with both displays ON. Android
kept the 1.5.3 root daemon alive across the package update, so the 1.5.4 app
correctly rejected its version and held replacement. The 1.5.5 candidate
(versionCode 54) permits replacement only after an authenticated v1.5.3
health response, a matching root process command line, and a fresh Q snapshot
showing BOTH with both CRTCs ON. It waits for that listener to disappear before
launching the new daemon in boot hold. No reboot has been requested.

The 1.5.5 APK replaced the authenticated 1.5.3 daemon without a reboot; the
new daemon became reachable, the old TCP listener remained absent, and BOTH
stayed physically ON. A screenshot confirmed restored telemetry and an
explicit CPU-property-unknown warning, but exposed one malformed UI glyph.
The replacement incorrectly started without boot hold because the protocol
migration marker was already set. The 1.5.6 candidate (versionCode 55) fixes
that, corrects the glyph, and only completes its health check at `BOOT READY`.
It must repeat the affected migration and display checks before release.

The signed 1.5.6 APK (versionCode 55, SHA-256
`B96D6E664448567A6F1DDBB1830232C7B67807FEE5DA0776A3BA2EE2A5687074`)
was installed on 2026-10-01 over 1.5.5. With both physical screens ON,
the app authenticated and identified the old root PID, stopped only that
process, and launched one new daemon with `bootHold=true`. The trace records
`BOOT_CPU_FIX_NOT_APPLIED` because `vendor.display.disable_system_load_check`
is absent, then `RECONCILE_DISPLAY`, `BOTTOM_ON_CONFIRMED`, and `BOOT_READY`
without a compositor restart or kernel reboot. The app-side health check
completed only after READY. One root `app_process` and one private Unix
listener were present; TCP 3804 had no listener. A shell-UID client was denied
at the socket path. The screenshot was visually checked in BOTH with both
CRTCs ON and a clear red `CPU FIX STATE UNKNOWN · NO RESTART` warning.

With the Hall snapshot `SW_LID=0`, enabling Wake Guard through the UI showed
`LID OPEN · WAKE GUARD ON · 0 BLOCKED`; it was returned to OFF. This is **not**
a closed-lid wake or loop test. The user then selected AYN TOP ONLY via the
physical button. The AYN mode became 1; the daemon logged `WATCH_TOP OFF`;
DRM showed top CRTC 181 active=1 and bottom CRTC 243 active=0; the UI showed
`TOP ONLY · TRUE OFF` with the correct art. Android `dumpsys display` still
reported the logical lower display ON; it does not supersede the DRM CRTC
hardware result. Return to BOTH, BOTTOM, sleep/wake, dock and CPU Fix
confirmation remain pending. No green flash was reported in this non-reboot
sequence.

One preliminary read-only CPU sample in TOP with the app foreground and
telemetry polling measured the root daemon's `/proc/<pid>/stat` user+system
time rising by 150 jiffies over about 10 seconds (100 jiffies/s), roughly
15% of one core. This includes the whole daemon, not just the 20 ms watcher;
it is not a battery-life estimate. Repeat under controlled idle conditions
before changing watcher cadence in a separate build.

The user returned to BOTH through the physical AYN shortcut. Mode=0, both
CRTCs active=1, `display.power.state=1`, and the UI artwork and label returned
to BOTH. The CPU property was still `?`, while one instantaneous screenshot
showed LITTLE and BIG at their maxima. That snapshot alone does **not**
establish sustained pinning; the 1.5.6 UI intentionally paused the 12-second
clock classifier on an unknown property. Candidate 1.5.7 (versionCode 56)
allows the existing low-load, time-in-state window to report the raw clock
symptom even while keeping CPU Fix application explicitly unconfirmed. This
candidate is not installed or published yet.

One open-lid ADB sleep/wake cycle in BOTH used `KEYCODE_SLEEP` then
`KEYCODE_WAKEUP`; no reboot. While Android reported `Asleep` and both logical
displays OFF, the lower DRM CRTC still read active=1 and the upper active=0.
The app did not infer a healthy awake display state from that sleep snapshot.
After wake, Android reported `Awake`, AYN mode remained 0, both logical
displays were ON and both CRTCs active=1. No extra repair action was requested.
This is one smoke test, not the full sleep/wake matrix.

This ledger separates host-side checks from device behavior. An unsigned APK
must not be installed as an update. Keep the existing v1.4.2 installation and
avoid unnecessary reboots until the signed candidate and recovery steps are ready.

## Completed without modifying the Thor

- [x] Java model tests: boot/lid, strict property parsing, display decisions,
  delayed-action invalidation, and IPC UID policy.
- [x] Local APK build and alignment of an **unsigned** 1.5.0 candidate.
- [x] Static check: the smali watcher no longer writes display state directly;
  no legacy exported On/Off/Apply receiver remains in the manifest.
- [x] Read-only ADB: Thor connected, battery 100%, USB power present,
  compositor running, Android boot completed, v1.4.2 root daemon still present.

## Gates before installation

- [ ] Review the complete code diff, especially the composer relaunch helper,
  migration process identity, and property rollback after failed display action.
- [x] Prepare the initial 1.5.0 signed candidate with the existing Jesty certificate;
  versionCode 49, versionName 1.5.0, package
  `com.thor.displaypowertest`, alignment, v1/v2/v3 signatures, and certificate
  SHA-256 `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
  verified. Candidate APK SHA-256:
  `627A174FDF18030A012A79C05E5793BDAAF2DDD6DA12AAB116F66DEBA4E274FB`.
  Any code edit requires a rebuild and a new hash entry; this is not yet a
  publication hash.
- [x] Run the pre-publication scan: passed for the 1.5.6 staged source.
- [ ] Preserve current device logs and a recovery path; warn the user before
  the one in-place installation. Do not trigger a reboot merely to preflight.

## Device gates, in this order

- [x] Install the exact signed 1.5.6 candidate over the immediately preceding
  signed candidate; the original in-place migration from v1.4.2 was tested
  separately above.
- [x] Confirm private socket path, daemon UID on the app side, app UID on the
  daemon side, READY health response, and disappearance of the legacy TCP
  listener via the authenticated handshake and shell-UID rejection. Exact
  inode owner/mode and a second-app client still need direct confirmation;
  there is no TCP fallback.
- [ ] Try a silent client, a different app UID, and an occupied namespace;
  verify bounded failure, no command execution, and no daemon duplicate.
- [ ] Without reboot, exercise the physical AYN button through BOTH, TOP,
  BOTTOM and back. Confirm CRTC states, effective/pending UI, stale repair
  invalidation, and no speculative ON in unknown/sleep state. BOTH→TOP and
  its UI/CRTCs passed; return to BOTH passed. In BOTTOM ONLY on 2026-10-01,
  AYN reported `mode=2` while both physical CRTCs (181 and 243) stayed active,
  and both display devices reported ON. The old model incorrectly required
  upper CRTC 0 and would show a mismatch. Local source now classifies this
  observed state separately and never powers the lower panel ON from `mode=2`
  alone. The user returned to BOTH through the physical shortcut. Host model
  tests pass, but on-device validation of the corrected BOTTOM-only UI remains
  pending. No reboot was performed for this test.

The in-place v1.5.8 test build was installed while physically in BOTH and
recovered a single authenticated daemon without reboot, but its APK version
was 1.5.8 while the health response still reported 1.5.7. This was a release
identity defect, not a display transition. Candidate v1.5.9/versionCode 58
uses one `DaemonIdentity.VERSION` for both daemon and app health checks, with
a host test enforcing the manifest match. Its signed APK SHA-256 is
`4A2E2B2E4429C40CD3275767FE325FAF3D01DC5857EDB9BF3EDDD3029F4A0FA0`;
certificate SHA-256 remains
`727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`.
It installed in BOTH over v1.5.8 without reboot, replaced the known prior
daemon, and reached `BOOT READY` with one root daemon reporting 1.5.9. Both
CRTCs stayed active. The BOTTOM-only UI correction still needs direct device
validation before publication.
- [ ] Test wake repair, lid-close/open/false wake, and external display only
  where hardware and safe observation are available. Stop at first anomaly.
- [ ] Test one user-approved CPU Fix change only if needed; it intentionally
  restarts the display composer/framework. Confirm pending then confirmed,
  daemon recovery, USB reconnection, and no restart loop.
- [ ] Compare existing early SurfaceFlinger logs separately from the
  composer-restart `DEAD_OBJECT` sequence. Do a cold boot only to answer a
  specific unresolved question, with user attention and preserved evidence.

## Publication

- [ ] Publish the exact signed APK/hash as a 1.5.9 pre-release only after app
  gates pass. Update README, release notes, screenshots where actually changed,
  integrity documentation, and profile links from observed results.
- [ ] Promote that same tag and APK to stable only after the remaining app
  gates pass; confirm the published SHA-256 remains identical. Any APK change
  requires a new version/code and affected validation again.

The firmware bridge finding is handled privately with the vendor later. This
ledger does not claim a firmware correction or full device security.

## Review continuation on 2026-10-01

The working tree was clean at `1c45000` on `work/thor-v1.5.9-review` before
this review. The connected Thor still reports installed versionName 1.5.9 and
versionCode 58. A read-only snapshot found AYN mode 0, one root `app_process`
daemon, the private Unix listener in `/proc/net/unix`, no TCP 3804 listener,
and the CPU property still empty. At the time of the DRM read, Android was
asleep and both CRTCs were inactive; this is **not** an awake BOTH-mode
confirmation. The shell UID cannot stat the private socket inode, so its
owner and mode remain unverified by this check.

Both host suites (`test-boot-lid.ps1` and `test-dashboard.ps1`) passed again.
The local signed APK still hashes to
`4A2E2B2E4429C40CD3275767FE325FAF3D01DC5857EDB9BF3EDDD3029F4A0FA0`.
`git diff --check origin/main...HEAD` passed. This review inspected the
authenticated socket, old-daemon replacement gate, display coordinator,
CPU/composer restart path, wake guard, and dashboard effective-state logic.
It does not close the adversarial IPC or physical transition gates above.
In particular, the composer relaunch remains untested with the current
unknown CPU property; do not force it merely to clear a checklist item.

After the user selected BOTTOM ONLY through the physical AYN control, a live
read found `dual_screen_display_mode=2`, Android awake, CRTC 181 active=1,
CRTC 243 active=1, and both Android display devices ON. This reproduces the
hardware combination that v1.5.9 classifies as BOTTOM ONLY with upper
hardware still active. The daemon remained a single root process and
`display.power.state=1`; the CPU property stayed unknown. The app running on
the lower display reported `BOTTOM ONLY · TOP HARDWARE ON` and `Upper panel
remains active in AYN mode`; its screenshot showed the both-panels-on artwork.
The user confirmed this text. Whether the upper glass displayed content or
black was not established by visual inspection. The user does not plan to use
BOTTOM ONLY and moved further work on that mode to phase 2. Its remaining
visual and transition cases are therefore explicitly deferred, not passed.

## v1.5.10 review candidate — 2026-10-01

This phase covers BOTH and TOP. Further BOTTOM ONLY work is deferred to a
separate phase whose implementation is not yet decided. The original 20-boot
matrix has been replaced by proportional, observed validation: at most two
cold boots, only when each answers a specific open question. No release is
authorized by this change in test scope.

The v1.5.10 source uses versionCode 59 and adds the installed v1.5.9 daemon
to the narrow replacement allowlist. It lets dashboard text and metrics grow
vertically instead of clipping inside fixed-height rows. The private socket
listener now verifies its bound inode owner/type/mode and refuses to remove
any reachable listener. Host boot/lid and dashboard suites passed; the signed
APK is aligned, has v1/v2/v3 signatures, and uses the established certificate.
The exact candidate APK SHA-256 is
`EFA19FD4333694F8833503860577A12FBEE19152CB4F5D1874F8C2D4B97C3031`.

Before installation, the connected Thor reported v1.5.9, AYN mode 0, Android
asleep, one root daemon, the private Unix listener and no TCP 3804 listener.
The CPU property was still unknown. The previous signed v1.5.9 APK and a
sanitized boot trace were preserved locally. After the user woke the device,
BOTH had CRTC 181=1 and 243=1, battery 100%, and temperature 28.0 C.

One in-place installation of the exact v1.5.10 APK succeeded without clearing
app data or rebooting. The old authenticated daemon was replaced only after
the BOTH/CRTC gate; the new daemon reached `BOOT READY` with one root process
and the private listener. Both CRTCs stayed active, the compositor and
SurfaceFlinger PIDs stayed unchanged, and the kernel boot ID stayed unchanged.
The APK pulled back from the installed package matched the signed host APK
byte-for-byte by SHA-256.
The unknown CPU property produced `BOOT_CPU_FIX_NOT_APPLIED`, not a restart.
A real lower-display screenshot in BOTH shows the feature descriptions and
CPU metrics readable after scrolling; the warning remains `CPU FIX STATE
UNKNOWN · NO RESTART`. The reviewed image is
[`dashboard-both-v1.5.10-review.png`](images/dashboard-both-v1.5.10-review.png),
SHA-256 `53B5C849313D3E880432CBB9FB1671BC8DBCE9A350EB5D0062981C90B413B538`.

Adversarial IPC checks on this installed build: a temporary app with UID
10122 could not connect to the private socket owned by the Jesty app UID
10166; it was removed after the check. A temporary signed instrumentation
client running as the Jesty app UID 10166 connected but sent no command. The
daemon closed that client after its 1.5-second read timeout, and one root
daemon, the private listener, mode 0, and both active CRTCs remained. That
instrumentation package was also removed. A same-UID instrumentation check
read the live socket inode as a socket owned by UID 10166 with mode 0600 and
SELinux label `u:object_r:app_data_file:s0`. The v1.5.10 listener also checks
type, owner, and mode before accepting clients.

An isolated occupied-path exercise exposed a further Thor-specific defect:
Android allowed a second `LocalSocket.bind()` to replace the filesystem inode
even while a `LocalServerSocket` still held the first listener. The test used
only `jesty-isolated-collision-v2.sock`; it did not touch the active control
socket. The original inode was 372490 and the replacement 372489. The live
daemon remained unique and healthy. Because a pre-bind reachability check
cannot make concurrent starts atomic, v1.5.10 is superseded by v1.5.11 before
the final physical test. No cold boot has been used in this review phase.

## v1.5.11 final review candidate — 2026-10-01

VersionCode 60 / versionName 1.5.11 adds a cross-process file lock in the
private app directory. The daemon acquires it before stale-socket cleanup or
bind and holds it for its lifetime. Lock-file type, owner and mode are checked;
the migration allowlist adds only the installed v1.5.10 identity. The code
compiled against Android 34, both host suites passed, the APK is aligned and
signed with the established certificate (SHA-256 certificate digest
`727d4850779bed1e51018108e13bc399d4da38cfc68f4f7504120ad5e2dad6fc`).
The exact signed candidate APK SHA-256 is
`257910077F61831F2550A986C5BC580224106B29FA26CA5B4D230D4503E13084`.

Physical migration, lock contention, BOTH/TOP transitions, lid guard, and one
observed cold boot were completed below. The CPU Fix property is still
unknown; no compositor transition is justified while it stays unknown. No
physical dock testing is part of a future improvement by user decision, so
external-display behavior remains host-tested only. This is a draft PR
candidate, not a public release.

### Supervised Thor run — 2026-10-01

The user was beside the Thor and visually observed every display transition.
Before installation, BOTH was awake with CRTC 181=1 and 243=1. The signed
v1.5.11 APK was installed once in place; no app data was cleared. The old
v1.5.10 root daemon PID 16446 was replaced by one v1.5.11 daemon PID 21330.
The kernel boot ID remained `6518dfe2-cf86-413a-9da7-0a91f6472c03`,
both CRTCs remained active, and the user observed no blink or anomaly. The
daemon reached `BOOT READY`. The APK pulled from the installed package matched
the signed host candidate byte-for-byte by SHA-256. The temporary same-UID
lock probe found an app-owned regular file with mode 0600 and was denied a
second-process lock (`second_process_lock=DENIED`); it was then uninstalled.

The physical AYN control changed BOTH to TOP. CRTC 181 remained active and
243 became inactive. The user saw the lower panel switch off without green
flash. The app was opened on the upper display and showed `TOP ONLY · TRUE
OFF` with readable controls and metrics. A short physical sleep/wake in TOP
restored the same effective state without an observed flash or artifact. The
physical control then returned to BOTH, where both CRTCs and images were
active. Reviewed real-device screenshots are
[`dashboard-top-v1.5.11-review.png`](images/dashboard-top-v1.5.11-review.png)
(SHA-256 `B057A75B284560F8184F098A63186059E68691A875530A77C41A122C2275480B`)
and [`dashboard-both-v1.5.11-review.png`](images/dashboard-both-v1.5.11-review.png)
(SHA-256 `890B7DFFAC03FF68C32500B4B9F6ACE713DF6921C5676A5E88C7AD40C183065C`).

The Thor exposed `/dev/input/event1` as `hall_switch` with `SW_LID`, and
`getevent -S` read `0000` while open and `0001` while closed. The guard was
temporarily enabled after `BOOT READY`; the UI reported `LID OPEN · WAKE
GUARD ON · 0 BLOCKED`. The user made two close/open cycles and saw normal
sleep and wake. With the lid held closed and the Thor asleep, one controlled
`KEYCODE_WAKEUP` produced `Awake`, then `Dozing`, then `Asleep` within about
two seconds, with no loop. After opening, the UI reported `1 BLOCKED` and
normal BOTH operation. The guard's sleep path invokes `input keyevent 223`;
the observed return to `Asleep` confirms that path worked on this Thor. The
guard was switched OFF and the UI confirmed it before the cold boot.

One genuine power-off/power-on cold boot was performed with the user watching.
The new kernel boot ID was `4824e62f-7523-4b9e-9a55-b24d26c73625`. The
sanitized local trace for this boot reads `WAIT_FOR_ANDROID` at elapsed 59.116
s with mode `?`, then `BOOT_CPU_FIX_NOT_APPLIED` at 71.574 s, followed by
`RECONCILE_DISPLAY`, `BOTTOM_ON_CONFIRMED`, and `BOOT_READY` at 72.183 s.
There was no speculative panel-ON action while mode was `?`. At completion,
one root daemon PID 7710 was present, SurfaceFlinger PID 1297 remained
`running` and stable across later checks, the CPU property was still empty,
and CRTCs 181/243 were both active. The user reported both screens displaying
normally, without green flash, artifacts, or a second boot. The post-boot UI
reported `BOTH SCREENS`, `CPU FIX STATE UNKNOWN · NO RESTART`, and `WAKE
GUARD OFF`. Device trace and filtered logs remain local under
`C:\Temp\jesty-thor-159-review`; they are not committed.

At this v1.5.11 checkpoint, no second cold boot was needed. BOTTOM ONLY remains outside this phase. The
CPU Fix compositor-restart path was **not physically exercised**: its vendor
property remained unknown throughout, so the staged boot correctly skipped
it. Thus this run confirms one kernel boot without a compositor restart, but
does not prove that a future known-state CPU Fix transition avoids the
previous double-transition appearance. External-display behavior was tested
by host models only; physical dock behavior and BOTTOM ONLY are separate
future improvements by user decision. The PR remained a draft for user review;
no release or stable promotion followed from this run.

### Read-only CPU property investigation — 2026-10-01

The signed v1.5.11 APK and daemon were left unchanged. The Thor reports
`ro.board.platform=kalama`, SoC ID `603`, and
`/sys/devices/soc0/platform_subtype_id=0`. The vendor boot script
`/vendor/bin/init.qti.display_boot.sh` enters the `kalama` branch for SoC
603 but sets `vendor.display.disable_system_load_check=1` there only when
`subtype_id=1`. Other properties from that branch are present on the device
(`vendor.display.target.version=4`, `vendor.display.timed_render_enable=1`),
while the CPU Fix property is empty. This explains why that script leaves it
unset on this Thor; it does **not** establish the compositor's effective
default behavior when the property is absent.

The app preference remains ON but `cpu_fix_phase=UNKNOWN` is the only honest
effective state. The current safety rule rejects a CPU Fix transition on an
unknown property. No `setprop`, compositor restart, extra installation, or
reboot was performed for this investigation. A future CPU Fix design decision
must address this firmware default explicitly before claiming automatic
restore or resolving the apparent double-transition behavior.

### Supervised anti-loop check — 2026-10-01

The user returned to the Thor for a no-reboot closed-lid loop check. After the
cold boot, Linux had renumbered `hall_switch` from `/dev/input/event1` to
`/dev/input/event0`. An initial read of the old node misleadingly returned
`SW_LID=0`; no wake command was sent on that basis. `dumpsys input` and the
`/sys/class/input/event*/device/name` entries identified the new Hall node.
The daemon's Hall watcher discovers the node by name, rather than retaining
the old event number. The correct node read `SW_LID=1` with the lid closed.

The guard was temporarily enabled again. With the lid closed and the Thor
asleep, three controlled `KEYCODE_WAKEUP` attempts returned to `Asleep`.
The fourth left the Thor `Awake` with the lid still closed, as expected after
the three-attempt limit within ten seconds. The filtered local log records
`sleep_after_closed_wake` at 13:52:59.736, 13:53:01.673, and 13:53:03.609,
with no fourth sleep action. On opening the lid, the UI showed
`LID OPEN · WAKE GUARD ON · 3 BLOCKED`; normal BOTH operation resumed. The
guard was switched OFF and the UI confirmed `LID OPEN · WAKE GUARD OFF · 3
BLOCKED`. Both CRTCs were active, one root daemon remained, and the kernel
boot ID was unchanged. The reviewed screenshot is
[`wake-guard-loop-v1.5.11-review.png`](images/wake-guard-loop-v1.5.11-review.png),
SHA-256 `5ABADFFF604D9BE3CAAB165EADC6C11E2E258FC21421EE8EC8683F464EEA52F9`.
The source screenshot and filtered log remain local; no APK, log or key was
added to Git.

## v1.5.12 compositor-transition safety candidate — 2026-10-01

Static review found three defects in the still-unexercised CPU Fix compositor
path. The transition helper released its wake lock after a fixed 18 seconds,
even if the post-restart daemon had not reached `READY`. It reused the initial
boot's 60-second deadline for the post-restart phase. During `BOOT HOLD`, it
also sampled the inactive Lid Guard watcher rather than carrying the saved
guard preference to the relaunched daemon.

VersionCode 61 / versionName 1.5.12 corrects these points. The post-restart
daemon releases the transition wake lock only when its reconciliation ends;
the timed kernel lock has a 150-second fallback if relaunch fails. The new
phase gets a fresh 60-second deadline, and the saved Lid Guard intent is
passed through the relaunch. The migration allowlist adds only the installed
v1.5.11 identity. Both host suites passed, the APK is aligned and signed with
the established certificate (SHA-256 certificate digest
`727d4850779bed1e51018108e13bc399d4da38cfc68f4f7504120ad5e2dad6fc`).
The exact signed v1.5.12 APK SHA-256 is
`B4AD98728032F3A987B2F82DF62DE0E5102F13657E7239397BCCACD6F8EB4411`.

The v1.5.12 APK was installed once in place under observation, without a
kernel reboot. Before installation, BOTH had both CRTCs active, one v1.5.11
daemon, and boot ID `4824e62f-7523-4b9e-9a55-b24d26c73625`. The user saw
no blink or visual anomaly. The old daemon was replaced by one v1.5.12 daemon;
the boot ID stayed the same. The installed APK bytes matched the signed host
APK SHA-256 above. The UI reported `BOTH SCREENS`, `CPU FIX STATE UNKNOWN · NO
RESTART`, and Wake Guard OFF. A same-UID second-process lock probe returned
`DENIED`; its temporary helper was removed.

The second and final permitted cold boot was then performed as a genuine
power-off followed by the user's physical power-on. The user observed one
visual boot with no green flash or artifact. Both CRTCs and the stable app
view subsequently showed BOTH active.
The new kernel boot ID was `f7062f15-b3fb-44ac-87a6-6dfad529035d`. The
sanitized local trace records `WAIT_FOR_ANDROID` at elapsed 60.359 s with mode
`?`, `BOOT_CPU_FIX_NOT_APPLIED` at 72.700 s, `RECONCILE_DISPLAY` at 72.802 s,
`BOTTOM_ON_CONFIRMED` at 73.208 s, and `BOOT_READY` at 73.310 s. There was no
speculative panel-ON action while mode was unknown. At READY both CRTCs were
active, the CPU property remained empty, and exactly one v1.5.12 root daemon
was present (PID 7802; SurfaceFlinger PID 2524). After a normal idle sleep,
one wake restored BOTH with both CRTCs active. The reviewed screenshot is
[`dashboard-both-v1.5.12-review.png`](images/dashboard-both-v1.5.12-review.png),
SHA-256 `323FC05B6AAE173719C6AF057818E8D37DD80B1759A867D4C2FC13A580FE00B9`.
It shows stable `BOTH SCREENS`, Wake Guard OFF, and the CPU state unknown.
Trace, logcat, and source screenshot remain local under
`C:\Temp\jesty-thor-159-review`, not in Git.

Both allowed cold boots have now been used; no further reboot is part of this
phase. The known-state CPU Fix transition and compositor relaunch remain
physically untested because the vendor property is absent. No `setprop` was
used to manufacture a known state. Physical dock and BOTTOM ONLY work remain
future improvements outside this phase by user decision. The PR stays in
draft; this is not a release or stable promotion.

### CPU Fix absence investigation — 2026-10-01

Read-only checks after the second boot reconfirmed `kalama`, SoC ID `603`,
platform subtype `0`, a running vendor display composer, and an empty
`vendor.display.disable_system_load_check`. The boot ID stayed
`f7062f15-b3fb-44ac-87a6-6dfad529035d`; no property write, compositor
restart, package install, or further cold boot was performed. The actual
Thor vendor boot script sets the property only for subtype `1`. Android's
property service persists `persist.*` properties, not ordinary `vendor.*`
properties, so an earlier runtime `setprop` does not survive a cold boot.

The on-device `/vendor/lib64/libsdmextension.so` contains the exact property
name and the diagnostic string `System load check disabled`. A publicly
available Qualcomm-derived `ResourceImpl` reference initializes
`disable_system_load_check` to false and changes it only after a successful
property read of value `1`; its `CheckSystemLoad` path skips the check when
that flag is true. This supports, but does not prove for the Thor binary,
that an absent property leaves the check enabled. The local binary was not
patched or redistributed. Its local SHA-256 is
`B23AC200FAA14F81DAC69AC3C83CF922A02C237E3FC7A3202B00137622862370`.
Reference code:
[`resource_impl.h`](https://github.com/Ambition66/interview_knowledge_base/blob/2d0bb68ef4a2ac12a051094ffa36cdea3234f9d4/clstc_and_stc/sdm/resource_impl.h)
and [`resource_impl.cpp`](https://github.com/Ambition66/interview_knowledge_base/blob/2d0bb68ef4a2ac12a051094ffa36cdea3234f9d4/clstc_and_stc/sdm/resource_impl.cpp).
Those files are not a verified source match for this proprietary build.

The code history exposes the behavioral regression directly. Older releases
called `setprop vendor.display.disable_system_load_check 1` whenever the
desired value differed from the observed value, including when the property
was absent, then restarted the vendor composer. v1.5.12 rejects an absent
property before any write. This prevents the risky speculative transition
but also means a saved CPU Fix ON preference is **not restored** on this
firmware after a cold boot. The UI reports `CPU FIX STATE UNKNOWN · NO
RESTART`; there is no evidence that the fix was applied in this run. The
earlier focused 0/25 high-clock result belongs to an older build after a
known property write/restart and must not be carried over as v1.5.12 proof.

To resolve the block, a future change must make an explicit decision about
the absent-property baseline, apply only when that decision is justified,
observe one controlled compositor transition with the user present, and
verify the property, composer PID, daemon recovery, BOTH/TOP CRTCs, and CPU
clock result. It must not silently relabel empty as `0` or claim success from
the saved preference. Until then, the draft PR must not be recommended as a
working CPU Fix or published as a stable release. The user-approved two
cold-boot limit for this review has already been reached.

## v1.5.13 CPU Fix restore candidate — 2026-10-01

The user authorized a supervised property `1` / compositor transition to
resolve the CPU Fix regression. This extends the CPU Fix test scope; it does
not change the earlier BOTH/TOP, BOTTOM ONLY, or dock decisions. The code now
uses `getprop NAME DEFAULT` to distinguish a successfully read unconfigured
property from a failed read or an invalid value. The device command was
checked read-only: absent returns the default marker; a known property
returns its value. An unconfigured property with saved CPU Fix ON may schedule
one composer restart. A read failure, invalid value, or unconfigured state
after that restart still fails safe. CPU Fix OFF with an unconfigured property
is never claimed confirmed. The daemon acquires its timed transition wake
lock before writing and verifies that the property became `1` before
requesting the composer restart. A failed pre-restart write releases the lock
and attempts to restore the prior unconfigured state. The post-restart daemon
must observe `1` before reporting `CONFIRMED`.

The candidate is versionCode 62 / versionName 1.5.13. The migration allowlist
adds only the installed v1.5.12 identity. Both host suites passed. The signed
and aligned APK verifies under the established certificate; SHA-256 is
`B03732EA16E11DDB80FCA6DA47C8C833205C643B13C5F65CC1405AF1A56141B9`.
The signed APK remains outside Git. The Thor preflight showed v1.5.12, kernel boot ID
`f7062f15-b3fb-44ac-87a6-6dfad529035d`, composer PID 1337,
SurfaceFlinger PID 2524, one root daemon PID 7802, property absent, and
`Awake`; the user confirmed image on both panels. One supervised in-place
install succeeded. Before app launch, boot ID, composer PID, daemon PID, and
absent property were unchanged. Launching the new app replaced the daemon,
then the trace recorded `APPLY_CPU_FIX` at elapsed 1792.310 s with mode 0
and both CRTCs active. ADB disconnected during the one compositor transition
and reconnected. The kernel boot ID stayed unchanged; the composer changed
from PID 1337 to 29410, SurfaceFlinger from 2524 to 29407, and the root
daemon from 7802 to one v1.5.13 PID 30391. The property read back `1`.
Trace continued through `WAIT_AFTER_COMPOSER` at 1802.625 s,
`RECONCILE_DISPLAY` at 1810.131 s, and `BOOT_READY` at 1810.650 s with mode
0 and both CRTCs active. The installed APK hash matched the host SHA-256
above byte for byte. The user observed a normal visual return with no green
flash, artifacts, or unexpected repeated boot; Android showed its ordinary
USB-mode chooser after reconnection, which was dismissed. The v1.5.13 app
then showed `BOTH SCREENS` and `CPU FIX ACTIVE · CLOCKS NORMAL`, with Wake
Guard OFF. The local screenshot SHA-256 is
`3081875BDB6EDF8F1979EC193AFD58BF2061FBC13966B0DB7FDA3F08DEE08602`.

This verifies the in-place transition, not yet the original AYN Dashboard
high-clock reproduction or automatic restore after a genuine cold boot. Those
are the remaining gates before calling the CPU Fix regression fully resolved.
Stop at the first flash, unexpected panel state, failed daemon recovery, or
restart loop. No additional kernel reboot has occurred in this extension.

## v1.5.15 CPU Fix restoration and final observed state — 2026-10-01

The preceding v1.5.13 paragraph records the state at that point in the
chronology. Its two remaining gates were then exercised. With AYN Dashboard
open on the lower display and Jesty on the upper display, the app showed
`CPU FIX ACTIVE · CLOCKS NORMAL` and LITTLE/BIG current clocks below their
reported maxima. This confirms effective property `1` and observed clock
movement in that session; it is not a broad performance benchmark.

The user requested a shorter, single-line CPU Fix hint at the existing font
size. The final v1.5.15 hint is `Restarts Android UI once per boot (may look
like a second boot)` when enabled. The v1.5.14 and v1.5.15 APKs were each
installed in place under observation. The property stayed `1`, the kernel
boot ID and composer PID stayed unchanged, and only the daemon was replaced;
neither text-only install triggered another compositor restart. The final
candidate is versionCode 64 / versionName 1.5.15. Both host suites passed.
The exact signed APK and the APK pulled from the Thor have matching SHA-256
`093B6AF26E86E072343988C04CBF03256005703D567177E99D308B9BADC71F2B`.
The APK verifies with the established signing certificate, SHA-256
`727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`.
APK, trace, logs, and signing material remain outside Git.

One additional supervised power-off/power-on cold boot exercised automatic
CPU Fix restoration on this final APK. The prior kernel boot ID was
`f7062f15-b3fb-44ac-87a6-6dfad529035d`; the new ID was
`85266414-667f-4691-bce0-e3ecaca53068` and did not change during the
compositor/UI restart. The trace recorded `WAIT_FOR_ANDROID` at elapsed
64.528 s with mode `?`, both CRTCs active, and CPU property absent;
`APPLY_CPU_FIX` at 77.016 s in mode 0; `WAIT_AFTER_COMPOSER` at 87.391 s
with property `1`; `RECONCILE_DISPLAY` at 94.887 s; and `BOOT_READY` at
95.417 s with mode 0, both CRTCs active, property `1`, and one root daemon.
The composer changed once from PID 1203 to 8254 and SurfaceFlinger changed
once to PID 8251. The user observed the expected second visual Android UI
phase, with no green flash, artifact, restart loop, or second kernel boot.
Android's USB chooser appeared and was dismissed with CANCEL. The user
confirmed normal image on both panels, `CPU FIX ACTIVE · CLOCKS NORMAL`,
and Wake Guard OFF.

AYN Dashboard was reopened on the lower screen after this boot while Jesty
remained visible on the upper screen. After approximately 30 seconds, Jesty
still showed `CPU FIX ACTIVE · CLOCKS NORMAL`, with LITTLE 0.90/2.02 GHz
and BIG 1.65/2.80 GHz in the captured sample. The property, composer PID,
daemon PID, boot ID, and both CRTCs remained stable. The opportunistic real
screenshots are [`dashboard-both-v1.5.15-review.png`](images/dashboard-both-v1.5.15-review.png)
(SHA-256 `615D8977AC057CB7E2276BEF8FA316F961BF5D036B453C2F3A118A03C5A30587`)
and [`ayn-dashboard-both-v1.5.15-review.png`](images/ayn-dashboard-both-v1.5.15-review.png)
(SHA-256 `851E162C2C0C094E18AF6B8A1E72E7CF609DC5D8E03F13A884A0277BA1B205C8`).
No old TOP or Wake Guard scenario was restaged solely to make new images.

The dashboard's `LID UNKNOWN` with Wake Guard OFF reflects that its Hall
watcher is inactive and has not populated the in-memory lid model. It is
independent of the CPU Fix. The guard was left OFF and the Thor in BOTH.

The CPU Fix regression is resolved for the observed in-place and cold-boot
paths. The approximately 95-second time to `BOOT_READY` needs separate
performance investigation; it is not a correctness failure in these traces.
This extension had one genuine cold boot, without a repeat. The older
BOTH/TOP, IPC, and Wake Guard evidence remains in the chronology above.
Physical dock testing and BOTTOM ONLY are deferred by user decision. The
maintainer subsequently approved merging this review and publishing the
exact tested v1.5.15 APK as a testing pre-release, without stable promotion.

### Boot-duration follow-up for independent analysis

The local sanitized trace uses kernel elapsed milliseconds, so the numbers
below measure time from kernel start, not wall time after the power button.
The first recorded daemon milestone is `WAIT_FOR_ANDROID` at 64.528 s; the
trace does not decompose the preceding 64.528 s. From that milestone to
`APPLY_CPU_FIX` at 77.016 s is 12.488 s. From apply to
`WAIT_AFTER_COMPOSER` at 87.391 s is 10.375 s. From there to
`RECONCILE_DISPLAY` at 94.887 s is 7.496 s, and from reconciliation to
`BOOT_READY` at 95.417 s is 0.530 s. The observed total is 95.417 s.

The vendor composer service declares an `onrestart` of SurfaceFlinger, and
SurfaceFlinger declares an `onrestart` of zygote. The second visual boot is
therefore consistent with this one compositor restart cascading through
Android UI. The single kernel boot ID and one composer PID change support
that interpretation. These data do **not** establish which part of the
95 seconds dominates, whether it varies on later boots, or whether the
device became usable before `BOOT_READY`.

For a later performance investigation, capture monotonic timestamps for
kernel/Android startup, `sys.boot_completed`, first composer `running`,
first known AYN mode and three stable CRTC samples, the configured 10-second
boot grace, property write, composer restart request and new PID, the
post-composer five-second grace, and final reconciliation. Compare a
CPU Fix ON boot with a comparable OFF boot only if that extra physical boot
is justified and supervised. Keep the current safety delays until a measured
segment shows a safe reduction. No additional boot is required for this
testing pre-release.
