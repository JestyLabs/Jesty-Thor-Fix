<p align="center">
  <img src="assets/branding/jesty_thor_header_lockup.png" alt="Jesty Thor Fix" width="620">
</p>

<p align="center">
  <strong>Two fixes for AYN Thor display and CPU behavior.</strong><br>
  True lower-screen hardware OFF in TOP mode, plus a separate fix for
  LITTLE/BIG clock pinning caused by AYN Dashboard.
</p>

<p align="center">
  <strong>No Magisk. No terminal. No need to keep the app open.</strong><br>
  Install it, choose the fixes you want, and forget about it.
</p>

<p align="center">
  <a href="https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.1.0"><strong>Download APK</strong></a>
  · <a href="#the-two-fixes">How it works</a>
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
  <img src="docs/images/dashboard-two-fixes-both-v1.1.0.png" alt="Jesty Thor Fix with both independent fixes enabled" width="100%">
</p>

## The two fixes

The two toggles solve related but independent Thor firmware behaviors:

| Fix | What it changes | When it helps |
| --- | --- | --- |
| **True Bottom Display Fix** | Powers down the lower display hardware in TOP mode and restores true-off after wake | When you want to use only the top screen |
| **AYN Dashboard CPU Fix** | Releases LITTLE/BIG clocks that can remain pinned in dual-screen mode | When AYN Dashboard or the dual-screen workflow triggers the clock bug |

Both choices are saved. The app can be closed or removed from Recents after
setup; its privileged background daemon keeps working without the dashboard UI.

> [!WARNING]
> Enabling or disabling **AYN Dashboard CPU Fix** briefly restarts the displays
> and USB. It can look like a reboot, but Android and open apps keep running.
> When this fix is enabled, the same brief display restart happens once during
> each normal boot and may look like a second reboot.

## The AYN Dashboard clock bug

<p align="center">
  <img src="docs/images/ayn-dashboard-cpu-pinning.png" alt="AYN Dashboard open with LITTLE and BIG CPU clusters pinned at maximum" width="100%">
</p>

This real-device capture was taken with AYN Dashboard genuinely open on the
lower display, the Dashboard CPU Fix disabled, and both screens active. Under
low load, LITTLE remained at **2.02 / 2.02 GHz** and BIG at
**2.71 / 2.71 GHz**. Jesty Thor Fix detected the sustained condition in red.

With the Dashboard CPU Fix active in BOTH mode, the focused validation run
produced **0/25 simultaneous LITTLE+BIG maximum samples**. The app does not
force clocks, governors, or frequency limits; it disables the vendor display
system-load check responsible for the reproduced behavior.

## Stock TOP-only vs Jesty true-off

AYN's stock TOP-only mode can make the lower panel look black while its display
hardware remains active. Jesty true-off verifies the physical DRM state and
powers that hardware down.

| | Stock TOP-only | Jesty true-off |
| --- | --- | --- |
| Lower panel | Looks black | Looks black |
| Lower display hardware | **Still active** on tested firmware | **Powered off** |
| Low-load CPU behavior observed | LITTLE/BIG can remain pinned | Continuous pinning released |
| Sleep/wake | Lower hardware can return | True-off restored automatically |
| App must stay open | — | **No** |
| User root/Magisk setup | — | **No** |

<p align="center">
  <img src="docs/images/dashboard-two-fixes-v1.1.0.png" alt="TOP-only with true lower-screen hardware off and both fixes enabled" width="100%">
</p>

> [!IMPORTANT]
> A black lower panel does not prove that its hardware is off. Tap
> **Bottom screen & CPU check → Check now** to inspect the physical lower CRTC
> and current LITTLE/BIG state.

## Potential battery benefit

In one controlled A/B/A low-load capture, true-off reduced the measured
system-power proxy from **2.030 W to 1.239 W**:

| Metric | Native TOP | Jesty true-off | Difference in this capture |
| --- | ---: | ---: | ---: |
| LITTLE average | 2.016 GHz | 1.616 GHz | -19.8% |
| BIG average | 2.707 GHz | 1.654 GHz | -38.9% |
| BIG at >=95% maximum | 75/75 samples | 0/45 samples | continuous lock removed |
| System-power proxy | 2.030 W | 1.239 W | **-0.792 W / -39.0%** |

This is a short, directional measurement—not a promise of 39% more battery
life. Firmware, workload, brightness, temperature, charger, and battery state
all affect the result. No battery percentage is claimed for the separate
Dashboard CPU Fix. See the [method and sanitized samples](docs/BENCHMARKS.md).

## Install, enable, forget

1. Download `Jesty-Thor-Fix-1.1.0.apk` from the
   [v1.1.0 release](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.1.0).
2. Install and open **Jesty Thor Fix** once.
3. Enable **True Bottom Display Fix** for physical lower-screen OFF in TOP mode.
4. Optionally enable **AYN Dashboard CPU Fix** and accept the clearly labelled
   brief display/USB restart.
5. Use **Check now** to verify the current hardware and CPU state.

You do **not** need to root the Thor yourself, install Magisk, use Termux, or
run commands. The app uses the privileged `PServerBinder` bridge supplied by
the Thor firmware.

After setup:

- close the dashboard whenever you want;
- swipe the app away from Recents;
- switch normally between TOP and BOTH;
- reboot normally and both saved choices are restored.

**Android Settings → Force stop is different.** Force stop explicitly blocks
the app until it is opened again. Swiping it from Recents does not.

The service is event-driven with lightweight periodic state checks and no busy
loop. The UI is optional once setup is complete.

## Compatibility and coexistence

- Built specifically for the **AYN Thor**.
- Tested on Android 13 firmware `TKQ1.231222.001`, build dated 2026-02-06.
- Requires the Thor firmware's built-in privileged bridge.
- Do not install it on unrelated Android devices.

Jesty Thor Fix does not write CPU governors, clock limits, or tuning profiles.
It is designed not to take ownership of Pulse or Cluster Tune settings, but a
specific combination should only be called confirmed after that exact setup is
tested on-device.

## v1.1.0 verification

The exact signed APK was installed in place on a physical AYN Thor:

- package `com.thor.displaypowertest`, version code `42`, version `1.1.0`;
- signing certificate unchanged from the existing update line;
- TOP true-off: `crtc181=1`, `crtc243=0`, `OFF_OK` after sleep/wake;
- BOTH: both CRTCs active and the Dashboard fix produced 0/25 simultaneous
  LITTLE+BIG maximum samples in the focused run;
- enabling the Dashboard fix restarted display/USB without rebooting Android;
- the privileged daemon restarted cleanly after that display reset;
- a real Android reboot restored both saved fixes and TOP true-off;
- Back/closing the UI left the daemon and fixes active.

APK SHA-256:

```text
10240A143C2B3C73333F089ACD45D563D476E571FA93EDEA9F765DA421D0EE77
```

<details>
<summary><strong>How wake repair works</strong></summary>

During wake in TOP mode, Android can reactivate Display 4 through its shared
display-power pipeline. The service lets Android finish waking, then performs
one final true-off operation. A generation token cancels stale work during
rapid mode or sleep changes. See [Architecture](docs/ARCHITECTURE.md).

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
- Generative-AI assistance to code, investigation, documentation, and visual
  work: [full disclosure](AI_DISCLOSURE.md).

AYN and Thor are trademarks or product names of their respective owner. This is
an independent community project and is not affiliated with or endorsed by AYN
Technologies. AI-assisted work was directed, supervised, reviewed, and tested
by the maintainer; runtime claims above come from physical-device evidence.
