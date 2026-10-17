## Summary

Describe the change and why it is needed.

## Behavior affected

- [ ] Dashboard only
- [ ] Telemetry only
- [ ] Display power
- [ ] Wake repair
- [ ] Boot/persistence
- [ ] Build/docs/assets

## Validation

List the checks performed. Display-power, wake-repair, and boot changes require physical AYN Thor validation before release.

## Safety

- [ ] No signing material, binaries, raw device dumps, serials, account data, or local paths were added.
- [ ] Clock telemetry remains read-only.
- [ ] Any change to the 700 ms / 400 ms repair strategy is supported by device traces.
- [ ] Jesty branding and third-party asset terms were respected.

## Roadmap execution (required for JTF-QUALITY-2026-10-17 work)

- Workstream: S0 / S1 / S2 / S3 / S4 / S5 / S6 / NOT APPLICABLE (justify).
- Objective, source SHA, affected files and explicit out-of-scope behavior:
- Pre-change characterization, negative controls and exact executed commands:
- Evidence classification: HOST PASS/FAIL/NOT RUN; EMULATOR PASS/FAIL/NOT RUN; PHYSICAL PASS/FAIL/NOT RUN.
- Consequences for BOTH/TOP, sleep/wake, boot, daemon, CPU, IPC, updater, signing:
- Remaining uncertainty, STOP conditions and recovery/fallback:
- Scope decision if altered (link to separate maintainer approval):
- [ ] Read root AGENTS.md, [quality roadmap](../docs/ENGINEERING-QUALITY-ROADMAP-2026-10-17.md), and [execution contract](../docs/ENGINEERING-QUALITY-EXECUTION-CONTRACT.md).
- [ ] One scoped workstream; no unapproved deviation or silently expanded predecessor/hardware support.
- [ ] No signing, real-device test, merge or release unless separately authorized and accurately evidenced.

These fields are review aids, **not** proof of compliance or substitutes for configured required checks and maintainer approval.
