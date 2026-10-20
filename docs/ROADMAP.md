# Current roadmap

Updated 2026-10-20. Current published stable: **[v1.7.4 / code84](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.4)**. The quality integration [#101](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/101) and display-stall containment [#123](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/123) are integrated. [Final software/signing/publication gates passed](VALIDATION-1.7.4.md); exact released-APK physical acceptance follows publication by explicit maintainer instruction and remains NOT RUN. This is not closure of the hardware blackout or completion of S6. Earlier physical CPU/suspend results and the RC78 historical FAIL remain scoped to their original builds.

See [physical validation evidence](VALIDATION-EVIDENCE.md) for completed test results. Historical investigations remain in their original, dated reports rather than being repeated here as future work.

## Engineering quality programme (post-v1.7.3)

The current **JTF-QUALITY-2026-10-17** improvement track is governed by the [evidence-led quality roadmap](ENGINEERING-QUALITY-ROADMAP-2026-10-17.md), the [execution contract](ENGINEERING-QUALITY-EXECUTION-CONTRACT.md), and [root AGENTS.md](../AGENTS.md). It starts with offline sleep/wake characterization; no bug fix, Thor validation or release is implied by opening the governance PR. The archived PR #67 hardening ledger is historical rather than an active instruction source.

## Completed and hardware-validated

- **True Bottom Screen Off:** verified physical display-pipeline changes in TOP, BOTH restoration and normal post-wake repair.
- **Dashboard CPU Fix:** reproduced and mitigated high-cluster-frequency pinning on the tested Thor.
- **Watcher efficiency (v1.7.0):** approximately **89% lower daemon CPU** in controlled hardware A/B compared with the preceding implementation, with safe display recovery preserved.
- **v1.7.0 signed candidate:** verified in-place handoff, physical mode transitions, wake repairs and supervised reboot.
- **RC2 physical baseline (2026-10-12):** six repeatable CPU idle windows and successful sustained-suspend experiments in BOTH and TOP. [Dataset and calculations](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md).
- **Code76 physical primary checks (2026-10-12):** installation, CPU Fix confirmation recovery, BOTH/TOP display, short sleep/wake, six CPU windows **and both long suspend trials PASS** with Guard OFF. Optional Guard ON normal-cycle regression subsequently **PASS** in BOTH/TOP; active closed-lid wake interception **NOT OBSERVED**. [Physical measurements](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md).

These tests support the named outcomes. They do not need to be rerun solely because their original work item was previously phrased as an investigation.

## Priority 1 — validate v1.7.4 on hardware

- **Released:** safe process-bound service transfer from published 1.7.1–1.7.3, TOP repair cancellation correction, display-stall containment and the scoped quality-programme changes. [Release notes](RELEASE-NOTES-1.7.4.md).
- **Physical gate:** owner observation, then supervised exact-APK health, preference retention, BOTH/TOP/BOTH and normal sleep/wake checks. Do not force repeated blackouts or infer a rare failure resolved from one quiet night.
- **Containment:** lower OFF may be attempted in BOTH after a slow call returns; the lower screen can remain off until protection clears. It cannot interrupt an already blocked call. [Investigation and limits](BLACKOUT-CONTAINMENT-1.7.4.md).
- **Separate follow-up:** [#84 containment delivered](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) is closed by maintainer decision; [#95/S6](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95) retains physical acceptance; [#65 brightness flash](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) retains its separate investigation. No brightness/framework modification is included.
- **Preserved historical evidence:** code80/81/82 trials, RC78 T09 FAIL and later A1/T10 PASS are unchanged; none validate the code84 mitigation.

## Priority 2 — performance and battery evidence

- Measure representative gameplay frame times (including upper-percentile frame times) under controlled matched conditions to quantify frame pacing.
- Characterize energy usage using repeatable device/battery measurements, suitable resolution and stable workloads. Measure fix effects and background overhead separately.
- Continue targeted optimizations only when hardware profiling identifies a worthwhile cost or a user-visible correctness issue. Preserve established wake-repair reliability.

## Separate research, outside RC3 acceptance

- [#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37): **pre-existing AYN Thor 120 Hz / mixed-refresh tearing**, not a regression introduced by Jesty. Investigation only: root cause and a safe fix remain unproven. Do not market this as a Jesty bug or resolved feature; no extra device capture without a separate approved protocol.
- [PServerBinder lifecycle](PSERVERBINDER-LIFECYCLE-INVESTIGATION.md): read-only reliability research when a naturally occurring incident supplies evidence.
- TOP-mode app focus/input routing; only advertise a fix when independently reproduced and verified.
- Compatibility research for other devices, including Retroid Pocket Duo, without assuming AYN-specific logic generalizes.
- Optional BOTTOM-only/dock and updater end-to-end coverage as hardware/time becomes available.

## Engineering constraints

Where stock firmware prevents an earlier or atomic operation, prefer a bounded, verified app-controlled solution instead of speculative vendor modification. The one-time display-component recovery needed by CPU Fix is documented as a justified constraint of the supported configuration, not a failure of optimization.

See [architecture](ARCHITECTURE.md), [test records](VALIDATION-EVIDENCE.md) and [public evidence policy](PUBLIC-EVIDENCE-POLICY.md).
