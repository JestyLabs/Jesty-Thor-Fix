# GitHub Actions build and release

The repository has two GitHub Actions workflows:

- `.github/workflows/host-tests.yml` runs on pull requests, pushes to `main`,
  and manual dispatch. It runs the host suites and builds an unsigned APK.
  A successful push to `main` also builds one signed candidate and stores that
  exact APK as an Actions artifact for 30 days.
- `.github/workflows/release.yml` is manual. It takes the run ID of a successful
  `main` CI build, downloads that exact signed candidate, verifies its source
  commit, version, SHA-256 and signing certificate, then creates a pre-release
  or promotes the same unchanged APK to stable.

This preserves the physical-validation rule: **test the exact signed APK that
will be released; do not rebuild after the Thor test.**

## One-time repository setup

Create these repository Actions secrets under
**Settings -> Secrets and variables -> Actions**:

- `THOR_KEYSTORE_B64`: Base64 of the established Android release keystore.
- `THOR_KEYSTORE_PASSWORD`: the keystore/key password used by `build.ps1`.

The key alias stays the existing `thor-display-power-auto` default in
`build.ps1`. Do not commit the keystore, password, Base64 text, or a signed APK.

PowerShell can generate the Base64 without modifying the keystore:

```powershell
$base64 = [Convert]::ToBase64String([IO.File]::ReadAllBytes($signKeyPath))
$base64 | Set-Clipboard
```

Paste the clipboard contents directly into the `THOR_KEYSTORE_B64` secret.

The CI job recreates the keystore only under the ephemeral runner temp
directory. The password is converted there to the DPAPI-protected password
file format already accepted by `build.ps1`; the plain password is not passed
as a command-line argument.

Until both secrets exist, pushes to `main` will fail deliberately at the
signed-candidate step. Pull-request and unsigned builds do not need the signing
secrets.

Because a workflow merged into `main` can use repository secrets, keep `main`
protected and require the CI/review policy appropriate for this repository.

## Pull requests

Every pull request runs:

```text
host tests
-> Java/APK build
-> zipalign verification
-> unsigned APK artifact
```

The unsigned artifact is retained for 7 days. It is useful for compilation and
packaging validation only; it cannot replace the installed release build.

The workflow downloads Apktool 3.0.3 from its upstream GitHub release and
checks the pinned SHA-256 before using it.

## Pushes to main

A successful push to `main` performs the same checks, then:

```text
decode release keystore in runner temp
-> build signed APK
-> apksigner verification
-> established certificate check
-> SHA-256
-> candidate metadata
-> signed-candidate Actions artifact
```

The signed artifact is retained for 30 days and contains:

- `Jesty-Thor-Fix-<version>.apk`
- `candidate-metadata.json`
- `candidate-sha256.txt`

Download **that exact signed APK** from the CI run and use it for the supervised
Thor validation.

The Actions run ID is the numeric value in a run URL:

```text
https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/123456789
                                                         ^^^^^^^^^
```

## Publish the tested candidate

After the exact candidate has the required physical approval:

1. Open **Actions -> Publish tested candidate -> Run workflow**.
2. Enter the successful CI `candidate_run_id`.
3. Choose `prerelease` or `stable`.
4. Run the workflow.

The publish workflow refuses candidates that are not from the completed CI
workflow on a successful push to `main` in this repository. It checks out the
exact candidate commit, downloads only the signed
candidate artifact from that run, and verifies:

- metadata commit == CI head SHA;
- manifest version/versionCode == artifact metadata;
- exact APK filename;
- package name, versionName and versionCode inside the APK;
- APK SHA-256;
- established release signing certificate;
- matching `docs/RELEASE-NOTES-<version>.md`.

If no release for `v<version>` exists, the workflow creates it from the exact
candidate commit and uploads the exact candidate APK.

If a pre-release already exists, the workflow first downloads its existing APK
and requires its SHA-256 and tag target to match the candidate. It can then promote that same
release to stable without replacing the asset. It refuses to demote an existing
stable release back to pre-release.

Finally it downloads the published asset again and verifies the SHA-256 one
more time.

## Version changes

The existing release guide still applies. Before the candidate build, keep
these version values aligned:

- `apk/AndroidManifest.xml`
- `DaemonIdentity.VERSION`
- the default `ArtifactBaseName` in `build.ps1`
- the guarded previous-daemon migration allowlist when required.

The host suite already rejects the relevant version mismatches.

## Cost

For this public repository, standard GitHub-hosted runners are covered by
GitHub Actions for public repositories. The workflows deliberately use standard
`windows-latest`, not larger paid runners. Artifact retention is kept short
(7 days unsigned, 30 days signed) to avoid unnecessary storage.
