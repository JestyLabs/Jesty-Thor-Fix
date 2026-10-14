# Candidate and forward-recovery source identity

This contract prepares private candidate builds without changing published
provenance or reserving a release identity. App metadata remains `1.7.1/code76`.

The reviewed numbering proposal is:

| Role | Proposed versionName | Proposed versionCode | Functional source |
| --- | --- | --- | --- |
| Candidate | `1.7.2-rc1` | 78 | Accepted PR67 candidate head |
| Forward recovery | `1.7.2-recovery` | 79 | Published stable `f929ff910bfa8e5179a2c6a63948a18fc3d6b252` |
| Future public stable | `1.7.2` | 80 | Separately accepted release head |

These values remain proposals pending owner approval and artifact verification.
The recovery must preserve stable functional code, with only approved identity
metadata and isolated build/CI adaptations. It must not inherit PR67 product
changes or depend on the candidate's updater. It does not resolve the separate
stable black-screen incident.

## Explicit source intent

The current-release contract admits a numeric triple, optionally followed by
exactly `-rc1` or `-recovery`. A suffixed source requires a reviewed root-level
`release-source-intent.json` with exactly three fields. For example, the candidate
proposal would use:

```json
{
  "role": "candidate",
  "versionName": "1.7.2-rc1",
  "versionCode": 78
}
```

No intent file is added to the actual baseline by this preparation step. A future
metadata change must include its matching intent in the reviewed source so CI
checks it automatically; a local-only file is insufficient for remote CI.
Accepted roles are `candidate` for `-rc1`, `recovery` for `-recovery`, and `stable`
for a numeric version. Numeric versions may omit intent; any supplied intent is
still validated. Role and version strings are case-sensitive and the code must
be a JSON integer equal to the manifest code. Unknown fields, suffixes, roles and
manifest mismatches fail the contract.

The complete version must match `DaemonIdentity.VERSION`, with `RUNTIME_ID`
aliasing that constant. The numeric base and code must both advance past the
published baseline for any new identity. Existing boot/build checks also require
the artifact basename to match the complete manifest version. Historical
`1.7.1/code76` source/digest/link evidence, package, SDK, installer permissions,
non-exported components and unobserved-case caveats remain independently checked.

Passing this source contract does not verify signing, artifact contents,
candidate-to-recovery installation or preference preservation. Artifact records
must separately establish that recovery has a higher code than the candidate,
the established package/signature and independent stable-source provenance.

## Existing updater behavior and remaining activation boundary

The production comparator is unchanged. It treats the two proposed suffixed
versions as equal, so RC-to-recovery cannot rely on automatic update discovery.
The suffix-free `1.7.2` sorts above either, subject to ordinary release eligibility,
exact asset name/digest and archive/signature checks. Host tests exercise the real
comparator and retain complete suffixes in expected asset names.

Version ordering does not authorize daemon handover. The predecessor allowlist
remains unchanged and does not admit these proposed identities. First controlled
device activation remains installation followed by a normal owner-controlled
reboot and identity/health verification. A future public rollout requires a
separate handover decision before publication. Prolonged TOP sleep and external
sleep automation remain excluded from the first physical session.
