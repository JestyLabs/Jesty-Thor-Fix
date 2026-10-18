# S1-15 — missing observations and process-loss evidence (host characterization)

**Programme:** JTF-QUALITY-2026-10-17, **S1**. **Base:** integrated candidate `4ff18b48a2c7bc019a6e42f241a53ec0aa776139`. **Change scope:** existing real `AppDiagnosticReport` test class only, plus this evidence document. No app/runtime, daemon, power, diagnostics export, polling, persisted format, privacy policy or release change.

## Four additional real-class host regressions

| Case | Synthetic loss scenario | Required observation | Limitation |
| --- | --- | --- | --- |
| S1-D07 | One TOP Q sample between repeated known BOTH and then a missing-Q interval | Unconfirmed TOP is not logged as an observed display edge; no invented wake/repair or blackout event | Only scripted foreground Q; missed physical transition is **UNKNOWN** |
| S1-D08 | Process killed **after** a real `DISPLAY_OBSERVED` edge was built in RAM but **before** the next durable `SharedPreferences` journal write | Reconstructed report contains the last saved snapshot only; sample freshness is `unknown`; two new consistent post-restart samples required | No Android process kill occurs; models absence of durable write only |
| S1-D09 | First missing/null Q, then repeated missing/null replies | One `DAEMON_UNAVAILABLE` classified observation, no event flood and **no inferred Android process death or panel failure** | A missing socket reply does not establish daemon death |
| S1-D10 | More than 64 later lifecycle events evict original evidence, followed by process restoration | Bounded ring cannot reconstruct evicted incident, no fabricated activity destruction and no fresh sample age | If old incident data was evicted, it is irrevocably unavailable in this export |

Test command: existing `scripts/test-app-diagnostics.ps1` compiles and executes `tests/S1PassiveDiagnosticsEvidenceTest.java` against **unchanged production** `AppDiagnosticReport` and `AppTelemetryTransitions` on pinned JDK17 CI.

## Significant limitation (explicitly NOT fixed)

No durable `GAP_BEGAN` / `PROCESS_KILLED` event is written when the app cannot run or a `SharedPreferences.apply()` write has not reached disk. The exported `sample_age_ms=unknown` and absence of event are **not a measured outage duration**; neither is proof that no display transition occurred. `ApplicationExitInfo` can be unavailable or incomplete and is not the privileged daemon lifecycle. The full ring keeps at most 64 events and may discard evidence of an older incident; its existence is not a complete fault trace.

The existing `limits` text is preserved exactly and remains conservative. An explicit durable gap marker would require new app runtime/persistence semantics, added overhead and a separate measured safety/privacy decision. **Not authorized** under the present test-only scope. This finding is an S1 evidence limitation, not a demonstrated cause/fix of [#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) or [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65).

## Gates / STOP

- [ ] JDK17 host diagnostics tests PASS at exact PR head.
- [ ] Full Android emulator CI and CodeQL PASS at exact integration head after staging; re-run if SHA changes.
- [ ] Classify above as **HOST** only; **THOR:** NOT RUN. User-supervised read-only capture/recovery protocol needed before declaring physical acceptance.
- [ ] Do not increase always-on event collection, alter wake timing, turn on/off the panel, retry unsafe sleep, access production signing or publish a release.
- [ ] `main` / v1.7.3 unchanged. Existing #94 handover physical acceptance remains an independent mandatory release gate.

The next S1 deliverable, under the original roadmap, is an exact-APK **supervised physical capture plan** with explicit STOP and recovery criteria. Drafting that plan does not authorize execution.
