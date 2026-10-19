# Local TLS fixture shutdown investigation — 2026-10-13

Baseline: PR #67 at `ce558360026c257cce008bb73fa8abb1c41da835`.
The prior metadata step retained Windows native TLS recovery failures while its
independent Linux CI matrix passed. This investigation changes only the fixture,
its protocol regression and CI coverage. Product runtime and TLS policy stay unchanged.

## Observed defect and controlled comparison

The fixture closed its TLS socket directly after sending complete HTTP responses.
A strict real TLS client rejected four completed-response cases with ragged EOF:
fixed-length, chunked, HTTP 503 and redirect. The intentionally truncated response
remained an expected abrupt EOF. That protocol defect is independently reproducible
on the host, without Android or the updater.

Prespecified Android status-failure/recovery trials used the unchanged APK and
the same disposable AVD, default TLS settings and session-ticket configuration:

| Block | Fixture close after complete recovery response | PASS | FAIL |
| --- | --- | --- | --- |
| A | Direct socket close | 4/6 | 2/6 |
| B | Explicit TLS `close_notify` | 6/6 | 0/6 |
| A return | Direct socket close | 3/6 | 3/6 |

The failed trials retained their actual statuses; no automatic retry converted
FAIL to PASS. Observations include unexpected stream EOF and native
`DECRYPTION_FAILED_OR_BAD_RECORD_MAC`. All blocks negotiated TLS 1.3 with
`TLS_AES_256_GCM_SHA384`; recovery connections reused the TLS session, including
all six successful B trials. Certificate/hostname verification was preserved.

A separate host-only probe passed 40 response/recovery pairs, but its attempted
session reuse was not negotiated. It is therefore not equivalent to the Android
resumption comparison and does not close that gap by itself.

The comparison supports fixing the fixture's incomplete TLS shutdown. It does
not isolate the exact record-layer mechanism producing the MAC error or establish
behavior on other runtime versions, firmware, networks or production endpoints.

## Scoped fix and failure boundaries

`TransportServer.respond` reports whether its HTTP response is complete. Completed
responses perform orderly TLS shutdown and wait at most two seconds for the peer's
acknowledgement; timeout/error evidence is recorded before closing the socket.
Framing, payload, status, redirects and verification inputs remain unchanged.

Deliberate incomplete-response modes retain their abrupt termination: truncated
body, mismatched size header without its body, response-header timeout and body
timeout. The native client's production 30-second read timeout is unchanged.
Default protocol/ciphers, session tickets, trust and hostname verification remain
enabled. No downgrade, cache clearing or retry bypass is added to the product.

The strict host regression covers six boundaries: orderly fixed/chunked/error/
redirect responses, preserved abrupt truncation, and bounded shutdown when a peer
does not acknowledge. It failed against the original fixture's four completed
responses and passes after this fix. The PR build job now enforces this regression;
the existing 13-case native HTTPS matrix remains an independent requirement.

Exact-final-head CI must pass before accepting this step. Raw diagnostic trials,
negotiation/events, logs and certificates/keys remain owner-local outside Git;
public evidence is the sanitized table and named protocol results. Production
APK/Dex/manifest, IPC, CodeQL #30, signing, publication and physical Thor are
unchanged. The PR stays draft and full dashboard/physical acceptance remains separate.
