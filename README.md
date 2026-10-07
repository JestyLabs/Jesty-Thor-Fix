<p align="center">
<img src="assets/branding/jesty_thor_header_lockup.png" alt="Jesty Thor Fix" width="620">
</p>

<p align="center">
<strong>Make the AYN Thor behave the way it should.</strong><br>
Actually turn off the lower screen in TOP mode · Stop unnecessary high CPU clocks · Prevent false wakes that drain the battery
</p>

<p align="center">
<a href="https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.6.0"><strong>Download v1.6.0</strong></a>
· <a href="#what-it-fixes">What it fixes</a>
· <a href="#measured-on-a-real-thor">Measured results</a>
· <a href="#roadmap">Roadmap</a>
· <a href="https://www.buymeacoffee.com/jesty">☕ Support</a>
</p>

<p align="center">
<img alt="AYN Thor" src="https://img.shields.io/badge/device-AYN%20Thor-7C3AED?style=for-the-badge">
<img alt="Android 13" src="https://img.shields.io/badge/Android-13-3DDC84?style=for-the-badge&logo=android&logoColor=white">
<img alt="No Magisk or manual rooting" src="https://img.shields.io/badge/setup-no%20Magisk%20%2F%20manual%20rooting-16A34A?style=for-the-badge">
<img alt="GPL 3" src="https://img.shields.io/badge/code-GPL--3.0-8B5CF6?style=for-the-badge">
</p>

**Made for the AYN Thor.** No Magisk, no terminal, no manual rooting.  
Set the switches once - the fixes keep working after you close the app.

<p align="center"><strong>Open source · signed releases · published hashes · hardware-tested</strong></p>

<p align="center">
<img width="1080" height="483" alt="Jesty Thor Fix dashboard" src="https://github.com/user-attachments/assets/d1f1d875-792b-4b84-8d52-41b307d11ae4" />
</p>

---

### The big one: TOP mode does not fully turn the lower display off

A common question is:

> **"It's OLED. If the lower screen is black, isn't that basically the same as off?"**

For the pixels, black is cheap. But on the Thor, **black does not mean the display hardware is off**.

In stock TOP mode, Android still reports the lower display controller as active. With **True Bottom Screen Off**, that hardware path becomes inactive.

```text
Stock TOP mode        lower display: ACTIVE
True Bottom Screen Off lower display: INACTIVE
```

Technically, this is verified from the DRM CRTC state — not inferred from whether the OLED pixels look black.

That also matters beyond the pixels: keeping the second display path active can keep extra Qualcomm display/CPU work alive.

---

### What it fixes

| Control | What it does |
|---------|--------------|
| **True Bottom Screen Off** | Powers down the lower physical display in TOP mode instead of leaving it active behind a black image. Repairs the state after sleep/wake. |
| **AYN Dashboard CPU Fix** | Stops the Dashboard from keeping the main CPU clusters stuck near their top speeds under light load. Does **not** force CPU frequencies or change governors. |
| **Closed-Lid Wake Guard** | Puts the Thor back to sleep if it wakes while the lid is still closed. Optional and **off by default**. |

The dashboard is there to make this visible: it shows whether the lower hardware is really off, live CPU speed, power estimate and Wake Guard state.

<p align="center">
<img src="docs/images/dashboard-both-v1.5.15-review.png" alt="Jesty Thor Fix dashboard showing live display and CPU state" width="780">
</p>
<p align="center">
<sub>Physical capture on the Thor. The dashboard reports live hardware state, not just the selected mode.</sub>
</p>

---

### Measured on a real Thor

Short controlled A/B/A test in TOP mode, same device/setup/workload:

| Metric | Native TOP | True Bottom Off |
|--------|------------:|----------------:|
| Efficiency CPU cluster avg. | 2.016 GHz | 1.616 GHz |
| Performance CPU cluster avg. | 2.707 GHz | 1.654 GHz |
| Performance CPU samples near max | 75/75 | 0/45 |
| System power proxy | ~2.03 W | ~1.24 W |

The power number is a **system-level proxy**, not a panel-only measurement and not a battery-life promise. In this test, true-off also let the CPU scale down normally, so the reduction is the combined system effect. Android/Qualcomm commonly call these CPU groups LITTLE and BIG; the technical docs keep those names.

<details>
<summary><strong>Technical proof in one minute</strong></summary>

- DRM/CRTC state confirms whether each physical display pipeline is active.
- In stock TOP mode the lower CRTC remained active; with True Bottom Screen Off it became inactive.
- The Thor's Qualcomm display stack also exposed a separate low-load CPU performance-hint problem when both display paths were active.
- Controlled A/B/A captures are published with raw CSV samples.

See [benchmarks](docs/BENCHMARKS.md), [architecture](docs/ARCHITECTURE.md) and [live results](docs/LIVE-RESULTS.md).

</details>

Full method + raw CSVs → [benchmarks](docs/BENCHMARKS.md)

---

### Why this project exists

The original investigation started with two reproducible Thor behaviours:

1. TOP mode left the lower physical display path active even though the screen looked black.
2. The AYN Dashboard could keep LITTLE and BIG CPU clusters near maximum under light load.

Jesty Thor Fix turns those observations into simple switches, keeps the fixes active in the background, and exposes enough telemetry to verify what the hardware is actually doing.

Original investigation and discussion:  
**[Reddit thread](https://www.reddit.com/r/AynThor/comments/1wrsmmo/found_two_weird_ayn_thor_issues_top_only_doesnt/)**

---

### How to use

1. [Download the latest signed APK](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.6.0)
2. Install and open **Jesty Thor Fix**
3. Enable the controls you want
4. Close the app (or swipe it away). The fixes continue in the background.

The app uses the Thor’s own privileged bridge. No Magisk or root required.

> **Note:** Changing the CPU Fix restarts Android UI/display once and closes open apps. On later boots, the required display recovery happens during startup; a slightly longer black startup phase is normal.

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
| **Retroid Pocket Duo** | Research / testers wanted — not supported yet |
| Other devices | Unsupported |

Power savings depend on usage, brightness, firmware and workload. **No fixed battery-life percentage is claimed.**

---

### Roadmap

**Working on**

- 🎯 **TOP-mode focus/input bug** — testing whether physically powering off the lower display also prevents games or apps from losing focus to the inactive screen.

**Next investigations**

- 🖥️ **120 Hz screen tearing / mixed refresh** — investigate AYN's handling of the Thor's 120 Hz upper display and physically 60 Hz lower panel.
- 🧪 **Retroid Pocket Duo compatibility** — preparing the codebase for device profiles and looking for Pocket Duo owners who want to help test dual-screen power, focus, wake and display behaviour. **No Pocket Duo support is claimed yet.**

See the [current roadmap and research status](docs/ROADMAP.md) for technical details and deferred work.

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
[Compatibility](docs/COMPATIBILITY.md) · 
[Benchmarks](docs/BENCHMARKS.md) · 
[Release notes](docs/RELEASE-NOTES-1.6.0.md) · 
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
- Project provenance & attribution: [NOTICE](NOTICE.md)
- AI assistance: [full disclosure](AI_DISCLOSURE.md)

Independent community project. Not affiliated with or endorsed by AYN.  
Hardware claims are based on physical device evidence.

</details>
