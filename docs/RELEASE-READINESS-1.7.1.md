# v1.7.1 release readiness — owner checklist

**Status:** PREPARATION ONLY · not permission to publish, merge product code, install another APK or operate the Thor.  
**Date opened:** 2026-10-12  
**Planned version:** `1.7.1` / Android versionCode `76` · candidate [PR #68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68)

## Completed evidence

- [x] Exact code76 PR CI, CodeQL, host tests and emulator installer/lifecycle suites passed.
- [x] Signed test candidate and separate forward-only code77 contingency prepared with expected signing identity.
- [x] Supervised code74 → code76 in-place upgrade with preserved data and authenticated daemon handoff — **PASS**.
- [x] CPU Fix adoption issue resolved physically; newer verified `APPLIED` proof retained, CPU status active and normal.
- [x] BOTH/TOP and short sleep/wake smoke under the baseline configuration — **PASS**.
- [x] Three ~182 s physical awake-idle CPU windows per mode, independent recalculation — **PASS**. BOTH 0.087931%, TOP 0.317188% of one core; [sanitized data merged in PR #70](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/70).
- [x] **Final DELTA-03 integrity: 188/188 entries SHA-256/size PASS**; physical session recorded as CLOSED, exact code76 and normal final BOTH, CPU Fix confirmed, Screen Fix ON, Guard OFF, Home/USB, stable daemon/boot/compositor.
- [x] Signed recovery77 source/tests/artifact prepared in [PR #69](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/69). **Not installed or physically exercised**.

## Remaining acceptance

- [x] Code76 unplugged BOTH quiet-suspend completed — **PASS**, **812.828–813.138 s** clock-derived suspend; continuity and owner-confirmed wake documented in the consolidated private evidence.
- [x] Code76 unplugged TOP quiet-suspend completed — **PASS**, **776.449–776.860 s** clock-derived suspend; raw parsed clock bounds independently verified, continuity and owner-confirmed wake checked. [Public evidence PR #72](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/72). Preserve the [RC2 controls](evidence/RC2-PHYSICAL-BASELINE-2026-10-12.md) without repeating them.
- [x] **Lid Guard ON basic normal-cycle regression — PASS** in BOTH and TOP: owner-tested close/brief Power/open, normal display/CPU recovery, and restored OFF at end. **Blocked wakes: 0; no active closed-lid wake block was observed.**
- [ ] Resolve **scope/claims for active Guard interception**: active closed-lid wake blocking is **UNKNOWN / NOT OBSERVED** (not a PASS or FAIL); document whether the owner accepts this limited physical coverage or requests a specific active-blocking reproduction before stable publication.
- [ ] Check that passive Sleep trial Start/Cancel/resume/export and diagnostics lifecycle behavior are adequate for the **combined candidate**, using already completed host/emulator evidence and only the additional physical observation needed for its release scope.
- [x] Final physical restore state independently cross-checked; owner clarifies that a brief maximum-brightness flash was observed **only once historically**, with **no recurrence** in subsequent runs or ordinary use. This is a non-reproduced observation, not proof of a diagnosed/fixed cause and not, by itself, a current release blocker. Keep #37 research outside release.
- [ ] Review any remaining product/release blocker against the completed physical scope; do not automatically infer that unobserved Guard interception is validated.
- [x] **UI-only PR [#73](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/73) merged**: exact two descriptions, vector gear, and Diagnostics/Support/GitHub placement. The diagnostics assertion was updated, builds and emulator checks passed. No production daemon, CPU/display or Lid Guard logic changed. **No rerun of the completed physical CPU/suspend baselines is required for UI-only copy/layout.**
- [ ] Review a screenshot or perform a quick visual smoke of the exact final signed release APK to confirm the intended button placement and strings.
- [x] Rewrite [release notes](RELEASE-NOTES-1.7.1.md) for end users: plain-English feature summary, update/safety guidance and optional-Guard limitation. Verified against `UpdateVersion.summarize`: **488 characters** displayed in the app, **no truncation**, full release body free of release-operator checklists.
- [ ] Record the exact final signed artifact SHA-256, signing certificate, source SHA and CI run in [release integrity](RELEASE-INTEGRITY.md). Do not publish private full logs, identifiers, keystores or vendor extracts.

## Exact-artifact promotion sequence

1. **Completed:** approved code76 [PR #68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68), UI-only [#73](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/73) and physical evidence [#70](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/70)/[#72](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/72) are now merged into `main`; source integration is finished, publication is not.
2. **Pending release-document finalization:** finish these release notes/identity records and integrate only approved publication documentation. The merged runtime code matches the physically accepted candidate except the explicit UI-only changes from #73; no other research PR is included.
3. Use successful **main push CI** and the environment-protected signer to obtain the release-eligible signed candidate. This is distinct from the manually signed PR test candidate.
4. Verify complete APK SHA-256 and established signer, package `com.thor.displaypowertest`, versionName `1.7.1`, code `76`, artifact naming `Jesty-Thor-Fix-1.7.1.apk`, and signed-versus-unsigned payload. Record the exact main commit and CI run.
5. The final main APK **will differ** from the physically installed code76 candidate because of the approved four-file UI-only change (#73). Compare code/resources/manifest and signer/provenance, complete a short targeted UI presentation and install/handoff sanity check as warranted for that changed artifact, and retain the existing functional CPU/suspend evidence for the unchanged implementation. No rerun of matched RC2 controls or long code76 CPU/suspend experiments is needed merely for UI wording/layout.
6. Only after explicit owner approval, manually dispatch protected [Publish tested candidate](../.github/workflows/release.yml) on `main` with the successful main CI run ID. The workflow verifies provenance and publishes the exact signed artifact. Keep the existing v1.7.0 stable release as Latest until owner-authorized stable promotion.
7. Verify released GitHub asset digest, certificate, tag `v1.7.1`, release notes, README/latest link and in-app updater discovery after publication.

## User update and recovery contract

- `PreviousSecureDaemonIdentity` on the code76 branch already lists installed `1.7.0` alongside the reviewed RC identities, and requires protocol 2, healthy watcher, safe BOTH/active CRTCs and a valid PID before replacing a prior daemon. **Source-level compatibility is verified; the physical code71 → code76 handoff has not been separately observed on this Thor.**
- An updater failure must not trigger uninstall, data clear, forced stock-service restart or bypass of handoff gates. Follow the established recovery plan.
- Recovery code77 is for supervised contingencies **only**. A normal Android code76 → code75 downgrade is not supported; code77 is not a release candidate to publish automatically.

## PR disposition once physical acceptance closes

| PR | Suggested action |
| --- | --- |
| [#70](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/70) sanitized V6 CPU evidence | **Merged** into main after all checks passed |
| [#72](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/72) sustained suspend and Guard ON smoke | **Merged into main** with sanitized evidence and active-blocking limitation clearly documented |
| [#68](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/68) code76 | **Merged into main** after successful exact-head CI/CodeQL/emulator and physical acceptance; public publication still pending |
| [#73](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/73) UI polish | **Merged into main** (four-file UI/test diff); final APK visual check still pending |
| [#69](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/69) recovery77 | Keep as unmerged contingency during acceptance; archive/close when its emergency purpose ends |
| [#63](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/63) original code74 | **Closed unmerged**, superseded by #68; historical investigation preserved |
| [#64](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/64) recovery75 | **Closed unmerged**, superseded by code77 contingency |
| [#67](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/67) engineering hardening | Keep draft, separate from the 1.7.1 release |
| [#37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37) mixed refresh research | Remains a separate draft |

No release tag, GitHub Release, release notes “stable” claim, protected signing workflow, daemon install, version bump or production source change is authorized by this preparation document. Raw physical captures, firmware reverse engineering and signing material stay private.
