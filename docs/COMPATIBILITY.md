# Device compatibility

Jesty Thor Fix is currently a **tested AYN Thor app**. Support for another handheld is never inferred from a similar chipset or dual-screen layout.

## AYN Thor

Tested firmware family:

```text
Android 13
TKQ1.231222.001
```

| Area | Status | Evidence |
|---|---|---|
| True Bottom Screen Off | ✅ Tested | TOP mode physically verified with the lower DRM CRTC inactive |
| Sleep/wake repair | ✅ Tested | Focused TOP sleep/wake checks return the lower display to true-off |
| AYN Dashboard CPU Fix | ✅ Tested | Qualcomm property/restart path verified on hardware; low-load clock pinning no longer reproduced with the fix active |
| Cold-boot restore | ✅ Tested | Saved fixes restored on the tested Thor with the expected one-time framework/compositor recovery |
| Closed-Lid Wake Guard | ✅ Focused checks | Optional feature; available closed-lid checks passed on the tested device |
| BOTTOM-only mode | ⚠️ Limited | Not part of the current stable validation scope |
| Dock / external-display use | ⚠️ Limited | Safety exclusions exist, but physical dock coverage is still deferred |
| Other Thor firmware / revisions | 🧪 Test first | Hardware IDs and vendor behavior must be checked before making compatibility claims |

See [device validation](DEVICE-VALIDATION.md), [benchmarks](BENCHMARKS.md) and [release integrity](RELEASE-INTEGRITY.md) for the underlying evidence.

## Retroid Pocket Duo

**Research / testers wanted — not supported yet.**

The Pocket Duo is interesting because it is another Qualcomm dual-screen handheld, but Thor display IDs, CRTC IDs, lid inputs and vendor behavior must **not** be reused blindly.

The first phase is read-only:

- device and firmware identity;
- physical/logical display topology;
- refresh-rate and SurfaceFlinger behavior;
- available DRM/display state;
- lid/hall input behavior;
- Qualcomm system-load-check behavior.

Only after a device profile is physically measured should an experimental build be allowed to write display state.

See [Retroid SDM comparison](RETROID-SDM-COMPARISON.md) and the [current roadmap](ROADMAP.md).

## Want to help test another device?

Open an issue with:

- device model;
- Android / firmware build identifier;
- what behavior you are trying to reproduce;
- read-only diagnostics requested in the issue.

Please remove serial numbers, account details, local paths, tokens and unrelated logs before posting.

Compatibility testing is community research, not an affiliation with or endorsement by the device manufacturer.
