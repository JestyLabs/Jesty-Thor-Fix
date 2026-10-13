# Release metadata extraction — 2026-10-13

Baseline: PR #67 at `5c77c3477e1176ce6f7b944df2ad83b32c1b812f`.
Characterization commit: `a77edf2abcea94389a27a233a4f77c5afeafa233`.
The branch remains draft; this is an offline maintenance step.

## Resulting ownership

`UpdateReleaseCatalog` owns the immutable release record, JSON release selection,
trusted-field revalidation, seven-field cache codec and bounded UTF-8 response
reader. Version ordering, accepted asset names, URL and digest policy still
delegate to `UpdateVersion`. This component has no Activity, preferences,
installer, daemon, executor or network connection ownership.

`AppUpdater` retains HTTPS creation, timeouts, endpoint selection, stream and
connection cleanup, preference writes and eviction, UI/lifecycle, APK verification
and PackageInstaller. Download, signer, session, confirmation and cancellation
paths remain unchanged. Cache format, endpoint paths, the 1 MiB JSON response
bound and 64 MiB APK metadata bound are preserved.

This extraction makes no behavior or performance improvement claim. The existing
first matching asset policy is retained: an invalid matching asset is not bypassed
in favor of a later matching asset. Native JSON coercion and malformed-cache
handling are retained rather than silently replaced with a new policy.

## Characterization and failure behavior

The same test helper passed **36 cases / 100 assertions** before and after the
extraction on a disposable API 33 emulator. The pre-extraction execution requires
`AppUpdater`; the post-extraction execution requires `UpdateReleaseCatalog`, so
fallback cannot silently validate the old implementation in final CI.

Coverage includes missing/null/invalid digests, draft/tag/name/URL rejection,
positive/exact/over-limit size, unrelated and duplicate matching assets,
stable/prerelease and installed-version selection, native JSON coercion, Unicode
cache roundtrip and mandatory cache-field revalidation. The actual updater check
worker is also exercised against controlled response adapters:

- A failed status or malformed/read response disconnects; an opened stream closes.
- A failed check preserves cached release and last successful check timestamp.
- A successful check with no eligible release removes the cache and updates time.
- A successful eligible check replaces malformed cache with the same validated format.
- Exactly 1 MiB of UTF-8 response bytes is accepted; one additional byte fails.

Android `org.json` and SharedPreferences are real. HTTP responses in these 36
cases are controlled adapters, so this suite does not establish native transport
behavior. The separate [13-case native local HTTPS matrix](UPDATER-TRANSPORT-VALIDATION-2026-10-13.md)
and [16-case native lifecycle matrix](UPDATER-CONFIRMATION-DEDUP-2026-10-13.md)
remain required regressions, alongside real-class host suites and unsigned build.
Exact-final-head CI must pass before accepting this step.

Local retained native lifecycle passed 16/16; real-class host lifecycle passed
198 assertions / 33 scenarios and ownership coupling passed 137 / 18. The
unsigned product build contains 232 classes, including the new catalog and its
release record. The old nested `AppUpdater$Release` and all test helpers are
absent. Product DEX changes as expected for this extraction; byte identity from
the earlier transport-only step is not asserted here.

One retained local HTTPS execution reached its retry request after the expected
read timeout, but the native client reported `SSLProtocolException` with
`DECRYPTION_FAILED_OR_BAD_RECORD_MAC`; native consent did not appear. The updater
released its transfer/reservation, removed the partial download and created no
installer session. This is recorded separately from a complete PASS execution.
Its cause is not established and no product TLS policy or automatic test retry
was changed to bypass it. Focused reproduction, a fresh complete matrix and
independent CI results must be distinguished from that failed run.

The subsequent [fixture shutdown investigation](UPDATER-TLS-FIXTURE-SHUTDOWN-2026-10-13.md)
independently reproduces ragged EOF in completed fixture responses and compares
orderly TLS shutdown using controlled Android A/B/A trials. Its scoped fixture
fix does not alter the metadata extraction or erase the original failed evidence.

## Integration and remaining limits

The PR Android CI job runs the metadata suite between native lifecycle and native
HTTPS suites. Host/native compile lists include the new production component;
host tests retain their assertions and update only the reflected release class
name. The updater's no-daemon/no-shell boundary includes the extracted source.
The characterization Activity/helper and control receiver stay absent from
product DEX; the product manifest and signing/publication policy are unchanged.

This does not validate live release discovery, public DNS/CDN, production trust
chains, full dashboard readiness, vendor handover or physical Thor behavior.
CodeQL #30 and IPC review remain independent. No physical device, production
signing, release, force-push or merge is part of this change. A rollback can revert
this extraction without migrating persisted preferences or installer state.

Raw logs, process/session identifiers, APKs and disposable keys remain outside
Git. Publish only sanitized case summaries; dispose of the AVD and temporary
private keys after validation.
