# Current roadmap

Updated 2026-10-17. Current published stable: **[v1.7.3 / code83](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.3)** (after [v1.7.2 / code80](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.2)). PRs #67, #89, #90 and #91 are merged. The v1.7.3 safe in-place daemon handover passed host, CI and Android emulator checks; it only proceeds under verified predecessor and device conditions, with guarded fallback otherwise. Historical RC78 physical testing remains scoped to its exact candidate: **15 PASS / 1 historical FAIL**, with separate A1 BOTH acceptance PASS. See [physical results and limits](PR67-PHYSICAL-VALIDATION-2026-10-16.md). Earlier candidate and recovery records remain historical evidence.

See [physical validation evidence](VALIDATION-EVIDENCE.md) for completed test results. Historical investigations remain in their original, dated reports rather than being repeated here as future work.

## Completed and hardware-validated

- **True Bottom Screen Off:** verified physical display-pipeline changes in TOP, BOTH restoration and normal post-wake repair.
- **Dashboard CPU Fix:** reproduced and mitigated high-cluster-frequency pinning on the tested Thor.
- **Watcher efficiency (v1.7.0):** approximately **89% lower daemon CPU** in controlled hardware A/B compared with the preceding implementation, with safe display recovery preserved.
- **v1.7.0 signed candidate:** verified in-place handoff, physical mode transitions, wake repairs and supervised reboot.
- **RC2 physical baseline (2026-10-12):** six repeatable CPU idle windows and successful sustained-suspend experiments in BOTH and TOP. [Dataset and calculations](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md).
- **Code76 physical primary checks (2026-10-12):** installation, CPU Fix confirmation recovery, BOTH/TOP display, short sleep/wake, six CPU windows **and both long suspend trials PASS** with Guard OFF. Optional Guard ON normal-cycle regression subsequently **PASS** in BOTH/TOP; active closed-lid wake interception **NOT OBSERVED**. [Physical measurements](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md).

These tests support the named outcomes. They do not need to be rerun solely because their original work item was previously phrased as an investigation.

## Priority 1 — maintain v1.7.3 and monitor guarded upgrades

- **Released:** PR #67 hardening, PR #89 verified same-kernel handover, PR #90 protected publisher correction and PR #91 public README update are integrated. The exact signed stable APK is linked in the [v1.7.3 release](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.3).
- **Guarded activation:** v1.7.3 may transfer from an authenticated v1.7.1/v1.7.2 daemon without a full reboot only when the checks permit it. Otherwise preserve the documented conservative restart/recovery route and user data. See [v1.7.3 release notes](RELEASE-NOTES-1.7.3.md) and [activation guide](ACTIVATION-RECOVERY-GUIDE.md).
- **Preserved earlier evidence:** exact code80 hardware acceptance and the separate code81/code82 external recovery trial passed in their defined healthy-device scope; the RC78 T09 CPU FAIL and later A1/T10 PASS remain distinct historical results.
- **Separate unresolved issues:** keep [#84 persistent TOP wake blackout](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) (with documentation draft [#82](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/82)) and [#65 brief BOTTOM brightness flash](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) in their issue trackers. Neither is an advertised v1.7.3 fix; retain [private captures outside Git](PUBLIC-EVIDENCE-POLICY.md).

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
