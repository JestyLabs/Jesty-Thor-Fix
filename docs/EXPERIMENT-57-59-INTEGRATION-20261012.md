# Experimental integration proposal — Lid #57 + Passive Sleep #59

**Status: research/draft — CI-only. NOT APPROVED for signing, installation, merge or release.**
**Baseline:** `17bac625fa915d8f8be724e6d0af2bdc16d44dd8` (`main`, versionName `1.7.1-rc2`, versionCode 73).  
**Inputs:** [#57](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/57) `136ee907872021160a72bb91f260ab8bef4e7468`; [#59](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/59) `7cb3268deb4e9477f3d0c7c84f3ebc266c9ac531`.

## Purpose and boundaries

Propose a *reviewable, isolated* joint source candidate to catch integration issues early. This draft copies the **exact net modified/added files** from #57 (8) and #59 (15) on top of the exact RC2 `main`. The input path sets do not overlap; this is a file-level composition, **not a tested semantic merge**. The production RC2 and both source research PRs remain untouched. This draft does not include #37 or #62, and does not modify display refresh, firmware, backlight, CPU governor or vendor services.

The preliminary owner-local offline composition passed nine host suites (boot/lid, diagnostics, dashboard, display/CPU read paths, watcher runtime/measurement, PServer fixture, publication and content guard). This record is from the owner-provided **POST-RC2-PR-READINESS** report, **not** evidence that this GitHub draft's own CI has passed. No combined signed APK, combined emulator, or combined physical test has yet been demonstrated.

The existing CI on a pull request may build an **unsigned** artifact. It must not be installed. It retains the RC2 manifest identity solely for static/offline combination; **before any authorized signed physical experiment** choose and review a distinct experimental versionCode/versionName and rollback plan. Do not publish this as `v1.7.1-rc2`, do not reuse its tested APK hash or claim RC2 evidence for this branch. The protected `Sign test candidate` workflow is manual, separate and is **not** authorized by this proposal.

## Change inventory

- #57: preserves public `LidGuard` facade, existing G/H/Q IPC keys, Hall-event decision epochs, and coordinator paired CRTC reads; implementation in `LidProtectionRuntime`. Guard OFF avoids passive Q-triggered `dumpsys display` probing; `external_display=unknown` with Guard OFF is an intended telemetry change. A previously opened blocking Hall FD/thread may persist after OFF.
- #59: explicitly armed Activity `onPause()` and `onResume()` battery/clock endpoint snapshots in diagnostics, no extra timer, receiver loop, root query or privileged daemon change. Adds V1-compatible preview/export, host fixtures, offline worksheet and A/B analyzer with independent battery-interval-evidence provenance checks.
- Preserve #55 diagnostics, #58 paired DRM/CPU read-path optimizations and #61 RC identity/Unicode. Do not amend the RC2 signer, production workflows, boot/CPU control, daemon identity or vendor hardware profiles.

## Conditions to pass *before requesting a signed experimental APK*

1. Compare this branch against the exact starting `main`: expect 23 copied #57/#59 file paths plus this proposal document, and **no unreviewed runtime changes**. Verify exact copied blob SHAs with the input PR heads; verify immutable production/release safety files.
2. GitHub CI for **this combined head** must PASS: Java host and source-contract tests, diagnostics V1 compatibility, Python energy fixtures, unsigned Android build, Android emulator gate, and CodeQL. A successful individual PR pipeline cannot substitute for combined evidence.
3. Independently review cross-feature contracts: `LidGuard` facade; G/H/Q and `external_display` with Guard OFF; Hall ON/OFF/ON epochs, deferred wake repair, PowerManager null/late responses, and normal BOTH/TOP wake; Activity pause/resume during picker, cancellation, rotation/recreate, app-only force stop and possible process death; diagnostic export and updater handoff.
4. Address/record the known persistence gap: `PassiveSleepMonitor.onPause/onResume` calls `SharedPreferences.Editor.commit()` but does not check its return; assess failure handling and process death. Do not imply durable boundary evidence on a failed commit.
5. Validate actual-vs-desired fix representation independently of #59's preference-derived bitmask: Guard Hall failure or unknown effective state may differ from desired checkbox values. Comparisons must not silently equate these.
6. Enforce data interpretation limits: duration >= 300000 ms does not prove true sleep, uninterrupted unplugged state or calibrated Wh. A/B battery samples need independent interval evidence; paired USB=0 endpoints are insufficient. A short/unknown/counter-invalid trial is not an energy savings result.
7. New unique experimental identity, reviewable signature provenance, complete owner-approved hardware protocol/rollback and backup of existing state **before signing or installation**. No auto-dispatch to protected signing or release.

## Proposed gated validation after separate owner approval

| Gate | Environment | Required observation | Outcome now |
| --- | --- | --- | --- |
| A | Source review | exact file/sha inventory, separate original drafts, no overlap or production drift | Source compose proposed |
| B | GitHub combined CI | host suites + emulator + CodeQL + unsigned build pass on this exact head | NOT RUN on this combined head |
| C | Review/fault fixtures | stale Hall epochs, missing PowerManager, failed preference commits, lifecycle ambiguity, Q semantic checks | OPEN |
| D | Signed experiment | new identity and trusted signer, recoverable in-place install, explicit rollback | NOT AUTHORIZED |
| E | Physical safe baseline | Guard OFF, trial disarmed: BOTH/TOP, wake and CPU/default diagnostics, no unexpected daemon restarts | NOT AUTHORIZED |
| F | Isolated #57 | Guard ON/OFF/ON, Hall and wake, deferred repair, external display Q, no unsafe sleep | NOT AUTHORIZED |
| G | Isolated #59 | Activity lifecycle, picker/cancel, force stop and unplugged trial >= 10 minutes; validate boot/counters before reconnect | NOT AUTHORIZED |
| H | Joint acceptance | compare isolated vs combined and retain failures/UNKNOWN as such | NOT AUTHORIZED |

A physical Guard ON result is not equivalent to **active wake prevention** unless a qualifying wake and prevented action are evidenced. RC2 observed normal recovery with `blocked_wakes=0`; active blocking remains UNKNOWN. #62 brief BOTTOM bright flash remains an independently observed unresolved symptom and #37 refresh black blinks remain separate.

## Stop conditions, ownership and next decisions

Stop on unexpected sleep/wake loops, delayed screen repair, black/bottom display loss, persistent daemon/boot change, data loss, incompatible Q/export, or a baseline regression. Preserve private captures offline; publish sanitized conclusions only. No synthetic Hall/Power key injection or disruptive 120Hz/display/firmware changes are authorized here.

**Review outcomes:** (1) keep #57/#59 individual drafts and this combined proposal draft; (2) inspect exact-head GitHub CI results; (3) patch only documented failures in source PRs or a reviewed combined integration; (4) request a new and separate decision before signing/installing; (5) merge per PR only with exact-build physical acceptance.
## Follow-up review — 12 October 2026 (combined draft only)

- Added fail-closed trial boundary persistence handling and pure-Java fixtures: a false/throwing `SharedPreferences.commit()` invalidates the trial *in the running process*, even if Android's memory cache was changed. Explicit successfully saved re-arm/cancel recovers. **Process-death recovery and filesystem durability after a power failure remain unverified.**
- Trial record now includes `fixes_effective_state=NOT_SAMPLED`: switch preferences are requested state, never measured effective daemon/DRM state. No-trial diagnostics V1 stays byte-identical.
- Added host tests and a source guard; new CI + emulator + CodeQL on this exact combined HEAD must pass before handing off to Codex.
- Did NOT modify #57 hardware sleep control or pretend the Hall→input keyevent interval is atomic. A separate owner-supervised hardware gate remains.
- These are research draft source changes, not an authorization for RC3 signing, installation or publication.
