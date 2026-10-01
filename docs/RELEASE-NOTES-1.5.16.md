# Jesty Thor Fix v1.5.16 — stable release

This candidate targets the boot delay observed with v1.5.15. A filesystem
socket inode can survive power-off; the previous launcher waited up to 30
seconds on that pathname before starting the daemon. v1.5.16 stamps the
daemon lock with the kernel boot ID and launches immediately when it can prove
the inode belongs to an earlier boot. If the ID cannot be read or the stamp is
missing, it keeps a bounded five-second grace. The first boot after upgrading
from v1.5.15 can therefore still take that fallback.

An authenticated daemon in a same-version boot phase is treated as already
starting. The compositor helper checks the old daemon's root UID and command
line before signalling it, waits for its exit, and aborts replacement if the
identity or exit check fails. Receiver, service, gate, compositor and helper
timestamps now appear in the local trace with PID, kernel boot ID and source.
The 10-second and 5-second boot grace periods, 8-second compositor floor and
60-second safety timeout are unchanged.

**Validation status:** Host model tests, the signed build, APK alignment and
v1/v2/v3 signature checks passed. This exact APK was installed over v1.5.15
without clearing data, then completed **one supervised cold boot** in BOTH.
The daemon reached `BOOT READY` at 65.479 seconds, both CRTCs were active,
the CPU Fix property read `1`, and one compositor restart occurred with the
same kernel boot ID. The owner saw the second visual Android UI boot but no
green flash or artifact. This is one observation; other boot conditions and
longer-term reliability remain unproven. There are no v1.5.16 screenshots yet.
v1.5.16 is now the stable release; v1.5.15 remains available in history.

The boot trace and filtered Android log were retained locally outside Git.
The first daemon began at 35.364 seconds, with a 100 ms launch wait after
classifying the prior-boot socket; the staged safety waits were unchanged.
See the [validation diary](https://github.com/JestyLabs/Jesty-Thor-Fix/blob/v1.5.16/docs/VALIDATION-1.5.16-PENDING.md).

APK: `Jesty-Thor-Fix-1.5.16.apk` (versionCode 65)

APK SHA-256: `4C697E4AD43D689BAF14ECD8184F827AC1E3E86BFD4CA198B18334104B6050F5`

Signing certificate SHA-256: `727D4850779BED1E51018108E13BC399D4DA38CFC68F4F7504120AD5E2DAD6FC`
