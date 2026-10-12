# Current roadmap

Updated 2026-10-12. Stable release: **v1.7.0**. The current device-test baseline is **v1.7.1-rc2**; the combined **RC3 [PR #63](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/63)** remains a candidate, not a public stable release.

See [physical validation evidence](VALIDATION-EVIDENCE.md) for completed test results. Historical investigations remain in their original, dated reports rather than being repeated here as future work.

## Completed and hardware-validated

- **True Bottom Screen Off:** verified physical display-pipeline changes in TOP, BOTH restoration and normal post-wake repair.
- **Dashboard CPU Fix:** reproduced and mitigated high-cluster-frequency pinning on the tested Thor.
- **Watcher efficiency (v1.7.0):** approximately **89% lower daemon CPU** in controlled hardware A/B compared with the preceding implementation, with safe display recovery preserved.
- **v1.7.0 signed candidate:** verified in-place handoff, physical mode transitions, wake repairs and supervised reboot.
- **RC2 physical baseline (2026-10-12):** six repeatable CPU idle windows and successful sustained-suspend experiments in BOTH and TOP. [Dataset and calculations](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md).

These tests support the named outcomes. They do not need to be rerun solely because their original work item was previously phrased as an investigation.

## Priority 1 — complete RC3 device acceptance

- Preserve the signed RC2 comparator before any candidate replacement.
- Validate exact RC3 signing, identity, safe in-place handoff, CPU Fix and True Bottom Screen Off, normal BOTH/TOP and wake repair.
- Repeat RC2-matched CPU/suspend experiments on RC3, with **Lid Guard OFF** and no unrelated configuration changes.
- Test the optional **Lid Guard ON** path separately, with safe behavior and a small overhead budget. It remains OFF by default.
- Keep the temporary recovery [PR #64](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/64) distinct from the final release.
- Update the [public evidence index](VALIDATION-EVIDENCE.md) only after the RC3 run is physically complete.

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
