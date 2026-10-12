# Early CPU Fix recovery — physical validation

**Result: PASS.** The early, single-recovery startup model was tested on a physical AYN Thor and became part of the released CPU Fix implementation.

## Goal and shipped behavior

The supported firmware needs the relevant display component to reinitialize before an already-running system can pick up a newly enabled CPU Fix setting. Rather than allowing an additional disruptive recovery after Android has finished most of its startup, Jesty Thor Fix coordinates **one bounded recovery during the natural boot phase**.

The implementation uses the device's existing permitted privileged execution capability and app-owned opt-in state. It retains boot-scoped attempt provenance, current-boot identity checks, state verification and a no-repeat rule.

The feature does **not** require modified vendor/system partitions or a general-purpose rooting framework, and does not make speculative changes to CPU frequencies or governors.

## Safety properties

- **Explicit ownership:** never overwrite or remove startup resources the app cannot prove it owns.
- **Opt-in:** the early action applies only for an eligible saved CPU Fix choice and verified current installation state.
- **At-most-one automatic recovery per boot:** uncertain or interrupted attempts cannot authorize an uncontrolled second display restart.
- **Verification before acceptance:** validate the observed post-recovery state and preserve recorded progress for the normal daemon.
- **Conservative failure:** an ambiguous result leaves the action unproven rather than claiming success.
- **Independent display recovery:** normal BOTH/TOP reconciliation and wake repair remain governed by their existing hardware checks.

These controls are exercised in the project's own host tests and the supervised device acceptance process.

## Physical findings

The 2026-10-07 Thor experiment observed an authorized early recovery, a verified successor display component, preservation of boot-scoped CPU Fix progress, and **no subsequent duplicate CPU Fix recovery**. The normal daemon continued to a ready state, and both display pipelines were observed active in BOTH.

The v1.6.0 implementation adopted this approach. The **exact signed v1.7.0** release subsequently passed a separate supervised reboot and recovery acceptance, confirming the production form of the feature.

**Engineering conclusion:** the one-recovery approach is the validated safe app-controlled behavior on the tested stock firmware. A zero-recovery alternative would depend on verified earlier platform support, not an optimization omission in this release.

See [v1.7.0 physical acceptance](VALIDATION-1.7.0-PENDING.md), [release notes](RELEASE-NOTES-1.7.0.md) and [validation evidence index](VALIDATION-EVIDENCE.md).

## Third-party disclosure

Public results describe the observed behavior and safeguards of **Jesty's implementation**. We do not need to republish third-party firmware binary identities, disassembly, internal symbols, exact extracted startup scripts or vendor-owned configuration to support these findings.

This change limits current public-facing detail; it is **not** a rewrite of historic Git objects. See [public evidence policy](PUBLIC-EVIDENCE-POLICY.md).
