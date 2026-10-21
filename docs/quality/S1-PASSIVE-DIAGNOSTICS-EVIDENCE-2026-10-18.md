# S1 passive diagnostics: evidence clocks, privacy and bounded retention

**Programme:** JTF-QUALITY-2026-10-17 · **Scope:** host-only test characterization against the **real existing** `AppDiagnosticReport` and `AppTelemetryTransitions`. No production source, daemon, display, logging cadence, user preference, sign or APK behavior changes.

## Six replayable tests

| ID | Input | Expected evidence |
| --- | --- | --- |
| S1-D01 | Foreground Q samples with independent wall and monotonic elapsed clocks, including elapsed rollback | `sample_age_ms` uses elapsed-time sample freshness, becomes `unknown` on rollback; no raw absolute clocks |
| S1-D02 | Application event wall clock jumps backward then forward | Relative `event;age_ms` may decrease or be unknown; history preserves insertion order, **not** an inferred event-causality clock |
| S1-D03 | 3,000 identical foreground Q samples | No additional journal entries; freshest foreground sample still available; bounded export |
| S1-D04 | Previously confirmed BOTH, dashboard paused, then TOP observations | No inferred transition across unobserved pause; new observed tuple only after two consistent foreground samples |
| S1-D05 | Malformed, secret-bearing native Q and malformed restored persistence plus 200 lifecycle events | 64 event ceiling, invalid persisted lines dropped, raw text redacted, restored sample freshness unknown |
| S1-D06 | Unknown upper CRTC while lower is inactive | No falsely confirmed display transition or claim of physical illumination |

The test uses the existing `scripts/test-app-diagnostics.ps1` suite so Java 17 host CI runs it as part of the already enforced diagnostics step. The report is **app-only and on-demand**; `AppTelemetryTransitions` classifies already received foreground Q values. It cannot diagnose the physical #84 blackout, because vendor compositor state and pixel luminance are unobserved. It must never be marketed as a physical fix.

## Architecture findings / release implications

The app diagnostic report distinguishes `sample_age_ms` (monotonic `elapsedRealtime`) from activity event `age_ms` (wall clock); **never sort or causally order separate clocks**. A restart intentionally loses the live sample while keeping a capped validated event journal. Boot trace uses separate daemon monotonic marks and rotates local files at 256 KiB with one prior generation; these files are not merged into the app diagnostics export. The passive sleep trial observes two Activity boundary endpoints, not continuous sleep or a panel measurement.

**HOST:** new real-model regression required. **ANDROID EMULATOR:** CI regression suite only, not vendor evidence. **THOR:** NOT RUN. Distinct [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) and [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) stay unresolved. #94 runtime handover still requires accepted physical tests before a main merge/release.

No new always-on background work, export field, persisted format, root command or logging overhead was introduced.
