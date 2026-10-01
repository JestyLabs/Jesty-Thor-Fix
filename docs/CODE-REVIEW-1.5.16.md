# Review disposition for v1.5.16

The supplied review and patch were assessed against the v1.5.15 source.
The patch applied after normalizing mixed line endings. One supervised
v1.5.16 Thor boot then reached READY at 65.479 s; this supports the
stale-socket diagnosis but does not establish repeatability.

| Review item | v1.5.16 disposition |
|---|---|
| 1. 30-second stale socket wait | Fixed in the launch decision model. The supervised boot identified a prior-boot socket and launched in 100 ms; unreadable/unstamped IDs still retain up to five seconds. |
| 2. Stuck daemon/boot states | Open. This patch does not add recovery from BOOT SAFETY TIMEOUT, exhausted watcher retries, lost display callback, helper failure, or a degraded same-version daemon. Do not claim self-healing. |
| 3. Unchecked root kill | Fixed in the compositor helper. Root UID and daemon command line must both match immediately before signalling. A live mismatched or unreadable process aborts with `DAEMON_IDENTITY_MISMATCH`; an absent or zombie process may proceed. |
| 4. Fixed sleep after kill | Replaced with bounded wait for process exit. If it remains alive, replacement aborts to protect the file lock. Android shell behavior pending. |
| 5. Frequent DRM debugfs reads | Open. The proposed compositor-jank link is unmeasured; optimize only after a measured trace. |
| 6. 20 ms watcher polling/reflection | Open. Needs an event-driven design and reliability tests. |
| 7. STARTING/false health failure/wake lock | Authenticated same-version boot phases are accepted as starting; non-handoff coordinator endings release the wake lock. Liveness after STARTING is still not independently watched. |
| 8. CPU OFF plus unset warning | Open UI issue; no CPU property behavior changed in this patch. |
| 9. Hardcoded Thor display IDs | Open compatibility issue. Do not infer support for another firmware. |
| 10. Fork frequency | Open. The helper avoids one repeated `pm path` call; the gate and dashboard fork patterns remain. |
| 11. Manual migration allowlist/TOP upgrade | v1.5.15 added to the exact allowlist. General migration policy and TOP upgrade UX remain open. |
| 12. Display callback retry | Open. A dead or absent display service still needs explicit re-registration/health. |
| 13. Socket path permission calls | Open hardening item; private app directory and UID checks remain the security boundary. |

Lower-priority debt also remains: synchronization audit of shared daemon
fields, bounded trace retention, shell-script testability, CI, and dashboard
telemetry cost. The trace now has PID, boot ID and source on each new mark,
but trace rotation is not part of this release. The changelog gap was closed.

The patch's helper had an additional failure path: when the old PID was still
alive but its UID or command line did not match, it could continue to the
successor launch. That path now aborts. This can leave `APPLYING CPU FIX`
held until the timed wake lock expires or manual intervention, so the
recovery gap in item 2 remains open. The supplied `DAEMON_MAIN` initially
labeled service start as receiver time; the smali receiver now passes its own
timestamp and the trace records both separately.

The [validation diary](VALIDATION-1.5.16.md) records one physical
boot and the remaining limits. The maintainer approved stable publication
after that supervised result.
