# S1-10 — display failure outcomes

Programme: **JTF-QUALITY-2026-10-17**, S1-10. Base integration:
`9d1edcb9898571fe7540055a066699259add7671`. Test-only characterization;
production, smali, timing, package identity and permissions are unchanged.

## Gap and evidence

The existing coordinator suite rejects stale readback (S1-09), but its vendor
double always returns success. It does not execute the reflection wrapper in
`DisplayHardware`, failed-apply admission, confirmation expiry or interruption.
The new tests exercise those boundaries, rather than repeat the decision matrix.

Run with JDK 17 and PowerShell:

```powershell
./scripts/test-display-coordinator-host.ps1
```

| Real class / boundary | Characterization |
| --- | --- |
| `DisplayHardware`, inert reflected Android endpoints | ON/OFF ordering; missing token performs no property write; failed property write performs no power request; vendor exception attempts rollback of a known previous property; failed rollback returns false with unresolved logical state; unknown/unreadable previous property is not invented as a rollback value. |
| `DisplayActionCoordinator`, scripted Settings/CRTC/clock, inert hardware | Apply failure cannot commit a preference, generation or success receipt; expired CRTC confirmation returns unconfirmed; interruption retains the flag and rejects success; watcher retries are suppressed at 1499 ms and admitted at 1500 ms. |
| Pending repair, real coordinator with scheduler double | Failed and throttled attempts retain pending status without completion; a subsequent admitted, confirmed OFF completes once; a later display-on note cannot extend that completed generation. |

Local HOST: **8 hardware cases / 36 checks**, **6 coordinator cases / 39
checks**, plus the unchanged **139-check** coordinator baseline and **13 cases /
126 checks** of S1-09. Three new controls compile temporary mutations and must
fail at the intended assertion: missing property rollback, ignored apply failure,
removed retry admission. Existing S1-09 controls remain enabled.

The hardware class is compiled unchanged, independently of the coordinator's
hardware double. Its reflection targets are inert fixtures, never Android or a
vendor service. The coordinator still uses the explicitly documented temporary
Settings-read adapter from S1-09; production admission and confirmation logic is
not rewritten. Separate JVMs prevent fixture state leaking between suites.

## What this improves and what it cannot establish

This supplies executable regression protection for failure reporting, rollback
attempts and retry admission. A boolean vendor-command return alone does not
produce a successful coordinator receipt.

The **1200 ms deadline begins after synchronous `DisplayHardware.apply` returns**.
A synthetic 5000 ms command duration followed by valid CRTC confirmation is
accepted by current code. The deadline is a readback window, not an end-to-end
vendor-call timeout. There is a same-key retry interval, **not a proven global
retry-count limit**. Tests retain these boundaries without changing policy.

Successful rollback of a property does not prove physical rollback. Failed
rollback can leave the logical property unresolved; the wrapper reports failure.
Native reflection behavior, vendor blocking, actual scheduler timing, watcher
lifecycle and panel presentation remain **UNKNOWN** at this tier. Android emulator
CI does not execute these host-only failure fixtures on vendor hardware.

No explanation or fix of #84 or #65 is claimed. These remain separate open
observations. No production change, signing, release or device test. Any physical
affected-path acceptance still requires the programme's separate owner gate.
