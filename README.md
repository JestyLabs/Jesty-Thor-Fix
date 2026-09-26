<p align="center">
  <img src="assets/branding/jesty_wordmark_header.png" alt="Jesty" width="390">
</p>

<h1 align="center">Jesty Thor Fix</h1>

<p align="center">
  <strong>Real lower-display power-off for the AYN Thor.</strong><br>
  Keeps TOP mode responsive, repairs the display after wake, and shows live CPU/display telemetry.
</p>

<p align="center">
  <img alt="AYN Thor" src="https://img.shields.io/badge/device-AYN%20Thor-7C3AED?style=for-the-badge">
  <img alt="Android 13" src="https://img.shields.io/badge/Android-13-3DDC84?style=for-the-badge&amp;logo=android&amp;logoColor=white">
  <img alt="Root bridge required" src="https://img.shields.io/badge/requires-PServerBinder-F59E0B?style=for-the-badge">
  <img alt="GPL 3" src="https://img.shields.io/badge/code-GPL--3.0-8B5CF6?style=for-the-badge">
</p>

<p align="center">
  <a href="https://github.com/SirJesty/Jesty-Thor-Fix/releases/tag/v0.32"><strong>Download the signed 0.32 pre-release</strong></a>
  · <a href="docs/DEVICE-VALIDATION.md">Validation checklist</a>
  · <a href="docs/BENCHMARKS.md">Measurements and raw data</a>
</p>

<p align="center">
  <img src="docs/images/dashboard-fix-active.png" alt="Jesty Thor Fix dashboard showing true-off active" width="100%">
</p>

> [!IMPORTANT]
> **Made specifically for the AYN Thor.** This is an independent community
> project and is not made, supported, or endorsed by AYN Technologies. It uses
> privileged, firmware-dependent Android APIs and should not be installed on
> unrelated devices.

## The Thor problem, in plain English

AYN's stock **TOP-only** mode makes the lower screen look disabled, but on the
tested firmware Android can continue driving its scanout hardware. The lower
display also wakes again during every normal system wake. That can leave extra
display work active and, in the captured low-load case, keep CPU clusters at
high frequencies.

Jesty Thor Fix changes the final hardware state instead of merely showing a
black lower screen:

| | Stock TOP-only | Jesty true-off |
| --- | --- | --- |
| What you see | Lower screen looks black | Lower screen is black |
| Bottom scanout | Still active | **Inactive** |
| Display property | `power=1` | **`power=0`** |
| Bottom CRTC 243 | active | **inactive** |
| After wake | Android wakes the lower display | App repairs true-off after Android finishes waking |

### See the difference

| Native Thor mode | Jesty fix active |
| --- | --- |
| ![Native Thor mode with bottom display and pinned clocks](docs/images/dashboard-native-mode.png) | ![True-off active with lower screen off](docs/images/dashboard-fix-active.png) |

## Why it is useful

- **True lower-display off:** verified through DRM CRTC state, not just a black
  overlay or a setting value.
- **Fast normal switching:** manual TOP/BOTH transitions remain immediate.
- **Stable wake repair:** waits for Android's wake pipeline instead of fighting
  SurfaceFlinger at the worst possible moment.
- **No CPU modification:** reads clock telemetry but never changes governors,
  limits, frequencies, composer settings, or SystemLoadFix.
- **Works without the dashboard open:** the root daemon is independent from the
  Activity and survives removing the app from recents.
- **Remembers your choice:** `BootReceiver` reconciles the saved ON/OFF state
  after a normal reboot.

## Measured on real hardware

A controlled A/B/A capture compared native TOP mode with true-off under the
same low-load, USB-powered conditions:

| Metric | Native TOP | True-off | Result in this capture |
| --- | ---: | ---: | ---: |
| LITTLE average | 2.016 GHz | 1.616 GHz | -19.8% |
| BIG average | 2.707 GHz | 1.654 GHz | -38.9% |
| LITTLE at >=95% maximum | 75/75 samples | 22/45 samples | no longer continuous |
| BIG at >=95% maximum | 75/75 samples | 0/45 samples | lock eliminated |
| System-power proxy | 2.030 W | 1.239 W | **-0.792 W / -39.0%** |
| 1-minute load average | 0.465 | 0.425 | comparable low load |

> [!NOTE]
> The power number is a short-run directional proxy calculated from USB input
> minus battery charging power. It is **not** a promise of 39% more battery
> life. Firmware, brightness, workload, battery state, charger, and temperature
> can change the result. The full method and sanitized CSV samples are in
> [docs/BENCHMARKS.md](docs/BENCHMARKS.md).

The exact downloadable APK was installed on the tested Thor. Manual native/fix
transitions succeeded, a sleep/wake repair ended `OFF_OK`, and a one-shot DRM
verification returned:

```text
top CRTC 181    active
bottom CRTC 243 inactive
```

## Installation

1. Download `Jesty-Thor-Fix-0.32.apk` from the
   [GitHub release](https://github.com/SirJesty/Jesty-Thor-Fix/releases/tag/v0.32).
2. Install it as an update if an earlier build with the same package is present.
3. Open **Jesty Thor Fix** once and confirm `FIX ACTIVE`.
4. Select TOP mode and press **Verify bottom scanout**. Bottom CRTC 243 should
   report inactive.

Requirements:

- AYN Thor.
- Tested Android 13 firmware `TKQ1.231222.001`, build dated 2026-02-06.
- The Thor firmware's privileged `PServerBinder` command bridge.

Self-built APKs will not update the official build unless they are signed with
the same private key.

## Everyday behavior

- Closing the dashboard or removing it from recents does not normally stop the fix.
- A normal reboot restores the saved ON/OFF choice.
- Turning the master toggle OFF cancels pending work and restores native behavior.
- The app respects Android's normal display timeout.

**Android Settings -> Force stop is different.** Force stop may prevent the app
from restarting automatically until it is opened again.

<details>
<summary><strong>How the wake repair works</strong></summary>

Manual TOP/BOTH transitions are handled immediately. During a wake in TOP
mode, Android can reactivate Display 4 as part of its shared display power
pipeline. Trying to switch it off immediately caused collisions and severe
jank in earlier builds.

The current strategy waits 700 ms from wake and never repairs earlier than
400 ms after a late Display 4 ON callback. A generation token cancels stale
work across fast mode or sleep changes. This gives Android time to complete
screen-on before one final true-off operation.

This userland fix guarantees the final state. It cannot stop Android from
briefly rebuilding lower-display topology during wake; eliminating that churn
would require a framework or `system_server` policy change.

The daemon listens only on `127.0.0.1:3804`. See
[docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) for the protocol and architecture.

</details>

<details>
<summary><strong>Building from source</strong></summary>

Required tools: PowerShell 5.1/7, JDK 17, Android SDK platform 34, Android Build
Tools 35.0.0, and apktool 3.0.3 or compatible.

Place `apktool.jar` at `tools/apktool.jar`, or set `APKTOOL_JAR`, then run:

```powershell
.\build.ps1
```

The source contains no keystore, password, token, device log, or
maintainer-specific path. Signing is opt-in and reads the password interactively.

</details>

## Support the project

If the fix improves your Thor experience, a coffee helps fund testing, device
work, documentation, and future updates.

<p align="center">
  <a href="https://www.buymeacoffee.com/jesty">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me a Coffee" width="217">
  </a>
</p>

Bug reports and carefully redacted device evidence are welcome. Never upload a
keystore, password, device serial, account email, or unreviewed log bundle.

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Hardware validation checklist](docs/DEVICE-VALIDATION.md)
- [Live results](docs/LIVE-RESULTS.md)
- [Benchmarks and raw samples](docs/BENCHMARKS.md)
- [Release integrity](docs/RELEASE-INTEGRITY.md)
- [AI assistance disclosure](AI_DISCLOSURE.md)

## License and credits

- Source code and scripts: [GPL-3.0-only](LICENSE).
- Jesty mascot, wordmark, and application artwork: [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
- External references and trademarks: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

AYN and Thor are trademarks or product names of their respective owner. Their
use here is descriptive only. This project was developed with disclosed
generative-AI assistance under the maintainer's direction.
