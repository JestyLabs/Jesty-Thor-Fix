# Containment status correction — v1.7.5

## Failure and correction

The production coordinator could retain a successful isolation result after the platform reactivated the lower display. If a subsequent OFF attempt failed, was unconfirmed, or was rejected because the mode changed, that previous result overwrote the current diagnostic status with `BOTH_DEGRADED`.

The correction invalidates that previous result when the lower display is observed active. A pending retry reports `BOTH_ON_BACKOFF`; an admitted attempt retains its current error or readback result. Only a newly confirmed OFF reports `BOTH_DEGRADED` again. This field is diagnostic telemetry, not a new dashboard control or exported report schema.

No display action, retry interval, timeout, lock, back-off reset, Guard policy, CPU action or updater operation is changed. This corrects a stale success indication; it does not repair the underlying panel/compositor failure or make a synchronous platform call cancellable. The [v1.7.4 containment limitations](BLACKOUT-CONTAINMENT-1.7.4.md) still apply.

## Reproducible evidence

Run `scripts/test-display-backoff-host.py` with an existing JDK. It exercises the real coordinator and hardware wrapper with synthetic platform boundaries.

- The released v1.7.4 coordinator fails all three new regressions: failed OFF, unconfirmed OFF and mode changed before OFF. Each fails at the intended current-status assertion.
- The corrected coordinator passes 173 assertions across the display wake, bottom transition, stall observation, back-off and status regressions.
- Hardware-call and property-write counts match the released coordinator at each comparable failure point. The waiting case also verifies that retry admission and command effects remain unchanged.
- Eighteen compiled mutants are rejected, including both new stale-status controls. Six frozen baseline checks retain the intended before-change failures, including the three new v1.7.4 cases.

The Android fixture in `scripts/test-display-wake-android.py` adds failed and unconfirmed retry cases, including status recovery. It uses Android scheduling with synthetic panels; it cannot reproduce a vendor DSI failure. Execution results belong in the release validation record, separately from fixture compilation and host results.

## Updating from v1.7.4

The published v1.7.4 service was not in the exact predecessor policy. Version metadata alone would therefore leave this update on the guarded fallback path. v1.7.5 admits that exact published predecessor while retaining authentication, matching identity, retained-process exit proof, readiness checks and conservative refusal conditions.

The frozen v1.7.4 source fixtures come from release commit `8fb93cbd67f0fde4b35859fb7ddc4eab067ab24c`, with paths and SHA-256 values in `tests/retirement/published/SOURCES.json`. Its real producer is rejected by the previous policy and accepted by the updated policy under the existing safe conditions. Missing or duplicate runtime identity is rejected; v1.7.4 cannot borrow the older v1.7.1 missing-field exception. Current/future and suffixed versions remain unapproved.

Host tests cover published v1.7.1–v1.7.4 producers, service activation and negative controls. The Android retirement suite exercises the same four origins with native support both available and unavailable. Generic Android results do not establish a successful transfer on every Thor firmware.

## Acceptance boundary

Physical acceptance of v1.7.5 is NOT RUN during preparation. The owner explicitly authorized publication after successful software gates, followed by owner testing and a later supervised physical session. This is a timing exception, not physical evidence or a relaxation of the code's safety checks. No device was contacted for this change. The remaining hardware and performance work stays in tracker #95/S6.
