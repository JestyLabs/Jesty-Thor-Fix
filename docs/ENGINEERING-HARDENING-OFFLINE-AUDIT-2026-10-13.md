# Offline engineering audit — 2026-10-13

**Baseline:** stable v1.7.1, published release source `f929ff910bfa8e5179a2c6a63948a18fc3d6b252`; review against `main` `8f0e7ca815c72b2d66b2dc1762c235e247ab32bb` when this report was produced. The #67 maintenance branch was subsequently merged forward to this `main` without product-source modifications.

**Evidence level:** public source, manifests, CI configurations, GitHub run/job states, published release metadata, sanitized historical physical evidence. **No device connected, no production APK rebuilt/installed, no fault injection, no admin branch/environment settings independently inspected.** This document closes static inventory tasks and records specific next-code/test actions; it does not claim those actions have passed.

## A. CI and release gates — findings and actions

| Path | Observed | Implication / next action |
| --- | --- | --- |
| Main CI `.github/workflows/host-tests.yml` | `build` runs on PR, main push and dispatch. PR and manual runs also schedule `android_emulator_smoke`; **main push does not**, by explicit `if:` condition. | The actual v1.7.1 source run [38320243808](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/38320243808) finished `build=success`, `Sign main candidate=success`, `android_emulator_smoke=skipped`. **Passing main CI is not a repeat of the emulator suite for that exact main push**; relevant PRs can carry prior evidence. |
| Main signer | `sign_main_candidate` requires `build`, `main` push, designated original/triggering actor, `thor-signing` environment and `THOR_SIGNING_ENV_READY=true`; signed output is a separately protected artifact. | Keep source/build identity, key isolation and exact-artifact validation intact. GitHub-side environment approval/branch-selection policies **cannot be confirmed** with the current integration privileges. |
| Published release | [38322379696](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/38322379696) completed successfully for the published source; publication workflow checks artifact, package/version, SHA-256, signer, source run and re-downloaded asset. | Documentation can close public source/run/digest provenance. Do not claim an independently recalculated APK hash or signer from metadata-only inspection. |
| Branch checks | GitHub reports `main.protected=true`; an active repository tag ruleset called **Protect published version tags** exists. The branch protection detail API returned HTTP 403 to this connector. | **Required status check list / review requirements unverified**, regardless of workflow YAML. An administrator should export/review branch protection or ruleset settings before changing enforcement. |
| Focused updater gate | `.github/workflows/updater-gate-emulator.yml` is path-filtered to gate/harness/workflow files; it does **not** trigger for every edit to `AppUpdater.java` / `UpdateInstallReceiver.java`. | When the updater flow changes, include/extend relevant integration coverage rather than assuming that focused job ran automatically. Full PR emulator smoke still runs separately. |

### Safe CI improvement proposal (not yet applied)

1. Make a required, *always-created* PR integration check with an explicit `success`/failure signal, avoiding skipped or indefinitely pending required contexts. Decide if emulator load/flakiness merits merge-blocking enforcement, and keep a clear per-PR exception policy.
2. Consider a separate optional **main-push** emulator observation job before the signing/publish path; if made a release gate, require the right job in the signer dependencies and avoid cycles/irrelevant ref or actor conditions. Do not assume adding `needs: android_emulator_smoke` works while that job is skipped on main pushes.
3. Broaden the updater gate's path coverage for producer/receiver integration as appropriate, but avoid two expensive identical emulator suites triggered needlessly.
4. Have an authorized repository admin verify protected `thor-signing` and `thor-publication` GitHub environments, restrictions, reviewers, signed-tag protection and effective required contexts. Record screenshots or policy export privately; keep secrets and reviewer identities out of public documentation.
5. Confirm that a forked PR cannot request protected signing/publishing credentials and that PR-only tests use disposable keys. Re-run full CI after **any** eventual workflow change.

**Completion boundary:** this audit documents the actual sources of evidence and the CI mismatch, but it **does not** change required checks or verify private administrative policy.

## B. Android/updater integration — next runnable tests without a Thor

Existing evidence includes Java host model suites, Android API 33 emulator lifecycle smoke, disposable-key signing, and a separate real Android `PackageInstaller.Session` harness. See [installer tests](ANDROID-PACKAGE-INSTALLER-TESTS.md). The harness tests the **platform** behavior, not a complete end-to-end production updater plus daemon transition.

| Test tier | Already present | Offline gap to close |
| --- | --- | --- |
| Host Java models | `UpdateCommitGateTest`, `UpdateVersionTest`, `HandoffRecoveryModelTest`, `IpcPeerPolicyTest`, `BootAndLidModelTest`, `WatcherCadenceModelTest` and other suites | Add deterministic characterization for *concurrent* commit/cancel events, denied install callback ordering, stale callback/session cleanup, retry/rebind races and old/new daemon identities using non-Android seams |
| Android API 33 emulator | Real session create/write/commit, user confirmation/denial, app lifecycle smoke; ephemeral test certificate | Wire `UpdateInstallReceiver` and `AppUpdater` explicitly through an isolated test harness; verify unchanged installed package after denial and no double-dispatch when callbacks reorder or Activity/process state changes |
| Production Thor | Source-level vendor bridge and hardware checks; physical evidence from the signed code76 test candidate | Real `PServerBinder`, DRM/CRTC, hall/reboot/suspend and privileged daemon handoff **not testable via ordinary emulator**. Requires separate owner-approved matrix |
| Static release policy | APK naming, signer expectations, exact-artifact publication, code scanning, build tests | Independent published-APK byte-level signature and digest verification optional outside this docs audit; branch/environment admin policy check still open |

Any offline coding should be a **separate small commit** containing a specific test or pure-code extraction plus its green test run. Never equate passing a model mock with physical validation.

## C. Android 13/SDK and hardware boundary — feasibility only

The current source manifest targets SDK **25** while running on the project's tested Android 13 Thor family; it explicitly declares `REQUEST_INSTALL_PACKAGES`, an exported launcher activity, an exported boot receiver, a non-exported `AutoService` and non-exported `UpdateInstallReceiver`. No target or manifest change is proposed here.

- Check a target-SDK migration **only** against real requirements: boot receiver and background service restrictions; PackageInstaller source/permission/user-consent policy; hidden vendor `PServerBinder` access; process/foreground lifecycle; exported components; notification and accessibility behavior. An Android 13 emulator cannot prove the vendor bridge remains accessible.
- Review SDK migration with a compatibility *decision matrix* and safe CI compile tests before a separately authorized device trial. Do not raise the target SDK simply to achieve a cosmetic modern-version badge.
- Device-specific DRM CRTC, display ID, hall and vendor property paths must remain **fail-safe** on unknown firmwares. Firmware .377 is explicit in the [code76 physical report](evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md). Dock/external display and BOTTOM-only remain unverified, not failed/supported.
- Normal Guard ON wake-recovery passed; active blocked-wake handling was not triggered. Future reproduction must distinguish absence of an unintended wake from confirmed interception, with the guard counter and daemon action evidence.

## D. UX and measurement — static audit, not a physical PASS

- `MainActivity` is landscape-only; a long press on the GitHub action exposes advanced updater preferences, which has weak discoverability. Prefer proposing an explicit, labeled settings entry in a **separate UI-only PR**. Do not silently alter onboarding, update approval or installer safety.
- Accessibility test plan: TalkBack labeling/focus, 200% font scale and clipping, hit target size, contrast and physical gamepad/keyboard navigation. These are **NOT RUN** on a device in this audit.
- Already measured: sanitized per-process CPU ticks during awake idle and clock-bound Android suspend duration. **Not measured here:** energy in Wh/mAh with calibrated instrumentation, comparative battery-runtime gain, gameplay frame times or input latency. Keep these different evidence tiers explicit.
- Optional future energy A/B/A protocol: same firmware, brightness, FPS cap, radios, temperature, battery state and workload; record energy instrument, units, controlled runtime, variance and raw provenance. Do not optimize the 0.32% TOP CPU merely because it is higher than BOTH; safety-repair polling may justify the difference.

## E. Offline exit criteria and blocked items

- [x] Reconcile #67 with shipped `main` without runtime edit.
- [x] Review published release identity and mark metadata-derived observations honestly.
- [x] Identify emulator-versus-main-push gating and which review settings could not be inspected.
- [x] Inventory the existing host/emulator test tiers and highest-value missing integration cases.
- [x] Record SDK/firmware support and UX/accessibility/energy follow-ups.
- [x] Expand the existing `IpcPeerPolicyTest` host-only test with UID boundary/adversarial cases and an exhaustive `0000`–`0777` socket-permission matrix. Isolated `javac` + `java` execution **PASS on OpenJDK 21** (2026-10-13). Production `IpcPeerPolicy` unchanged; GitHub JDK 17 CI and physical socket credentials **not covered** by this local run.
- [ ] Extend other pure host tests for daemon read timeouts/partial frames, resource ceilings, installer callbacks and handoff recovery; run supported JDK 17 CI and keep evidence tier explicit.
- [ ] Verify production updater flow inside a real Android emulator after adding deterministic seams/harnesses.
- [ ] Confirm effective branch/security-environment policies using an authorized administrator.
- [ ] Execute hardware-only Guard/dock/BOTTOM/external display tests with explicit owner approval.
- [ ] Conduct a new production release only after separate approval and physical gate.

**No runtime changes are implied by completing the checked static tasks.** See [#67](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/67) for the maintenance workstream.
