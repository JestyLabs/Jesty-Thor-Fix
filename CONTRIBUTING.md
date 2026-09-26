# Contributing

Thanks for helping improve Jesty Thor Fix.

## Before opening an issue

Include only the evidence needed to reproduce the problem:

- Thor firmware/build identifier;
- app version and whether it was installed as an update;
- selected TOP/BOTH mode and fix ON/OFF state;
- the relevant `Q` and one-shot `V` result;
- a short, time-bounded log excerpt around the failure.

Remove device serials, account names, email addresses, local computer paths, tokens, and unrelated Android logs before posting.

## Pull requests

- Preserve the validated wake-repair timings unless new device traces justify a change.
- Keep clock telemetry read-only.
- Do not add signing material, binaries, device dumps, or generated build directories.
- Explain user-visible behavior and validation performed.
- Replace Jesty branding in redistributed forks unless permission has been granted.

Changes involving display power, boot behavior, or pending-repair cancellation require physical-device validation before release.
