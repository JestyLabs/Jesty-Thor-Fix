# Long TOP sleep: persistent black screens during wake and lower-screen activation

Status updated 19 October: investigation and coordinator hardening are included
in PR #82. A cancellation defect is reproduced and corrected in host and Android
emulator fixtures. The physical incident's causal transition remains uncaptured.
See [investigation results](TOP-WAKE-INVESTIGATION-RESULTS.md) for findings,
validation boundaries and the owner's requested closure disposition.
This record concerns a different incident from the engineering-hardening PR #67.

## Verified release and reported conditions

The installed package/version metadata and on-device APK hash match the owner's
archived published v1.7.1/code76. The public release provenance is recorded in
[release integrity](RELEASE-INTEGRITY.md#171-stable-publication--verifiable-public-metadata-2026-10-12).
The currently reviewed PR #67 artifact was not installed on this device.

The owner reports TOP mode before opening the lid after a prolonged sleep.
Jesty Thor Fix's Closed-Lid Wake Guard was OFF. Separate sleep automation was
active during the incident and disabled afterward. The owner reports no further
persistent blackout after disabling it and attributes the incident to that
interaction. This is an observed association; the lost transition prevents
confirming which operation caused both screens to lose presentation.

The post-reboot app diagnostic sample reports display fix and CPU fix requested,
Guard OFF, BOTH mode and CRTCs1/1. This is a recovery snapshot, not confirmation
of every setting or display state during the blackout.

## Owner-observed sequence

1. Opening the lid initially produced an image on the upper display.
2. The image disappeared; the owner perceived another sleep transition and
   could not recover normal display operation with ordinary controls.
3. Brief Power/block-unblock actions appeared to show a frame momentarily,
   followed by black again.
4. On the first prolonged Power press intended to restart, the upper display
   showed normal image and the Android menu containing reboot/shutdown options.
   This recovery occurred before the actual reboot.
5. Attempting to activate the lower display then returned both displays to black.
6. An owner-triggered reboot finally restored both displays.

The Power menu observation establishes that UI presentation was possible at
that point. It does not establish whether later black frames were caused by
system sleep, display-power transitions, compositor behavior or another failure.

## Evidence and limitations

| Evidence retained owner-local | What it establishes | What it does not establish |
| --- | --- | --- |
| Package metadata and installed/archive APK hash comparison | Published1.7.1/code76 identity | Execution of PR67 corrections |
| Recent app diagnostic export | Post-reboot foreground sample with Guard OFF and recovery state | A sample during the blackout; physical panel light from CRTC flags alone |
| Older diagnostic export | Historical RC2 evidence | Evidence of this incident |
| Separate sleep-automation report | Restore/wake entries near the reported incident and another boot entry; enabled at export time | Attribution of the blackout or proof of a fresh system sleep request |
| Persistent boot trace | Historical boot/recovery records plus BOOT READY after reboot | Wall-clock alignment of every old entry to the incident |
| Current-boot logcat and display/power snapshots | State after the owner restarted | The lost pre-reboot transition |
| Previous-boot log attempt and pstore inventory | Previous log unavailable; pstore empty in this collection | Absence of a pre-reboot crash or display failure |

The app report has approximately28 hours without foreground observations before
the new post-reboot events. It therefore does not observe the long-sleep wake
failure. The automation's latest short sleep summary was already replaced by a
session around the reboot; it must not be reported as the duration of the long
sleep. Its later service-absence snapshot follows the owner's clarified disable
action, rather than proving it was disabled during the incident.

Reports, full traces, identifiers, application inventory and device-specific
details remain owner-local outside Git. This document is a sanitized engineering
record; it contains no raw captures or signing material.

## Original investigation questions

- Distinguish renewed global sleep from black presentation while the device is
  awake, using evidence at the transition itself rather than post-reboot state.
- Examine concurrent TOP wake repair, firmware mode changes and lower-display
  activation. A request to enable the lower display is a reported trigger;
  temporal proximity alone does not prove which component wrote the final state.
- Verify the actual runtime Guard state and sleep-request source if the failure
  occurs again; preserve mode, panel/CRTC, wake/sleep and daemon/compositor evidence.
- Keep corrections narrow and supported by meaningful failing tests or
  demonstrated mechanisms. PR #82 now includes the demonstrated coordinator
  correction and its regression coverage.

Collection was read-only after recovery. No new lid/sleep cycle, install, mode
change, privileged signal or reboot was performed by the investigator. Any
future device reproduction requires a separately agreed capture plan, stop
conditions and data-preserving recovery. Existing historical short-sleep PASS
results remain historical; they do not close this reported long-sleep incident.

PR #82 now includes source changes as well as this incident record. No new
physical session or release is implied. Display-power changes still require
physical acceptance before release under the contributing policy.
