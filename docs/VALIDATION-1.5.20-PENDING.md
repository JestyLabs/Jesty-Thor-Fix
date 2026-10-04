# v1.5.20 daemon launch repair candidate

**Status (2026-10-04): host-only, unsigned, not installed.** The Thor is back
on v1.5.17 in visually confirmed BOTH. Do not start a 1.5.20 boot test until
its in-place daemon handover has passed.

## Reason for this candidate

The supervised 1.5.19 installation stopped the authenticated 1.5.17 daemon,
then submitted two new-daemon launch commands through the vendor bridge.
Neither produced a successor. No compositor or kernel restart occurred, the
two CRTCs stayed active, and a data-preserving reinstall of 1.5.17 restored a
healthy daemon. Details and evidence pointers are in
[the 1.5.19 diary](VALIDATION-1.5.19-PENDING.md).

The 1.5.19 bridge command was around 359 characters, compared with around 239
in 1.5.17. The bridge may truncate or reject long commands, but that has not
been proved. v1.5.20 keeps the log-file link/owner guard, compresses the four
boot timing fields into one validated `JT` environment value, and rejects any
generated launch command longer than 255 characters before calling the
bridge. A host test checks the worst case at 255 and round-trips the trace
fields. No display, boot gate, CPU property or Wake Guard behavior changed.

## Host evidence and gates

- `scripts/test-boot-lid.ps1`: passed, including `DaemonLaunchScriptTest`.
- `scripts/test-dashboard.ps1`: passed.
- Unsigned `build.ps1`: compiled and zipaligned versionCode 69 / 1.5.20;
  SHA-256 `2D923676BC92094ECA569DAE792069BC2A52D2B67D503AE263C5B0B597A292EB`.
- **Outstanding:** security review of the launch script and shell semantics;
  signed build with the established certificate; hash/signature/alignment and
  prepublication scan. The unsigned APK must not be installed.

## Minimum supervised device check after signing

1. Recheck BOTH physical image, both CRTCs, v1.5.17 healthy daemon, boot ID,
   compositor PID, CPU property and saved preferences. Keep the exact signed
   1.5.17 APK ready for the same data-preserving rollback used today.
2. Install signed v1.5.20 once in BOTH and open the app. Wait for one new
   authenticated root daemon and `READY 1.5.20`. Confirm its socket, identity,
   saved controls, CPU state, CRTCs and unchanged boot/compositor PIDs. If the
   daemon does not appear promptly, stop; preserve evidence and reinstall
   1.5.17 without clearing data. Do not repeat blind launches.
3. Only after that passes, resume the 1.5.19 combined feature sequence:
   BOTH/TOP, wake repair, owner-observed Wake Guard, then at most one
   supervised TOP cold boot with both visual phases and the boot trace.

The command-length explanation remains a hypothesis until the signed Thor
trial succeeds or a root-side shell error is captured. Even a successful
handover alone does not validate the 1.5.18/1.5.19 runtime paths or the
second visual boot latency.
