# Jesty Thor Fix v1.7.1

**RELEASE PREPARATION DRAFT — NOT PUBLISHED. Remove this banner only after the exact-artifact release gates and owner approval are complete.**

Jesty Thor Fix 1.7.1 continues improving the AYN Thor's true lower-screen power-off, Dashboard CPU Fix and dependable background behavior. It also makes on-device diagnostics and controlled sleep investigations easier to use.

## What's new

- **More reliable CPU Fix across app updates.** The daemon now keeps a newer, independently verified completed CPU Fix state during an authenticated in-place handoff, instead of mistaking an older boot attempt for a new failure. This addresses the CPU confirmation error observed during testing of the earlier 1.7.1 candidate.
- **App Diagnostics.** Open the dashboard's cogwheel to review readable, bounded event history and Android app exit information. Use **Save report** to export a sanitized diagnostic report without sending data automatically.
- **Opt-in Sleep trial.** The diagnostics screen can record passive Android battery/clock endpoints while the app goes into the background and returns. The trial uses Activity lifecycle boundaries, not a new background collector or a daemon polling loop.
- **Closed-Lid Wake Guard maintenance.** The optional feature remains **OFF by default**. Internal changes isolate its runtime behavior, maintain guarded sleep decisions, and avoid unnecessary external-display probing through telemetry while disabled. Physical Guard ON normal sleep/wake recovery passed in BOTH/TOP; *active interception of an unintended closed-lid wake was not observed*.
- **Preserved display safeguards.** True Bottom Screen Off continues to use physical display-state verification and wake repair. The existing CPU governor/frequency, display refresh and firmware settings are not retuned by this release.
- **Dashboard polish ([UI PR #73](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/73) merged).** Clarified the CPU Fix and Screen Off descriptions, improved the diagnostics gear icon, and swapped its placement with the existing Support and GitHub actions. This is now present in the integrated 1.7.1 source; the earlier physical code76 baseline was installed before these UI-only changes.

## Physical AYN Thor validation

On Android 13 firmware .377, the owner-approved signed **1.7.1 / versionCode 76** candidate passed physical in-place handoff, recovery of CPU Fix confirmation, BOTH/TOP display checks, short sleep/wake smoke **and both prolonged quiet-suspend trials** with Lid Guard OFF. A separate basic Guard ON normal-recovery smoke in BOTH/TOP also **passed**; actual closed-lid wake interception remained **unobserved**.

The completed awake-idle CPU comparison measured daemon utilization as a percentage of one CPU core:

| Mode | RC2 baseline | 1.7.1/code76 | Comparison |
| --- | ---: | ---: | ---: |
| BOTH | 0.2894% | **0.0879%** | **69.6% lower** |
| TOP | 0.3223% | **0.3172%** | **1.6% lower** |

Both configurations passed the predeclared CPU budgets. Prolonged suspend was also confirmed: **812.828–813.138 s (BOTH)** and **776.449–776.860 s (TOP)** from bounded Android clock intervals. These tests measure daemon CPU and estimated time in suspend, not battery-energy savings, and do not isolate attribution to the CPU adoption fix.

[Physical validation and sanitized data](VALIDATION-EVIDENCE.md) · [Code76 physical validation and six-window dataset (documentation PR #70)](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/70)

## Install and safety

- The intended package is `com.thor.displaypowertest`, version `1.7.1`, code `76`.
- For a normal update, install the **official signed APK in place** to preserve settings and diagnostics; do not uninstall the previous version as a workaround.
- Update/handoff safety checks remain: the installed daemon is replaced only after the expected authenticated protocol, healthy watcher and safe BOTH display state are verified.
- The **Dashboard CPU Fix** may require a one-time Android UI/display restart when the user changes that setting; it does not set CPU frequencies or governors.
- Guarded recovery is an operator-supervised procedure, not a user-facing automatic rollback.

## Publication checkpoint (remove before stable publication)

Both code76 long quiet-suspend trials and the separate **basic Guard ON normal-recovery smoke are complete and PASS**; the bounded clocks and detailed Guard test distinction are documented in [physical evidence PR #72](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/72). **No active Guard block was observed** (`blocked_wakes=0`), so the release owner must approve the scope of the claim or request focused further testing. The final product/release identity checks are separately required before these draft notes can be promoted to stable.

The publication workflow accepts only the exact signed candidate from a successful **`main` push CI**. The physically tested PR-signed APK cannot simply be assumed identical to the eventual `main` artifact. Compare the complete APK hash, signing certificate, manifest/package/version and relevant payloads. If they differ, perform the necessary additional physical acceptance before publishing, rather than relabelling an untested rebuild.

- [x] Long quiet-suspend BOTH/TOP completed — **PASS**; evidence published through merged [PR #72](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/72).
- [x] Guard ON basic BOTH/TOP normal-cycle regression **PASS**; final Guard OFF state restored.
- [ ] Owner adjudication of unobserved *active* Guard wake interception and any remaining scope-sensitive acceptance.
- [x] Integrate [UI-only PR #73](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/73) — merged, build and emulator checks PASS.
- [ ] Confirm the final signed APK's exact UI presentation and established release identity before publication.
- [ ] Release candidate on `main` and exact signed APK identity verified against the physically tested candidate.
- [ ] Final code76 installation/update path and data-preservation gate reviewed, including direct upgrade from stable v1.7.0.
- [ ] Release SHA-256, signing certificate and source/head provenance recorded in [release integrity](RELEASE-INTEGRITY.md).
- [ ] Owner explicitly authorizes publication; remove this draft section and banner, then use the existing protected `Publish tested candidate` workflow.

**Current recommended public stable release remains v1.7.0.**
