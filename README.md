<p align="center">
  <img src="assets/branding/jesty_thor_header_lockup.png" alt="Jesty Thor Fix" width="620">
</p>

<p align="center">
  <strong>True bottom-screen OFF for the AYN Thor.</strong><br>
  Fixes stock TOP-only mode, releases the CPU clock pinning seen with the stock behavior, and restores the fix automatically after wake.
</p>

<p align="center">
  <strong>No Magisk. No terminal. No need to keep the app open.</strong><br>
  Install it, enable it, and forget about it.
</p>

<p align="center">
  <a href="https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.0.4"><strong>Download APK</strong></a>
  · <a href="#stock-top-only-vs-jesty-true-off">How it works</a>
  · <a href="docs/BENCHMARKS.md">Measurements</a>
  · <a href="https://www.buymeacoffee.com/jesty">☕ Support development</a>
</p>

<p align="center">
  <img alt="AYN Thor" src="https://img.shields.io/badge/device-AYN%20Thor-7C3AED?style=for-the-badge">
  <img alt="Android 13" src="https://img.shields.io/badge/Android-13-3DDC84?style=for-the-badge&amp;logo=android&amp;logoColor=white">
  <img alt="No Magisk or rooting" src="https://img.shields.io/badge/setup-no%20Magisk%20%2F%20rooting-16A34A?style=for-the-badge">
  <img alt="GPL 3" src="https://img.shields.io/badge/code-GPL--3.0-8B5CF6?style=for-the-badge">
</p>

<p align="center">
  <img src="docs/images/dashboard-fix-active.png" alt="Jesty Thor Fix dashboard with true bottom-screen off and CPU clocks released" width="100%">
</p>

## What the app does

On the tested Thor firmware, stock **TOP-only** makes the lower panel look black
without fully disabling its display hardware. Under low load, that state also
left the LITTLE and BIG CPU clusters pinned near their maximum clocks.

Jesty Thor Fix makes TOP-only behave as expected:

- powers down the lower display hardware;
- releases the continuous LITTLE/BIG clock pinning observed in stock TOP-only;
- repairs true-off automatically after sleep/wake;
- remembers whether the fix was enabled after a normal reboot;
- leaves TOP/BOTH switching available;
- does **not** set CPU governors, frequency limits, or forced clocks.

The app uses the privileged `PServerBinder` bridge already included in the Thor
firmware. You do **not** need to root the device yourself, install Magisk, use
Termux, or run ADB commands.

## Stock TOP-only vs Jesty true-off

| | Stock TOP-only | Jesty Thor Fix |
| --- | --- | --- |
| Lower panel | Looks black | Looks black |
| Lower display hardware | **Still active** on tested firmware | **Powered off** |
| Low-load CPU behavior | LITTLE/BIG remained pinned | Continuous pinning released |
| Sleep/wake | Lower hardware can return | True-off restored automatically |
| App must stay open | — | **No** |
| Root/Magisk setup | — | **No** |

### The stock clock-pinning bug

<p align="center">
  <img src="docs/images/dashboard-native-mode.png" alt="Stock AYN Thor TOP-only mode with bottom hardware active and LITTLE and BIG clusters pinned" width="100%">
</p>

This real-device capture shows stock TOP-only with the bottom hardware still
active, LITTLE at **2.02 / 2.02 GHz**, and BIG at **2.71 / 2.71 GHz** during a
light dashboard workload. The app's **Bottom screen & CPU check** reads both the
display state and current CPU clocks in one tap.

> [!IMPORTANT]
> A black lower panel does not prove that the hardware is off. Use **Check now**
> to confirm true-off and inspect the current LITTLE/BIG state.

## Potential battery benefit

In one controlled A/B/A low-load capture, true-off reduced the measured
system-power proxy from **2.030 W to 1.239 W**:

| Metric | Native TOP | Jesty true-off | Difference in this capture |
| --- | ---: | ---: | ---: |
| LITTLE average | 2.016 GHz | 1.616 GHz | -19.8% |
| BIG average | 2.707 GHz | 1.654 GHz | -38.9% |
| BIG at >=95% maximum | 75/75 samples | 0/45 samples | continuous lock removed |
| System-power proxy | 2.030 W | 1.239 W | **-0.792 W / -39.0%** |

> [!NOTE]
> This is a short-run directional measurement, **not a promise of 39% more
> battery life**. Firmware, brightness, workload, battery state, charger, and
> temperature can all change the result. See the
> [method and sanitized samples](docs/BENCHMARKS.md).

## Install and forget

1. Download `Jesty-Thor-Fix-1.0.4.apk` from the
   [latest release](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.0.4).
2. Install and open **Jesty Thor Fix** once.
3. Enable **True Bottom Display Fix**.
4. Select TOP mode and tap **Bottom screen & CPU check → Check now**.

After that:

- the dashboard can be closed;
- the app can be swiped away from Recents;
- the background service continues applying the fix;
- sleep/wake repair happens automatically;
- a normal reboot restores the saved enabled/disabled choice.

**Android Settings → Force stop is different.** Force stop explicitly blocks
the app until it is opened again. Swiping it away from Recents is fine; Force
stop is not the same action.

The background component is event-driven with lightweight periodic telemetry;
it does not use a busy loop. The dashboard is optional once setup is complete.

### Compatibility

- Built specifically for the **AYN Thor**.
- Tested on Android 13 firmware `TKQ1.231222.001`, build dated 2026-02-06.
- Requires the Thor firmware's built-in privileged `PServerBinder` bridge.
- Do not install it on unrelated Android devices.

The fix does not write CPU governors or frequency limits. It is designed not to
take ownership of tuning-app settings, but individual Pulse/Cluster Tune
combinations should only be described as confirmed after device testing.

## Verification

The downloadable APK was installed on the tested Thor. Manual TOP/BOTH
transitions succeeded, sleep/wake repair ended `OFF_OK`, and the final DRM state
for TOP true-off was:

```text
top CRTC 181    active
bottom CRTC 243 inactive
```

The current `1.0.4` update only changes presentation and immediate visual
highlighting. Display control, the
CPU/DRM check, and wake-repair behavior are unchanged from the validated line.

<details>
<summary><strong>How wake repair works</strong></summary>

During wake in TOP mode, Android can reactivate Display 4 through its shared
display-power pipeline. Immediate shutdown attempts caused collisions and jank
in earlier builds, so the service waits for Android to finish waking before one
final true-off operation. A generation token cancels stale work during rapid
mode or sleep changes.

This userland fix guarantees the final state. Avoiding the brief topology churn
entirely would require a framework or `system_server` policy change. The local
daemon listens only on `127.0.0.1:3804`; see
[Architecture](docs/ARCHITECTURE.md) for details.

</details>

<details>
<summary><strong>Build from source</strong></summary>

Required tools: PowerShell 5.1/7, JDK 17, Android SDK platform 34, Android Build
Tools 35.0.0, and apktool 3.0.3 or compatible.

Place `apktool.jar` at `tools/apktool.jar`, or set `APKTOOL_JAR`, then run:

```powershell
.\build.ps1
```

The repository contains no signing key or password. Self-built APKs will not
update the official build unless signed with the same private key.

</details>

## Support and documentation

Jesty Thor Fix is free and open source. No feature is locked behind donations.

- ⭐ Star the repository so other Thor owners can find it.
- 🧪 Share carefully redacted results from another firmware version.
- 🐛 Report bugs or compatibility issues.
- ☕ [Buy me a coffee](https://www.buymeacoffee.com/jesty) to support device
  testing, documentation, and future updates.

Documentation: [architecture](docs/ARCHITECTURE.md) ·
[device validation](docs/DEVICE-VALIDATION.md) ·
[live results](docs/LIVE-RESULTS.md) ·
[benchmarks](docs/BENCHMARKS.md) ·
[release integrity](docs/RELEASE-INTEGRITY.md)

## License, provenance, and independence

- Source code and scripts: [GPL-3.0-only](LICENSE).
- Jesty branding and application artwork: [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
- External references and trademarks: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- AI assistance: [full disclosure](AI_DISCLOSURE.md).

AYN and Thor are trademarks or product names of their respective owner. This is
an independent community project and is not affiliated with or endorsed by AYN
Technologies. Code, documentation, and visual assets were developed with
disclosed generative-AI assistance under the maintainer's direction,
supervision, review, and final approval. Runtime claims are based on physical
device evidence, not AI output alone.
