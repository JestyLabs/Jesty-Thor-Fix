# Jesty Thor Fix 1.1.0

This release gives Jesty Thor Fix two independent controls:

- **True Bottom Display Fix** powers the lower display hardware fully off in
  TOP mode and restores that state after wake.
- **AYN Dashboard CPU Fix** addresses the reproduced LITTLE/BIG clock pinning
  seen with AYN Dashboard and dual-screen use.

## Important display-restart notice

Changing AYN Dashboard CPU Fix briefly restarts both displays and USB. It can
look like a reboot, but Android and open apps remain running. When enabled, the
same brief reset runs once during a normal boot and can look like a second
reboot.

## Other changes

- Added two clear, equal feature toggles.
- Consolidated display mode, larger CPU speeds, warning state, and manual check.
- Mode/background now follow the physical lower CRTC rather than only AYN's
  logical mode value.
- Extended pin detection to dual-screen mode.
- Kept the existing true-off wake timing and display-control behavior.

## Validation

The exact signed APK was installed in place and tested on the physical Thor.
TOP/BOTH, true-off, sleep/wake `OFF_OK`, display-reset recovery, closing the UI,
and real-reboot persistence passed. With the Dashboard CPU Fix enabled, the
focused BOTH-mode check recorded 0/25 simultaneous LITTLE+BIG maximum samples.

SHA-256:

```text
10240A143C2B3C73333F089ACD45D563D476E571FA93EDEA9F765DA421D0EE77
```
