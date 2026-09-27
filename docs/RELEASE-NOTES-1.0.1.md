# Jesty Thor Fix 1.0.1

This patch makes the Thor's stock TOP-only bug much easier to understand and
verify without technical tools.

## What changed

- **Bottom screen & CPU check** now verifies the lower display hardware and
  current LITTLE/BIG frequencies together.
- Stock TOP-only explicitly explains that a black lower panel can still have
  active hardware and keep LITTLE/BIG cores pinned.
- A confirmed low-load lock is shown clearly as
  `STOCK BUG CONFIRMED - LITTLE/BIG PINNED AT MAX`.
- Compact Support and GitHub bubbles now sit in the top-right corner.

## What did not change

Display control, wake repair, timing, governors, frequency limits, and the
privileged bridge are unchanged from 1.0.0.

## Artifact

- Version: `1.0.1` (`versionCode 38`)
- APK: `Jesty-Thor-Fix-1.0.1.apk`
- SHA-256: `445704EC32E864D617CC6594AC4CC4A3DDF54E1D03EC81454990CED884838913`
- Signing certificate SHA-256:
  `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`

No functionality is locked behind donations.
