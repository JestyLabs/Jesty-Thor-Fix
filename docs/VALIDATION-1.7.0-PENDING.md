# v1.7.0 production candidate validation

Status: **GO: scoped exact-APK validation passed. Owner authorized PR #41 merge and stable/Latest publication of this unchanged APK.**

The owner accepted preparing the production candidate after measured exp2 CPU
benefit and scoped display/wake checks. Gameplay and battery studies are deferred
and are not release gates for this change. Exact stable Settings/DRM call-count
percentages remain unavailable; measured daemon CPU/context-switch benefit
supports this decision. No claim of meeting measured 90%/70% call-count targets
is substituted for those missing counts.

## Candidate contract

- Version 1.7.0 / code 71; production runtime identity follows package version.
- Guarded prior-daemon handover retains stable 1.6.0 and adds only the exact
  tested exp2 identity. Existing authenticated RUNNING/BOTH/CRTC 1/1 checks stay.
- Polling intervals, hardware write authority, boot gates and CPU Fix live
  verification stay unchanged from the tested exp2 code.
- Minute watcher metrics remain for bounded diagnostics. Successful repair logs
  `WAKE_REPAIR_RESULT result=OFF_OK;watcher_health=...` only when repair completes,
  allowing direct observation without exposing a new IPC endpoint or accepting
  shell peers. Confirm physical CRTC state separately.
- Host boot/lid/IPC/CPU/cadence/migration and dashboard/updater suites passed
  after the production identity and migration change. Signed artifact identity
  and verification results are recorded below after build.

## Candidate identity and supervised results

Prepared local signed candidate:

- Source commit: `c8838d179816e13afff3320ce5dd38e1bd44354d`.
- APK: `Jesty-Thor-Fix-1.7.0.apk`, package 1.7.0 / code 71.
- SHA-256: `0c237de7bb730e90cf1beb76118d21bd53a090de3dcf4ed605a7006f7c4f31c8`.
- Certificate SHA-256: `727d4850779bed1e51018108e13bc399d4da38cfc68f4f7504120ad5e2dad6fc`.
- Build, DEX merge, alignment and v1/v2/v3 signature verification: PASS.
- Decoded signed APK confirms both VERSION and RUNTIME_ID are 1.7.0 and the
  successful-repair result/health log is packaged. Host suites passed.
- Raw build/test/decode evidence stays locally outside Git under
  `C:\Temp\jesty-thor-v1.7.0-candidate-evidence`.

Authorized install/handover: **PASS** on Thor firmware .377 / Android 13, awake
BOTH with CRTC 1/1. The installed base.apk SHA-256 exactly matches the candidate.
The existing roughly 30 s prior-daemon grace identified and replaced exp2;
one daemon reported READY 1.7.0, EARLY_CPU_ATTEMPT_PROGRESS_KEPT (APPLIED),
CPU_GATE_COMPLETE with composer_restart=not_needed, then BOOT_READY.
Kernel boot ID, composer/SF identities and CPU property 1 stayed unchanged.
The legitimate dashboard displayed v1.7.0, BOTH SCREENS and CPU FIX ACTIVE /
CLOCKS NORMAL. No marker edit, CPU toggle, app data clear or reboot occurred.
Dashboard was then closed for the physical tests.

Exact-candidate physical regression: **PASS for the scoped checks**. The owner
performed BOTH -> TOP -> BOTH -> TOP and two power-button sleep/wake cycles,
reporting normal images without flash, failure or abnormal delay. The continuous
timeline captured CRTC 1/1 -> 1/0 -> 1/1 -> 1/0 and the first wake returning to
1/0. That sampler ended during the second sleep; the separate final snapshot
after the second wake confirmed Awake TOP / CRTC 1/0. Do not claim continuous
CRTC coverage of that second wake.

Both repairs logged directly `WAKE_REPAIR_RESULT result=OFF_OK;watcher_health=RUNNING`.
Framework wake -> repair OFF action was 1.243 / 1.251 s; wake -> explicit result
log was 1.272 / 1.301 s. Stable's single 1.240 s comparison used the OFF-action
definition. These few observations do not establish latency distributions.
Kernel boot ID, composer/SF identities and CPU property remained unchanged.
Host suites, signed packaging checks, CI build and all CodeQL checks passed on
the prepared source/documentation head `1837f3d`.

1. Owner approval for the exact signed APK installation/open, with the Thor
   awake in BOTH and both CRTCs active. Preserve all app data and proof records.
2. Confirm installed APK SHA-256, runtime 1.7.0, one healthy daemon, BOOT_READY,
   completed CPU proof/CONFIRMED state and no unexpected composer/SF replacement.
3. Owner performs BOTH -> TOP -> BOTH; verify CRTC 1/0 and 1/1 and normal images.
4. Dashboard closed: TOP sleep/wake twice. Capture direct OFF_OK plus RUNNING
   repair log, final CRTC 1/0, and timing. Reopen the legitimate dashboard for
   authenticated telemetry/visible confirmation when needed; do not bypass UID
   checks to query the socket from adb shell.
5. Obtain separate authorization for one normal supervised startup in TOP,
   allowing the existing expected early CPU Fix recovery. Capture boot trace,
   restart provenance, completion and final display state. Do not toggle CPU Fix
   or manufacture another restart. Stop on unexpected repeated recovery, wrong
   CRTC, failed proof or daemon failure.

No installation, physical result or reboot is implied by host/build success.
The existing 11-minute timeout remains the owner's requested setting.

## Supervised reboot

Supervised normal reboot in TOP: **PASS**. The owner rebooted through the normal
power menu, keeping TOP and the lid open, and reported a normal boot at the USB
menu. A changed kernel boot ID and reset uptime confirm a new boot. The earlier
unchanged-boot interaction was not counted as startup validation.

The daemon imported the current-boot RESTART_REQUESTED early record, verified a
successor composer against its recorded baseline, and durably marked APPLIED.
CPU_GATE_COMPLETE at 34.789 s reported composer_restart=applied. Display boot
hold/grace completed, BOTTOM_OFF_CONFIRMED followed at 41.177 s, and BOOT_READY
at 41.196 s with mode TOP, CRTC 1/0 and CPU property 1. No CPU Fix setting was
toggled and no manual service restart was sent.

The daemon/composer/SF identities were unchanged from the first post-boot
observation through the 138.15 s final snapshot; later daemon metrics still
advanced at 155.174 s. Reopening the legitimate app returned trusted-daemon
healthy and displayed TOP ONLY / TRUE OFF and CPU FIX ACTIVE / CLOCKS NORMAL.
The dashboard was then closed. Installed APK hash still matches the pinned
candidate; the requested 11-minute timeout remains in place. No second late
recovery was observed in this bounded observation, not an indefinite guarantee.

The known pre-daemon EGL/SurfaceFlinger signature also occurred; retain the
existing [documented limitation](VALIDATION-1.5.20-PENDING.md) rather than reopening
that investigation or claiming a crash-free boot. The private early-hook trace
and daemon log are not shell-readable; imported durable proof, boot trace,
logcat, process continuity and physical/UI observations provide the evidence.

Raw file references and hashes: [candidate evidence index](VALIDATION-1.7.0-SHA256.txt).
This is one normal supervised reboot, not a cold-power-cycle quota or proof for
BOTTOM-only/dock/closed-lid cases. Gameplay and battery work remain deferred.

## Publication boundary

The owner explicitly authorized PR #41 merge and stable/Latest publication using
the exact signed APK tested here. Use the documented local/manual release route
to preserve that file, without rebuilding it. The runtime source/resources/build
script must still match candidate source `c8838d1` after merging current main;
only documentation/integration metadata may differ. Record the final main/tag
identity and verify both GitHub asset digest and a downloaded copy against the
pinned APK hash. The main CI signed artifact is a separate build; this local APK
is not eligible for the automated `Publish tested candidate` workflow. Do not
silently substitute that rebuilt artifact for the physically tested file.

Rollback to published v1.6.0 requires owner approval and may require a supervised
normal reboot because the published daemon does not recognize newer identities.
Preserve app data and durable CPU proof; do not edit markers or force-kill services.
