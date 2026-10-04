# Changelog

## 1.5.20 (signed testing pre-release candidate)

- Shorten the root bridge daemon launch command and bound it to 255 characters.
  Four timing environment variables become one compact `JT` value; the daemon
  expands it back into the existing trace fields. The root log pathname still
  rejects links and files not owned by the launcher before redirection.
- Add a pure command/metadata test including the longest possible timing values.
  The 1.5.19 candidate's command was substantially longer and the root bridge
  reported submission but no successor daemon started. A bridge command-size
  limit is a hypothesis, not a confirmed root cause. The signed v1.5.20 APK
  passed the supervised in-place handover that failed on v1.5.19.
- Include 1.5.19 in the narrow older-daemon migration allowlist. The installed
  Thor was safely returned to 1.5.17 after the failed 1.5.19 migration.
- On the Thor, BOTH/TOP switching, TOP wake, one TOP cold boot with one expected
  compositor restart, and the available closed-lid Wake Guard checks passed.
  No green flash or wrong final panel was seen. The known early firmware
  SurfaceFlinger abort still occurred before the app started. Updater install
  and adversarial helper recovery remain untested on hardware; see the
  [validation diary](docs/VALIDATION-1.5.20-PENDING.md).

## 1.5.19 (failed supervised in-place migration; not suitable for release)

The 1.5.18 safety changes remain a separate local commit for review. A
supervised 1.5.19 install in BOTH on 2026-10-04 did not launch the new daemon
after replacing the 1.5.17 daemon. The display and compositor stayed normal,
but the app had no active root daemon. The 1.5.17 APK was reinstalled without
clearing data; its daemon became healthy again without a reboot. Do not promote
or install 1.5.19 again. See the 1.5.19 validation diary for evidence.

- The root daemon's former static `D` code is split into objects with explicit
  constructor dependencies, built by `DaemonRuntime`: `BootSession`,
  `BootTrace`, `SystemProbe`, `BootCoordinator`, `CpuFixController`,
  `TransitionWakeLock`, `DaemonCommandHandler`, `DaemonIpcServer` and
  `AndroidRecoveryTrace`. `D` is a thin entry point. Commands, replies, trace
  lines, gate waits and the helper script are unchanged. The static classes
  called from smali stay static.
- `MainActivity` keeps lifecycle and wiring only. New `DashboardLayout`,
  `DashboardViews`, `DashboardStyle`, `DashboardRenderer`,
  `DashboardSettingsController`, `TelemetryPoller`, `TelemetryValues`,
  `BackgroundMediaController` and `UpdateReadiness` hold the former code.
- `DaemonArgs` parses the daemon command line, with a host test. Host checks
  keep `D` thin and keep daemon requests and view building out of
  `MainActivity`; the IPC checks now read `DaemonIpcServer`.
- v1.5.18 joins the replaceable-version allowlist.
- Physical check before release: BOTH/TOP switches, CPU Fix on/off, Wake Guard,
  cold boot in BOTH and TOP, app update from 1.5.18, and the dashboard texts
  during boot hold.

## 1.5.18 (host-built safety candidate; not validated on the Thor)

Safety:

- A boot daemon whose compositor-restart helper aborts no longer stays in
  `APPLYING CPU FIX` with display actions held until reboot. The daemon now
  observes the helper. When the compositor provably never restarted (exit 10,
  watcher still running), a held boot daemon runs the post-restart gate
  itself, with the unchanged five-second grace and 60-second timeout; a
  runtime toggle only releases the transition wake lock. The CPU phase reports
  `ERROR` and the previous property is restored while the old compositor still
  runs.
- Every other helper abort can follow a framework restart, after which the old
  daemon's watcher and display callback are stale. That daemon now holds as
  `HANDOFF FAILED`, releases the wake lock, takes no display or Wake Guard
  action and is replaced by `AutoService` on the next boot broadcast or
  dashboard open. Helper aborts use distinct exit codes (10-14). Only one
  helper can run at a time.
- `I` reports `phase_ms`; `Q` reports `boot_phase_ms` and `handoff`.
  `AutoService` also replaces a same-version daemon whose starting phase has
  not changed for 90 seconds, after the usual root-UID and command-line
  checks, with a successor launched in hold. v1.5.17 joins the
  replaceable-version allowlist.
- No gate wait, sample rule or timeout changed. These paths have host-model
  tests only; do not provoke a helper abort on a physical Thor. If the CPU
  property cannot be applied, each replacement may restart the interface once.

Hardening:

- Root-written boot trace and launch log in `/data/local/tmp` no longer follow
  links: `O_NOFOLLOW` plus regular-file, owner and link-count checks in Java,
  and a builtin-only `safe_log` check in the shell. The world-readable trace
  for the ADB collector is kept; `setReadable(true, false)` is gone.
- `ThorHardwareProfile` centralizes the Thor display, CRTC, DRM and Hall
  identifiers; the boot/lid test script rejects copies elsewhere and checks
  the smali logical display literal.
- Wake Guard keeps `hall_switch` first and falls back only to a single
  `SW_LID` input device. Not reachable on the tested Thor.
- SECURITY.md describes the current Unix-socket channel instead of the old
  loopback listener; ARCHITECTURE.md documents why the target SDK stays at 25.

In-app updates:

- While the dashboard is open, check this repository's latest stable GitHub
  release at most once an hour. A yellow UPDATE button appears beside SUPPORT
  and GITHUB only when a newer verifiable release exists; the first detection
  shows a short release-notes prompt.
- Download only `Jesty-Thor-Fix-<version>.apk` from that tag, verify GitHub's
  size and SHA-256 digest, package name, version name, higher version code and
  signing certificate, then use Android's `PackageInstaller` confirmation.
- Do not commit an install during the boot transition, while a switch command
  is in flight, or without a fresh daemon sample; switches are locked from the
  Download tap until commit, and Cancel abandons the session. Explain the
  BOTH-only daemon handover.
- Long-press GITHUB to check now, disable automatic checks, or opt into test
  pre-releases. Adds `REQUEST_INSTALL_PACKAGES` and a non-exported install
  status receiver. The updater never uses the root daemon or a shell.

## 1.5.17 (installed pre-release; supervised BOTH and TOP boots recorded)

- Replace every timed `Process.waitFor` with a 5 ms bounded poll. The v1.5.16
  trace showed 102-103 ms gaps around short commands and 702-713 ms sample
  spacing; the causal timing benefit still needs measurement on the Thor.
  Timeouts and failure handling are unchanged.
- Gate samples run at a fixed 500 ms cadence from each sample's start and the
  sample after the grace is taken when the grace completes. Three matching
  samples, the 10-second and 5-second graces, the fresh-sample READY rule and
  the 60-second timeout are unchanged. Removing overhead moves the expected
  compositor restart about 2 seconds earlier in absolute time; this is a
  measurement-based estimate until a supervised cold boot confirms it.
- Read both CRTCs from one DRM state read in the gate and in trace lines,
  giving one consistent snapshot and half the debugfs reads per sample.
  Unknown or malformed neighboring CRTC blocks cannot be attributed to the
  top or bottom panel.
- Observability only: after a compositor restart, trace the new
  `system_server` PID, package/settings service registration (helper and
  successor), `service.bootanim.exit` edges and the first identity query,
  because `sys.boot_completed` keeps its first-boot value across that restart.
- Helper: a daemon that exits between the identity check and the signal is
  no longer reported as `KILL_FAILED`; a process that disappears while its
  status is read is classified as gone, not as an identity mismatch.
- `BootReceiver` ignores any action other than `BOOT_COMPLETED`.
- The local boot trace rotates at 256 KiB, keeping one previous file.
- The v1.5.16 daemon is added to the guarded replacement allowlist.
- Use the v1.5.17 name for the signed build artifact and check its agreement
  with the manifest in the host suite. The optional second-boot observer
  samples once per second to limit its diagnostic overhead.

## 1.5.16 (stable)

- Classify private socket state by kernel boot ID, avoiding the 30-second
  pathname-only wait after a cold boot when the stale inode is identifiable.
  An unreadable or unstamped ID retains a five-second safety grace.
- Treat an authenticated same-version boot phase as an in-progress daemon,
  and release the wake lock when a non-handoff boot coordinator ends.
- Verify the old daemon's root UID and command line before signalling it;
  wait for its exit and abort replacement on identity or exit failure.
- Add receiver, service, process, gate, compositor, helper, PID and boot ID
  timing marks to the local boot trace. Safety grace periods are unchanged.
- Host tests, signed build, in-place installation and one supervised Thor
  cold boot passed. That boot reached READY at 65.479 s with both CRTCs
  active and one Android UI restart; no green flash or artifact was reported.
  Repeatability across boot conditions is not yet established.

## 1.5.15 (stable)

- Restore a saved CPU Fix ON setting from an unset vendor property after a
  cold boot, with one compositor restart and verified active status.
- Preserve staged display boot, BOTH/TOP true-off repair, and optional
  Closed-Lid Wake Guard; physical review and a cold boot passed on the Thor.


## 1.4.2 (testing pre-release)

- Show the installed app version beside the dashboard subtitle, read directly
  from the package manifest so future releases do not require a manual label.
- No changes to display control, CPU fix, lid guard, daemon protocol, boot
  timing, permissions or backgrounds compared with v1.4.1.
- Update the README's recommended testing download and current dashboard image.

## 1.4.1 (testing pre-release)

- Fix an inverted boot-hold condition that prevented the daemon from following
  AYN's physical-button TOP/BOTH mode changes after boot was ready.
- In stable TOP mode, restore true lower-display OFF if AYN reactivates the
  lower physical display, without interfering with a pending wake repair.
- Refresh the running daemon during an in-place upgrade from the older
  protocol. No new UI, permissions, or automatic reboot behavior.
- Physical AYN-button TOP/BOTH cycling passed on the maintainer's Thor; the
  full cold-boot matrix, closed-lid guard and dock cases are still pending.
  This is a testing pre-release, not a stable promotion.

## 1.4.0 (testing pre-release)

- Hold all lower-display and sleep actions during boot until Android, the
  display compositor, AYN mode, and both physical CRTCs settle; add a 10-second
  grace period, or five seconds after the one required CPU-fix restart.
- Never turn the lower panel on speculatively while mode is unknown. After 60
  seconds without a stable state, leave native hardware untouched and report
  `BOOT SAFETY TIMEOUT`.
- Add an opt-in Closed-Lid Wake Guard using the Thor Hall switch, with a
  dock/unknown-state exclusion and a three-attempt wake-loop limit.
- Expose boot and lid state in additive `Q` fields and in the dashboard.
- The CPU Fix transition still restarts Android's UI/display stack once at
  boot when needed. An adaptive post-restart daemon relaunch shortened the
  observed READY time from about 72 to 62 seconds without reducing the
  pre-restart boot grace period.
- **Not stable-validated:** one user-observed clean cold boot of the exact
  signed APK; the 20-boot configuration matrix, closed-lid/false-wake/dock
  checks, and remaining screenshots are pending. The lid guard is OFF by
  default. See `docs/VALIDATION-1.4.0-PENDING.md`.

## 1.3.0

- Reworked the dashboard around one automatic display/CPU status panel and
  removed the redundant manual `Check now` action.
- Replaced instantaneous clock snapshots with a 12-second LITTLE/BIG
  `time_in_state` diagnosis so brief frequency spikes are not reported as
  pinning. The high-frequency band includes the Thor's reproduced 2.71 GHz BIG
  lock even though that policy exposes a separate 2.80 GHz ceiling.
- Added stable physical-display transitions and explicit BOTH, AYN black
  screen, true-off, BOTTOM, transition, and mismatch states.
- Replaced the charging-only system-power proxy with smoothed battery draw,
  shown only while the Thor is actually discharging.
- Simplified the two fix labels, centered the header over the dashboard column,
  and reduced the open-source badge to its essential message.
- Kept the Android UI/display restart and open-app warning visible directly
  under the Dashboard CPU Fix toggle in both OFF and ON states.
- Added an automatic daemon protocol migration for upgrades from 1.2.0.

## 1.2.0

- Added a three-state display model: BOTH, AYN fake-off, and Jesty true-off.
- Added separate background artwork for fake-off and true-off so the dashboard
  reflects the lower display's real hardware state.
- Added live USB input and estimated system-power telemetry to the dashboard.
- Hardened CPU pinning detection with settling time and sustained sampling.
- Prevented red stock-bug verdicts while the Dashboard CPU Fix is active.
- Shortened and clarified the Android restart confirmation shown by the CPU
  fix toggle.
- Expanded physical-device validation for OFF/ON framework restarts and a full
  reboot with both saved fixes restored.

## 1.1.1

- Prevented the Thor from suspending while the Dashboard CPU Fix restarts the
  Qualcomm display stack. A timed kernel wake-lock now covers only the
  transition and is explicitly released after Android returns.
- Corrected the confirmation and status copy: changing this setting restarts
  the Android framework and closes open apps; it is not merely a display/USB
  flicker.
- Kept the true bottom-display control and wake-repair timings unchanged.

## 1.1.0

- Added a separate, persistent **AYN Dashboard CPU Fix** for the dual-screen
  clock-pinning behavior reproduced with AYN Dashboard open.
- Added an explicit confirmation before the one-time display-compositor restart
  used to enable or disable that fix. The warning explains that both displays
  and USB can briefly reset and that the effect may look like a second reboot.
- Reapplies the saved Dashboard CPU Fix choice once during a normal boot; merely
  opening Jesty Thor Fix never restarts the display.
- Restarts the privileged daemon after the display reset so mode observation
  and true-off wake repair remain active when Android's display services return.
- Added DRM-backed lower-display telemetry so the displayed mode and background
  follow the physical CRTC state instead of relying only on AYN's logical mode.
- Redesigned the UI around two equal feature toggles and one consolidated live
  status/check panel with larger CPU readings.
- Expanded clock-pinning detection to BOTH/dual-screen mode while keeping all
  existing true-off wake repair and display-control timing unchanged.

## 1.0.4

- Moved the live fix/native status beside the header lockup and aligned it to
  the right edge of the control column.
- Replaced the dense Display & Service block with a clear display-mode label:
  TOP ONLY, BOTH SCREENS, or BOTTOM ONLY.
- Highlighted LITTLE and BIG values immediately in red when stock TOP-only has
  the bottom hardware active and a cluster is near maximum under low load.
- Kept the multi-sample confirmed bug warning and all control/wake behavior
  unchanged.

## 1.0.3

- Returned the Support and GitHub actions to the top-right corner.
- Moved the open-source badge to the bottom-right and reordered its copy so
  the support line appears above the amber Jesty message.
- Returned the header lockup to left alignment within the control panel.
- Kept all display-control, CPU/DRM check, wake-repair, and layout behavior
  unchanged from 1.0.2.

## 1.0.2

- Restored the full `JESTY APPS ARE FREE & OPEN SOURCE` message in amber at
  the top-right, with Support and GitHub actions grouped at the bottom-right.
- Centered the Jesty Thor Fix lockup within the left control panel, tightened
  the telemetry layout, and removed the duplicated top-screen status line.
- Kept the 1.0.1 CPU/DRM check and all display-control behavior unchanged.
- Bumped the visual-only patch to versionCode 39 / versionName 1.0.2.

## 1.0.1

- Made the stock TOP-only warning explicit: a black lower panel can still have
  active display hardware and keep LITTLE/BIG cores pinned.
- Extended **Bottom screen & CPU check** to verify both DRM state and current
  LITTLE/BIG frequencies in one tap.
- Preserved the continuous low-load clock-lock detector and made its confirmed
  warning visible as `STOCK BUG CONFIRMED`.
- Kept display control, wake repair, timing, and privileged bridge behavior
  unchanged from 1.0.0.
- Bumped the patch update to versionCode 38 / versionName 1.0.1.

## 1.0.0

- Promoted the tested Thor fix to the first stable public release.
- Added the final transparent Jesty Thor Fix lockup to the app and README.
- Replaced both dashboard states with the approved AYN Thor ON/OFF artwork.
- Regenerated the transition loop from those exact states.
- Kept the display-control, wake-repair, daemon, clock monitoring, and
  privileged bridge behavior unchanged from the validated 0.33 line.
- Bumped the public update to versionCode 37 / versionName 1.0.0.

## 0.33

- Replaced the separately assembled header with the approved transparent
  Jesty Thor Fix project lockup in both the app and repository landing page.
- Consolidated LITTLE, BIG, and PRIME readings into one CPU speeds panel.
- Aligned dashboard panels and active-state colors with the Retroid app.
- Moved the lower-screen hardware check beside the main fix control and
  replaced scanout/CRTC-first wording with a user-friendly result.
- Kept the underlying daemon protocol, wake-repair timing, and clock-lock
  detection unchanged.
- Added the compact Support Jesty / Star on GitHub footer.
- Updated the provisional ON/OFF dashboard artwork supplied by the maintainer.
- Bumped the public update to versionCode 36 / versionName 0.33.

## 0.32

- Integrated final maintainer-supplied ON/OFF backgrounds and the new Jesty
  header wordmark.
- The dashboard background follows confirmed lower-display power and the ON
  loop includes brief lower-screen flicker frames.
- Added the landscape dashboard and persistent true-off/native-mode control.
- Added LITTLE, BIG, PRIME, load, daemon, display, and wake-repair telemetry.
- Added the sustained low-load clock-lock warning.
- Added one-shot DRM scanout verification.
- Added lower-screen-aware dashboard artwork and animation behavior.
- Preserved the stable 700 ms wake repair with a minimum 400 ms delay after Display 4 ON.
- Preserved immediate manual TOP/BOTH transitions and final-state reconciliation.

## 0.31.1

- Invalidated stale repair deadlines across rapid wake/sleep/wake sequences.
- Validated twenty mixed wake cycles and a real screen-timeout wake.

## 0.31

- Moved wake repair out of the early Android wake pipeline.
- Retained the display callback as a late confirmation and reconciliation signal.
