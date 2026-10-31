# v1.7.7

- With the display fix enabled, the app now holds back the panel setting that the AYN firmware writes on every
  refresh-rate change (Smooth Display), and applies it only while the lower screen is on. Changing the refresh rate
  while the lower screen is off in TOP no longer breaks the lower screen until the next reboot; in an owner-supervised
  test that sequence reproduced the black screens on demand.
- If the lower screen still fails to start, the app detects it from the kernel log, keeps the lower screen off and asks
  for a restart (dashboard line and one notification) instead of letting both screens fall into a black-screen loop.
- The dashboard shows the refresh protection state (REFRESH GUARD).
- Updating from the v1.7.6 pre-release completes after one normal restart.

The display-fault detection only asks for a restart; the app never restarts the Thor itself. CPU Fix, Closed-Lid Wake
Guard and the documented hardware limitations are unchanged.
