# S3 — client trust-chain characterization

Programme **JTF-QUALITY-2026-10-17**, S3. Base:
`21d2077f82d6c24ffa8a3b0a153760819b958f1d`. Test-only. Detailed security
assessment stays owner-local; this is a sanitized coverage summary, not an
independent native security certification.

## Existing coverage and the specific gap

Real `SecureChannel` tests already cover peer/directory admission and conservative
stale-endpoint error classification. Real `DaemonIpcServer` tests already cover
queue saturation, untrusted queued clients, pre-dispatch errors and listener
recovery. Pure ACK and identity policies also have negative cases. No replacement
for those suites is added.

The missing link was actual `SocketClient.request`: activation tests replace
this class. The new fixture executes **unchanged `SocketClient`, `SecureChannel`,
`IpcPeerPolicy` and `DaemonResponsePolicy` together**, with fake native endpoints.

```powershell
./scripts/test-secure-channel-host.ps1
```

HOST: existing **44 transport assertions** remain PASS; new **8 cases / 28
checks** pass. A valid fragmented UTF-8 first line is returned after one command
byte and flush; the socket closes. A positive EOF-terminated line is accepted
under the current contract. Unknown/untrusted peer metadata cannot reach command
or response bytes. Invalid/missing ACK, simulated read failure and write failure
cannot return success and close their socket. Timeout propagation is checked,
not actual timeout enforcement.

Two controls compile temporary source mutations: bypassing the real peer gate,
and bypassing client ACK validation. Both must fail their targeted rejection
assertions. Baseline classes are restored between mutations. Production sources
are never edited; fixtures cannot open native sockets or reach a vendor service.

## Quality impact and residual boundaries

The caller/transport/policy composition now has regression protection. A test of
the ACK string helper alone could not establish that the client actually invokes
it or closes resources when it rejects a reply.

EOF after a positive line is accepted by `BufferedReader.readLine`; there is no
new strict framing rule. ACK acceptance is not field completeness, authenticated
runtime identity, successful stop or successor READY. Higher-level policies
retain those responsibilities. Read-size limits, actual slow peers, native
queue/socket semantics, kernel credentials, filesystem races, singleton lock,
Binder/process-incarnation assurance and SELinux remain **UNKNOWN** at this tier.
No new demonstrated native exploit or runtime correction is claimed.

Exact-head source and combined CI/CodeQL are recorded on the PR and #95.
Generic emulator checks do not establish vendor/native security acceptance.
PHYSICAL NOT RUN. No main merge, signing, release, installation or Thor contact.
No changes to display, CPU, boot, updater, protocol, identities or trust policy.
Independent privileged-boundary review and affected physical gates remain open;
the original S4 characterization-first workstream follows without treating these
host results as a release approval.
