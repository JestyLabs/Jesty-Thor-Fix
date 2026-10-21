**20 October 2026 status update:** [v1.7.4 release record](VALIDATION-1.7.4.md) supersedes current-status statements below. New live captures led to display-stall containment; physical acceptance is pending. #84 is closed as delivered software mitigation at the maintainer's request; physical follow-up remains in #95/S6. Earlier closure/automation attribution and integration gates are historical, not proof of the later incident's cause. The maintainer explicitly authorized release after software gates, with physical checks afterwards.

# TOP wake: investigation and coordinator hardening

PR #82 addresses a demonstrated cancellation defect found while investigating
the [persistent blackout incident](INCIDENT-TOP-WAKE-BLACK-SCREENS-2026-10-14.md).
The owner reports no further persistent blackout after disabling external sleep
automation and requests closure of issue #84 when this work is integrated.
The physical blackout was not reproduced in this investigation.

## Evidence and the two candidate paths

The external automation report records Battery Saver being enabled during sleep
and restored at wake near the incident. Source inspection shows that restoration
acknowledges the low-power setting; it does not wait for all resulting display
and platform policy work to settle. The app's lid guard was reported OFF.

Offline inspection of the archived firmware establishes that removing low-power
policy can remove a global display refresh constraint and schedule asynchronous
display configuration work. Whether the effective specifications actually change
depends on the remaining votes and display state. The post-reboot sample has
60 Hz minimum/peak policy; it does not establish a 120-to-60-to-120 Hz transition
at failure, a zero-brightness policy, or a new sleep request caused by restoration.

The firmware display power path also interacts with paired display configuration.
This gives the app's TOP reconciliation and system policy restoration a plausible
shared transition path. It does not establish an invalid display state at the
incident. Neither the firmware-only path nor an interaction with the app can be
selected as the physical root cause from the available post-reboot evidence.

The code and bounded tests do establish an independent problem in the app:
pending TOP repair used a firmware-visible power flag as an internal marker.
Switching to BOTH before the delayed repair cancelled the work without restoring
the flag. A subsequent BOTH sample could report confirmation with both CRTCs
active while that flag still indicated OFF. This sequence does not require
Battery Saver, and reproducing it does not reproduce the physical blackout.

Other public reports examined during research concern different screen/recovery
conditions. They are not independent reproductions of this both-screen incident.
The separate delayed lower-screen activation on 15 October also remains distinct
from the persistent blackout.

## Implemented correction

- Pending repair is represented by the coordinator's generation and scheduler,
  without an early write to the firmware power flag.
- Display confirmation callbacks query that internal pending state and recheck
  authority before updating the existing deadline.
- Repeated awake samples do not continually reschedule pending repair.
- An observed both-panels-inactive snapshot cancels old repair work. A subsequent
  wake starts a fresh interval; cancellation never forces a global wake flag.
- Actual lower-panel power operations retain their existing write, rollback,
  fresh-mode check and confirmation behavior. The mode reader is extracted
  to make that command boundary testable and pins `/system/bin/settings`
  rather than resolving the command through the process environment.

The existing 700 ms wake delay and 400 ms display-confirmation minimum are
unchanged. There is no blanket retry, forced ON on every sample, low-power write,
or new global sleep action in the product.

## Validation

| Test | Result and boundary |
| --- | --- |
| Production coordinator and display hardware on host | Cancellation, stale callbacks, sleep/new wake, unchanged timing, fresh mode and authority gates; Android observations are synthetic |
| Android emulator without Battery Saver | Real screen OFF/ON and PowerManager observations, production coordinator, synthetic panel/power boundary |
| Android emulator restoring Battery Saver before wake | Real low-power setting and saver broadcasts, real sleep/wake; same synthetic panel boundary |
| Android emulator restoring Battery Saver after wake | Same checks with the restoration order reversed |
| Previous coordinator negative control | All three emulator scenarios leave the synthetic flag at 0 after BOTH cancellation, with no panel command |
| Corrected coordinator | All three preserve 1 with no property write or panel command after cancellation |
| Existing host suites and unsigned build | Run alongside the new regression coverage; CI checks the integrated source |

The local Android run uses API 35. CI adds the candidate fixture to the existing
API 33 emulator job. The fixture uses real Android power broadcasts and clock,
with scheduler/runtime adapters and injected panel observations. It does not
execute the product's display callback smali, vendor Binder or physical panels.
The unsigned product build assembles the callback; a host boundary check requires
it to use the coordinator's pending query. Short emulator sleep is not a
long-suspend or physical display acceptance result.

Reproduce the coordinator regression with
`./scripts/test-display-wake-coordinator.ps1`. For Android, build and run
`scripts/test-display-wake-android.py` with explicit SDK, JDK, fresh build directory,
emulator serial, owned AVD name and results directory. See its `--help` options.
APKs, temporary keys and raw results must remain outside the checkout.

## Remaining platform limitation and disposition

A separate host fault injection shows that a blocked platform display call can
hold the display monitor beyond the later 1,200 ms CRTC polling window. Static
inspection confirms a synchronous platform call path; it does not prove that
such a stall occurred in this incident. No safe cancellation of that call is
established. Starting competing recovery calls after a timeout would leave the
original operation unresolved. Operation observability and serialization across
any future isolation boundary must precede such a redesign.

Logical CRTCs at 1/1 are also insufficient to prove visible presentation. This
patch corrects the demonstrated pending-state defect; it does not add a physical
black-frame detector or claim a vendor/compositor repair.

Issue #84 is to be closed at the owner's request after integration, recording
the cessation after external automation was disabled and the hardening completed
here. Closure is an investigation disposition, not confirmation of the missing
physical causal chain. Display-power and cancellation changes require physical
acceptance before release. No Thor contact, physical test, production signing,
or release is part of this work. Full evidence and exact research provenance
remain owner-local outside Git.
