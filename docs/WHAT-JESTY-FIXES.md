# What Jesty Thor Fix fixes — and what it does not

**Current stable:** [v1.7.3](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.3) · **Device:** AYN Thor (tested Android 13 firmware family) · **Positioning reviewed:** 2026-10-18.

## The two verified fixes

| Stock AYN Thor behaviour | What Jesty changes | What the physical evidence confirms |
| --- | --- | --- |
| **Native TOP Only:** the bottom image can look black while the lower display controller/pipeline remains active. | **True Bottom Screen Off** requests an actual hardware-level lower-display OFF transition, verifies it, returns the lower panel to active in BOTH and reconciles after ordinary sleep/wake. | On the tested Thor, native TOP showed `power=1` and both CRTCs active; Jesty TOP showed `power=0`, top CRTC active and **bottom CRTC inactive**. Mode changes, normal wake repair, reboot restore and closed-dashboard operation were verified. |
| **AYN Dashboard CPU pinning:** in observed configurations the LITTLE/BIG CPU clusters stayed unnecessarily close to their highest clocks under light load, including BOTH with Dashboard displayed. | **AYN Dashboard CPU Fix** uses the vendor-side corrective state and a guarded one-time Android framework/display-component recovery when necessary. This is independent of the True Off switch. | With Dashboard open in BOTH, pinning was reproduced when OFF and a separate focused CPU Fix ON test found **0/25 samples** with both LITTLE and BIG at maximum together. No manual CPU frequency, voltage or governor tuning. |

**Our distinguishing proposition:** To our knowledge, Jesty Thor Fix is **the only publicly documented utility for AYN Thor combining a verified hardware-level lower-display-off path with the separately validated Dashboard CPU clock-pinning correction**. We have not found another publicly documented implementation of equivalent true-off verification and wake repair. This is a **best-known public-market statement**, not a claim to have audited every private tool or future release.

Native AYN TOP Only, launcher display selection and an app simply covering the bottom display with black are **not equivalent evidence** of the lower hardware pipeline being inactive. Conversely, the observed vendor power flag and DRM CRTC are **not a laboratory measurement of every electrical component's current**, so avoid asserting absolutely zero lower-panel wattage.

- [Physical display and 1.0.0 hardware validation](DEVICE-VALIDATION.md)
- [Independent CPU Fix 1.1.0 hardware validation](DEVICE-VALIDATION.md#110-two-fix-validation)
- [Live verification and wake repair](LIVE-RESULTS.md)
- [Version-specific acceptance, limitations and regressions](VALIDATION-EVIDENCE.md)

## Measurements we may quote

- A **controlled A/B/A** native TOP vs true-off capture reported **2.030 W vs 1.239 W mean system-power proxy** (about 39% lower on that capture); this is neither direct calibrated electrical energy nor a battery-runtime guarantee. The comparison belongs to the observed **configurations**, not automatically to either fix in isolation. [Method and CSV](BENCHMARKS.md).
- Separate v1.7.1 screenshot configurations show around **1.72 W** fixes OFF and **0.95 W** both ON. They are snapshots, not a controlled battery test or proof which switch contributed how much.
- Latest documented physical idle candidate measurements include about **0.09% of one CPU core** in BOTH and **0.32%** in TOP. These are **daemon overhead**, not system-wide CPU savings and not universal device specifications. [Provenance](VALIDATION-EVIDENCE.md).

## Optional safeguard, not a third independently demonstrated firmware fix

**Closed-Lid Wake Guard** is optional and OFF by default. It attempts a policy-checked return to sleep after an unintended closed-lid wake. Normal Guard-ON BOTH/TOP recovery passed on the tested device, but **effective interception of a genuinely unintended wake was not observed in those acceptance sessions**. Describe it as an optional safeguard, not a proven universal wake-prevention fix. [Coverage](COMPATIBILITY.md).

## Existing problems we investigate but did not introduce

**120 Hz / mixed-refresh tearing:** a pre-existing **AYN Thor device/firmware behaviour**, **not caused by Jesty Thor Fix**. We investigate the stock mixed-refresh path in [research PR #37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37); the cause of physical tearing and an effective, safe correction remain unknown. **Not fixed in v1.7.3.** Keep this separate from our two released fixes and do not imply that a refresh-rate selector or black overlay addresses it.

**Persistent black screen after some TOP sleep/wake sequences:** [issue #84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) is open, and the causal path is **unconfirmed**; not every wake is covered by the successful ordinary recovery tests. **Brief lower-screen brightness flash:** [issue #65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) is a different unresolved observation. Neither is a claimed v1.7.3 fix.

Other-device support (including Retroid Pocket Duo), BOTTOM-only, docks/external-display coverage and private firmware variations are not implied by successful stock-Thor tests. [Compatibility matrix](COMPATIBILITY.md).

## Public-claim rules

1. **Say clearly:** two independently verified AYN Thor fixes, one app; the True Off behavior is **hardware-level**, not a black-image trick. Link the real-device evidence.
2. **Use "only known / only publicly documented" with scope** when discussing uniqueness. Do not assert an independently established worldwide monopoly or that no private/recent tools exist.
3. **Name the source of the 120 Hz issue:** stock Thor hardware/firmware. Say **investigation**, never Jesty regression or shipped fix.
4. **Do not overclaim power:** short-run proxy ≠ battery life, vendor/CRTC verification ≠ calibrated electrical measurement, and screenshot configurations combine switches.
5. **Keep acceptance honest:** normal wake recovery ≠ no possible black screen; Guard normal-cycle recovery ≠ observed active interception. Test failures and unknowns remain tracked.
6. **Version-stamp evidence**: reported tests may concern earlier exact APKs. A new unsigned CI build or isolated integration PR is not automatically validated on a physical Thor. 

This document concerns public description and the current **released** application only. It does not authorize changes to the daemon, display power, CPU handling, signing, firmware, releases, `main` or the separate quality integration branch.
