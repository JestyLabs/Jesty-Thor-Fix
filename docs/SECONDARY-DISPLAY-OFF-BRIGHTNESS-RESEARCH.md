# Secondary-display OFF brightness: correction feasibility

## Purpose and present status

This follow-up to the [completed BOTTOM wake-flash investigation](BOTTOM-BRIGHT-FLASH-AFTER-LID-WAKE-20261011.md) records the framework correction to investigate and the conditions for a future optional feature. It is separate from the integrated TOP pending-repair correction. It contains no runtime patch, firmware payload, supported toggle, installation procedure or release claim.

The archived firmware converts the secondary display's special OFF brightness input into a positive value before checking for the sentinel. Restricted interpretation of the extracted arithmetic produces approximately 0.505 before device-specific backlight mapping. This proves loss of sentinel semantics; it does not measure luminance, prove an accepted hardware command or establish the cause of the reported flash.

## Observations and limits retained from the investigation

- The first report concerns a brief lower-panel flash on opening the lid after sleep. An otherwise similar preceding cycle had no reported flash. Both panels recovered.
- A later report describes roughly one second of extra lower-panel brightness after a hardware-button wake. Its full configuration was not established.
- In the retained original filtered log, the application's closing-time ON operations precede wake by approximately 35 and 42 seconds. The log is not a complete brightness trace and cannot rule out other operations.
- Later controller brightness-reason messages are not optical measurements. They must not be joined to the original cycles as if captured in the same event/timebase.
- Firmware power, brightness, fade, caches and panel-enable gates can alter what becomes visible. The causal contribution of the conversion defect remains unmeasured.

## Candidate correction and invariant

The narrow correction belongs immediately before the secondary brightness curve. Each brightness component must preserve its OFF sentinel independently, including the SDR component. The ordinary secondary calibration must remain unchanged for ordinary values. Preserve the existing invalid-input handling and primary-display path.

Conceptually, for each admitted component:

```text
if value represents OFF:
    preserve OFF into the existing downstream conversion
else:
    apply the existing secondary curve and downstream conversion
```

This is a design invariant, not integrated executable code. Substituting zero for OFF is not equivalent: zero also becomes positive in the existing curve. The change must not introduce a new brightness curve, force panel power, change refresh settings or retry power operations.

## Why the current application cannot expose a working switch yet

The affected method runs inside Android's display service. The application daemon is a separate process and currently controls panel power, not this conversion. Loading or reflecting the same class in another process does not change the running service.

The power-state producer generates OFF whenever the screen is OFF or its fade level is zero, regardless of a normal user brightness setting. An upstream settings override therefore does not repair the conversion. A downstream brightness write before or after the application's own power call races with independent system writes and does not cover system wake paths that need no application ON operation.

An optional switch does not resolve those ownership and ordering problems. A framework-side integration mechanism must first be designed and validated. No supported deployment, removal mechanism or reboot-free activation is established for this prospective feature.

## Work to complete before implementation can be accepted

1. Independently reproduce the original producer, curve and downstream sentinel path from the exact firmware input, retaining hashes and private provenance.
2. Execute a minimal candidate correction against the actual extracted method, with the unmodified method as a failing control. Cover each brightness component independently, OFF/ordinary combinations, invalid values and unchanged primary/normal-secondary behavior.
3. Determine a narrowly scoped framework integration mechanism. Review privilege, compatibility, boot impact, partial installation, removal and recovery. Never infer support from a model name alone.
4. Characterize power/brightness order, cached values and independent writers. Do not claim that a sleep callback or a successful command submission makes the operation atomic.
5. If exposed through the application, default the option OFF. Distinguish supported, installed, enabled and effectively active states; unsupported environments must not show an effective protection claim. Confirm rollback behavior and activation requirements.
6. Before release, perform separately authorized acceptance on the target firmware with an exact candidate and known-good recovery. Correlate system state, accepted brightness operations and visible presentation where feasible. Test normal brightness controls, BOTH/TOP transitions, lid and button wake, SDR behavior and feature OFF/ON.

Offline analysis and host/emulator prototypes are permitted investigation steps. This document does not authorize device contact, firmware modification, system-service restart, production signing or publication.

## Delivery and claim boundaries

The first deliverable is a reproducible feasibility result: validated narrow correction plus supported integration/rollback design, or an explicit explanation of the unresolved mechanism. Keep the future implementation independently reviewable rather than adding a speculative brightness workaround to an unrelated release.

A validated implementation may claim preservation of the firmware OFF sentinel. Claiming that it fixes the observed physical flash requires corresponding physical evidence. If the flash remains unmeasured, document that limit even when the arithmetic correction passes.

## Evidence custody and validation of this proposal

Exact firmware, extracted classes, native disassembly, original logs and detailed binary provenance remain owner-local outside Git. Public engineering records contain only sanitized findings and acceptance criteria. Any portable private handoff must verify its inputs, distinguish redacted logs from originals, preserve required notices and state remaining identification in unmodified firmware.

This PR is documentation-only. Production source, APK inputs, version metadata, signing and release configuration are unchanged. No physical test or firmware patch has been performed for this proposal.
