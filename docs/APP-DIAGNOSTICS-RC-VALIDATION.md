# App diagnostics — RC validation and release gate

Status: **candidate checklist, not evidence of final-device acceptance**.
This documents the planned #55 on-demand diagnostics feature. It does **not**
authorize Android/ADB, installation, hardware manipulation, signing, merging,
publishing a GitHub release or changing any clock, display, CPU or vendor state.

## Scope and provenance

- In-app bottom-right **⚙ cogwheel**, with accessible label **App diagnostics**.
- On demand: a readable presentation of last known foreground Q state,
  Android-reported app exit reasons and up to 64 newest-first app events.
- On demand: the **Save report** action writes the original sanitized
  `JESTY_THOR_DIAGNOSTICS_V1` record through Android's document picker,
  not the prettified view. No data is automatically uploaded.
- During foreground dashboard operation: use the **existing** 1 Hz authenticated
  Q telemetry response. The diagnostics classifier adds bounded Java work and
  an asynchronous SharedPreferences write **only when a new allowed event is
  recorded**. It never asks the daemon for extra samples.
- With the app/dashboard closed: no new service, timer, or background diagnostic
  watcher is created. True Bottom Screen Off, CPU Fix and vendor daemon control
  are unchanged by this PR.
- Energy / sleep experiments are **excluded** (research PR #59); Lid maintenance
  (#57) is **excluded**; Display/CPU read-path optimization (#58) is
  included as candidate-only code subject to full test/telemetry parity.

**Performance:** No extra daemon polling does **not** imply zero app CPU/disk I/O.
No physical CPU, energy, or gaming-frametime parity has yet been established for
the exact final candidate.

## Gate A — merge readiness (offline GitHub)

- [ ] Confirm the exact #55 HEAD and base; review **all** new commits and comments.
- [ ] Confirm all required GitHub Actions checks are green for **that HEAD**:
      app diagnostics privacy/preview JVM fixtures, boot/lid/IPC, dashboard,
      unsigned APK build, CodeQL and Android emulator/installer.
- [ ] Validate privacy and labels: exit reasons are Android app-only; Activity
      destruction isn't a crash; no paths, PIDs, account identifiers or logcat.
- [ ] Check docs/README/architecture links and code against each other.
- [ ] Check mergeability with the latest protected main; avoid force pushes.
- [ ] Perform only owner-approved merge; do not confuse a merge with hardware
      validation or signed APK authorization.

## Gate B — prepare next unique RC identity (post-merge)

- [ ] Reconcile current GitHub `main` vs published release version and tags.
      Historical repo references can lag; never assume the new RC version.
- [ ] Select a strictly newer unique version code/name and migrate only the
      allowlisted daemon identity per `BUILD-AND-RELEASE.md`. No arbitrary
      pid- or version-matching recovery.
- [ ] Obtain the **exact signed CI APK** through the already-protected signing
      environment and verify APK package/version, certificate, APK SHA-256,
      signer and source commit. Do not store secrets, private signed APKs
      or device logs in the repo.
- [ ] Record the candidate's SHA-256, source commit and signing certificate
      before any installation. No automatic release upload.
- [ ] Prepare accurately scoped prerelease notes: UI/cogwheel, readable event
      preview, privacy, unchanged machine export, paired DRM read improvements,
      property-read fallback, no new daemon polling and
      remaining physical/energy unknowns.

## Gate C — separate owner-authorized Thor test

Before touching hardware, present exact APK SHA-256, signing identity,
read-only command allowlist if required, initial device conditions, rollback,
expected system impacts and stop conditions. Owner approval must be specific.

1. In-place install, preserving settings and private app journal. Verify
   installed package/certificate and exactly **one** authenticated daemon.
2. Normal BOTH / TOP / BOTH: lower CRTC and property state, True Bottom Screen
   Off, native screen recovery and both CRTCs under normal power transitions.
3. CPU Fix enabled/disabled **without toggling it for convenience**: observe
   current effective/desired values; any switch that causes a composer restart
   requires separate approval and a controlled session.
4. Ordinary sleep/wake in BOTH and TOP; verify wake repair latency,
   display state, boot/daemon health. Closed-Lid Wake Guard remains OFF by
   default; exercise ON and lid movement only after a dedicated consent.
5. Cogwheel tap/display/accessibility; readable newest-first events, known
   vs unknown hardware values, stale sample caveats; app-only exit reasons
   and no daemon-process claims.
6. Compare Q keys/types and `system_load_fix` (0/1/?), paired CRTC
   confirmations and unchanged CPU Fix boot/wake policy against the prior build.
   Evaluate Q request latency and distinguish daemon CPU from UI processing.
7. Preview, Save report, picker cancel, unavailable picker, Activity recreation
   while picker is open. Compare the saved V1 raw technical record with the
   expected privacy field allowlist; **it intentionally differs from the
   human-readable preview**.
8. With the dashboard closed, verify no extra diagnostic service or Q
   activity. With dashboard open, record existing Q cadence and any
   material responsiveness, memory/CPU or write-rate changes.
9. Stop at the first unexpected display, restart, lock, sleep, command,
   permission, privacy or daemon identity issue. No blind retries.

## Gate D — prerelease and stable release

- [ ] Publish a labelled **prerelease** only when the owner approves the
      prerelease scope and exact build. Preserve current latest stable.
- [ ] Verify downloaded asset hash matches the signed/tested candidate.
- [ ] Document measured CPU impact as a range with methodology, or **UNKNOWN**
      when matched measurements were not performed.
- [ ] Promote the same artifact to stable **only** after the agreed hardware
      gates and explicit owner acceptance. New APK bits require a new
      version, re-verification and re-approval.
- [ ] Update actual release notes, integrity ledger and README version links
      after facts are confirmed; don't pre-announce an untested candidate.

## Existing partial evidence

The original combined #55 candidate (2026-10-09) physically exercised
preview, picker save/cancel and app-only force-stop. That binary predated
the diagnostics-only split, cogwheel and human-readable formatter. It is
**not validation for this final build**. See
[previous evidence](APP-DIAGNOSTICS-VALIDATION-20261009.md).

For build/signing policy see [maintainer build/release](BUILD-AND-RELEASE.md)
and [GitHub Actions](GITHUB-ACTIONS.md). Do not conflate this candidate
with research PR #59 (energy), PR #57 (Lid), or PR #37
(mixed refresh).
