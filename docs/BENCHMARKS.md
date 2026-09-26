# Thor benchmark notes

## 2026-09-26 A/B/A capture

The public `0.32` APK was installed on an AYN Thor running Android 13. The
device was left in TOP mode with the top display awake and the dashboard moved
out of focus. The same USB connection, power profile, brightness, and workload
were retained across all passes.

Sequence:

1. Native Thor mode: 45 one-second samples.
2. True bottom-display off: 45 one-second samples.
3. Native Thor mode again: 30 one-second samples.
4. Restore the fix and verify `crtc181=1`, `crtc243=0`.

The two native passes were combined only after the return pass reproduced the
same frequency lock. Native therefore contains 75 samples and true-off contains
45 samples.

## Results

| Metric | Native TOP | True-off |
| --- | ---: | ---: |
| LITTLE mean / median | 2.016 / 2.016 GHz | 1.616 / 1.901 GHz |
| BIG mean / median | 2.707 / 2.707 GHz | 1.654 / 1.651 GHz |
| PRIME mean / median | 2.302 / 1.843 GHz | 1.843 / 1.843 GHz |
| LITTLE samples >=95% max | 75/75 | 22/45 |
| BIG samples >=95% max | 75/75 | 0/45 |
| System-power proxy mean | 2.030 W | 1.239 W |
| System-power proxy median | 1.888 W | 1.275 W |
| Load average mean | 0.465 | 0.425 |
| Battery temperature | 30.0 C | 30.0 C |

The native return pass averaged 2.182 W versus 1.929 W in the first native
pass; true-off averaged 1.239 W between them. This brackets the observed proxy
reduction at roughly 0.69-0.94 W for this short capture.

## What the power proxy means

The host sampled the firmware's USB voltage/current and battery
voltage/current nodes once per second. The reported proxy is:

```text
USB input power - battery charging power
```

It is useful for an A/B direction and approximate magnitude, but it is not a
calibrated wall-power measurement. USB conversion loss, sysfs update timing,
battery regulation, screen brightness, background work, and charger behavior
can affect it. Do not translate the 39% short-run proxy delta directly into a
39% battery-runtime promise.

## Raw data

- [`native-pass-a.csv`](benchmarks/2026-09-26/native-pass-a.csv)
- [`true-off.csv`](benchmarks/2026-09-26/true-off.csv)
- [`native-pass-b.csv`](benchmarks/2026-09-26/native-pass-b.csv)

The CSV files contain sample numbers and hardware telemetry only. Device
serials, account data, local paths, and raw logs are not included.
