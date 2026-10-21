# v1.7.4 and v1.7.5 — ongoing maintainer device testing

On 21 October 2026, the maintainer reported that ongoing testing of v1.7.4 and v1.7.5 on the Thor was going well:

- No black screens or other issues observed during this testing so far.
- Background-service transitions worked normally, with no errors observed.

This is a maintainer-reported physical observation, separate from the automated host/Android evidence and the pending supervised S6 matrix. This update does not add captured APK identities, test counts, durations or performance measurements. The publication-time **NOT RUN** records remain historical facts; the observation is encouraging evidence, not proof that the underlying panel/DSI fault cannot recur.

## Planned v1.7.6 supervised validation

The maintainer plans to repeat the complete applicable physical battery together with the engineering agent for the planned v1.7.6 candidate. Identify the exact signed APK and installed origin before testing, then record update/service activation, BOTH/TOP transitions, sleep/wake, applicable Guard cases, diagnostics, recovery and performance results under the existing S6 gates. The new retained TOP handover requires its own physical case on an eligible available origin.

That session has **NOT RUN**. A new release name alone does not establish an artifact identity or hardware acceptance, and these observations do not authorize device contact, installation or reboot.

See [v1.7.4 validation](../VALIDATION-1.7.4.md), [v1.7.5 validation](../VALIDATION-1.7.5.md), [physical evidence index](../VALIDATION-EVIDENCE.md) and [the original quality programme](../ENGINEERING-QUALITY-ROADMAP-2026-10-17.md).
