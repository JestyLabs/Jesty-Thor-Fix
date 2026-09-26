<p align="center">
  <img src="assets/branding/jesty_wordmark_header.png" alt="Jesty" width="430">
</p>

# Jesty Thor Fix

**True lower-display power-off and a live system dashboard for the AYN Thor.**

Jesty Thor Fix exists because the stock TOP-only mode does not consistently leave the lower display in a true hardware-off state. The app runs a small root-side daemon that reconciles the lower display after mode changes and wake events, while keeping normal TOP/BOTH changes responsive.

> [!IMPORTANT]
> This is an independent community project. It is not made, supported, or endorsed by AYN Technologies. It targets one specific device and uses privileged, firmware-dependent Android APIs. Read the requirements and limitations before installing it.

> [!NOTE]
> This project was developed with AI assistance. Parts of the code, documentation, UI artwork, and branding were generated or refined with generative AI under the maintainer's direction. The behavior described here was reviewed and tested on real hardware; AI output was not treated as proof of correctness. See [AI_DISCLOSURE.md](AI_DISCLOSURE.md).

## What the bug looks like

On the tested Thor firmware, Android's wake pipeline powers both physical displays as a group. Even when TOP-only is selected, Display 4 can be switched on during wake, which activates the lower viewport and related framework state before a userland callback can react.

The stock mode and the fix therefore differ:

| State | Thor mode | Power property | Top CRTC 181 | Bottom CRTC 243 |
| --- | ---: | ---: | ---: | ---: |
| TOP with Jesty fix | `1` | `0` | active | inactive |
| TOP using native behavior | `1` | `1` | active | active |
| BOTH | `2` | `1` | active | active |

The CRTC mapping above is specific to the tested firmware. The dashboard's **Verify bottom scanout** action performs a one-shot DRM read instead of continuously polling debugfs.

## How the fix works

- Manual TOP/BOTH transitions are handled immediately.
- After a wake in TOP-only mode, the daemon waits **700 ms** before repairing the final state.
- If Android reports Display 4 ON later in the wake, the repair is held until at least **400 ms after that event**.
- A generation token cancels stale repairs across rapid sleep/wake or mode changes.
- Disabling the fix cancels pending work, restores native display power, and reasserts ON after an in-flight OFF so the requested state wins.

This deliberately does not race Android at the start of wake. Earlier versions tried to switch the panel off immediately and could collide with SurfaceFlinger, producing severe jank. The delayed repair gives Android time to finish screen-on first.

### Userland limitation

The fix guarantees the final hardware state; it cannot prevent the framework from briefly waking Display 4 and rebuilding lower-display topology. Eliminating that churn would require a framework or `system_server` policy change.

## Dashboard

- Persistent **True Bottom Display Fix** toggle.
- FIX ACTIVE, NATIVE THOR MODE, APPLYING, and DAEMON UNAVAILABLE states.
- Read-only LITTLE, BIG, and PRIME clock telemetry.
- Sustained low-load clock-lock warning after ten qualifying samples.
- Display mode, power property, daemon uptime, and last wake-repair result.
- One-shot DRM scanout verification.
- Animated background while the dashboard is visible; playback pauses when the Activity loses focus.

The app never changes CPU governors, CPU frequencies, composer settings, or the old SystemLoadFix behavior.

## Requirements

- AYN Thor.
- Tested on Android 13 firmware `TKQ1.231222.001`, build dated 2026-02-06.
- The Thor firmware's privileged `PServerBinder` command bridge.
- The official APK for update-in-place installation. Self-built APKs use a different signing identity unless you possess the official private key.

Compatibility with other firmware versions is not guaranteed. Do not install this on unrelated Android devices.

## Installation and persistence

1. Download the APK from the GitHub Release, not from an issue attachment or mirror.
2. Install it as an update if an earlier Jesty Thor Fix build is present.
3. Open **Jesty Thor Fix** once and confirm that the dashboard reports `FIX ACTIVE`.
4. In TOP mode, use **Verify bottom scanout** and confirm that bottom CRTC 243 is inactive.

The root daemon is independent of the dashboard Activity. Closing the app, removing it from recents, or clearing ordinary background apps does not stop the fix. `BootReceiver` reconciles the saved ON/OFF choice after a normal reboot.

**Android Settings → Force stop is different.** Force stop may prevent the app from restarting automatically until it is opened again.

## Local daemon protocol

The daemon listens only on `127.0.0.1:3804`:

| Command | Action |
| --- | --- |
| `0` / `1` | Legacy bottom OFF / ON |
| `E` | Enable the fix and reconcile the current mode |
| `N` | Disable the fix and restore native behavior |
| `Q` | Return daemon, display, CPU, load, and last-repair state |
| `V` | Perform one DRM CRTC verification |

## Building

The source intentionally contains no keystore, password, token, device log, or maintainer-specific path.

Required tools:

- Windows PowerShell 5.1 or PowerShell 7
- JDK 17
- Android SDK platform 34
- Android Build Tools 35.0.0
- apktool 3.0.3 or compatible
- ffmpeg only when regenerating the animated background

Place `apktool.jar` at `tools/apktool.jar`, or set `APKTOOL_JAR`, then run:

```powershell
.\build.ps1
```

That produces `dist/Jesty-Thor-Fix-0.32-unsigned.apk`. To sign a personal build, pass `-Sign -KeystorePath <path>`; the script asks for the password interactively and never stores it in the repository.

See [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the split Java/smali build, [docs/DEVICE-VALIDATION.md](docs/DEVICE-VALIDATION.md) for the hardware validation gate, and [docs/RELEASE-INTEGRITY.md](docs/RELEASE-INTEGRITY.md) for APK hashes and the signing certificate.

## Safety and known limitations

- The app performs privileged display operations and can leave display state wrong if used on unsupported firmware.
- The scanout check reads `/sys/kernel/debug/dri/0/state` only when requested.
- Clock telemetry is diagnostic only and is not evidence of a performance problem by itself.
- The wake repair is intentionally conservative; reducing its timing without device traces can reintroduce jank.
- Only the final state can be repaired from userland. Temporary wake-time topology churn remains possible.

## Support

If the fix helped you, you can support future work at [Buy Me a Coffee](https://buymeacoffee.com/jesty).

Bug reports and carefully redacted device evidence are welcome. Never upload a keystore, password, device serial, account email, or full unreviewed log bundle.

## Licensing and credits

- Source code and scripts: [GPL-3.0-only](LICENSE).
- Jesty mascot and wordmark: separate terms in [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
- AI assistance: [AI_DISCLOSURE.md](AI_DISCLOSURE.md).
- External references and trademarks: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

AYN and Thor are trademarks or product names of their respective owner. Their use here is descriptive only.
