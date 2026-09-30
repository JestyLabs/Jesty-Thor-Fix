# Jesty Thor Fix 1.4.1 — testing pre-release

This update fixes TOP/BOTH mode reconciliation after the physical AYN button
shortcut. An inverted boot-hold branch had stopped the watcher from processing
mode changes after boot reached READY. With True Bottom Screen Off enabled, the
watcher also restores physical lower-display OFF if AYN reactivates that panel
in stable TOP mode. It waits for any pending wake repair rather than racing it.

The maintainer tested physical-button TOP/BOTH cycling on an AYN Thor with the
same watcher logic and reported it working; daemon logs recorded three ON and
three OFF watcher actions. The v1.4.1 APK passed boot/lid model tests, dashboard
tests, a clean build, manifest check, alignment and signing verification. This
version has no new UI or permissions and does not add a reboot to mode changes.

**This is still an opt-in testing pre-release, not a stable promotion.** The
five-boots-per-configuration matrix, closed-lid/false-wake/external-display
guard cases and further sleep/wake checks remain pending. Closed-Lid Wake Guard
defaults to OFF. A transient green lower screen was observed on older boots;
its cause is not confirmed and this release has not proved it gone. If you see
a flash, stuck display, wake loop or unexpected restart, disable both fixes
before another reboot and report the details. The CPU Fix can still perform
one expected Android display/UI stack restart when its setting changes.

APK SHA-256: `E2D67F5A02CD1CB1BD117A19A2F7775BD4D2B8A5216422D1B8D1110BB47F8A96`
Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`

Validation log: `docs/VALIDATION-1.4.1-PENDING.md`.
