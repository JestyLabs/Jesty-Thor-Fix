# PR67 closure decisions and candidate activation

> **Historical checkpoint:** this document preserves preparation on 2026-10-14. Candidate activation and the approved supervised session subsequently completed; see [the 2026-10-16 closure and limits](PR67-PHYSICAL-VALIDATION-2026-10-16.md) and [integration gates](PR67-INTEGRATION-GATES-2026-10-16.md). Dated main, signing and NOT RUN statements below are not current execution instructions.

Runtime/test baseline: `e190544b311cc173c9ae19a70d2ed77c6768340a`.
Current main: `1868aca0cb5cbcdd28218d43cb87135921195d7c`; its tree matches
the reviewed merge base `9714de9726fca22f417e624a3834a59b20f50b0a`.
This is preparation for review, not signed-candidate or physical acceptance.
Further optional architecture and UI changes remain frozen.

## Verified offline checkpoint

[CI38568124004](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/38568124004)
completed successfully, including build and the generic Android lifecycle and
installer job. Signing was skipped. Both jobs in
[CodeQL analysis38568123688](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/38568123688)
succeeded. The aggregate CodeQL failure at that original snapshot was later
resolved by the scoped formal false-positive disposition of alert30. At head
`6fc1f51`, [CI38573605900](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/38573605900)
completed successfully, including Android; both analyses and the aggregate
CodeQL check are SUCCESS. Owner-authorized disposition followed independent
review and the original current SARIF. Scanner execution alone was not used
as a disposition.

The CI artifact is compiled at synthetic merge
`9732e66cbf2176fff590a45a1bc6df6068965e8c`, associated with the pinned PR head.
Its manifest remains package `com.thor.displaypowertest`, version `1.7.1`,
code76 and target SDK25. The unsigned artifact's DEX defines233 classes;
the inspected inventory contains no Android fixture class definitions or the
named updater/UI/native harness entry points. Required product entry points
are present. This is artifact inspection, not runtime proof or signed provenance.

## Reviewed runtime scope

The baseline diff contains17 changed or added Java source files. Main was
reconciled read-only; the differing commit history has no net tree change from
the reviewed merge base, so no merge is needed merely to change that history:

| Responsibility | Files / behavior | Applicable evidence and remaining boundary |
| --- | --- | --- |
| Updater | AppUpdater, receiver, confirmation/session ownership, queued-task dispatch, native-session admission, release catalog | Controlled real-source host and Android installer/consent/failure/recreation suites; live release discovery and vendor daemon activation remain separate |
| IPC | SecureChannel, server close-error handling, strict ACK policy and client | Host/native transport evidence and stale-probe regression; no complete vendor privilege or handover acceptance |
| Boot trace | BootCoordinator and BootTraceFieldPolicy | Characterization of extracted formatting; no claimed change to boot timing or CPU controller behavior |
| Display safety parser | ExternalDisplayModel | Fail-closed malformed/unknown viewport tests; physical Guard/external-display truth remains outside generic emulator coverage |
| UI | Diagnostics, dashboard layout and views | Native generic Android checks; TalkBack, physical controls and device-specific layout remain explicitly bounded |

At the reviewed runtime baseline before the approved identity bump, blob
comparison confirms D, CpuFixController, AutoService, PServer, predecessor
policy, DaemonIdentity, UpdateVersion, manifest, boot receiver, build script and
signing/release workflows are unchanged against the reviewed main baseline.
The parser and IPC corrections do change product behavior; an unchanged CPU
controller does not make the entire candidate equivalent to code76.

## Physical-entry gates after independent review

- [x] Formal CodeQL30 disposition recorded and verified as contextual false
  positive; check SUCCESS at `6fc1f51`. This is specific to the reviewed result.
- [x] Independent risk review conditionally accepts the first activation as
  in-place installation followed by a normal owner-controlled reboot. The
  stale-probe correction is accepted at source level; live handover remains
  unvalidated. The decision does not authorize installation or certify all IPC.
- [ ] Approve a candidate/recovery pair, activation method and artifact provenance.
   Higher version/code, daemon identity and rollback constraints must be resolved
   together. Exact final-candidate CI is required after any metadata/source change.

Branch-protection enforcement remains a separate pre-merge administrative
decision. This checkpoint does not apply the previously prepared settings proposal.

## Recommended first candidate activation: install, then controlled reboot

For the consolidated owner session from stable1.7.1, prefer an approved in-place
install followed by a normal owner-controlled reboot, before opening the new app
or issuing further app commands. Preserve package data. Do not expand the stable
predecessor allowlist merely to make a live handover test pass.

This recommendation deliberately bounds the activation test: it does not close
the production live-handover safety question. After a reboot, the former process
is gone; the boot receiver requests the held launch path. Vendor stale-endpoint
classification and subsequent BOOT READY/health must still be observed. A host
model or successful APK install cannot establish that these device events occurred.

The current code76 artifact cannot test a true upgrade from stablecode76. The
owner approved candidate `1.7.2-rc1/code78`, stable-source forward recovery
`1.7.2-recovery/code79`, and planned future stable `1.7.2/code80`. Candidate
manifest, DaemonIdentity and artifact basename now match the approved identity.
This approval covers metadata and unsigned-build preparation.

The independent risk decision requires the first session to exclude prolonged
TOP sleep and external sleep automation, recorded NOT RUN. The separately
documented [stable black-screen incident](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/82)
is not resolved by those exclusions. Recovery must be independently prepared
before any installation; vendor failure surfaces still have explicit stop gates.

## Candidate CI identity contract

The former source/publication contract required the source manifest's version
to have an existing stable-publication record. A private synthetic higher-version
fixture reproduced the failure before a candidate could be prepared.

The contract now verifies published1.7.1/code76 provenance independently from
source identity. Source must either match that identity or advance both numeric
version and code, with manifest and daemon identity synchronized. Published
links, source/digest record, Guard caveats, package, SDK and non-exported installer
components remain checked. Forty-five scenarios run through the real contract
script in disposable source fixtures, including invalid identities and corrupted
provenance. The release-policy suite invokes this regression in existing CI.

The [source-intent contract](CANDIDATE-SOURCE-IDENTITY.md) additionally admits
only the reviewed `-rc1` and `-recovery` suffixes, with explicit matching role,
full version and integer code. The approved identities are exercised in fixtures;
candidate manifest, daemon identity, intent and artifact basename are synchronized.
Real-comparator tests confirm that differing suffixes compare equal and a
suffix-free stable version sorts above both. No production updater or
predecessor-policy change accompanies this CI extension.

These checks neither publish a candidate, authorize signing nor prove artifact
recovery. Candidate identity changed only after explicit owner approval;
signing/publication workflows remain unchanged. The pinned publication baseline
must be updated through a reviewed provenance change after a later real release;
it is not inferred from newer source metadata. CI on the resulting head is still
required before using any candidate artifact.

## Data-preserving recovery contract

Before installation, require two independently verified artifact records:

- Candidate: exact source head and CI build, approved version/code, established
  package/signature, and unchanged payload across the unsigned/signed pair.
- Forward recovery: reviewed stable-source behavior rebuilt at a code greater
  than the candidate, with synchronized manifest/runtime identity and the same
  established package/signature. Specify which source and metadata differ.

An archived code76 APK is evidence of the baseline, not proof that Android permits
a downgrade after installing a higher-code candidate. The recovery artifact must
already be locally available and reviewed before physical entry. Do not depend on
the candidate app, its network connection or its updater remaining operational:
use the separately approved ordinary Android installer, then a controlled reboot.
An explicit installation transport may be selected for the session; this document
does not authorize one or provide arbitrary privileged commands.

Verify package, code, runtime identity and retained preferences after recovery.
If an in-place recovery install is refused or identity/health remains unknown,
stop the session; do not uninstall, clear data, relax signature/version checks or
substitute a forced daemon termination. A later public version/code must also
account for any installed forward-recovery code, so recovery numbering is a
release decision, not an automatic temporary bump.

Recovery APK construction, production signing, source-to-signed verification and
device recovery have not been performed. A procedure without the two verified
artifacts is not an executable recovery gate.

## Separate updater discovery from installer and activation

The Android fixtures already exercise the real updater/PackageInstaller paths
under controlled inputs. They do not publish a release or prove that the shipped
app discovers a privately staged candidate. Production discovery requires
reviewed release metadata, asset naming/digest and a trusted download URL; draft
releases and a CI artifact alone are not discoverable updates.

Until a separately authorized distribution decision exists, label the proposed
physical path as installation/activation validation. Do not report a full
production discovery-to-install PASS or publish a release solely to enable testing.

The [physical exit matrix](ENGINEERING-HARDENING-PHYSICAL-EXIT-MATRIX-2026-10-13.md)
retains owner approval, stop conditions and restoration. Every physical path in
this PR remains NOT RUN. Detailed reports, captures and identity/signing records
remain owner-local outside Git; public documentation contains only scoped results.
