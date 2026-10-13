# Candidate code 76 — physical CPU, smoke and sustained-suspend validation

**Device:** AYN Thor · Android 13 · firmware .377  
**Test date:** 2026-10-12  
**Build tested:** Jesty Thor Fix **1.7.1 / versionCode 76**, test candidate [PR #68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68), source commit `92f92ef117de1eb556e946bbe2e2628cce3f437d`  
**Evidence:** V6 CPU/smoke and subsequent owner-private BOTH/TOP suspend and Guard ON delta packets (2026-10-12). **Physical smoke PASS · CPU PASS · sustained suspend BOTH/TOP PASS · basic Guard ON recovery smoke PASS.** Active blocked-wake interception was **not observed**; stable-release approval remains separate. This is **not** a stable release.

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

## Sustained Android suspend — PASS in BOTH and TOP

The owner subsequently supplied private delta evidence closing **BOTH** and **TOP** trials. Each trial used the established unplugged quiet-sleep protocol with Lid Guard OFF. Owner-confirmed normal wake/recovery and identity-continuity checks accompany the bounded Android clock comparison. The TOP start/end clock offsets were independently recomputed from the supplied paired Power → Alarm → Power endpoints. The consolidated delta report confirms the previously completed BOTH trial.

The calculation bounds time in Android suspend by subtracting clock offsets: `elapsedRealtime - uptimeMillis` across two endpoints. It is **a clock-derived suspend duration, not a battery-energy or kernel-residency measurement**.

| Mode | Whole window, code76 | RC2 suspend | Code76 suspend | RC2 suspend share | Code76 suspend share | Result |
| --- | ---: | ---: | ---: | ---: | ---: | --- |
| **BOTH** | 892.506 s | 743.229–743.601 s | **812.828–813.138 s** | 87.894–87.938% | **91.073–91.107%** | **PASS** |
| **TOP** | 829.494 s | 750.554–751.027 s | **776.449–776.860 s** | 93.445–93.504% | **93.605–93.655%** | **PASS** |

The predeclared allowable decline in suspend share was **5 percentage points**. Neither configuration regressed: BOTH's bounded difference is **+3.135 to +3.213 percentage points**, TOP's **+0.102 to +0.210 percentage points** relative to RC2. Because each total observation window includes awake preparation, owner wake and USB/ADB reconnection, **do not interpret the higher percentages as isolated power-efficiency or battery-life gains**.

**Finding — PASS:** the signed code76 candidate completed prolonged normal suspend in **both modes** while the validated display/CPU fixes and daemon were present. This closes the matched Guard OFF CPU-and-suspend primary acceptance for the tested scope.

[Recalculate the public clock-bound comparisons](CODE76-SUSPEND-2026-10-12.csv).

## Optional Lid Guard ON — normal-cycle smoke PASS; active interception UNKNOWN

The completed **DELTA-03** tested opt-in Lid Guard **ON** separately after the Guard OFF baselines. In BOTH and TOP the owner closed the lid, waited ~10 seconds, made a brief physical Power-button attempt while the lid was closed, waited ~10 seconds, then opened it. In both cases the owner confirmed **normal display recovery**, CPU Fix remained active, and the intended display control remained intact. UI observation confirmed Guard ON during the cases and later confirmed Guard OFF after restoration.

**Important coverage boundary:** `blocked_wakes` was **0 before and after both cases**, and **no `sleep_after_closed_wake` action was observed**. Therefore this is a **PASS for normal ON-state sleep/wake recovery and Guard OFF restoration**, **not** proof that the Guard intercepted an actual unintended closed-lid wake. Active blocking and the Hall-to-sleep timing race remain **NOT OBSERVED / UNKNOWN**, not PASS or FAIL.

The final owner-private capture confirms BOTH mode, 120 Hz manually selected, Screen Fix ON, CPU Fix ON/confirmed, Guard OFF, trial disarmed, Home screen and USB connected. Both internal CRTCs were active, with unchanged daemon/boot/compositor identities through the measured run. Recovery77 was signed/prepared but never installed.

The owner reported **no brief maximum-brightness flash** during this physical session; this is a qualitative observation only, not attribution of a prior display issue's root cause.

## Provenance and publication

The owner's original private V6 archive contained **1,232 verified content members** (**1,232/1,232 PASS**). The later TOP sleep delta contains **114 verified manifest entries** (**114/114 PASS**); it records the completed TOP endpoints and consolidates the earlier completed BOTH result. The DELTA-03 Guard/final-state packet adds **188 content files, all verified** for SHA-256 and byte length (**188/188 PASS**). Daemon CPU values were independently recalculated from six source tick deltas and elapsed durations; TOP suspend bounds were also recalculated from the raw parsed private clock endpoints. Exact signed candidate and forward-only recovery77 underwent source, payload and signature verification prior to physical authorization. Code77 remains **prepared, not installed**.

Only sanitized numerical CPU and clock measurements, public app version, test method and observed functional outcomes are published here. Signed APKs, full logs, process/boot/device identifiers, private source captures, screenshots and proprietary firmware inspection material remain in the owner's private evidence archive.

A successful short sleep/wake smoke and sustained **unplugged** quiet-suspend are separate tests; **both have now passed** in the supported physical test scope.

**Next release gate:** the basic optional **Lid Guard ON** smoke is now complete for the tested normal-cycle scope. Assess the explicitly unobserved *active* blocked-wake coverage against the proposed release scope; verify the final exact signed-release artifact and obtain owner publication authorization. The completed RC2 and code76 CPU/suspend controls do not need repeating.

Back to [validation evidence index](../VALIDATION-EVIDENCE.md).
