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
