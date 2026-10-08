# GitHub Actions build and release

The repository has **four** GitHub Actions workflows:

- `.github/workflows/host-tests.yml` (`CI`) runs on pull requests, pushes to
  `main` and manual dispatch. It runs the host suites, builds an unsigned APK,
  and on pushes to `main` runs a **separate, environment-gated job** that signs
  the exact unsigned APK from the successful build job.
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

## Protected environments and one-time migration

Administrative readback on 2026-10-08 confirmed `thor-signing` and
`thor-publication`: only the **branch** `main` is allowed, `SirJesty` is the
required reviewer, self-review is allowed for the solo maintainer, and
administrator bypass is disabled. The readiness variables are scoped to their
respective environments. Both signing secrets are staged in `thor-signing`;
the existing local keystore certificate matches the established signer.

Repository-scoped copies are temporarily retained until a protected signing run
has succeeded and its artifact has been checked. Therefore repository-wide
secret isolation is **not yet complete**. Do not treat PR CI success or Settings
readback as evidence that a protected signing/publication job has run.

The following is the migration/recovery procedure, not a claim that all runtime
acceptance tests have completed:

The workflow files declare **two named GitHub Environments**. A YAML reference
alone does **not** create reviewers or protect secrets: configure the environments
in the repository **before merging this PR**. Both environments must allow only
deployments from `main` (a same-repository PR's build never enters either one).

1. In **Settings -> Environments**, create `thor-signing`: restrict deployment
   branches to `main`, configure required reviewer(s), and check whether
   `Prevent self-review` would block the actual maintainer/review model.
   A sole maintainer cannot satisfy an enforced non-self-review rule alone;
   arrange a trusted second reviewer before enabling that restriction.
2. Add **environment secrets** `THOR_KEYSTORE_B64` and
   `THOR_KEYSTORE_PASSWORD` to `thor-signing`. Create the Base64 locally
   from the existing keystore; never print or commit it. Keep the signing
   alias `thor-display-power-auto` and verify the established public
   certificate fingerprint. If the secrets also exist at repository scope,
   leave them only during the controlled migration window.
3. Add an **environment variable** `THOR_SIGNING_ENV_READY=true` to
   `thor-signing` **only after** required reviewers, `main` restrictions
   and environment-scoped secrets are confirmed. The job fails closed if
   this ready marker is absent or different. Do not add the marker at the
   repository or organization scope.
4. Create `thor-publication`, restrict deployment branches to `main`,
   require reviewer(s), and add its **environment variable**
   `THOR_PUBLICATION_ENV_READY=true` only after the rules are saved.
   The `Publish tested candidate` job requests this approval before it
   gets the job-scoped `contents: write` GitHub token.
5. Confirm the `main` branch requires PRs and the `build`,
   `Analyze (actions)`, `Analyze (java-kotlin)` checks, disallows force
   pushes/deletion and applies applicable admin restrictions.
   `.github/CODEOWNERS` is only advisory unless review enforcement is
   configured. For a solo maintainer, enabling required CODEOWNERS approval
   without a separate eligible reviewer can block every self-authored PR.
6. Merge the workflow change only when environments are configured. On a
   permitted push, **CI / build** finishes with its unsigned artifact and
   required check without awaiting secrets. **Sign main candidate** is a
   separate job; it must wait for the protected `thor-signing` approval,
   validate the downloaded unsigned APK, then sign **those exact bytes**.
   Check the signed candidate metadata, digest and signer certificate.
   Similarly require `thor-publication` approval for a legitimate
   supervised release (do not publish a dummy stable release).
7. Once both workflows are proven and you have checked no other workflow
   consumes the repository-level signing secrets, **delete the repository
   scoped** `THOR_KEYSTORE_B64` and `THOR_KEYSTORE_PASSWORD` secrets.
   Do not delete the only recoverable signing keystore; retain an offline
   owner-controlled backup. Confirm denied/unauthorized runs never sign
   or publish. Restrict repo collaborators, tag/release permissions, and
   keep repository-default `GITHUB_TOKEN` permissions read-only.

**Migration caveat:** until the environment approvals and secret relocation are
applied in GitHub settings, this is only configuration-as-code preparation.
An automatically created environment has no default protection. The
`*_ENV_READY` markers make intended jobs fail rather than silently sign,
but cannot prevent another, malicious workflow merged by an authorized
writer from using repository-level secrets while those still exist.

**Authorization:** both the original and rerun actors must be `SirJesty`,
checked before a privileged job requests an environment and again at runtime.
This is only defense in depth; branch rules,
review rights and protected environments are the meaningful trust boundary.

### Current job permissions and artifacts

- `CI / build`: unsigned build, pure host checks and artifacts, **no signing
  secrets or signing environment**. `build` remains the required status check.
- `CI / Sign main candidate`: depends on green `build`, restricted
  `thor-signing` environment and operator, `actions: read` /
  `contents: read` GitHub token. Signs the same-run unsigned artifact,
  verifies package/version/certificate, publishes the existing
  `signed-candidate-v<version>-<commit>` artifact and metadata.
  It uses the signing keystore in an ephemeral temporary directory and
  deletes it before the verification/upload steps.
  Metadata records the unsigned input SHA-256, source run/attempt, signed APK
  SHA-256 and certificate; unsigned and signed whole-file hashes differ normally.
- `Sign test candidate / sign`: manual signing from a successful same-repo
  pull-request CI artifact only, also gated by `thor-signing` and operator.
  Its signed **test** APK is not publishable through the production release path.
- `Publish tested candidate / publish`: manual `main` only,
  `thor-publication` and operator gate, serial publication;
  verifies the **same signed candidate** and uses job-scoped
  `contents: write` for the release.


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

A successful push to `main` completes the same unsigned checks. A separate
`sign_main_candidate` job then waits for `thor-signing` approval and:

```text
download exact unsigned APK from the same CI run
-> verify zipalign, package and version
-> load environment-scoped release keystore in runner temp
-> sign exact CI APK bytes with apksigner
-> delete the keystore
-> verify certificate, package, version and SHA-256
-> candidate metadata
-> signed-candidate Actions artifact
```

The signed artifact is retained for 30 days and contains:

- `Jesty-Thor-Fix-<version>.apk`
- `candidate-metadata.json`
- `candidate-sha256.txt`

Download **that exact signed APK** from the CI run **after the signing job
has been approved and completed**, and use it for supervised Thor validation.
If approval is withheld, CI can have a green `build` job but has no signed
candidate and cannot be published.

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

The publish workflow first waits for `thor-publication` approval and refuses
candidates that are not from the **completed successful CI workflow** (including
its signing job) on a push to `main` in this repository. It checks out the
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
