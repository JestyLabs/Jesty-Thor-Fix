# Public evidence and responsible disclosure policy

Jesty Thor Fix publishes useful, reproducible engineering evidence while respecting the AYN Thor owner's privacy and third-party software rights.

## What we publish

- AYN Thor model, publicly identifiable firmware release, app version, scenario, test duration, configuration and outcomes.
- Sanitized aggregate metrics and minimum numeric inputs necessary to recalculate results, e.g. CPU tick deltas, elapsed times and bounded suspend-clock differences.
- Original Jesty source, tests, documented app-controlled behavior, relevant GitHub commits and build verification.
- Hardware-observable state (e.g. active/inactive display pipeline), reported without exposing proprietary vendor implementation.

## What stays private

- Unique device identifiers, unfiltered logs, accounts, network addresses, unrelated app data, private file paths, keys, certificates' private material or screenshots with personal content.
- Third-party firmware APKs/binaries, vendor images, extracted proprietary source, disassembly, decompiler dumps, internal symbol tables, offsets, private registration messages or non-public interfaces recovered by reverse engineering.
- Full process traces or vendor configuration extracts where a summarized behavioral finding will do.

The public technical result should **describe what was observed**, not reproduce the third party's implementation. Where a vendor component constrains what an ordinary app can do, explain the verified architectural constraint at the level needed to justify the design; avoid copying proprietary material.

## Review procedure

1. Confirm the evidence came from the exact app version and physical configuration claimed.
2. Reduce the record to numerical fields and public behavioral observations. Remove or replace session identities with consistent, non-reversible aliases.
3. Scan all new Markdown, CSV, images and attachments for identifiers, private paths, executable payloads, disassembly, license-restricted content and unexpected metadata.
4. Recalculate results from the sanitized output. Keep original logs and source capture hashes privately where practical.
5. Distinguish completed hardware validation from CI, emulator, simulated failure tests and new candidates still awaiting device acceptance.

The public reports do not require vendor binary internals to be credible: measured CPU consumption, elapsed suspend time, CRTC observations and observed recovery are independently useful evidence.

**Scope:** This policy governs new public evidence. It does not claim that rewriting a current document removes anything already present in Git history; any retrospective repository-history review is a separate, deliberate maintenance task.
