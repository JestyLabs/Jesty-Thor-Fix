# Passive sleep research after diagnostics integration

This PR preserves the optional, user-armed *two-endpoint* energy study from the original #59 branch.
Its code now rests on current main (17bac625fa), including merged
#55 cogwheel and human-readable `AppDiagnosticPreview`, #58 DRM/CPU read-path
optimization and #61 RC identity/UTF-8 diagnostics correction. Old #55 app/CI files
were intentionally NOT carried across.

The unchanged V1 machine report is emitted when no trial was armed. On opt-in
trial completion, one bounded `passive_sleep` line is added, and the normal
human-readable diagnostics screen remains available. No extra telemetry loop,
daemon or CPU/display policy change is added by the feature. Awake portions
before/after sleep are INCLUDED in every comparison.

Known limitations: USB-connected or missing battery endpoints must yield
UNKNOWN/REJECTED, not invented device watts; Android elapsed-vs-uptime clock
difference is NOT a precise deep-suspend residency measurement. Old experiments
and synthetic fixtures are retained as research context, NOT proof of battery
life improvement or real panel energy.

Validation gates: current full host CI, new V1 compatibility tests, Python
energy analyzer fixtures, and later **owner-approved** device testing. Keep
this draft separate from #57 lid maintenance and ongoing RC2 physical acceptance.
No auto sign, no release publish, no production merge.

## Cross-check against corrected RC2 validation (2026-10-11)

Owner-supplied corrected RC2 `1.7.1-rc2/73` evidence, firmware .377,
provides a **CPU baseline only**, NOT a passive-energy-trial validation:

- 3 × nominal 180-second windows in each BOTH/TOP app-closed/app-open
  condition. Total 12 valid daemon CPU windows; all CPU units refer to
  **percentage of one core**, not Wh or battery current.
- USB connected during the relevant lid cycle and capture windows.
  USB/unknown battery endpoints are `EXTERNAL_POWER_OR_UNKNOWN` and
  cannot be used to claim discharge, whole-device watts or panel savings.
- Each 180-second window is **below** this feature's 300-second minimum.
  A separate regression now ensures `SHORT_TRIAL` never exports estimated
  `energy_wh` / `mean_device_w`, even when synthetic counters differ.
- An unverified boot count now similarly suppresses plausible-looking
  energy values; the result remains `BOOT_NOT_VERIFIED`, not provisional.
- The existing RC2 app has no opt-in Passive Sleep Monitor. Even if the
  user successfully validates sleep/wake, those checks do not validate
  the #59 Activity pause/resume capture lifecycle, counter behavior,
  privacy or UX. Do not merge or publish from this evidence alone.

Private screenshots/raw logs, process identifiers, and device paths are not
carried into this PR. No follow-up battery discharge experiment is
required or authorized as part of the ongoing RC2 acceptance.
