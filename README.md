<p align="center">
  <img src="assets/branding/jesty_thor_header_lockup.png" alt="Jesty Thor Fix" width="560">
</p>

<p align="center">
  <strong>Make your AYN Thor's TOP mode truly turn off the bottom screen.</strong><br>
  Verified display power-off · Normal CPU behaviour · Better sleep recovery
</p>

<p align="center">
  <a href="https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.1"><strong>Download v1.7.1</strong></a>
  · <a href="#what-it-fixes">Features</a>
  · <a href="#measured-on-a-real-thor">Results</a>
  · <a href="#current-investigations">What's next</a>
  · <a href="https://www.buymeacoffee.com/jesty">☕ Support</a>
</p>

<p align="center">
  <img alt="Android 13" src="https://img.shields.io/badge/Android-13-3DDC84?style=for-the-badge&logo=android&logoColor=white">
  <img alt="No Magisk or manual rooting" src="https://img.shields.io/badge/setup-no%20Magisk%20%2F%20manual%20rooting-16A34A?style=for-the-badge">
  <img alt="GPL-3.0" src="https://img.shields.io/badge/code-GPL--3.0-8B5CF6?style=for-the-badge">
</p>

<p align="center">
  <sub>Signed releases · Tested on a physical Thor · Fixes keep working after you close the app</sub>
</p>

<p align="center">
  <img src="https://github.com/user-attachments/assets/d1f1d875-792b-4b84-8d52-41b307d11ae4" alt="Jesty Thor Fix on the Thor: true lower-screen power-off compared with stock TOP mode" width="900">
</p>

---

### What it fixes

| Feature | Why it matters |
|---|---|
| **True Bottom Screen Off** | When you use just the top screen, **actually powers down the lower display**, not just the image on it. Restores the correct state after sleep and wake. |
| **AYN Dashboard CPU Fix** | Stops the Dashboard from keeping CPU speeds unnecessarily high while the Thor is idle. No manual CPU tuning needed. |
| **Closed-Lid Wake Guard** | Optional protection for unexpected wakes with the lid shut. **You choose whether to enable it**; it starts switched off. Normal sleep/wake recovery was hardware-tested, but an actual blocked wake has not been captured. |
| **App Diagnostics (v1.7.1)** | Tap the **gear at the top** to see recent events, save a troubleshooting report and try the optional Sleep Trial. |

You can close the app after setting it up; the fixes keep working.

<p align="center">
  <img src="docs/images/dashboard-both-v1.5.15-review.png" alt="Jesty Thor Fix dashboard on a real AYN Thor; older app screenshot" width="760">
</p>

<p align="center"><sub>Dashboard on a real AYN Thor (earlier version shown; v1.7.1 moves Diagnostics to the top).</sub></p>

---

### Measured on a real Thor

**Switching off the bottom screen makes a measurable difference** — not just a black picture. In a controlled test on a physical AYN Thor, the lower display really powered down, and the high-performance CPU cluster stopped staying near its maximum frequency.

<p align="center">
  <img src="assets/charts/thor-top-mode-power.svg" alt="Controlled TOP-mode comparison: native mode showed about 2.03 W of estimated system-power proxy; True Bottom Screen Off showed about 1.24 W, roughly 39 percent lower in this test." width="880">
</p>

**About 39% lower estimated system-power draw in this short test.** This is a *power proxy* from the Thor's hardware readings, **not** a measured battery-life improvement. Brightness, workload and charging conditions affect real-world results.

<p align="center">
  <img src="assets/charts/thor-top-mode-cpu.svg" alt="High-performance CPU cluster: 2.707 GHz average in native TOP mode versus 1.654 GHz with True Bottom Screen Off; near-maximum CPU readings changed from 75 of 75 samples to 0 of 45." width="880">
</p>

**From stuck-near-maximum to normal clock behaviour:** all **75/75** native TOP samples were near the performance cluster's maximum speed, compared with **0/45** after the screen was really powered off.

**[See the full A/B/A benchmark and raw measurements](docs/BENCHMARKS.md)** · [How display power-off was validated](docs/DEVICE-VALIDATION.md)

### Lightweight in the background

The app's own CPU usage stays tiny, too. In separate physical idle tests, daemon CPU use with **both screens active** fell from about **0.29% to 0.09% of one CPU core** — around **70% less than the earlier RC2 candidate**. In TOP mode it remained around **0.32% of a core**.

No extra CPU polling is introduced when Diagnostics is closed. The updated candidate also passed sustained Android sleep tests in both display modes; those tests measure time asleep, **not battery lifetime**.

[View the full hardware validation dossier](docs/VALIDATION-EVIDENCE.md) · [CPU and sleep measurements](docs/evidence/CODE76-PHYSICAL-VALIDATION-2026-10-12.md)

---

### Current investigations

We are still looking into the following issues. **These are research areas, not advertised fixes in v1.7.1.**

- **🖥️ 120 Hz / screen tearing** — Understanding mixed-refresh behaviour when the two Thor displays run at different rates. [Research #37](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/37).
- **🔌 Device-service reliability** — Investigating rare cases where Android temporarily loses access to a built-in device service.
- **🎮 TOP-mode game focus** — Checking reports of games losing focus when the bottom screen is off.
- **⚡ Further efficiency and reliability** — More hardware edge cases, safer recovery and precise profiling. [Engineering research #67](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/67).

Interested in future device support, including Retroid Pocket Duo? That's **research only** and requires separate volunteer hardware testing — it is **not supported** today. [Full roadmap](docs/ROADMAP.md).

---

### How to use

1. **[Download Jesty Thor Fix v1.7.1](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.7.1)** (official signed APK)
2. Install and open **Jesty Thor Fix**
3. Enable the controls you want
4. Close the app (or swipe it away). The fixes continue in the background.

The app uses the Thor’s own privileged bridge. No Magisk or root required.

> **CPU Fix note:** Turning this fix on or off restarts the Android interface once. Open apps may close, and the screen can briefly go black. This is not a full device reboot.

---

### Safety

- Does **not** write CPU frequencies, voltages, thermal limits, or firmware
- Validates the result after every change
- Automatically restores normal behaviour if something looks wrong
- **Lid Guard is optional:** enable it only if you want extra protection against accidental wakes while the lid is shut. Normal recovery was tested; an active wake interception was not observed.
- Background efficiency, wake recovery and exact signed-build acceptance are covered by [physical validation data](docs/VALIDATION-EVIDENCE.md).

More details on resource use and known limitations are in the documentation below.

---

### Compatibility

| Device | Status |
|--------|--------|
| **AYN Thor** | Supported (tested on Android 13 firmware `TKQ1.231222.001`) |
| **Retroid Pocket Duo** | Research / testers wanted — not supported yet |
| Other devices | Unsupported |

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

[Physical validation & performance evidence](docs/VALIDATION-EVIDENCE.md) · 
[Public evidence policy](docs/PUBLIC-EVIDENCE-POLICY.md) · 
[Architecture](docs/ARCHITECTURE.md) · 
[Diagnostics](docs/APP-DIAGNOSTICS.md) ·
[Diagnostics RC validation](docs/APP-DIAGNOSTICS-RC-VALIDATION.md) · 
[Compatibility](docs/COMPATIBILITY.md) · 
[Benchmarks](docs/BENCHMARKS.md) · 
[What's new in v1.7.1](docs/RELEASE-NOTES-1.7.1.md) ·
[Build from source](docs/BUILD-AND-RELEASE.md) · 
[Release integrity](docs/RELEASE-INTEGRITY.md)

<details>
<summary><strong>Technical implementation</strong></summary>

Jesty Thor Fix follows the physical TOP/BOTH switch. It waits for Android and both displays to settle, then verifies the hardware state (CRTC).

The CPU Fix applies the tested Thor-side setting and performs its required one-time Android display-component recovery.

Closed-Lid Wake Guard performs a guarded sleep request only after verifying the current lid and device state.

See [Architecture](docs/ARCHITECTURE.md) for the full service, daemon, verification and boot behaviour.

</details>

<details>
<summary><strong>License, provenance & independence</strong></summary>

- Source & build scripts: [GPL-3.0](LICENSE)
- Branding & artwork: [ASSETS-LICENSE.md](ASSETS-LICENSE.md)
- Third-party notices: [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md)
- Project provenance & attribution: [NOTICE](NOTICE.md) · [research ledger](PROVENANCE.md)
- AI assistance: [full disclosure](AI_DISCLOSURE.md)

Independent community project. Not affiliated with or endorsed by AYN.  
Hardware claims are based on physical device evidence.

</details>
