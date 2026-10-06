# Contributing

Thanks for helping improve Jesty Thor Fix.

## Before opening an issue

Include only the evidence needed to reproduce the problem:

- device model and firmware/build identifier;
- app version and whether it was installed as an update;
- selected display mode and fix ON/OFF state;
- the relevant `Q` and one-shot `V` result when available;
- a short, time-bounded log excerpt around the failure.

For unsupported devices such as the Retroid Pocket Duo, start with read-only probes. Do not copy Thor display IDs, CRTC IDs or power-write assumptions onto another device.

Remove device serials, account names, email addresses, local computer paths, tokens, and unrelated Android logs before posting.

## Pull requests

- Preserve the validated wake-repair timings unless new device traces justify a change.
- Keep clock telemetry read-only.
- Do not add signing material, binaries, device dumps, or generated build directories.
- Explain user-visible behavior and validation performed.
- Replace Jesty branding in redistributed forks unless permission has been granted.

Changes involving display power, boot behavior, or pending-repair cancellation require physical-device validation before release.

## Credit and provenance

Keep existing license, copyright and provenance notices intact. Forks should clearly mark their own modifications and use their own branding unless separate permission has been granted.

If a contribution or downstream project builds on a non-trivial investigation, test method or implementation from this repository, please preserve the technical credit and link back to the original work. See [NOTICE.md](NOTICE.md).
