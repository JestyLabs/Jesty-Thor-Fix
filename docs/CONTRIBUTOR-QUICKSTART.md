# Clean-checkout development quick start (unsigned only)

This guide describes how to compile and test **Jesty Thor Fix** without a Thor, root access, production keys, device identifiers, or GitHub environment credentials. An unsigned local APK is **not** an accepted release or an installable upgrade of the published application.

## Windows prerequisites

- **JDK 17** (Temurin or an equivalent JDK); set `JAVA_HOME` to that JDK's root. The build script invokes Windows `java.exe`, `javac.exe` and `jar.exe`.
- **PowerShell** with permission to execute the checked-out scripts (CI uses `pwsh` on Windows); use the checkout directory as the current directory.
- **Android SDK** with `platforms;android-34` (including `android.jar`) and **`build-tools;35.0.0`**, which supplies `d8.bat`, `zipalign.exe` and `apksigner.bat`. Set `ANDROID_SDK_ROOT` to the SDK directory, or pass `-AndroidSdk` to `build.ps1`.
- **Apktool 3.0.3**, stored outside the repository or in ignored `tools/apktool.jar`. The CI-pinned SHA-256 of the exact Apktool JAR is `DBF930B076C6B9BE08D57C449CACEFC3BDD6B71EBD59B3066FC0E1F5B14F9423`. Obtain it from the upstream Apktool release and verify the downloaded bytes before use; do not commit binaries or substitute an unverified JAR.

### Check your toolchain

```powershell
java -version
javac -version
$env:JAVA_HOME = 'C:\Path\To\Your\JDK17'
$env:ANDROID_SDK_ROOT = 'C:\Path\To\Android\Sdk'
$env:APKTOOL_JAR = 'C:\Path\To\apktool-3.0.3.jar'
$expected = 'DBF930B076C6B9BE08D57C449CACEFC3BDD6B71EBD59B3066FC0E1F5B14F9423'
$actual = (Get-FileHash -LiteralPath $env:APKTOOL_JAR -Algorithm SHA256).Hash
if ($actual -cne $expected) { throw 'Unexpected Apktool JAR hash' }
```

The placeholders above are **examples**, not machine-specific paths from a maintainer's computer. Ensure `JAVA_HOME` points to JDK **17** when testing the release-compatible build. Do not add a keystore or password to this checkout.

## Test before building

Run from the repository root. These test-only paths do not require ADB or an attached device:

```powershell
./scripts/test-boot-lid.ps1
./scripts/test-dashboard.ps1
./scripts/test-app-diagnostics.ps1
./scripts/test-release-policy.ps1
./scripts/test-public-content.ps1
```

The dashboard suite includes `UpdateCommitGateTest`, the `UpdateReadiness` host-clock characterization, `UpdateConfirmationStore` tests, and the host-only `UpdateInstallReceiver` callback harness. The boot/lid suite includes peer-UID/mode and strict IPC ACK response tests. The Android placeholders under `tests/fixtures/` exist **only for host tests**, not for packaging.

The host Java suite and the real Android 13 emulator suite exercise **different boundaries**: the emulator runs in PR CI with a disposable signing key and no vendor bridge. Neither is physical Thor validation.

## Build an unsigned artifact

```powershell
./build.ps1
# Or, with explicit non-secret tool locations:
./build.ps1 -AndroidSdk $env:ANDROID_SDK_ROOT -ApktoolJar $env:APKTOOL_JAR -JavaHome $env:JAVA_HOME
```

The builder compiles Android hidden-API compatibility stubs from `stubs/`, compiles `src/` with Java source/target 8 against Android 34 and those stubs, merges classes with the Apktool/Smali base and D8, then zipaligns the result. The unsigned output is `dist/<ArtifactBaseName>-unsigned.apk`. The current default `ArtifactBaseName` is declared at the top of `build.ps1`; use that value rather than assuming a historical release version. A custom name may be supplied through the build script's `-ArtifactBaseName` option. **Do not install it as an in-place update**, rename it as a signed release, or upload it to GitHub Releases.

If a clean build fails, report the exact failing command and dependency versions, not your full environment or private paths. Prefer a minimal, sanitized diagnostic excerpt. CI uses the same checked-in Windows build script and public-source tests. The exact published stable APK is produced only via the separately protected signing and release workflow.

## Change review and contribution boundaries

- Keep each refactor small: characterization test, isolated code change, and then full CI. Mark whether the test uses **host mocks**, **Android emulator**, or **physical hardware**.
- Preserve `UpdateVersion` / `UpdateCommitGate` policy and all privilege, boot, screen, CPU and Lid Guard behavior unless a dedicated approved change explicitly addresses them.
- Changes to vendor IPC, real display/CPU restart, boot ordering or wake repair need separate scoped **physical-owner acceptance** before publication; only the owner can authorize a Thor session.
- Never commit APKs, proprietary firmware dumps, raw private evidence, keystores, account tokens, or signing material. Review [CONTRIBUTING.md](../CONTRIBUTING.md), [SECURITY.md](../SECURITY.md) and [publication policy](../SECURITY-PUBLICATION.md).
