# S5 investigation — native updater consent timeout (offline)

**Tracker:** [quality programme #95](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95), workstream **S5 — test quality and CI**. **Status:** repeatable symptom, root cause unconfirmed. **This PR changes only the test harness and its CI check.**

## Evidence (2026-10-17)

The governance PR [#97](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/97) encountered the same failure in CI runs [39061851640](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39061851640) and [39063947678](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/39063947678): during the **native-consent-accepted** updater Android emulator case, the package-replacement version check observed **1.0.1**, but `settled()` failed to observe simultaneously `transfer=false`, `reserved=false`, `session=-1`, `nativeSessions=0` before the existing 30-second bound. Another same-main-derived PR [#98](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/98) passed the full CI. This indicates variability, **not** a proved infrastructure-only failure or a safe reason to waive checks.

The original generic `Timed out waiting for Android state` gives no safe diagnostic evidence for the four blocking fields. The script stores fuller fixture logs only in ephemeral runner storage rather than uploading those raw files. CI printouts must not embed unbounded dumpsys/logcat, private paths or signing data.

## Minimum scope

1. On **failed** `settled()` only, read a fresh snapshot, installed fixture package version, whether the process incarnation matches the last snapshot, and whether the system installer is focused.
2. Print **allowlisted** synthetic fixture keys and the exact unsettled-field names, never arbitrary `state.json` values, raw Android diagnostics or full logcat.
3. Preserve the 30-second timeout, `AssertionError` failure and all installer acceptance assertions. A diagnostic failure should return `null`/unavailable evidence, not replace the test failure.
4. Host unit tests check classification, unexpected/missing snapshot and secret redaction. Normal E2E remains on emulator, **not on Thor**.

## What is NOT established

No product code/APK is changed, no bug is fixed or classified as a known harmless flake, and a clean emulator run cannot prove physical Android/Thor updater correctness. Next if the failure recurs: compare diagnostic stalled fields and Android fixture lifecycle, reproduce the exact cause, then propose a narrow separately reviewed fix. Do not inflate timeout or suppress a failing CI gate without evidence.

**Test state:** not run locally on opening this PR; await host and Android CI, report exact commit and run results. Signing, physical device, merging and publishing are outside this change.
