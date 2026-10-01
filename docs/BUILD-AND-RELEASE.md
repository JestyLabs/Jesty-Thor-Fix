# Build and release (maintainer quick guide)

This project builds on Windows with PowerShell, JDK, Android SDK build-tools
35.0.0, and `tools/apktool.jar`. Keep the keystore and password file outside
the repository. Never commit either of them or an APK.

1. Update `android:versionCode` and `android:versionName` in
   `apk/AndroidManifest.xml`, `DaemonIdentity.VERSION`, and the default
   `ArtifactBaseName` in `build.ps1`. The host test rejects a manifest/daemon
   version mismatch. Add the previously installed daemon version to the
   narrow `PreviousSecureDaemonIdentity` migration allowlist and test it;
   never accept an arbitrary reported version or kill an unidentified PID.
2. Run the host tests and build:

   ```powershell
   .\scripts\test-boot-lid.ps1
   .\scripts\test-dashboard.ps1
   .\build.ps1 -Sign -KeystorePath $signKeyPath -PasswordFile $signPasswordFile
   ```

   The signing paths are private maintainer inputs. Without a password file,
   the build script asks for the password interactively. Verify the build
   output reports alignment, v1/v2/v3 signatures, the established certificate,
   and the APK SHA-256. The output is `dist\<ArtifactBaseName>.apk`.
3. Install the **exact signed APK** over the installed version on the Thor only
   after checking battery, AYN mode, and both physical CRTCs. Record the
   installed version, daemon identity, actual display state, logs, and any
   required user observation. A signed build or host test is not device
   validation. Stop at the first unexpected restart, panel state, or error.
4. Stage only source and documentation, run `scripts\prepublish.ps1`, review
   `git diff --cached`, commit, and update the work branch through an authenticated
   transport that has been verified on this host. The local Windows
   `git-remote-https.exe` has crashed during push; do not retry `git push`
   blindly. The `gh api` Git objects/ref route has worked: compare the remote
   head before upload, update the ref without force, then verify the PR head.
   Keep the candidate APK out of Git; the release asset is uploaded separately.
5. A diagnostic **pre-release** may be published before physical tests when
   the release notes and README clearly mark it untested on the Thor and the
   stable release remains recommended. Only after
   the agreed gates in `VALIDATION-1.5.16-PENDING.md` pass may that exact
   artifact be considered for stable promotion. v1.5.16 completed the scoped
   supervised checks and received maintainer approval for stable release.
   Attach only that APK,
   record its SHA-256 and certificate in release documentation, and verify the
   downloaded asset hash. If staged as a pre-release, promote the **same release
   and unchanged APK** to stable after maintainer approval of the scoped
   physical/security evidence. If the APK changes,
   increment the version/code and repeat affected validation.

Example GitHub CLI workflow for the stable v1.5.16 release after maintainer
approval (write and verify the release-notes file first):

```powershell
$gh = 'C:\Temp\gh-2.101.0\unpacked\bin\gh.exe'
& $gh auth status
& $gh release create v1.5.16 'dist\Jesty-Thor-Fix-1.5.16.apk' --latest --title 'Jesty Thor Fix v1.5.16' --notes-file 'docs\RELEASE-NOTES-1.5.16.md'
& $gh release view v1.5.16
```

Do not run the release command merely because the branch is pushed. Confirm
the agreed physical result, exact signed APK hash, and remote main tree first.
