# Display blackout investigation and 1.7.4 containment

## Observations and attribution

Owner-local captures from naturally occurring TOP sleep/wake incidents include kernel logs, display/binder snapshots and native/Java stacks. The lower display path failed to complete a commit after wake. During the captured reset loop the vendor compositor held shared command/display locks while waiting for lower-display completion; presentation on the upper display was also affected. The app watcher was observed waiting in its synchronous display-power call.

The initial lower-path failure preceded a new app panel command in one captured window. This narrows that window's immediate trigger; it does not exclude effects of earlier display state or establish the electrical/firmware cause. Recovery button attempts and USB changes limit causal inference. No device/session identifiers, raw captures, binary offsets or firmware are published here.

An OFF admitted under TOP returned late after the platform mode became BOTH. Subsequent automatic BOTH reconciliation requested ON again. Captured lower-OFF completion and later queue drainage support lower-panel isolation as a containment candidate; they do not guarantee recovery from every blackout. The underlying rail/reset/DSI/panel/firmware cause remains unknown.

## What 1.7.4 changes

A display operation taking more than five seconds arms a volatile lower-ON back-off after it returns, including after error. In BOTH, automatic ON is suppressed; an observed active lower CRTC permits an admitted OFF attempt through the existing coordinator/hardware path. Failed OFF attempts retain the existing 1.5-second start-admission interval. There is no total attempt cap. Successful readback records BOTH_DEGRADED.

Normal BOTH without the measured stall remains unchanged. Boot hold, disabled fix, actual mode validation, known CRTC requirements, serialization and readback remain in force. CPU, authentication, native retirement and signing mechanisms are not changed by this mitigation.

The protection ends after a mode change to TOP/BOTTOM, an admitted TOP wake, confirmed manual reconciliation, or a sampled Android non-interactive → interactive edge. The read-only hidden PowerManager Binder query runs only during back-off, at most once per second and outside the coordinator lock. Unknown/unavailable state cannot itself clear protection. A sleep transition the watcher does not sample can be missed, including very short cycles. CRTC0/0 alone never clears it because the vendor reset also passes through that state.

## Limits and user-visible effects

- The lower screen may intentionally remain OFF in BOTH until protection clears.
- Five seconds is an elapsed-operation heuristic, not a Binder deadline or unique proof of a vendor failure. A slow operation for another reason can trigger containment.
- The patch cannot interrupt an already blocked call or repair the lower panel/DSI. The PowerManager RPC itself has no enforced duration bound.
- A recovery OFF can queue behind the same compositor locks. An observed CRTC OFF does not prove lit pixels returned on the upper screen or that the vendor accepted a particular command.
- Mode changes/manual retry/sleep-wake can expose the underlying failure again. Do not repeatedly force blackouts to test the release.
- The action status is exported through existing diagnostics. The dashboard's ordinary display mismatch indication remains; no dedicated recovery banner/control was added in this change.

## Validation

The production coordinator/hardware run against synthetic platform boundaries. Tests cover elapsed failures, strict threshold, stale TOP-to-BOTH return, healthy BOTH, refused OFF, retry admission, actual-mode/boot/fix/CRTC gates and protection reset. A real JVM blocked caller permits a platform mode change during the call; the original source fails the subsequent no-re-ON assertion. Frozen source fixtures and hashes make before/after proofs reproducible without Git history. Compiled mutation controls must fail for the intended assertion, not compilation.

Commands: `python scripts/test-display-backoff-host.py --output <fresh-directory>`; `scripts/test-display-wake-coordinator.ps1`; existing full CI host suites. The Android fixture uses the real PowerManager query and real Android sleep/wake, with synthetic panels and a disposable key. Full-app lifecycle, updater, dashboard and published predecessor/JNI tests retain their separate generic-emulator evidence.

[The final release record](VALIDATION-1.7.4.md) links the exact successful main CI, CodeQL, protected signature and publication. All five Android display-wake cases passed, including failed slow-call protection and degraded-BOTH sleep/wake restoration. The six installed predecessor cases passed for 1.7.1, 1.7.2 and 1.7.3, including refusal when the native retirement facility is unavailable. Physical acceptance of this mitigation is NOT RUN before this owner-directed release; overnight observation and supervised checks follow. The issue remains open until attributable hardware acceptance. Earlier physical passes belong to their original versions and do not validate this patch.

## Separate issues

[#84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84) tracks persistent TOP/wake blackout. [#65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65) tracks the brief brightness flash separately. This release does not infer a shared cause or a brightness fix. The independent refresh and timer work is not included.

## Next physical acceptance

Install the official signed update in place without clearing data, open the app and verify ACTIVE or STOCK. Record the installed version, firmware, mode, selected refresh, both fix settings, Guard, external sleep automation and USB state before interpreting a wake observation. Earlier versions' CPU/suspend results remain historical measurements.

Overnight owner observation is exploratory, not a controlled reproduction or proof that a rare failure is absent. Later supervised checks should cover healthy BOTH/TOP/BOTH, normal short sleep/wake with Guard OFF, preference retention and available diagnostic status. Stop at the first abnormal presentation and preserve evidence before recovery when safely possible. Do not deliberately re-enter a vendor reset loop, repeatedly press recovery buttons, or change firmware/system components to manufacture coverage.

A naturally occurring stall requires its own before/after timeline and visible-screen confirmation. BOTH_DEGRADED plus lower CRTC OFF demonstrates the app's containment state, not physical image recovery by itself. An incident without an observed slow app call can remain outside this mitigation's trigger.
