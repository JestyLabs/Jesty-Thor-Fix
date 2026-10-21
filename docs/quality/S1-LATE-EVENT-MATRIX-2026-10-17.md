# S1 late-event characterization on quality integration candidate

**Programme:** JTF-QUALITY-2026-10-17 · **Workstream S1 only** · **Base:** [quality integration branch](https://github.com/JestyLabs/Jesty-Thor-Fix/tree/integration/quality-v173-offline-20261017) · **Product change:** NONE · **Test tier:** Java17 host with inert vendor/Android fixtures (NOT physical Thor).

## Four new deterministic coordinator cases

| ID | Controlled sequence | Characterized result | Caveat |
| --- | --- | --- | --- |
| S1-L01 | TOP pending repair becomes due, then BOTH mode edge, then late display-on and duplicate BOTH | Old repair cancelled, no completion, no vendor power action | Synthetic deadline and CRTC snapshots |
| S1-L02 | TOP→BOTH→TOP followed by orphaned due timer after cancellation | No repair begin/completion and no unexpected display power command | Timer injection is intentionally adversarial |
| S1-L03 | Twenty duplicate TOP samples without wake property change | No fake wake repair, no excessive mode edge or unexpected power write | Does not measure real watcher cost |
| S1-L04 | **Artificial** concurrent TOP Guard callback pauses before hardware lock; newer BOTH sample completes first; older callback resumes; fresh BOTH resyncs | Prints an explicit observation about whether older TOP can replace newer BOTH; Guard deferral ensures no power write | **May violate real caller assumptions:** need audit whether production DaemonWatchThread can call concurrently, and real mode state must be confirmed from Settings |

## Potential ordering hazard is NOT certified as safe

S1-L04 intentionally creates two simultaneous calls to the **real production** `DisplayActionCoordinator.onWatcherSample()` while substituting *only* the guarded Android/vendor boundary. The coordinator's earlier `stableTopWake` is computed **outside** the shared hardware monitor. If an older sample resumes after a newer BOTH call, it may logically replace the later mode in the host experiment. This is a **conditional code-level observation**, **not evidence the real single watcher emits concurrent overlapping callbacks** and **not an established cause of #84**. This characterization is not a green safety proof for overlapping caller ordering; log any observation verbatim and keep this risk open until the actual watcher threading contract is verified.

All fixture power operations are fail-fast/inert. No actual DRM, physical panel, sleep, brightness, input, daemon restart or reboot is executed. **Do not add mode/power fixes or timestamp heuristics solely on this synthetic finding.** Read source/call graph and only propose behavior correction with reproducible supported ordering and negative tests.

## Acceptance / evidence

- [ ] Exact-head coordinator host tests green, including S1-L01..L04.
- [ ] Exact-head overall integration CI (Android updater, product unsigned build) and CodeQL reviewed.
- [ ] S1-L04 output classified as OBSERVATION, not as verified safe physical behavior.
- [ ] Separate upstream/integration merge review before candidate promotion.
- [ ] Real Thor / firmware / long TOP wake, #84 cause: **NOT RUN/UNKNOWN**.

**Change control:** this is a test-only branch from the integration candidate; it does not change or silently merge the underlying unrelated S2/S5 work. Signing, production merge, tag and release are not authorized by this test PR.