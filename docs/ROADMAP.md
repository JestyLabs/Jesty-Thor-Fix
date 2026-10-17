# Current roadmap

Updated 2026-10-17. Stable release: **v1.7.2 / code80**. The signed APK completed scoped Android 13 Thor acceptance of update, activation/reboot, lifecycle and normal mode transitions; the external recovery chain was separately tested on a healthy Thor. [Release notes](RELEASE-NOTES-1.7.2.md) and [activation guide](ACTIVATION-RECOVERY-GUIDE.md). PR #67 is merged. Original RC78 **15 PASS / 1 historical FAIL** and separate A1 PASS remain preserved in the [physical results](PR67-PHYSICAL-VALIDATION-2026-10-16.md); no universal reliability or blackout recovery is claimed.

See [physical validation evidence](VALIDATION-EVIDENCE.md) for completed test results. Historical investigations remain in their original, dated reports rather than being repeated here as future work.

## Completed and hardware-validated

- **True Bottom Screen Off:** verified physical display-pipeline changes in TOP, BOTH restoration and normal post-wake repair.
- **Dashboard CPU Fix:** reproduced and mitigated high-cluster-frequency pinning on the tested Thor.
- **Watcher efficiency (v1.7.0):** approximately **89% lower daemon CPU** in controlled hardware A/B compared with the preceding implementation, with safe display recovery preserved.
- **v1.7.0 signed candidate:** verified in-place handoff, physical mode transitions, wake repairs and supervised reboot.
- **RC2 physical baseline (2026-10-12):** six repeatable CPU idle windows and successful sustained-suspend experiments in BOTH and TOP. [Dataset and calculations](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md).
- **Code76 physical primary checks (2026-10-12):** installation, CPU Fix confirmation recovery, BOTH/TOP display, short sleep/wake, six CPU windows **and both long suspend trials PASS** with Guard OFF. Optional Guard ON normal-cycle regression subsequently **PASS** in BOTH/TOP; active closed-lid wake interception **NOT OBSERVED**. [Physical measurements](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md).

These tests support the named outcomes. They do not need to be rerun solely because their original work item was previously phrased as an investigation.

## Priority 1 — monitor post-release activation and reliability

- PR #67 is merged. Its historical T09 CPU failure and later A1/T10 acceptance results remain separately documented.
- The exact code80 candidate completed scoped Android 13 Thor update, activation/reboot, lifecycle and mode-transition acceptance. The controlled external code81/code82 recovery chain also passed on the healthy device.
- **Integrated and published:** PR #87 brought the accepted code80 runtime to `main`, and CI/CodeQL passed on the integrated commit. The stable APK is the preserved, physically tested signed artifact; no rebuild or resigning occurred as part of publication.
- Keep the blackout investigation in [issue #84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) and separate brightness investigation [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) open. Publication states known limits; healthy-device recovery does not establish recovery during blackout. Keep [private captures out of Git](PUBLIC-EVIDENCE-POLICY.md).

## Priority 2 — performance and battery evidence

- Measure representative gameplay frame times (including upper-percentile frame times) under controlled matched conditions to quantify frame pacing.
- Characterize energy usage using repeatable device/battery measurements, suitable resolution and stable workloads. Measure fix effects and background overhead separately.
- Continue targeted optimizations only when hardware profiling identifies a worthwhile cost or a user-visible correctness issue. Preserve established wake-repair reliability.

## Separate research, outside RC3 acceptance

- [#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37): mixed-refresh / 120 Hz behavior; investigation retained for later without forcing additional device capture now.
- [PServerBinder lifecycle](PSERVERBINDER-LIFECYCLE-INVESTIGATION.md): read-only reliability research when a naturally occurring incident supplies evidence.
- TOP-mode app focus/input routing; only advertise a fix when independently reproduced and verified.
- Compatibility research for other devices, including Retroid Pocket Duo, without assuming AYN-specific logic generalizes.
- Optional BOTTOM-only/dock and updater end-to-end coverage as hardware/time becomes available.

## Engineering constraints

Where stock firmware prevents an earlier or atomic operation, prefer a bounded, verified app-controlled solution instead of speculative vendor modification. The one-time display-component recovery needed by CPU Fix is documented as a justified constraint of the supported configuration, not a failure of optimization.

See [architecture](ARCHITECTURE.md), [test records](VALIDATION-EVIDENCE.md) and [public evidence policy](PUBLIC-EVIDENCE-POLICY.md).
