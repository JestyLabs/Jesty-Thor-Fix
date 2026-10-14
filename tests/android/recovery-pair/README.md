# Frozen forward-recovery installation gate

This isolated test-only workflow consumes the exact approved CI unsigned APKs.
It runs on its dedicated validation branch, leaving the candidate branch frozen.
It does not rebuild product code, use production signing or contact a Thor.
The candidate source/artifact remains frozen at `8d29357`; independent recovery
remains at `8746ea1`, based on published stable `f929ff9`.

The runner signs copies and a separate instrumentation APK with one disposable
key, checks signer equality and unchanged uncompressed payloads, and removes
the private key. It accepts only a fresh named API33 x86_64 emulator with no
vendor bridge and no existing target package. Every ADB command targets that
specific emulator. The target is never uninstalled or cleared.

Instrumentation writes seven real preferences through Android SharedPreferences:
three dashboard settings and four updater settings, with Boolean/Long/String
type checks and explicit key presence. It also exercises the actual automatic
update-check settings listener and reads the real settings dialog. Hardware
controls remain OFF. A nonce-derived private file and checkpoint provide a
separate application-data oracle.

The independent installation uses `adb install -r`, then verifies the recovery
version and loaded daemon constants, preferences and private data before opening
the app. UID, first-install time and data directory must remain unchanged, and
both actual preference XML files must be byte-identical across installation.
The real recovery Activity and updater settings are reopened, with values read
again from a fresh process. No verification step reseeds preferences.

Post-acceptance negative controls separately corrupt one actual preference and
the private file. Each must produce its specific verification failure, followed
by restoration and a fresh successful read/reopen. Raw captures and ephemeral
signed APKs are not published; only a sanitized result is printed.

Passing establishes this generic Android install/data-retention path with a
test certificate. Production certificate compatibility, vendor effects, daemon
handover, firmware display/wake behavior and PR82 remain outside its scope.
