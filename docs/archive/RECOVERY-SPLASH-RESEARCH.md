# Archived recovery-splash research summary

Status: **closed research / not release behavior**

This summarizes the recovery-splash / second-boot work before the v1.6.0 early-CPU-restart path moved the required compositor recovery into the natural startup window.

## What was established

- Suppressing the second vendor boot animation was physically validated.
- After suppression, the remaining user-visible gap was a short black interval before framework recovery.
- A root SurfaceControl splash could render, but targeting and geometry were fragile during compositor/framework recovery.
- The upper panel's native geometry and the visible recovery surface orientation differ; rotation/targeting had to be handled explicitly.
- Direct font rendering from the root app_process path caused a native Minikin/Typeface abort; any future recovery surface should remain font-free unless a proven safe text path exists.
- Curtain/top-exclusive experiments did not produce a sufficiently robust release path.
- The native recovery bootanimation override prototype was rejected after an on-device infinite-repeat failure.
- None of these prototypes were promoted to release behavior.

## Closed PR / branch map

- #15 — `research/thor-double-boot-phase3a` — second boot-animation suppression research — head `e3ff2ac62d7f9bfc11d9ba1871958f2fabdc7662`
- #16 — `research/thor-recovery-splash-instrumentation` — splash instrumentation/model — head `e4e2b5fb7c573bb1de58499c0e6a1422f5fef0dc`
- #17 — `research/thor-recovery-splash-integration` — guarded splash integration — head `7935cf7f578e0f1329baa38af3199d5d5c59aeb3`
- #18 — `research/thor-recovery-splash-surface-prototype` — SurfaceControl prototype — head `21864a1f2bcfdcbd6bfbb10a8b3992b94032e8f1`
- #20 — `work/thor-bootanim-suppression-current` — current-stack second boot-animation suppression — head `40f728a3eba3f2cbb79e12cad309814591e6886a`
- #21 — `work/thor-recovery-splash-model-current` — current-stack lifecycle models — head `c6a5e5b2b4a761bb4c38c38b52213829ccb708f7`
- #22/#23 — `work/thor-recovery-splash-current-prototype` — current-stack prototype / combined candidate — head `985b7447ab0266d1ce63947e2839ee2c1c08ca71`
- #27 — `work/thor-recovery-splash-curtain-prototype` — early curtain experiment — head `9ec333ef4bf86349411c38f61976d120854fab72`
- #28 — `work/thor-recovery-top-only-probe` — read-only TOP-only targeting probe — head `ad0bf8c06c4a08d29c69b6f6df7800c3ef131e0c`
- #29 — `work/thor-recovery-splash-top-exclusive-prototype` — top-exclusive branding experiment — head `98c8ae20067a04c0943f9472f45d5eaa74c76ec4`
- #30 — `work/thor-recovery-native-bootanim-prototype` — native bootanimation override experiment — head `5f9ee37a793af71e5ce4347ac90e1bb595d81437`

The closed PRs retain the exact implementation diffs and discussion. The head SHAs above are pinned here so branch pruning does not erase the research map.

## Why this was closed

PR #34 promoted the physically validated `pservice -> /data/boot_start.sh` early CPU restart into the v1.6.0 integration path. The required compositor restart now happens during normal startup instead of as a late second UI recovery, substantially reducing the original motivation for a recovery splash.

Do not reopen this stack unless a future UX problem justifies the complexity.
