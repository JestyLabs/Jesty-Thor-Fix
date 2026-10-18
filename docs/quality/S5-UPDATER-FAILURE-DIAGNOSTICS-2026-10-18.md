# S5 — retain a sanitized diagnostic assertion reason

Programme **JTF-QUALITY-2026-10-17**, independent S5 evidence increment. Base:
`1163a45e60742bafa04bc15f6786a7d0dc4339a1`, whose full CI and CodeQL passed.
No dependency on the S4 runtime extraction.

## Observed gap

S4 source CI `39159948057`, attempt1 at
`03377ad160ad10ba8d397e8fc7eddde254e7884d`, passed host/unsigned build but failed
the existing Android updater diagnostic-cleanliness assertion during
`native-consent-accepted`. The job log preserved only a bare AssertionError;
the ephemeral local case log was not uploaded. The retained evidence cannot
identify which of TEST_ERROR, SNAPSHOT_ERROR or FATAL EXCEPTION fired, or its
exception type. That FAIL remains historical. Root cause and causal connection
to any product change are **UNKNOWN**, not assumed benign or flaky.

## Minimal failure-only evidence

Keep the **exact existing assertion predicate**, all deadlines, actions, log
queries and case-result handling. Only its failure message gains a fixed schema:

- Three marker booleans, identifying which existing rejection condition fired.
- Strictly allowlisted exception type labels; never arbitrary exception text.
- Up to eight own-fixture source filename/line labels from a fixed class set.

No raw log, stack text, user/device/process identifier, URL, arbitrary class name,
path or state is emitted. No new query or diagnostic output occurs on success.
The existing local raw case-log behavior is unchanged; it is not newly published.
The schema classifies available log evidence, not cause, native timing or safety.

```powershell
python tests/test-updater-e2e-timeout.py
```

HOST PASS: four existing timeout-redaction tests plus three targeted checks of
the new failure path: fixed schema and redaction, unknown/oversized labels, and
the **real `Suite.case`** with an inert call endpoint. The latter must still raise,
record FAIL and expose the sanitized category/type/frame without the injected
secret. Native Android updater execution remains a separate source/combined CI
gate, never replaced by these host tests.

No source application change, assertion relaxation, deadline extension, ignored
failure, production signing, main merge, physical device or release. S5
administrative enforcement remains separate: its read-only audit found Android
and review-enforcement gaps; no required check, reviewer rule, environment or
secret is changed here. Exact-head source and combined CI/CodeQL are recorded on
the PR/#95. This does not retrospectively recover the lost original diagnostic.
