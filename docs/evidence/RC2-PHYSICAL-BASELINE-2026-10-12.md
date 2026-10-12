# RC2 physical baseline — CPU efficiency and Android suspend

**Test date:** 2026-10-12  
**Result:** **PASS — completed physical baseline for BOTH and TOP**  
**Build under test:** Jesty Thor Fix `1.7.1-rc2`, versionCode `73`  
**Device:** AYN Thor, Android 13, firmware .377

This is the accepted **RC2 comparator**, collected before replacing the tested candidate with RC3. The package was reviewed independently using a sanitized owner-supplied export. All **44 of 44** exported member hashes and byte lengths matched the package manifest; numerical results below were independently recalculated.

## 1. Awake-idle daemon CPU

The same device was on Android Home, Jesty dashboard in the background, CPU Fix and True Bottom Screen Off enabled, Lid Guard selected OFF, and 120 Hz manually selected. USB/ADB remained connected during the **awake-only** measurements. Mode and power at measurement endpoints were stable; boot and daemon identities matched across captures.

**Formula:** `CPU % of one core = 100 × CPU tick delta / (CLK_TCK × elapsed seconds)`, with `CLK_TCK = 100`.

| Window | BOTH: ticks | BOTH: seconds | BOTH: one-core CPU | TOP: ticks | TOP: seconds | TOP: one-core CPU |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 01 | 53 | 183.285486 | 0.289166% | 59 | 183.086544 | 0.322252% |
| 02 | 56 | 183.418353 | 0.305313% | 59 | 183.051565 | 0.322314% |
| 03 | 53 | 183.135761 | 0.289403% | 60 | 182.980078 | 0.327905% |
| **Median** | | | **0.289403%** | | | **0.322314%** |

- **BOTH:** 0.2892–0.3053% of one core across three samples.
- **TOP:** 0.3223–0.3279% of one core across three samples.
- The daemon consumed **0.53–0.60 seconds of CPU time** per approximately 183-second interval.
- Approximate CPU-counter resolution was 0.0055 percentage points per window.

**Finding — PASS:** low, repeatable daemon CPU usage under the measured idle workload. TOP's slightly greater reading is a difference between hardware modes, not a software regression.

[Download recalculable sanitized CPU measurements](RC2-CPU-IDLE-2026-10-12.csv).

## 2. Normal Android suspend

The device completed one unplugged, quiet-sleep experiment in EACH mode. The owner closed the lid during the sleep interval, then woke and reconnected the device for the final endpoint. The unchanged Android boot and healthy daemon identity were checked at the available boundaries.

The method compares `elapsedRealtime` (includes suspend) with `uptimeMillis` (excludes suspend). Paired `Power → Alarm → Power` probes bound the clock delta at each endpoint.

| Mode | Entire observation window | Calculated suspend time | Suspend share |
| --- | ---: | ---: | ---: |
| **BOTH** | 845.597 s | **743.229–743.601 s** | **87.894–87.938%** |
| **TOP** | 803.207 s | **750.554–751.027 s** | **93.445–93.504%** |

**Finding — PASS:** **both configurations successfully sustained over 12 minutes of Android suspend** while the fixes and daemon were present.

The share of the observation window includes wake/reconnection time and is not a ranking of BOTH against TOP. The absolute suspend times support the functional result more directly.

[Download recalculable sanitized suspend intervals](RC2-SUSPEND-2026-10-12.csv).

## 3. Test provenance and disclosure

- **Hardware observations:** USB-connected idle CPU captures; unplugged quiet sleep; startup and final snapshots of boot, firmware, and daemon identity; observed TOP display-pipeline state.
- **Independent review:** recomputed tick-based CPU figures and bounded clock differences; checked all 44 package-member manifest entries.
- **What is public:** numerical measurement inputs, test conditions, calculations and status. No device serial, process identifier, boot identifier, vendor firmware binary, disassembly, internal vendor symbol or unfiltered device log is published.
- **Original material:** kept in the owner's private evidence archive. Sanitization retains consistent session identity attestations, rather than publishing the original identifiers.

The CPU test measures **awake background work**, and the clock test measures **suspend duration**. Gameplay frame pacing and calibrated whole-device energy will have their own evidence when measured.

## 4. Comparison with RC3

The next supervised run compares the exact RC3 build against this baseline with unchanged refresh choice, Android Home workload, dashboard background state, fixes enabled, Lid Guard OFF and matched collector durations. It repeats BOTH/TOP CPU windows, then the two quiet-sleep intervals, before studying the optional Lid Guard separately.

**RC3 comparison status on this report:** **NOT RUN**. This RC2 report remains an immutable comparator in purpose; any RC3 results belong in a separate dated report.

Return to [validation evidence index](../VALIDATION-EVIDENCE.md).
