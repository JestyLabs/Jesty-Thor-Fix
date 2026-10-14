# Privileged IPC review — offline trust boundary and evidence

Source: PR #67, 2026-10-13. Source analysis plus production listener with host-only Android socket fakes; NOT an independent penetration test, kernel socket/SELinux audit, or hardware acceptance.

| Stage | Production boundary | Established offline | Still not proven |
| --- | --- | --- | --- |
| Socket directory and instance lock | SecureChannel.listen, IpcPeerPolicy | Source checks filesystem socket owner, mode 0600, inode type and exclusive file lock. All 512 POSIX permission cases plus UID extremes in host policy test | Native inode replacement race, SELinux context, kernel enforcement |
| Daemon identity | SecureChannel.connect | Client checks root peer UID; text response ACK is NOT authentication | Real Android LocalSocket peer credential behavior and vendor bridge |
| App identity | SecureChannel.isTrustedApp | Daemon checks app UID before reading the one-byte request; real server path covered with fake peer identity | Real kernel peer UID and native directory ownership |
| Bounded queue | DaemonIpcServer | Host tests actual 2 workers, 4 queued clients, seventh refused | Linux descriptor exhaustion, genuine 1500ms socket read timeout |
| Rejected client failure | DaemonIpcServer | Old listener could be terminated by IOException from socket.close on overload; reproduced in host fixture. Narrow catch now preserves accept loop | Unexpected non-IO runtime errors, kernel/resource pressure |
| One-byte command | DaemonCommandHandler and server | EOF, unknown byte, extra bytes, I/O and handler failures through real server with fake commands | Real privileged command side effects, boot hold and SELinux |
| Reply parsing | SocketClient + DaemonResponsePolicy | Strict positive ACK token instead of prefix matching | Actual daemon protocol regressions on tested Thor |
| Stale endpoint and daemon replacement | SecureChannel.removeStaleSocket + boot/handoff models | Read-only source inspection and model tests | Process death/race/Android framework restart with native socket |
| Publication pipeline | Main-only protected environments and PR emulator | Static workflow contract plus historic owner-authorized policy snapshot | Live admin branch protection cannot be re-read by current integration (403); two-person self-review decision remains open |

Priorities: continue offline Android LocalSocket/rejection/timeout integration if available without root, preserve 2+4 bounds and 1.5s timeout, request independent privileged review, combine all necessary device-path checks into the final owner session. Never test IPC by forcibly stopping vendor services, changing SELinux, or replaying arbitrary commands on the Thor.

Outcome: one reproducible listener-availability bug addressed; source-level trust boundaries documented. Do NOT mark independent security review, native identity or physical handoff as PASS.

### SecureChannel real-class host preflight (test fixtures only)

`scripts/test-secure-channel-host.ps1` compiles the production `SecureChannel`
and `IpcPeerPolicy` against disposable Android/Os doubles. It characterizes
root-only daemon peer acceptance, app-UID authorization and negative/error
branches before `listen()` opens an instance lock. The fixture deliberately
makes `Os.open()` fail before obtaining a real descriptor; it never binds,
unlinks, signs, installs or touches a device. The no-follow and close-on-exec
flags requested by production source are asserted.

**Evidence boundary:** no native filesystem lock was acquired; no real
`LocalSocket`, kernel peer credentials, SELinux label, overloaded connect,
stale pathname deletion or post-bind inode verification was exercised.
Stale-socket cleanup still treats any `IOException` from its reachability
probe as evidence of disconnection; notably, permission errors and
connection-refused errors are not distinguished. Preserve this as an open
fail-closed design review before changing the recovery path, because Android
vendor implementations and reboot recovery require explicit acceptance.

### Transport and predecessor test gates — 2026-10-14

Owner-local API 33 transport experiments use the unchanged production channel
and a synthetic responder, with SELinux Enforcing. Kernel peer rejection,
private socket/lock metadata and controlled stale recovery were observed. The
production command handler and vendor bridge are outside this fixture.

The initial timeout criterion accepted any read IOException within a broad
window. Its replacement keeps the peer alive for at least 1500 ms, acknowledges
read readiness, records the exact observed API 33 error and requires a narrow
100–399 ms window. A 200 ms client read failed at 204 ms; a 5000 ms control read
received its response at 1502 ms without an exception. Nine host criterion
checks reject unrelated errors, the former 500 ms counterexample and an
uncharacterized exception type. This validates the characterized client read,
not the production server's 1500 ms timeout, queue pressure or total deadlines.

The existing `PreviousSecureDaemonIdentityTest` now applies 51 additional
negative checks to old, release-candidate and stable identities: protocol,
watcher, BOTH/CRTC, PID, acknowledgement and null-input gates. All 119 assertions
pass in the host suite. Temporary source controls confirm a narrow future
allowlist hypothesis retains these checks, while an identity-specific bypass
is rejected even after revising the future positive expectation. Neither
temporary policy was applied to production; stable 1.7.1 remains excluded.

Raw captures, temporary policies and native fixtures stay owner-local outside
Git. This follow-up does not dispose the separate CodeQL alert or establish
independent security review, native vendor handover or physical acceptance.
