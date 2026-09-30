# Jesty Thor Fix 1.4.0 — testing pre-release

This candidate stages boot restoration to avoid speculative lower-display
power changes while AYN mode is unknown. A new optional Closed-Lid Wake Guard
can return an accidentally awakened Thor to sleep when its Hall switch reports
the lid closed. The guard defaults to OFF.

The CPU Fix still restarts Android's display/UI stack when its setting changes;
open apps close. On boot the app waits for that restart to finish before
reconciling the lower display. A 60-second safety timeout leaves display
actions blocked rather than guessing about the hardware state.

This build is **for opt-in testing**, not the validated stable replacement for
v1.3.0. The new Closed-Lid Wake Guard is OFF by default. The exact signed APK
passed one user-observed clean cold boot with both existing fixes ON, plus the
static/unit checks. It has **not** completed five cold boots in each fix
configuration, closed-lid/false-wake/external-display guard checks, or all
TOP/BOTTOM sleep/wake paths. The maintainer previously saw a transient green
lower screen during some boots; this build has not proved that symptom gone.
Please report any flash, stuck display, wake loop, or unexpected restart, and
switch off the new guard if it misbehaves.

APK SHA-256: `C3B5D4927A131EF900F30ACBA250823D43A5952CF6D095B0E4866C8EAC939E14`
Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`

Validation log: `docs/VALIDATION-1.4.0-PENDING.md`.
