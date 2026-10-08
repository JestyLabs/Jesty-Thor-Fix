# PServerBinder absence and compositor-handoff diagnostics

> Read-only / observation-first research. This change adds diagnostic events only when a
> **pre-existing** bridge transaction or handoff occurs. It does not poll the Binder,
> restart pservice, alter power/DRM, change CPU-fix behavior or release a new APK.

## Precisely what this patch records

**App process** (Android logcat tag `ThorDisplayAuto`):
- `bridge_result=MISSING;stage=SERVICE_BINDER`: reflection resolved, but
  `ServiceManager.getService("PServerBinder")` returned null.
- `bridge_result=TRANSACT_REJECTED;stage=TRANSACT`: a Binder handle
  existed, but `transact(0,...)` returned `false`.
- `bridge_result=SECURITY_DENIED|REFLECTION_FAILED|CLASS_LINKAGE_FAILED|RUNTIME_FAILURE|OTHER_FAILURE;stage=...`:
  an exception was mapped to a fixed, sanitized reason. These do **not**
  by themselves identify the vendor root cause.
- `elapsed_ms` is elapsed realtime for correlating events across logs in
  the **same boot**; it is not a wall-clock time or a durable event ID.

The bridge's **success path is unchanged**. No shell payload, Parcel content,
Android account details or exception messages are included in new failure events.
The existing broad log lines elsewhere in the app may still contain diagnostics:
keep whole log files private and manually sanitize excerpts before publication.

**Root daemon** (`BootTrace`, owner-local and sanitized):
- `HELPER_EXIT_OBSERVED` now records `exit`, `reason`, `action`,
  `boot`, `held`, `watcher_running` together, using state already observed
  to decide that action (no extra CRTC/composer/scheduler reads).
- Existing `HANDOFF_FAILED`, `HANDOFF_RECOVERY_BEGIN`,
  `HANDOFF_RECOVERED` / `HANDOFF_RECOVERY_FAILED` and
  `WAKE_UNLOCK_SENT` remain the follow-up events.

## Safe owner-operated investigation

1. Keep the current stable signed APK and root recovery plan untouched. **Do not**
   create an incident by killing `pservice`, SurfaceFlinger, the compositor,
   system_server, zygote or the existing Jesty daemon.
2. Confirm the intended Thor with `adb devices -l` and set `$Serial` locally to
   that device's serial. Pin every command to `adb -s $Serial`; never rely on
   an implicit default when multiple handhelds are attached. When an issue happens
   **naturally**, capture the private logcat buffer on your PC with
   `adb -s $Serial logcat -d -s 'ThorDisplayAuto:V' 'ThorDisplayDaemon:V' '*:S'`.
   Run the following separately (read-only; a failure to access a service
   is itself only a diagnostic observation):
   - `adb -s $Serial shell service check PServerBinder`
   - `adb -s $Serial shell pidof pservice`
   - `adb -s $Serial shell getprop init.svc.pservice`
3. Capture the approximate event order: `AutoService` intent/launch attempt,
   bridge result, existing daemon socket probe, helper exit (if any), recovery
   decision, final daemon health and actual top/bottom CRTC states from
   already-approved read-only collectors.
4. Treat **pservice process present** and **PServerBinder registered** as
   independent facts. A living process is not proof that Binder was published,
   nor does a missing service name identify why registration failed.
5. Store raw captures **outside the Git repository**, in an owner-controlled
   folder. Strip physical serials, account data, installed-package lists,
   private filenames, tokens, IPs, raw binary dumps and session IDs before
   posting sanitized summaries. See `SECURITY-PUBLICATION.md`.

## Failure interpretation

| Observation | What is proved | What is NOT proved |
| --- | --- | --- |
| `bridge_result=MISSING` on daemon-launch attempt | Framework lookup gave no handle at that moment | `pservice` crashed, the Android service table was permanently missing or a reboot is required |
| `bridge_result=REFLECTION_FAILED` | Reflection failed in app process | A target-SDK increase is the only cause |
| `bridge_result=TRANSACT_REJECTED` | Transaction was not accepted | Command executed or daemon replaced |
| `HANDOFF_FAILED` with `held=1` and watcher not running | The existing fail-safe selected HOLD instead of in-place display reconcile | The old compositor definitely restarted |
| `HANDOFF_RECOVERED` | Existing post-handoff gate completed according to the app | Every physical display state is correct without independent CRTC observation |

## Host coverage and remaining gaps

`BridgeFailureDiagnosticTest` tests classification of missing/unwrapped/wrapped
Java errors (including hidden-API/reflective errors). Existing
`HandoffRecoveryModelTest` covers the decisions; the additional fields
allow correlating real device traces to those decisions.

**Supervised normal-operation observations:** the exact signed diagnostic
candidate was installed in place with its installed hash verified and data
preserved. The maintainer confirmed a responsive dashboard, normal reboot and
normal lid/wake after reboot. Current-boot logs showed a fresh healthy daemon and
`BOOT_READY`; both physical CRTCs were active in the final observation. The
initial app launch reused the previous same-version daemon; the normal reboot
loaded the successor. This was an ADB replacement, not a PackageInstaller
end-to-end validation. Raw evidence remains owner-local.

**Pending failure-event evidence:** OS Binder registration chronology and bridge
process liveness during an actual failure, PackageInstaller replacement creating
the next healthy daemon, wake-lock ownership after a failed transition and the
physical top/bottom CRTC result during that failure. No bridge failure or helper
exit event occurred during the smoke test; failure handling remains host-tested.
Do not induce faults or wait indefinitely for an incident as a review gate.

**Stop conditions:** unexpected panel transitions, repeated compositor restart,
lost daemon identity/protocol, missing wake-lock release evidence, or logs
containing unexpected private data. Host CI and the supervised safe smoke
observations are complete; physical failure-event coverage remains an explicit
limitation for review.
