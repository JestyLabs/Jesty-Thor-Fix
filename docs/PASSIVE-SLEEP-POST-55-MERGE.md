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
