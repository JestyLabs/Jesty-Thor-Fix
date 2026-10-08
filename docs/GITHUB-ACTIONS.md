# GitHub Actions build and release

The repository has **four** GitHub Actions workflows:

- `.github/workflows/host-tests.yml` (`CI`) runs on pull requests, pushes to
  `main` and manual dispatch. It runs the host suites, builds an unsigned APK,
  and on a trusted push to `main` also signs a candidate for device testing.
- `.github/workflows/codeql.yml` (`CodeQL Advanced`) scans GitHub Actions and
  Java/Kotlin on pull requests, pushes to `main` and a weekly schedule.
- `.github/workflows/sign-test-candidate.yml` (`Sign test candidate`) is a
  manually dispatched test-only signing path for a successful same-repository
  pull-request CI run; it cannot publish that APK through the release workflow.
- `.github/workflows/release.yml` (`Publish tested candidate`) is manually
  dispatched after physical validation; it verifies and releases the exact
  signed APK from a successful `main` CI run.

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

## Release authorization and outstanding administrative controls

The signed-main, manual test-signing and manual publication workflows now fail
before privileged work unless **both** `github.actor` and
`github.triggering_actor` are `SirJesty`. The source test-signing workflow
also verifies that the selected CI run used `.github/workflows/host-tests.yml`,
not merely a workflow with the same display name. These are **defense-in-depth
checks**, not a replacement for GitHub repository permissions: anyone allowed
to merge arbitrary workflow edits into `main` could remove these checks.

`.github/CODEOWNERS` names an owner for release workflows, signing/build
scripts and this guide. This file is **advisory until** a branch rule requires
code-owner reviews. Requiring such reviews on a one-person repository can
prevent the author from merging their own PR, so arrange a trusted second
reviewer or a deliberately designed owner-controlled bypass first.

**Administrative work that cannot be completed by changing repository files:**

1. Under **Settings -> Branches / Rules**, confirm `main` requires PRs, the
   `build`, `Analyze (actions)` and `Analyze (java-kotlin)` checks, resolved
   conversations and no force-push/delete. Review any admin bypasses.
2. Under **Settings -> Actions -> General**, limit workflow-token defaults to
   read-only; permit write only per job (publication currently needs
   `contents: write`). Restrict who has repository write/admin access.
3. Create a protected **signing environment** with required reviewer(s),
   permitted branch `main` and the two signing secrets scoped to that
   environment. Then **split main CI unsigned tests from signing** into
   separate jobs (so PR tests do not wait for signing approval), declare the
   signing environment on both secret-using jobs, verify gating end-to-end,
   and remove the old repository-scoped secrets. Merely naming an
   environment in YAML does not protect secrets; unconfigured environments
   can be created without reviewers, and repo secrets can still be visible.
4. Create a separate **publication environment** with permitted `main` and
   required reviewer(s), and declare it on the `publish` job. Confirm
   release permissions and that a refused approval cannot publish anything.
5. Verify with a harmless unauthorized-dispatch attempt, an authorized
   approval, an unchanged signed-APK checksum, and a blocked workflow-edit PR.
   Do not test by exposing keystores or publishing dummy stable releases.

**Current protection boundary:** runtime operator guards are implemented in
the workflow YAML. Environment approvals, secret re-scoping, a separate
signing job and protected code-owner review settings are **not yet
configured or claimed to be enforced**. Until they are, keep manual approval
of all release-signing and publication runs and do not treat the workflow
guards alone as sufficient for production supply-chain security.


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

## Signed test candidates from pull requests

A pull-request CI run deliberately builds only an unsigned APK. To install an experimental
candidate over an existing release-signed installation without merging experimental code to
`main`, use the manual **Sign test candidate** workflow after that PR CI run succeeds.

The signing workflow itself lives on trusted `main` and does not check out or execute the PR
source while signing secrets are available. It accepts a `candidate_run_id`, then requires:

- workflow name `CI`;
- event `pull_request`;
- completed successful run;
- the same repository, never a fork;
- exactly one non-expired unsigned artifact whose name ends in the source commit SHA.

It downloads that exact unsigned APK, verifies the package/version, signs those same APK bytes
with the established release key, verifies the certificate/package/version again, records both
unsigned and signed SHA-256 values, and uploads a separate
`signed-test-candidate-...` artifact for 14 days.

This artifact is for supervised device testing only. It is not eligible for
`Publish tested candidate`; the release path still requires the signed candidate produced by a
successful push to `main`.

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
