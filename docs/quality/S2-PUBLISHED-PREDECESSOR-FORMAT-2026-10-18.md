# S2 — published predecessor response compatibility

Programme **JTF-QUALITY-2026-10-17**, S2. This corrects a fixture assumption,
not the product's identity gate. The #94 compatibility review remains distinct
from native/physical acceptance.

## Source-backed finding

The official v1.7.1/code76 Q producer does **not** emit `runtime_version`.
The current `LiveHandoverPolicy.safePid` requires I.version to equal
Q.runtime_version. Consequently that published producer cannot authorize the
current live-stop path, even in an otherwise permitted BOTH state. It is
conservatively rejected. This is a compatibility/coverage finding, not an unsafe
stop, bypass or demonstrated blackout cause.

Pinned published sources:

- v1.7.1/code76: `f929ff910bfa8e5179a2c6a63948a18fc3d6b252`.
- v1.7.2/code80: `9fa9323c153044bae06b8b87ab09138ab5e25f3f`.
- Reader policy: candidate `03377ad160ad10ba8d397e8fc7eddde254e7884d`.

An owner-local reproducible JDK17 probe compiles the **unchanged actual
`DaemonCommandHandler`, `DaemonState`, `DaemonIdentity` and `DisplayDecisionModel`
from each published commit**, with inert telemetry/CPU/Guard endpoints, against
the actual candidate reader policies. It finds unique response keys in both
producers, and produces:

| Actual producer | Q runtime identity field | Base predecessor policy | Live handover policy |
| --- | --- | --- | --- |
| v1.7.1/code76 | Absent | Accepts synthetic PID | Rejects |
| v1.7.2/code80 | Present | Accepts synthetic PID | Accepts synthetic PID |

The strings and PID are synthetic; this is real producer/reader source execution,
not authenticated device traffic. The full probe, exact source hashes and output
are owner-local. No Android/vendor process is run.

The same Q identity requirement exists on current main before #94; duplicate-key
hardening did not introduce this missing-field rejection. A source-format review
does not establish the identity, signature or runtime response of a particular
installed APK. Those remain separately gated.

## Minimal correction within S2

The activation fixture previously invented Q.runtime_version for every version,
including code76. After removing that invented field, the old positive case
fails at its stop-submission assertion. That failing evidence is retained.

Replace the existing case with the truthful **no stop / no successor / not
authorized** result, and correct the corresponding pure-policy case. The
activation scenario total stays **65**, rather than adding cases to disguise a
false premise. The live code80 positive case remains intact.

```powershell
./scripts/test-reboot-activation-host.ps1 -RunNegativeControls
./scripts/test-boot-lid.ps1
```

A compiled fixture-only negative control reintroduces the invented field; the
corrected code76 case must reject it for an unexpected stop submission. Existing
admission/second-confirmation controls remain enabled. Production code, trust
requirements, predecessor allowlist and release metadata are unchanged.

## Decision and remaining gates

**HOLD any claim of published-code76 live compatibility.** Keep its conservative
prepare/reboot route; do not remove I/Q identity agreement to manufacture a
passing handover. Supporting that predecessor without reboot would require a
separately reviewed, generation-bound design and affected physical evidence.
No such runtime correction is implemented here. Documentation of actual
compatibility must be reconciled before release approval.

HOST source/fixture evidence only. Exact source and combined CI/CodeQL are
recorded on the PR and #95. Native transport, process retirement, code80 real
handover and successor acceptance remain UNKNOWN/NOT RUN at this tier. #84/#65
remain separate. No main merge, signing, release, APK install or Thor contact.

## Exact-reader comparison and documented fallback — 2026-10-19

The existing producer probe was rerun against exact main
`98ab7c342a028eaea8e43fb867900c0f9a3b5152` and candidate
`d9d304b284d07c42a78d44c13efaab15b6f3dab3`, varying only its pinned reader
revision. Both published producers emitted identical replies and admission
results under both readers: code76 denied, code80 admitted in the matching
synthetic READY/BOTH state, with unique fields throughout. The source hashes,
commands and outputs remain owner-local. No new fixture fields were invented.

The activation guide and v1.7.3 release notes now state the actual contract:
published v1.7.1 uses preparation followed by a normal restart; v1.7.2 can use
the guarded transfer when all checks pass. This reconciles the public claim with
the existing implementation, without changing runtime or authorizing a broader
predecessor set. Existing activation and negative-control suites are reused.

This is a HOST source-format comparison and documentation correction. Independent
privileged-boundary review, installed-artifact provenance and native/physical
handover acceptance remain separate gates on #101.
