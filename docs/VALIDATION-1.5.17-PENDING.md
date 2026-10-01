# v1.5.17 validation diary — Thor tests pending

## Completed on the host

- Reviewed the supplied patch against the v1.5.16 main tree; corrected the
  build artifact version, guarded the one-pass CRTC parser against a
  neighboring or malformed block, and reduced the optional Android recovery
  observer to one sample per second.
- `scripts/test-boot-lid.ps1` and `scripts/test-dashboard.ps1` passed after
  these changes. The full signed Android build passed alignment and v1/v2/v3
  signature checks. The APK hash and certificate are in
  [release integrity](RELEASE-INTEGRITY.md).
- No v1.5.17 APK has been installed on the Thor. No boot, panel, CPU, wake or
  performance result is claimed for this version.

## Minimal supervised Thor plan

1. With the owner present, record the installed version, AYN BOTH mode,
   physical CRTCs, boot ID, daemon/compositor PIDs, CPU property, app data
   and a recovery reference. Keep v1.5.16 stable available. Stop if the
   baseline is unexpected.
2. Install only the exact signed v1.5.17 APK over the existing app in BOTH,
   without clearing data or rebooting. Open the app to trigger the guarded
   v1.5.16 daemon migration; confirm one daemon, same compositor/kernel PIDs,
   both CRTCs active and saved preferences intact. Stop on any mismatch.
3. If installation is healthy, perform **one supervised cold boot** with CPU
   Fix ON in BOTH. Observe both screens through the second visual Android UI
   phase. Stop on flash green, artifact, restart loop, unexpected CRTC or
   incomplete recovery. Do not use an extra boot merely to improve a timing
   average.
4. Save sanitized `jesty-thor-boot-trace.log` **and `.log.1`**, plus a
   filtered `adb logcat -b events -v monotonic -d` capture outside Git.
   Compare sample gaps with 500 ms, grace lengths with 10/5 s, property and
   compositor marks, new system_server/service marks, and final READY.
   Check one kernel boot ID, one intended compositor restart, one daemon and
   both CRTCs. The observer is diagnostic only; do not treat a missing
   boot-animation edge alone as proof of a failed boot.
5. A second boot requires a concrete code correction and fresh supervision.
   TOP cold boot, dock/BOTTOM ONLY, any grace reduction and removal of the
   composer restart remain separate future decisions.

## Results

| Check | Result |
|---|---|
| In-place upgrade and daemon migration | Pending |
| One supervised BOTH cold boot | Pending |
| GATE/HELPER/ANDROID trace and logcat correlation | Pending |
| Visual state, final CRTCs, CPU Fix and single restart | Pending |

Do not promote this pre-release to stable until the physical results have
been recorded and reviewed.
