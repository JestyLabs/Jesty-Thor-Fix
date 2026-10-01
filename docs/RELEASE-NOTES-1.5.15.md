# Jesty Thor Fix v1.5.15 — testing pre-release

This update restores the AYN Dashboard CPU Fix after a cold boot on the tested
AYN Thor. Its firmware leaves the vendor CPU Fix property unconfigured at boot.
The app now distinguishes that state from a failed property read, applies a
saved ON setting once, and confirms the result after one compositor restart.
The restart also restarts the Android UI and may look like a second boot. It
does not restart the kernel.

The release also includes staged boot safety, private daemon IPC, BOTH/TOP
display reconciliation, and the optional Closed-Lid Wake Guard. The guard is
OFF by default. The CPU Fix description beside its toggle now fits on one
line at the existing font size.

The final signed APK passed host tests, signature and alignment checks, one
supervised in-place CPU Fix transition, and one genuine supervised cold boot.
Both physical screens showed normal image in BOTH, CPU Fix read back active,
and AYN Dashboard on the lower screen no longer kept LITTLE/BIG at their
maximum in the observed sample. The user reported no green flash, artifact,
or restart loop. The boot reached `BOOT READY` at about 95 seconds; that
duration is recorded for a separate performance investigation.

Physical BOTH/TOP, TOP sleep/wake, Hall close/open, a controlled closed-lid
wake, and the anti-loop limit were also exercised in this review. BOTTOM ONLY
and physical use with an external-display dock are deferred. This is a
testing pre-release; the earlier v1.3.0 stable release remains available.

APK: `Jesty-Thor-Fix-1.5.15.apk` (versionCode 64)

APK SHA-256: `093B6AF26E86E072343988C04CBF03256005703D567177E99D308B9BADC71F2B`

Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`

Detailed results: [validation diary](https://github.com/JestyLabs/Jesty-Thor-Fix/blob/v1.5.15/docs/VALIDATION-1.5.0-PENDING.md).
