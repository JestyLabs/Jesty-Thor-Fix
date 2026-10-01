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
