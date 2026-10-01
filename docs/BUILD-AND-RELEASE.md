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
5. Only after the gates in `VALIDATION-1.5.0-PENDING.md` pass, create a tag and
   GitHub pre-release with the exact tested signed APK. Attach only that APK,
   record its SHA-256 and certificate in release documentation, and verify the
   downloaded asset hash. Promote the **same release and unchanged APK** to
   stable after the remaining physical/security gates pass. If the APK changes,
   increment the version/code and repeat affected validation.

Example GitHub CLI workflow (substitute the validated version and APK, and
write the release-notes file first):

```powershell
$gh = 'C:\Temp\gh-2.101.0\unpacked\bin\gh.exe'
& $gh auth status
& $gh release create v1.5.11 'dist\Jesty-Thor-Fix-1.5.11.apk' --prerelease --title 'Jesty Thor Fix v1.5.11' --notes-file 'docs\RELEASE-NOTES-1.5.11.md'
& $gh release view v1.5.11
```

Do not run the release command merely because the branch is pushed. In
particular, the v1.5.11 candidate is not cleared for public release yet.
