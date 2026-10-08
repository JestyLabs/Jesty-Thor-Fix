# v1.7.0 production candidate validation

Status: **GO to prepare and test; NOT approved for publication.**

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

## Supervised device checklist

Prepared local signed candidate (not installed):

- Source commit: `c8838d179816e13afff3320ce5dd38e1bd44354d`.
- APK: `Jesty-Thor-Fix-1.7.0.apk`, package 1.7.0 / code 71.
- SHA-256: `0c237de7bb730e90cf1beb76118d21bd53a090de3dcf4ed605a7006f7c4f31c8`.
- Certificate SHA-256: `727d4850779bed1e51018108e13bc399d4da38cfc68f4f7504120ad5e2dad6fc`.
- Build, DEX merge, alignment and v1/v2/v3 signature verification: PASS.
- Decoded signed APK confirms both VERSION and RUNTIME_ID are 1.7.0 and the
  successful-repair result/health log is packaged. Host suites passed.
- Raw build/test/decode evidence stays locally outside Git under
  `C:\Temp\jesty-thor-v1.7.0-candidate-evidence`.

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

## Publication boundary

Work remains isolated to PR #41. No merge, tag or release is authorized by
candidate preparation. The automated publication route requires a signed
candidate from a successful main push. A PR/local signed build is for supervised
testing; it is not eligible for that workflow. When a main candidate eventually
exists, verify and test its exact hash before publication. Never assume a rebuilt
APK is the same file as a physically tested candidate.

Rollback to published v1.6.0 requires owner approval and may require a supervised
normal reboot because the published daemon does not recognize newer identities.
Preserve app data and durable CPU proof; do not edit markers or force-kill services.
