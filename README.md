<p align="center">
<img src="assets/branding/jesty_thor_header_lockup.png" alt="Jesty Thor Fix" width="620">
</p>

<p align="center">
<strong>Make the AYN Thor behave the way it should.</strong><br>
Actually turn off the lower screen in TOP mode · Stop unnecessary high CPU clocks · Prevent false wakes that drain the battery
</p>

<p align="center">
<a href="https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.20"><strong>Download v1.5.20</strong></a>
· <a href="#what-it-does">What it does</a>
· <a href="#measured-results">Measured results</a>
· <a href="#roadmap">Roadmap</a>
· <a href="https://www.buymeacoffee.com/jesty">☕ Support</a>
</p>

<p align="center">
<img alt="AYN Thor" src="https://img.shields.io/badge/device-AYN%20Thor-7C3AED?style=for-the-badge">
<img alt="Android 13" src="https://img.shields.io/badge/Android-13-3DDC84?style=for-the-badge&logo=android&logoColor=white">
<img alt="No Magisk or manual rooting" src="https://img.shields.io/badge/setup-no%20Magisk%20%2F%20manual%20rooting-16A34A?style=for-the-badge">
<img alt="GPL 3" src="https://img.shields.io/badge/code-GPL--3.0-8B5CF6?style=for-the-badge">
</p>

**Made for the AYN Thor.** No Magisk, no terminal, no rooting.  
Set the switches once - the fixes keep working even after you close the app.

<p align="center">
<img width="1080" height="483" alt="Jesty Thor Fix dashboard" src="https://github.com/user-attachments/assets/d1f1d875-792b-4b84-8d52-41b307d11ae4" />
</p>

---

### Why this exists

On the Thor, two things don’t work the way most people expect:

1. **TOP mode does not fully power down the lower display.**  
   The screen looks black, but the display hardware can stay active behind it.

2. **AYN Dashboard can keep the LITTLE and BIG CPU clusters running near maximum** even under light load.

Both behaviours waste power and generate extra heat.  
Jesty Thor Fix corrects them and also adds an optional closed-lid protection.

Original investigation and discussion:  
**[Reddit thread](https://www.reddit.com/r/AynThor/comments/1wrsmmo/found_two_weird_ayn_thor_issues_top_only_doesnt/)**

---

### What it does

| Control | What you get |
|---------|--------------|
| **True Bottom Screen Off** | In TOP mode the lower display is actually powered down (not just showing black). Automatically restores the correct state after sleep/wake. |
| **AYN Dashboard CPU Fix** | Stops the Dashboard from pinning LITTLE/BIG clocks high under light load. Does **not** change governors or force frequencies. |
| **Closed-Lid Wake Guard** | If the Thor wakes while the lid is still closed, it goes back to sleep. **Off by default.** |

You can enable any combination. The dashboard shows the real display and CPU state so you can verify the fixes are active.

<p align="center">
<img src="docs/images/dashboard-both-v1.5.15-review.png" alt="Jesty Thor Fix dashboard" width="780">
</p>
<p align="center">
<sub>Physical capture on the Thor (v1.5.15). Current layout may differ slightly.</sub>
</p>

---

### Measured results

Short controlled A/B/A test on a physical Thor in TOP mode:

| Metric | Native TOP | With True Bottom Off |
|--------|------------|----------------------|
| LITTLE mean | 2.016 GHz | 1.616 GHz |
| BIG mean | 2.707 GHz | 1.654 GHz |
| BIG samples near max | 75/75 | 0/45 |
| System power proxy | ~2.03 W | ~1.24 W |

These are short diagnostic measurements, **not a battery-life claim**.  
Full methodology and raw data → [benchmarks](docs/BENCHMARKS.md)

---

### How to use

1. [Download the latest signed APK](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.20)
2. Install and open **Jesty Thor Fix**
3. Enable the controls you want
4. Close the app (or swipe it away). The fixes continue in the background.

The app uses the Thor’s own privileged bridge. No Magisk or root required.

> **Note:** Enabling the CPU Fix causes one Android UI/display restart (open apps will close). This is expected and also happens once on boot if the fix is saved as ON.

---

### Safety

- Does **not** write CPU frequencies, voltages, thermal limits, or firmware
- Validates the result after every change
- Automatically restores normal behaviour if something looks wrong
- Closed-Lid Wake Guard is off by default

More details on resource use and known limitations are in the documentation below.

---

### Compatibility

| Device | Status |
|--------|--------|
| **AYN Thor** | Supported (tested on Android 13 firmware `TKQ1.231222.001`) |
| Other devices | Unsupported |

Power savings depend on usage, brightness, firmware and workload. **No fixed battery-life percentage is claimed.**

---

### Roadmap

Current development stays deliberately isolated from the stable release path. Items below are research or work in progress until they are physically validated.

**Working on**

- **Cleaner CPU Fix recovery UX** — keep the required compositor/framework restart and its existing provenance/fail-safe guarantees, while avoiding the confusing second vendor boot animation and replacing the black recovery gap with a bounded Jesty Thor Fix recovery splash.
- **TOP-mode focus / input-routing investigation** — determine whether cases where Android loses focus are caused by the lower display remaining active in native TOP mode, and whether **True Bottom Screen Off** eliminates that condition. This is not claimed as fixed until reproduced and measured.

**Next investigations**

- **Mixed-refresh / 120 Hz tearing** — trace how AYN exposes the physically 60 Hz lower panel when the system is switched to 120 Hz, then compare SurfaceFlinger, Qualcomm HWC/SDM and physical display timing. The goal is to understand whether the Thor can run the upper display at 120 Hz while keeping the lower panel on its native timing without tearing.
- **Retroid Pocket Duo compatibility** — investigate compatibility feature by feature rather than assuming full support: True Bottom Screen Off, display telemetry, wake repair, lid/hall behaviour and whether the Thor-specific CPU Fix is applicable at all. No Pocket Duo support claim until tested on hardware.

---

### Support the project

Jesty Thor Fix is free and open source. No features are locked behind donations.

<p align="center">
  <a href="https://www.buymeacoffee.com/jesty">
    <img src="https://cdn.buymeacoffee.com/buttons/v2/default-yellow.png" alt="Buy Me a Coffee" width="217">
  </a>
</p>

- ⭐ Star the repository so other Thor owners can find it
- 🧪 Share results from another firmware
- 🐛 Report reproducible issues
- 💬 Join the original [Reddit discussion](https://www.reddit.com/r/AynThor/comments/1wrsmmo/found_two_weird_ayn_thor_issues_top_only_doesnt/)

---

### Documentation

[Architecture](docs/ARCHITECTURE.md) · 
[Benchmarks](docs/BENCHMARKS.md) · 
[Release notes](docs/RELEASE-NOTES-1.5.20.md) · 
[Build from source](docs/BUILD-AND-RELEASE.md) · 
[Release integrity](docs/RELEASE-INTEGRITY.md)

<details>
<summary><strong>Technical implementation</strong></summary>

Jesty Thor Fix follows the physical TOP/BOTH switch. It waits for Android and both displays to settle, then verifies the hardware state (CRTC).

The CPU Fix only sets the vendor property `vendor.display.disable_system_load_check`.  
Enabling it triggers one compositor restart.

Closed-Lid Wake Guard sends `KEYCODE_SLEEP` only after confirming the lid is still closed.

See [Architecture](docs/ARCHITECTURE.md) for the full service, daemon, verification and boot behaviour.

</details>

<details>
<summary><strong>License, provenance & independence</strong></summary>

- Source & build scripts: [GPL-3.0](LICENSE)
- Branding & artwork: [ASSETS-LICENSE.md](ASSETS-LICENSE.md)
- Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- AI assistance: [full disclosure](AI_DISCLOSURE.md)

Independent community project. Not affiliated with or endorsed by AYN.  
Hardware claims are based on physical device evidence.

</details>
