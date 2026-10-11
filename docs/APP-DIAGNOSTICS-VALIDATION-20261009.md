# PR #55 — diagnostics-only validation ledger (2026-10-09 evidence)

This ledger excerpts validated diagnostics observations from the original combined PR #55 study. It **does not** assert that the final split, diagnostics-only APK has been installed or tested on the Thor.

## Verified on prior combined candidate

- The owner installed a locally signed candidate whose DEX/manifest/resources were compared against the corresponding CI artifact. This candidate also contained experimental passive-energy features, now excluded from the diagnostics PR.
- The **APP DIAGNOSTICS** preview opened and displayed current foreground telemetry.
- **Save report** invoked Android's document picker and produced a readable text report. The saved body was checked against the preview, and no forbidden raw device/boot identifiers, PID/UID/path or absolute timestamp fields were observed in that sample.
- Picker cancellation returned to the dashboard when performed after the picker was ready.
- A controlled **app-only force-stop** followed by manual relaunch produced an Android `USER_REQUESTED` reason. This does not prove behavior for arbitrary crashes, OOM/LMK terminations or the privileged root daemon.
- The root daemon and existing display-service processes were unaffected by that controlled app-only stop.

## Offline validation already recorded

- Bounded diagnostics/privacy and transition fixtures: **561 assertions** reported on 2026-10-09.
- No extra daemon request, shell command or background diagnostic polling is intended. Normal build, dashboard, boot/lid/IPC, public-content and release-policy checks are required on each new commit.
- The previous combined package's 25 passive sleep model assertions, four energy-analysis fixtures and overnight USB-confounded interval are **not** acceptance evidence for the diagnostics-only PR; they belong to the stacked research branch.

## Remaining acceptance

1. Review the new split diff for unresolved `PassiveSleep*` references, stale UI controls, stale CSV/energy links, missing tests and unintended changes to Q wire keys.
2. Run CI on the final split head; confirm Java compilation, unsigned APK, diagnostics models, installer/emulator and CodeQL.
3. With separate owner authorization, test final APK on the Thor: previews, retained event bounds, on-demand exit history, picker save/cancel/recreation, unsupported/unavailable outcomes and app-only force-stop/relaunch without daemon disruption.
4. Avoid attributing Android process-exit classifications to vendor services without independent evidence; missing history never proves a process survived.

**No final acceptance or energy/battery saving has been established by this ledger.**
