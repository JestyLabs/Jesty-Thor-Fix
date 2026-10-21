# S2 — exact published 1.7.5 predecessor for the 1.7.6 candidate

This separate increment extends the [retained-process transfer](S2-TOP-RETAINED-HANDOVER-2026-10-21.md)
to the exact published 1.7.5/code85 predecessor. The candidate identity is
1.7.6/code86; no changed code85 artifact is an installation or distribution candidate.
It depends on the corrected S2 transport and recovery work in PR #129.

## Scope and provenance

Only the literal published identity 1.7.5 is added to the existing two predecessor
policies. The public stop gate remains BOTH-only. TOP admission remains restricted
to an authenticated retained process with matching intent, confirmed CRTC state,
terminal repair, stable mode/generation, both I/Q observations and confirmed exit
before a held successor. No native operation, signal path, timeout, display/boot
action or same-version recovery policy changes.

The eight producer/server fixtures are copied byte for byte from published source
commit `576919815bc1f0c54e5d399a8db8fcba507bfded`. Their hashes are added to
`tests/retirement/published/SOURCES.json`. Materialization verifies the complete
frozen file set and hashes before applying the existing namespace-only transform.
Current main is not substituted for a historical producer. Before admission, the
new actual published producer failed at the expected predecessor refusal.

## Validation and remaining gates

HOST activation now compiles the real candidate `DaemonIdentity` instead of a
fixed-version identity stub. The current-version fixture and successor use that
identity; published 1.7.5 is an update origin and is denied as same-version recovery.
The full frozen-producer tests cover all five origins, including the existing
negative controls for process/exit, second observation, TOP state and public gate.

The requested Android matrix has 24 distinct cases: twenty update cases across
five origins, native/unavailable support and BOTH/TOP, plus four recovery cases
using the actual current 1.7.6 producer. The report must contain the complete
unique matrix. JNI, process exit, preferences and owned parent waits are real in
that tier; panel/CPU/READY and prior activation inputs remain synthetic. Vendor
Binder and Thor are not validated by HOST or generic Android.

Local HOST and compile-only checks, exact-head CI/CodeQL/Android and independent
review are separate evidence. Physical acceptance must use the exact signed
1.7.6 artifact, cover the real 1.7.5 upgrade in BOTH/TOP where legitimately
available, and retain the supervised STOP/recovery procedure. No signing,
installation, merge, publication or physical PASS is implied by this draft.
