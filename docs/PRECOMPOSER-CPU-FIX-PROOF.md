# Pre-composer CPU Fix feasibility — verified engineering conclusion

**Research status: CLOSED for the tested stock AYN Thor firmware.** The adopted solution is physically validated and shipped; this is not an open release blocker.

## Question investigated

Could the CPU Fix become effective during cold boot **without** replacing the Android display component, using only a normal installable app and the Thor's existing permitted privileged capabilities?

## Findings

**The supported stock-firmware setup does not provide a verified, safe app-controlled opportunity to apply the setting before the first relevant display-component initialization.** The tested vendor display behavior also keeps initialization state for the lifetime of that component, so writing the setting later does not by itself change its effective behavior.

A controlled **single recovery of the display component** is therefore necessary when applying the change after that initialization. The production implementation moves the required boot recovery into the normal startup period, eliminating the earlier disruptive *late second recovery* on the tested configuration.

The project deliberately avoids modifying vendor/system partitions, impersonating hardware variants, or using an unverified early-boot hook. Those are not reasonable trade-offs for an optional, installable device utility.

## Evidence and release acceptance

- The feasibility investigation combined read-only hardware startup observations with own-code ordering and failure-state tests.
- The early recovery was implemented with bounded, boot-scoped attempt provenance and a conservative no-repeat policy.
- The path was physically exercised on the AYN Thor and shipped in v1.6.0.
- The exact signed v1.7.0 successor separately passed the supervised boot, daemon continuity and CPU Fix recovery checks.

**Result: PASS for the chosen safe app-level design.** A firmware-integrated alternative could change this constraint in future, but no additional speculative boot modifications are justified by the evidence on the supported version.

Public verification records:
[early-recovery release evidence](RELEASE-NOTES-1.6.0.md) ·
[exact v1.7.0 acceptance](VALIDATION-1.7.0-PENDING.md) ·
[physical evidence overview](VALIDATION-EVIDENCE.md).

## Publication boundary

This document records the **behavioral conclusion and release decision**, not copies of proprietary firmware initialization files, internal vendor code, extracted symbols or disassembled binaries. Details unnecessary to independently understand the product-level behavior are deliberately excluded from the current public-facing document.

The repository's historical Git revisions are not modified by this documentation update. See [public evidence policy](PUBLIC-EVIDENCE-POLICY.md).
