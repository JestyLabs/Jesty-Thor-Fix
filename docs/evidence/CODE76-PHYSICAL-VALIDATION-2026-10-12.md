# Candidate code 76 — physical CPU and smoke validation (V6)

**Device:** AYN Thor · Android 13 · firmware .377  
**Test date:** 2026-10-12  
**Build tested:** Jesty Thor Fix **1.7.1 / versionCode 76**, test candidate [PR #68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68), source commit `92f92ef117de1eb556e946bbe2e2628cce3f437d`  
**Snapshot:** **V6** — physical smoke **PASS**; CPU efficiency **PASS**; long quiet-suspend **IN PROGRESS**. This is **not** a stable release.

## Physical installation and functional acceptance — PASS

The owner-approved signed code76 APK was installed **in place** over code74, retaining application data and original installation identity. The expected authenticated daemon handoff completed, and the new daemon reached `BOOT_READY`.

The previously observed CPU Fix confirmation error was resolved: the controller accepted the **newer verified completed CPU attempt** rather than treating an older early-start record as current. Physical inspection reported the CPU Fix as **CPU_ACTIVE / CLOCKS_NORMAL**, with the verified attempt retained and no additional compositor restart triggered by the handoff. The display-related system processes retained their recorded identities across the update.

The owner also confirmed the **BOTH/TOP display states**, and two normal lid sleep/wake cycles passed with **True Bottom Screen Off ON, CPU Fix ON, Lid Guard OFF**. The 120 Hz setting was left as selected. A pre-update interrupted passive trial was cancelled through the normal UI, without creating a new trial.

**Finding: PASS** for supervised candidate76 installation, CPU Fix confirmation recovery, display mode smoke and normal short sleep/wake.

## Awake-idle background CPU — PASS

The physical comparison used the same Thor and workload class as the completed [RC2 baseline](RC2-PHYSICAL-BASELINE-2026-10-12.md): Android Home, app/dashboard in the background, both main fixes ON, Lid Guard OFF, 120 Hz manually selected and USB attached during ~182 s awake-idle collection windows.

The measurement is the daemon's CPU time as a percentage of **one core**. Six windows were individually validated, with a single consistent Android boot and daemon identity.

| Mode | Window 01 | Window 02 | Window 03 | Median code76 | RC2 median | Difference |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| **BOTH** | 0.087931% | 0.082480% | 0.087985% | **0.087931%** | 0.289403% | **−69.62%** |
| **TOP** | 0.333372% | 0.317188% | 0.317019% | **0.317188%** | 0.322314% | **−1.59%** |

The recorded pre-declared acceptance budget allowed up to **+0.10 percentage points of one core** relative to the RC2 reference; both configurations **passed**.

In BOTH, the observed daemon CPU median fell from approximately **0.2894% to 0.0879% of one core**. In TOP, the candidate preserved the already low baseline around **0.32%**. This is a comparison of measured daemon work, not a claim that the CPU adoption fix alone caused the entire observed difference.

**Input formula:** `CPU one-core % = 100 × process CPU ticks / (100 × elapsed seconds)`. Every source duration and CPU tick delta is included in the [sanitized CSV](CODE76-CPU-IDLE-2026-10-12.csv), with no device or process identifiers.

## Quiet-suspend tests — in progress at V6 snapshot

The **BOTH** quiet-sleep experiment had a valid initial clock endpoint, and the owner had disconnected USB and closed the lid for the defined observation window. Its final clock endpoint was **not present** in V6. The separate **TOP** long quiet-sleep trial had **not yet started**.

The completed RC2 reference remains **743.229–743.601 s in BOTH** and **750.554–751.027 s in TOP**. Candidate76 suspend results will be published only after the corresponding final endpoints have been captured and checked.

## Provenance and publication

The owner's private V6 archive contained **1,232 content members**, all independently checked against size and SHA-256 manifest entries (**1,232/1,232 PASS**). Daemon CPU values were independently recalculated from the six source tick deltas and elapsed durations; medians match the source summary. Exact signed candidate and forward-only recovery77 underwent source, payload and signature verification prior to physical authorization. Code77 remains **prepared, not installed**.

Only the numerical CPU measurements, public app version, test method and observed functional outcomes are published here. Signed APKs, full logs, process/boot/device identifiers, private source captures, screenshots and proprietary firmware inspection material remain in the owner's private evidence archive.

A successful short sleep/wake smoke and a sustained **unplugged** quiet-suspend trial are separate tests; the former already **passed**, while the latter is explicitly **pending completion** in this snapshot.

**Next evidence update:** publish independently checked code76 BOTH/TOP quiet-suspend results when supplied, then complete the optional Lid Guard ON regression under the established test plan. No RC2 rerun is needed.

Back to [validation evidence index](../VALIDATION-EVIDENCE.md).
