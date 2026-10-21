# S4 — isolate updater archive verification

Programme: **JTF-QUALITY-2026-10-17 / S4**. Tracker: [#95](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95).
Baseline: `d03529f5b111bbe30c1bbf70c8597c75da6a6b0f` (current main after v1.7.5 documentation).

This increment moves one existing responsibility from `AppUpdater` into the package-private, stateless `UpdateArchiveVerifier`. It changes no acceptance policy or user flow. It is a candidate for review, not a new release or a declaration that S4 or the entire programme is complete.

## Gap and ownership

The controlled lifecycle host fixture explicitly excludes APK validation. Existing Android tests exercise native installation, malformed archives and a foreign signer, while `UpdateVersionTest` covers signer-policy logic. Those tests do not characterize all metadata selections and short-circuit paths through the actual `AppUpdater.verifyArchive` entry. An earlier local verifier characterization used a copy of the method; it was not a CI regression against the production entrypoint.

| Owner | Before | After |
| --- | --- | --- |
| `AppUpdater` | UI/lifecycle, transport/checksum, archive metadata validation, install-session coordination | UI/lifecycle, transport/checksum and install-session coordination; delegates archive validation |
| `UpdateArchiveVerifier` | Absent | Reads Android archive/installed metadata; checks package, release version, increasing long version code and existing signer policy |
| `UpdateVersion` | Version and signer-policy decisions | Unchanged |
| Android | APK parsing, package identity/signatures, native install consent/enforcement | Unchanged |

The verifier holds no activity, preferences, mutable state, lock or executor. The existing worker calls it at the same point before session creation. No ownership transfer, new thread or new lock is introduced. `AppUpdater` goes from 794 to 753 lines; the helper has 61 lines. This reduces the responsibilities of the coordinator, not the total source-line count.

## Characterization before extraction

`UpdaterArchiveHostTest` invokes the actual private updater entrypoint. Production sources compile against Android API 34; only Android metadata and UI boundaries are inert at host runtime. No verification algorithm is copied into the fixture.

The same fixture passed **17 cases / 34 assertions** before and after extraction. It checks acceptance or the exact error type/message (and exception identity for a framework failure), metadata lookup flags, order and short-circuiting:

- Valid current signer; missing archive; different package; release-version mismatch.
- Lower/equal version code rejected; increasing codes above the signed 32-bit range accepted.
- Missing signing metadata; foreign signer; accepted rotation lineage; rejection of an old key alone.
- Matching unordered multiple signers; rejected signer subset, empty history and mixed signing shapes.
- Framework lookup failure propagated without changing the exception instance.

The recorded outcomes/query traces are identical before and after. The extracted statements and signature-encoding helper are identical after renaming the context receiver. The original lookup sequence, error messages and delegated `UpdateVersion.signersCompatible` call are preserved.

Five controls compile deliberately changed production code and require the intended test to fail: omit the version-code gate, omit signer rejection, use candidate contents instead of lineage, use installed history instead of the current signer, and omit the archive-signature lookup flag. All five were detected on both versions. These are sensitivity checks, not reported product defects.

## Reproduction and evidence tiers

Use an existing JDK 17 and Android API 34 SDK; nothing is installed by the host runner. Store optional evidence outside the checkout.

```powershell
# Run once with the unchanged baseline checkout, then without --production-root
# for the candidate. Use a fresh --output directory for each run.
python scripts/test-updater-archive-host.py --sdk "$env:ANDROID_SDK_ROOT" --java "$env:JAVA_HOME" --production-root '<unchanged-baseline-checkout>' --output '<fresh-external-output>' --negative-controls

./scripts/test-dashboard.ps1
./scripts/test-updater-lifecycle-host.ps1

# Build-only uses disposable fixture signatures, without contacting a device.
python scripts/test-updater-android-e2e.py --sdk "$env:ANDROID_SDK_ROOT" --java "$env:JAVA_HOME" --build '<fresh-external-build>' --build-only
```

| Tier | Observed evidence / gate |
| --- | --- |
| HOST | PASS: actual-entry characterization before/after and all five controls; existing version, readiness, commit, dispatch, confirmation, session ownership, receiver, coupling and lifecycle suites |
| BUILD ONLY | PASS: disposable Android updater fixture APKs compile with the extracted production verifier; not an Android runtime result |
| CI / EMULATOR | Required on the exact PR head: full build, CodeQL and existing native Android updater/metadata/transport regressions. See the PR checks for the final result; a local host/build PASS does not satisfy this gate. |
| PHYSICAL | NOT RUN. No Thor contact, installation or physical update acceptance in this increment. |

The archive runner is included in the existing CI lifecycle-host step. The existing emulator source list also includes the helper, and the existing updater boundary check continues to cover the extracted code.

## Preserved boundaries and remaining work

No change to download/checksum logic, release selection, signer trust, installer consent, session persistence, callback ownership, cancellation timing, boot, IPC, daemon identity, CPU, Guard, display modes or hardware control. No manifest/version/permission/SDK change, production signing, main merge or release. Full raw test evidence stays local; public test inputs use synthetic metadata.

Host adapters cannot prove native APK parsing or real signing-lineage behavior; those remain Android/platform evidence. A green emulator cannot prove Thor firmware or physical behavior. Stop on a changed rejection, query side effect, previously green regression or unexplained CI failure; do not relax a gate to obtain PASS.

S6 remains the separately authorized physical matrix. It includes acceptance of the current release's changes from 1.7.4/1.7.5 (upgrade/handover, current containment state, modes and ordinary sleep/wake), plus matched performance measurements. Record the exact installed APK/source/firmware, retain historical results and mark unavailable or unsafe cases NOT RUN/UNKNOWN. The version-1.7.5 results do not automatically validate a later artifact containing this refactor.
