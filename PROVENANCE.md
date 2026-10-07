<!-- SPDX-FileCopyrightText: 2026 Jesty Labs contributors -->
<!-- SPDX-License-Identifier: GPL-3.0-only -->

# Research provenance

This file records where important technical claims and implementation ideas in Jesty Thor Fix came from. It is a verification aid, not a claim that generic Android, Qualcomm, AYN or Retroid mechanisms were invented by this project.

See [docs/RESEARCH-WORKFLOW.md](docs/RESEARCH-WORKFLOW.md) for the standing workflow used before publishing substantial new reverse-engineering or cross-project findings.

## Scope and attribution rule

When project work materially depends on external code, research or prior art, record the upstream project and an exact URL or commit where practical. When a claim comes from this project, keep the commit, PR, device evidence and tested artifact tied together so later refactors do not erase the original history.

Downstream use remains governed by GPL-3.0. The project asks that non-trivial research and implementation provenance be preserved when reused.

## Public prior art and platform mechanisms

The AYN/Retroid `PServerBinder` privileged bridge predates Jesty Thor Fix and is used by other public projects. Examples include:

- OdinTools at commit `0eaf49392e263bf1de4d6c7eb37aeb03dadb0ccc`
- ClusterTune at commit `4920c5a82925772f5765dbee1f4679242a72cada`
- GameNative at commit `7d06fc32f119bb35ae2740827ca2a47ce9034cd4`

Those projects are reference points for the vendor bridge itself. This project does not claim authorship of that interface or of the generic idea of launching a privileged helper through it.

## Project research records

### JTF-RR-20260926-INITIAL

Initial public release: `462b8e9f62d747ace608f73d8f35f32b2c6c4677`

This establishes the public starting point for the Thor display/CPU utility and its original root-bridge integration.

### JTF-RR-20261006-PRECOMPOSER

Commit: `75dcc4e23b5651bc9203e2f7e3a897802b93b4fc`

Primary record: [docs/PRECOMPOSER-CPU-FIX-PROOF.md](docs/PRECOMPOSER-CPU-FIX-PROOF.md)

This work records the Qualcomm display-stack investigation around `vendor.display.disable_system_load_check`, the first-composer timing constraint, the Thor init-tree audit, and static analysis of stock `pservice` including its early `/data/boot_start.sh` execution path.

### JTF-RR-20261007-EARLYCPU-P33

Physical-test provenance: [PR #33](https://github.com/JestyLabs/Jesty-Thor-Fix/pull/33)

Tested source head: `cb7d7b19e9227674d316459458277b911f331cd4`

Tested CI merge candidate: `9ec3ad26f03fdf4dcc9da1dd9eb0ff0323212c36`

Signed test APK SHA-256: `CB18BC9A0EB77F0256D23E856658D160E9B838912A6437ED38D9C86DB0646A73`

Physical cold-boot record:
- boot ID `335174a3-a5a2-4b1b-8e28-479ec964bef7`
- imported phase `RESTART_REQUESTED`
- baseline composer PID `1221`
- successor composer PID `2314`
- attempt reached `APPLIED`
- one-shot gate was consumed
- managed boot hook and ownership sidecar were removed
- final state reached `BOOT_READY`
- no later second CPU-fix restart was required

This record is intentionally tied to the exact tested candidate even if the implementation is later consolidated or refactored before release.

## Evidence discipline

Technical claims should be labelled internally as one of:

- **PROVEN** — directly supported by code, static analysis, controlled device evidence or a reproducible artifact.
- **OBSERVED** — seen on a named device/build but not yet generalized.
- **HYPOTHESIS** — plausible explanation awaiting direct evidence.
- **UNTESTED** — designed or inferred but not physically validated.

Raw logs and binaries are not required to live in the public repository, but published claims should retain enough hashes, SHAs and test conditions to be independently traced.

## Licensing metadata

Maintained source, tests and scripts are covered by the repository's GPL-3.0 terms. Central SPDX annotations are in [REUSE.toml](REUSE.toml). Branding and artwork remain governed separately by [ASSETS-LICENSE.md](ASSETS-LICENSE.md).
