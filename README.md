<p align="center">
  <img src="assets/branding/jesty_thor_header_lockup.png" alt="Jesty Thor Fix" width="620">
</p>

<p align="center">
  <strong>Make the AYN Thor behave the way its screens and lid should.</strong><br>
  Truly turn off the lower display in TOP mode, let CPU clocks settle when using
  AYN Dashboard, and keep accidental closed-lid wakes from draining the battery.
</p>

<p align="center">
  <a href="https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.16"><strong>Download v1.5.16</strong></a>
  · <a href="#the-three-controls">What it does</a>
  · <a href="#get-started">Get started</a>
  · <a href="#whats-new-in-v1516">What's new</a>
</p>

**Made for the AYN Thor.** No Magisk, terminal, or manual rooting. Set it once;
the fixes keep working when the app is closed.

## The three controls

| Control | What it does |
| --- | --- |
| **True Bottom Screen Off** | In TOP mode, powers down lower-display hardware that the stock mode can leave active behind a black screen. Follows the physical AYN TOP/BOTH switch and repairs true-off after sleep/wake. |
| **AYN Dashboard CPU Fix** | Stops the reproduced AYN Dashboard behavior that keeps LITTLE/BIG CPU clocks pinned high under light load. It does not set frequencies or governors. |
| **Closed-Lid Wake Guard** | When enabled, returns an accidental wake to sleep if the lid is still closed. Opening the lid allows normal wake. Pauses after repeated blocked wakes to avoid a loop. **OFF by default.** |

Use any combination of the three. The app shows the actual display and CPU
state, so you can tell whether a fix is active.

<p align="center">
  <img src="docs/images/dashboard-both-v1.5.15-review.png" alt="Jesty Thor Fix dashboard showing its three controls and live BOTH-screen and CPU status" width="780">
</p>
<p align="center"><sub>Jesty dashboard on the Thor's upper screen. Physical v1.5.15 capture; the interface is unchanged in v1.5.16.</sub></p>

## What's new in v1.5.16

Boot restoration starts sooner when a private socket was left by the previous
boot. On one supervised Thor cold boot, Jesty reached READY at **65.5 seconds**
instead of about **95 seconds** in the earlier v1.5.15 observation. The safety
waits were kept, and the restart helper now checks the old daemon's identity
before replacing it. Boot tracing makes future timing problems easier to
diagnose. These are observed timings, not a speed guarantee.

The signed v1.5.16 APK passed host checks, an in-place upgrade and that
supervised cold boot. BOTH showed normal image on both screens, CPU Fix was
active, and no green flash or artifact was reported. Earlier physical tests
covered TOP true-off, TOP sleep/wake, closed-lid wake return and the guard's
anti-loop pause. [Read the validation diary](docs/VALIDATION-1.5.16.md).

## How it works

Jesty follows the Thor's physical TOP/BOTH mode rather than choosing a mode
for you. It waits for Android and both displays to settle before changing
hardware, then checks the result. The Wake Guard only acts while the lid is
closed and is disabled by default.

Enabling the CPU Fix requires **one Android UI/display restart**. Its saved ON
setting can cause the same restart during a fresh boot, which may look like a
second boot. The kernel does not reboot. The dashboard reports the fix active
only after the vendor setting is read back.

## Get started

1. [Download the latest signed APK](https://github.com/JestyLabs/Jesty-Thor-Fix/releases/tag/v1.5.16) and install it on an **AYN Thor**.
2. Open Jesty Thor Fix and enable the controls you want.
3. Close the app or switch to a game. Your choices are saved and restored after boot.

The app uses the Thor firmware's built-in privileged bridge. Swiping it away
from Recents is fine; Android **Force stop** pauses its background service until
you open the app again.

## Compatibility and details

Built for the **AYN Thor**; tested on its Android 13 firmware
`TKQ1.231222.001` (2026-02-06 build). BOTTOM ONLY and use with an external
display dock are outside the validated scope. Power savings depend on how you
use the device; no battery-life percentage is claimed.

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
available. [Support development](https://www.buymeacoffee.com/jesty).
