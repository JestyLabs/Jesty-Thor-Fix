# Dashboard UI emulator validation and remaining gates

Tested product source: `acbc3dd5b15efb05e8ad80346f5b2a476717b9b4`.
This supplements the [earlier static audit](ENGINEERING-HARDENING-UI-A11Y-2026-10-13.md).
It is a local Android UI validation, not physical Thor acceptance or complete
accessibility certification. This step changes documentation only.

## Method and evidence boundary

The actual product APK and a separate instrumentation APK were signed with a
disposable test key and installed in a disposable Android 13/API 33 emulator.
The landscape viewport was 1920 x 1080 pixels at density 2.625. Font scales
1.0, 1.5 and 2.0 were tested. The app's media was paused for static captures.
Screenshots, view measurements and Android accessibility trees were inspected.
The final collector checks that each active window belongs to the target app
and that the expected dialog title appears; collecting screenshots alone is
not a conformance PASS.

Automatic update checks and emulator network transports were disabled.
A synthetic cached release was seeded only to make the existing UPDATE action
and dialog visible. Its displayed version is not evidence of a published release.
No download, installation consent, hardware toggle, report export, sleep trial
or external-link action was activated. The vendor bridge was absent.
Raw captures, instrumentation and signing material remain owner-local outside Git.

## Results

| Area | Observed result | Boundary |
| --- | --- | --- |
| Tab / Shift+Tab | All seven controls reached: three switches, Support, GitHub, Update, Diagnostics; traversal wraps and reverses | Does not establish preferred screen-reader speech order |
| D-pad | Directional traversal works; DPAD_CENTER opens Diagnostics in the initial native runs | Geometry changes the DOWN destination at 200%; physical controller untested |
| Enter / Space | Each opens Diagnostics in the final guarded runs | Other destructive/external actions were not activated |
| GitHub long press | Native held DPAD_CENTER opens the existing Updates settings dialog | Does not resolve visual discoverability of the shortcut |
| Visible focus | Diagnostics and GitHub focused crops change visibly from the unfocused state | Android's default focus highlight supplies this even with non-stateful backgrounds; no missing-focus defect inferred from source alone |
| Switch semantics | Native accessibility nodes expose heading descriptions, checkable=true and checked=false for the seeded OFF state | No hardware toggle or spoken-feedback test |
| Dashboard scaling | No internal text clipping or ellipsis at 100%, 150%, 200%; lower metrics are reachable by scrolling | Hardware values are unavailable placeholders, not real telemetry; no proof for every future dynamic string |
| Updates dialogs | Settings and cached-release dialogs remain readable at all three scales | Cached dialog only; native installer acceptance is covered separately |
| Touch targets | Action views measure about 38dp high; switches about 40dp; Diagnostics 48dp | Confirms the earlier sub-48dp target finding, not an accessibility certification |
| Diagnostics at 200% | Close is partially clipped; Sleep Trial is outside the visible button area | Confirmed UI gap; Back dismisses the dialog; neither Save Report nor Sleep Trial was activated |

### Confirmed diagnostics layout finding

At 100% and 150%, all three dialog buttons are visible. At 200%, the native
dialog stacks its buttons vertically: Save Report remains visible, Close is
partially clipped and Sleep Trial is reported not visible by Android's
accessibility tree. The screenshot agrees with that geometry.
`AppDiagnostics.java` has no net difference between the tested PR head and
the fetched `main`, so this finding concerns existing UI, not a demonstrated
regression introduced by PR #67. Track a small layout-only correction separately
and preserve all report/sleep-trial handlers. Repeat the font-scale and keyboard
checks against that correction before accepting it.

TalkBack was not installed in this emulator image. Speech, duplicate spoken
announcements, quantitative contrast, physical gamepad behavior and real
hardware telemetry remain NOT RUN. An accessibility tree does not replace them.

## Consolidation review

At the tested head, the integrated PR has 127 changed files. The runtime change
inventory includes updater/session helpers, a trace-only boot formatter,
conservative external-display parsing and UI semantics. Privileged IPC files
and the separately reserved CodeQL finding were excluded from this review.
No new updater or boot defect was demonstrated in this pass; this is not an
independent security approval of the whole PR.

The boot extraction preserves the original formatter expressions; it does not
change boot gates or retry/restart decisions. CPU controller and watcher files
have no net runtime change in this PR. The conservative display parser still
requires vendor-format/physical acceptance for its affected paths.

Fetched `main` at `1868aca0cb5cbcdd28218d43cb87135921195d7c` has the same tree
as the PR's reported base `9714de9726fca22f417e624a3834a59b20f50b0a`.
No additional runtime overlap required a merge during this review. Recheck
both branches before a future implementation or freeze.

The previously completed exact-product-head CI is run
[38423957257](https://github.com/JestyLabs/Jesty-Thor-Fix/actions/runs/38423957257):
build and Android updater tests succeeded; production signing was skipped.
The aggregate CodeQL check remained failing and was not investigated or changed
here. Any later PR head must receive its own required checks.

## Freeze checklist

- Resolve the separate CodeQL/security review and independent native IPC gate.
- Reconcile required checks with actual GitHub branch-protection settings.
- Decide and validate the scoped diagnostics layout/touch-target follow-up;
  complete TalkBack and remaining accessibility checks without treating this
  emulator pass as complete UI acceptance.
- Freeze a reviewed commit and obtain its exact-head build/test/security results.
- Prepare one explicitly approved physical session covering affected updater,
  daemon handover, display/Guard paths and rollback; existing v1.7.1 physical
  evidence is not validation of the full maintenance diff.

No additional runtime refactor is justified merely by the remaining breadth of
the workstream. The PR remains draft; no Thor operation, production signature,
merge, release or reserved IPC/CodeQL change was performed in this step.
