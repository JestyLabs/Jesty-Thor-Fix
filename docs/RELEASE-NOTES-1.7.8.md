# v1.7.8

- Updating from v1.7.7 transfers the background service live, as updates from v1.7.1–v1.7.6 already do; no restart is
  needed. The one exception is a boot in which v1.7.7 has detected a lower-screen fault and is already asking for a
  restart: the update then completes at that restart, so the new service never starts on a screen that is known to be
  faulty.
- After the display fix is turned off, the refresh protection no longer records one more refresh request that it can no
  longer apply. It wrote nothing and had no visible effect; only the diagnostic log changes.

The refresh protection, the display-fault detection, CPU Fix, Closed-Lid Wake Guard and the documented hardware
limitations are otherwise unchanged.
