**v1.7.5 update (20 October 2026):** the exact published 1.7.4 predecessor is also admitted under the same guards. Four-origin host and eight installed generic Android/JNI cases passed. [Release record](../VALIDATION-1.7.5.md). Physical acceptance remains NOT RUN. The v1.7.4 implementation record below retains its original three-origin scope.

# S2: proposed process-bound published-predecessor retirement

Implementation increment prepared 2026-10-19 and **released in v1.7.4/code84 on 20 October**. [Final release validation](../VALIDATION-1.7.4.md) records host, installed generic Android/JNI, CodeQL, signing and publication PASS. Exact released-APK Thor/vendor-bridge acceptance remains NOT RUN. The architecture and residual device gates below retain their stated limits.

## Scope and ownership

The owner-approved supported live origins are exactly **1.7.1, 1.7.2 and 1.7.3**.
Later versions and suffixes are not admitted by a broad version comparison.
The existing unbound `LiveHandoverPolicy.safePid` still rejects real code76.
Only the internal retained-process route can accept its genuinely absent
`Q.runtime_version`. No response is rewritten to insert that field. A present
but wrong or duplicate runtime identity is still rejected.

`PServer.stopLivePredecessorIfSafe` serializes retirement attempts. Its return
value now means a **fresh confirmed-exit receipt**, not a submitted numeric
kill. Other same-version stalled recovery and legacy stop paths are unchanged;
this increment does not certify or rewrite those separate paths.

`LiveRetirement` owns one request, a random 128-bit identity, a private filesystem
control listener and a 15-second monotonic transaction-admission deadline on
each side. Native image checks have their own one-second bound; cleanup can
finish after the admission deadline. No new commit/signal is admitted after it.
`R` is a fixed one-shot root helper entry point; its only arguments are the
request identity and three boolean intent bits. The bridge still supplies no
execution attestation. There is no caller-selected PID, socket path, command,
signal, persistent receipt or process scan.

`RetirementNative`/the source-built native library own socket credential checks,
bounded framing, descriptor handoff, process-descriptor acquisition, SIGTERM and
exit polling. Missing support or load/permission errors are denial, never a
numeric-PID fallback. NDK/ABI loading and deployed policy still require owner
validation; HOST success cannot establish them.

## Protocol and process lifetime

1. The app binds a new private app-owned endpoint before submitting the helper.
   Both endpoints check kernel peer credentials, and all frames bind the same
   fresh request identity. Stale paths are not unlinked to make a request work.
2. The app opens **four** root-authenticated legacy sockets, one per I/Q request,
   and hands them to the helper with SCM_RIGHTS. The legacy daemon sees the
   original connecting **app UID**, not root. No process/thread changes UID.
3. Before the first I byte is sent, the helper obtains a descriptor from that
   socket's kernel peer. An I response produced only after this acquisition is
   essential: credentials plus a numeric PID alone cannot close the pre-bind
   exit/reuse window. The descriptor stays open through both I/Q rounds, signal
   and observed exit. All four connections must match the same live root peer.
   The existing fixed `app_process / D` command-line admission is retained;
   unreadable or changed process image is denial, not a reason to scan for a PID.
4. Both rounds validate actual published replies: exact known version, I.pid
   equal to the kernel peer PID, READY, running watcher, BOTH and confirmed CRTCs,
   matching desired/effective display/CPU/Guard, no held action or forbidden
   lid/dock condition, strict duplicate rejection. Dynamic age/sample fields are
   not immutable identities. Changed second-round state is not ignored.
5. PREPARED is not exit. The app sends COMMIT only while its request is current.
   Before signalling, the helper checks that the process descriptor is still
   live and the app control channel is connected without pending cancellation.
   Only descriptor-confirmed exit permits EXIT. The app requires the exact
   nonce/PID receipt and successful endpoint cleanup before returning permission.

Cancellation is honored at every Java stage and bounded native wait. COMMIT is
the irreversible-operation boundary: cancellation after a submitted signal
cannot resurrect the predecessor, but a lost/cancelled receipt still forbids
successor launch. There is no automatic retry of retirement. A process dying
before binding, before the validating request, or before signalling is not a
successful retirement receipt.

App-side observation waits allow the legacy Q read budget and native identity
checks to complete; they do not impose a one-second control-frame deadline on
an outstanding 2500ms Q. This changes no existing display/boot/wake timer.

## Activation remains separate

`AutoService`, activation persistence, boot coordinator, singleton lock and
dashboard state logic are unchanged. After confirmed old exit, the service
still requires absence of a responding predecessor and launches the successor
**held**, with its existing boot gates. A fresh authenticated same-version,
same-intent **STARTING** daemon may persist live-activation permission, as before.
This is not final acceptance and is not dashboard ACTIVE. Actual eventual
**READY** must be separately observed. Failed durable writes remain blocked.

Failure before stop leaves the old daemon alone. Signal without an exit event,
or exit without a usable receipt, leaves live activation held and requires the
existing normal-restart recovery. Do not clear data, edit markers, install a
lower version, repeatedly kill processes or bypass the restart fallback.

## Reproduction and limits

From the offline bundle, with an existing JDK:

```text
python3 work/scripts/test-retirement-host.py --negative-controls
python3 work/scripts/test-retirement-activation-host.py
python3 tools/run_published_probe.py
```

The first compiles unchanged supplied published command/state producers and the
real candidate controller/policy, with inert hardware and OS boundaries. Its
compiled second-Q and exit-proof removals must fail behavioural assertions.
The activation fixture exercises the real service/persistence/dashboard chain.
Neither is a native process-incarnation, SELinux, firmware, ARM64 or physical
display proof. Owner fixtures are under `tests/android/retirement/`.
The return's `results/TESTS.json` and logs, not this procedure, record what
actually ran. Android builds/execution, installed-library loading, physical and
performance acceptance, independent privileged-boundary review, admin
enforcement and release approval remain separate gates.
