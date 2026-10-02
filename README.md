<p align="center">
  <img src="assets/branding/jesty_thor_header_lockup.png" alt="Jesty Thor Fix" width="620">
</p>

<p align="center">
  <strong>Make the AYN Thor behave the way it should.</strong><br>
  Truly turn off the lower display in TOP mode, let CPU clocks settle when using
  AYN Dashboard, and keep accidental closed-lid wakes from draining the battery.
</p>

<p align="center">
  <a href="https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.16"><strong>Download v1.5.16</strong></a>
  · <a href="#the-three-controls">What it does</a>
  · <a href="#why-true-off-matters">Why it matters</a>
  · <a href="#get-started">Get started</a>
</p>

**Made for the AYN Thor.** No Magisk, terminal, or manual rooting. Set it once;
the fixes keep working when the app is closed.

Jesty Thor Fix started after I noticed two odd behaviors on my own Thor:
**TOP mode was not actually shutting down the lower display hardware**, and
under the same kind of light-load use the **AYN Dashboard could leave the
LITTLE and BIG CPU clusters running at unusually high clocks**.

I originally documented the investigation on Reddit, including the first
measurements and the discussion that followed:

**[Original AYN Thor investigation / discussion on Reddit](https://www.reddit.com/r/AynThor/comments/1wrsmmo/found_two_weird_ayn_thor_issues_top_only_doesnt/)**

That investigation eventually became the three independent controls below.

## The three controls

| Control | What it does |
| --- | --- |
| **True Bottom Screen Off** | In TOP mode, powers down lower-display hardware that the stock mode can leave active behind a black screen. Follows the physical AYN TOP/BOTH switch and repairs true-off after sleep/wake. |
| **AYN Dashboard CPU Fix** | Stops the reproduced AYN Dashboard behavior that keeps LITTLE/BIG CPU clocks pinned high under light load. It does not set frequencies or governors. |
| **Closed-Lid Wake Guard** | When enabled, returns an accidental wake to sleep if the lid is still closed. Opening the lid allows normal wake. **OFF by default.** |

Use any combination of the three. The app shows the actual display and CPU
state, so you can tell whether a fix is active.

<p align="center">
  <img src="docs/images/dashboard-both-v1.5.15-review.png" alt="Jesty Thor Fix dashboard showing its three controls and live BOTH-screen and CPU status" width="780">
</p>

<p align="center">
  <sub>Jesty dashboard on the Thor's upper screen. Physical v1.5.15 capture; the interface is unchanged in v1.5.16.</sub>
</p>

## Why true off matters

An OLED displaying black pixels can consume very little panel power, but
**black pixels are not the same thing as powering down the display hardware**.

On the tested Thor, native TOP mode can leave the lower display pipeline active
even though the screen appears completely black. Jesty verifies the physical
display state and shuts that lower pipeline down.

During the same investigation, a second and separate behavior also showed up:
the AYN Dashboard could leave the LITTLE and BIG CPU clusters running close to
their highest clocks even under light load.

That distinction matters: **Jesty does not claim that the OLED panel being
black instead of physically off is, by itself, responsible for all observed
power savings.** The display state and CPU behavior are separate issues and
are handled by separate controls.

## Measured behavior

A short controlled **A/B/A test on physical hardware** compared native TOP mode
with the corrected state while keeping the same USB connection, power profile,
brightness, and workload.

| Metric | Native TOP | Corrected state |
| --- | ---: | ---: |
| LITTLE mean | 2.016 GHz | 1.616 GHz |
| BIG mean | 2.707 GHz | 1.654 GHz |
| BIG samples near max | 75 / 75 | 0 / 45 |
| System-power proxy mean | 2.030 W | 1.239 W |
| Battery temperature | 30.0 °C | 30.0 °C |

The two native passes measured approximately **1.93 W and 2.18 W**, with the
corrected pass between them at approximately **1.24 W**.

These are short-run diagnostic measurements, **not a battery-runtime claim**.
The power figure is derived from the Thor firmware's USB/battery telemetry
rather than a calibrated external power meter, so charger losses, sampling
timing, background work, and battery regulation can affect the result.

Full methodology and raw CSV data are available in the
**[benchmark documentation](docs/BENCHMARKS.md)**.

## How it works

Jesty follows the Thor's physical TOP/BOTH mode rather than choosing a mode
for you. It waits for Android and both displays to settle before changing
hardware, then checks the result.

Sleep/wake transitions are watched so true-off can be restored if Android
brings the lower display back during wake.

The **CPU Fix** addresses the Thor vendor display behavior responsible for the
reproduced clock pinning. It does not write CPU governor or frequency values.

Enabling the CPU Fix requires **one Android UI/display restart**. Its saved ON
setting can cause the same restart during a fresh boot, which may look like a
second boot. The kernel does not reboot.

The dashboard reports the fix active only after the vendor setting is read back
and verified.

The **Wake Guard** is independent of the other two fixes. It only acts while
the lid is closed and is **disabled by default**.

## Get started

1. [Download the latest signed APK](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.16) and install it on an **AYN Thor**.
2. Open Jesty Thor Fix and enable the controls you want.
3. Close the app or switch to a game. Your choices are saved and restored after boot.

The app uses the Thor firmware's built-in privileged bridge. There is no Magisk
installation, manual rooting procedure, or terminal setup required.

Swiping it away from Recents is fine; Android **Force stop** pauses its
background service until you open the app again.

## Compatibility and details

Built for the **AYN Thor**; tested on its Android 13 firmware
`TKQ1.231222.001` (2026-02-06 build).

Power savings depend on device state, workload, brightness, firmware, and how
you use the device; **no fixed battery-life percentage is claimed**.

[Original Reddit investigation](https://www.reddit.com/r/AynThor/comments/1wrsmmo/found_two_weird_ayn_thor_issues_top_only_doesnt/) ·
[Release notes](docs/RELEASE-NOTES-1.5.16.md) ·
[How the service works](docs/ARCHITECTURE.md) ·
[Benchmarks](docs/BENCHMARKS.md) ·
[Build from source](docs/BUILD-AND-RELEASE.md) ·
[Release integrity](docs/RELEASE-INTEGRITY.md)

Testing a newer build? [v1.5.17](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.17)
is a pre-release for supervised Thor validation. v1.5.16 remains the stable
download above.

Jesty Thor Fix is free, open source under [GPL-3.0-only](LICENSE), and
independent of AYN. Artwork terms are in [ASSETS-LICENSE.md](ASSETS-LICENSE.md);
third-party notices and the [AI disclosure](AI_DISCLOSURE.md) are also
available.

If Jesty Thor Fix is useful to you, you can
**[support development here](https://www.buymeacoffee.com/jesty)**.
