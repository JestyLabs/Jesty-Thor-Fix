# Updater HTTPS transport validation — 2026-10-13

Baseline: PR #67 at `f7ea414afddb2bca7f03fbc4143d8002c394ab7a`.
The previous native 16-case updater suite passed locally and in exact-head CI.
This next step tests production download/verification code using Android's real
HTTPS client, HTTP framing and TLS against controlled local responses.

## Test boundary

The disposable test APK retains the native HTTPS URL handler. A test-only routing
adapter delegates to that native handler with an explicit per-connection proxy
and fixture trust anchor. The platform hostname verifier remains intact. The
terminating fixture binds only to host loopback and never forwards a request or
resolves/contact its requested authority. Supported authorities are fixed fixture
targets. No certificate is installed in the PC or Android system trust store.

The initial fixture used process-global proxy/trust configuration. A failed
certificate case recovered updater ownership, but its next attempt did not reach
the local server. That run is retained as a fixture failure; the routing adapter
now supplies proxy and trust explicitly on each native connection, rather than
depending on process-global settings. No product transport or trust policy was
changed to accommodate the tests.

Source release metadata stays cached; the initial URL must still pass the exact
repository/tag/asset validation. The download keeps production HTTP status,
HTTPS, declared/actual size, SHA-256, Android archive/version/signer checks and
PackageInstaller consent. The test manifest declares INTERNET for these connections;
the product manifest is unchanged. Fixture routing, certificate material and
server are absent from product source/DEX.

## Matrix and recovery

- Declared-length response cut short after partial bytes.
- Complete chunked response shorter than the authenticated release size.
- Content-Length different from the release metadata.
- Unknown-length/chunked response exceeding the release size.
- Actual HTTP503 response over TLS.
- Actual read timeout after response headers/partial body.
- Actual read timeout while awaiting response headers.
- HTTPS-to-HTTP redirect rejected without a plaintext request reaching the fixture.
- Redirect loop rejected after a bounded number of native requests.
- HTTPS redirect to a hostname not covered by the fixture certificate rejected.
- Untrusted certificate rejected during handshake, before an HTTP request.
- Valid HTTPS redirect completes verification and reaches native consent.
- Valid unknown-length/chunked body completes verification and reaches native consent.

Each failure must preserve installed version, create no native installer session,
release transfer/UI ownership and remove the partial APK. It then retries using
the same app process and unchanged client trust: a valid response must reach
native consent and cancellation must cleanly terminate that session. TLS request
evidence distinguishes an actual retry from a stale snapshot or file adapter.

The 30-second production read timeout is exercised without shortening it. The
TCP connect-blackhole/15-second connection timeout is not exercised; no firewall
or host-network rule is altered to simulate it. The server never approves an
install, and test inputs never enter the physical-device application.

## Limits

**Local results:** 13/13 TLS cases passed, including 11 same-process failure
recoveries and two valid flows to native consent. The fixture observed zero
plaintext HTTP requests. Host lifecycle (198 assertions/33 scenarios), dashboard,
ownership and release/build-boundary checks passed. The unsigned product build
contains 231 classes and its DEX is byte-identical to the prior deduplication build;
no product runtime source, product manifest or signing policy changed. Final-head
remote CI is an independent gate, not inferred from these local results.

Real local TLS, HTTP framing, timeout and redirect handling are tested; public
release discovery, live DNS/CDN, proxy authentication, production trust chains,
certificate rotation/revocation and every firmware/client variant are not.
Cached metadata and the permissive fixture Host cannot validate full-dashboard
readiness or physical daemon handover. This is not Thor or release acceptance.

Raw requests, logs, session/process identifiers, APKs and private fixture keys
remain owner-local. Public results report only closed-form case statuses.

The emulator reaches host loopback through its documented
[network address alias](https://developer.android.com/studio/run/emulator-networking-address).
The native client exposes the ordinary
[connection/redirect API](https://developer.android.com/reference/java/net/HttpURLConnection);
results here are observations of the tested API33 implementation, not a claim
that all Android variants behave identically.

Runner: `scripts/test-updater-android-transport.py`; setup and base APK build:
[Android updater harness](../tests/android/updater-e2e/README.md).
