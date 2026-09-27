# Jesty Thor Fix 0.32 live results

## Identity and migration

- Update-in-place succeeded with package `com.thor.displaypowertest`.
- Installed identity: `versionCode=35`, `versionName=0.32`.
- Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`.
- Existing installations migrated to fix ON without uninstalling or clearing data.

## Functional results

- TOP with fix ON: `power=0`, `crtc181=1`, `crtc243=0`.
- TOP with fix OFF/native: `power=1`, `crtc181=1`, `crtc243=1`.
- Sleep/wake while OFF produced no wake repair.
- Reboot while OFF returned to native mode.
- Re-enable reconciled TOP to true bottom-off.
- A disable command issued at the 650 ms wake boundary won after an in-flight OFF; final state was native ON.
- A normal fix-ON wake began repair after the preserved 700 ms window and ended `OFF_OK` after Android screen-on.
- Removing the app from recents left the independent root daemon active.
- A final reboot with fix ON returned `fix=1`, `mode=1`, `power=0`, `crtc181=1`, `crtc243=0`.

## Telemetry and presentation

- LITTLE, BIG, and PRIME current/max readings updated once per second while visible.
- The sustained low-load warning appeared only after ten qualifying samples and cleared when frequencies dropped.
- No governor, frequency, composer, or SystemLoadFix write path exists in the source.
- The dashboard mirrors confirmed bottom-display power: lower glass black/paused for `power=0`, artwork active for `power=1`.

## Public final-art APK verification

- The exact signed GitHub pre-release APK installed in place as version code
  `35` / version `0.32`.
- Manual native mode returned `power=1`; re-enabling reconciled to `power=0`.
- A sleep/wake cycle ended `OFF_OK` with the preserved timing and a one-shot DRM
  check returned `crtc181=1`, `crtc243=0`.
- An A/B/A low-load capture reproduced LITTLE/BIG frequency locking in native
  TOP mode and its release under true-off; methodology and raw samples are in
  [BENCHMARKS.md](BENCHMARKS.md).

Device identifiers and raw logs have been removed from this public summary.

## 1.0.0 stable artifact

- Installed in place as `versionCode=37`, `versionName=1.0.0`.
- Exact APK SHA-256: `1585BAEC43DD899270F522276D8F4752F5DE45E1B58D3B52EB632CD82652A840`.
- TOP native: `power=1`, `crtc181=1`, `crtc243=1`.
- TOP with fix: `power=0`, `crtc181=1`, `crtc243=0`.
- BOTH with the fix armed: `power=1`, `crtc181=1`, `crtc243=1`.
- Clearing Recents did not change true-off; the following wake ended `OFF_OK`.
- Reboot with the fix enabled restored `mode=1`, `power=0`, `crtc181=1`,
  `crtc243=0` before the UI was opened.

The refreshed dashboard screenshots in the README were captured from this
installed artifact. Device identifiers and raw system logs are not published.

## 1.0.1 CPU-check patch

- Installed in place as `versionCode=38`, `versionName=1.0.1` with the same
  signing certificate as the stable update line.
- With the fix disabled in TOP mode, the exact APK reported `power=1`,
  `crtc181=1`, `crtc243=1`, LITTLE `2.02/2.02 GHz`, and BIG
  `2.71/2.71 GHz`; the one-tap check displayed `STOCK BUG CONFIRMED`.
- With the fix enabled, the exact APK reported `power=0`, `crtc181=1`,
  `crtc243=0`, and the one-tap check displayed that the lower hardware was
  fully off and CPU clocks were released.
- A full app stop and relaunch preserved and rendered each selected state.

## 1.1.0 Dashboard CPU Fix

- Installed in place as `versionCode=42`, `versionName=1.1.0`, signed by the
  established update certificate.
- With AYN Dashboard genuinely open on Display 4, both displays active, and the
  new fix disabled, the app observed LITTLE `2.02/2.02 GHz` and BIG
  `2.71/2.71 GHz` and confirmed sustained clock pinning.
- Enabling the fix set `vendor.display.disable_system_load_check=1` and briefly
  restarted display/USB without changing the Android boot ID.
- The release daemon restarted itself after the display reset; subsequent mode
  changes updated correctly and the true-off watcher remained functional.
- With the Dashboard CPU Fix enabled, the focused BOTH-mode run recorded 0/25
  simultaneous LITTLE+BIG maximum samples.
- TOP true-off returned `crtc181=1`, `crtc243=0`; a sleep/wake repair ended
  `OFF_OK`.
- A real reboot restored `system_load_fix=1`, `fix=1`, `mode=1`, `power=0`, and
  `bottom_crtc=0`. After settling, another 25-sample run recorded 0/25
  simultaneous LITTLE+BIG maximum samples.

These are focused device results, not a battery-life percentage claim for the
new Dashboard CPU Fix.
