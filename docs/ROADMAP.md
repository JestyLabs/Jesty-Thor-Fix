# Current roadmap

Updated 2026-10-12. Stable release: **v1.7.0**. The completed control baseline is **RC2 / code73**. The corrected integrated **code76 [PR #68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68)** is undergoing supervised physical acceptance; the original code74 [PR #63](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/63) remains a separate historical candidate.

See [physical validation evidence](VALIDATION-EVIDENCE.md) for completed test results. Historical investigations remain in their original, dated reports rather than being repeated here as future work.

## Completed and hardware-validated

- **True Bottom Screen Off:** verified physical display-pipeline changes in TOP, BOTH restoration and normal post-wake repair.
- **Dashboard CPU Fix:** reproduced and mitigated high-cluster-frequency pinning on the tested Thor.
- **Watcher efficiency (v1.7.0):** approximately **89% lower daemon CPU** in controlled hardware A/B compared with the preceding implementation, with safe display recovery preserved.
- **v1.7.0 signed candidate:** verified in-place handoff, physical mode transitions, wake repairs and supervised reboot.
- **RC2 physical baseline (2026-10-12):** six repeatable CPU idle windows and successful sustained-suspend experiments in BOTH and TOP. [Dataset and calculations](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md).
- **Code76 functional smoke and daemon CPU (2026-10-12):** installation, CPU Fix confirmation recovery, BOTH/TOP and short sleep/wake **PASS**. Six CPU windows **PASS** with lower BOTH daemon CPU than RC2 and unchanged low TOP cost. [Physical measurements](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md).

These tests support the named outcomes. They do not need to be rerun solely because their original work item was previously phrased as an investigation.

## Priority 1 — finish code76 physical acceptance

- **Completed:** code76 signed in-place installation, authenticated daemon handoff, restored CPU Fix confirmation, BOTH/TOP and short sleep/wake smoke.
- **Completed:** matched ~182 s daemon CPU windows (three in BOTH, three in TOP); both mode budgets **PASS**. The RC2 control is preserved and does **not** need repeating.
- **In progress at V6 snapshot:** normal long quiet-suspend BOTH experiment. Collect the final bounded Android clock endpoint after the owner finishes the unplugged interval. TOP long quiet-suspend follows.
- **After suspend:** check the optional Lid Guard ON path separately, keeping the ordinary Guard OFF behavior intact.
- **Prepared recovery:** signed code77 [PR #69](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/69), not installed. Code75 [PR #64](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/64) is historical and is not a normal downgrade after code76.
- **Documentation:** append only confirmed results to the [physical evidence index](VALIDATION-EVIDENCE.md), keeping raw private captures and proprietary vendor data out of Git.

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
