# S2 — retained-process update in confirmed TOP

Owner-requested follow-up to programme **JTF-QUALITY-2026-10-17**, tracker [#95](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/95). Starting main `f53c41c14f70c8a00354af9185cff59f68b46414`. This is an unreleased candidate; published1.7.5 bytes and their pending physical acceptance are unchanged.

## Concrete gap and minimum change

The existing retained update rejected TOP even when the upper CRTC was active and the lower inactive. The old daemon must still exit before a new one can own the exclusive endpoint and run current code; installing an APK does not replace a running daemon. A kernel reboot already ends the predecessor. No always-kill-on-app-open policy is introduced.

The public/unbound predecessor gate remains BOTH-only. Only the existing root-peer and retained-process route may additionally admit TOP, with Display Fix ON, exact upper1/lower0, matching confirmed CPU/Guard intent and all existing identity, lid/dock and readiness checks. No extra predecessor version is admitted.

TOP also requires `display_action_status=CONFIRMED`, a known terminal `repair_result` (`NONE`, `OFF_OK` or `CANCELLED`) and a canonical nonnegative generation. These fields already exist in all four frozen producers. No response is rewritten to manufacture legacy identity evidence. Missing, duplicate, unknown, pending/running/retry or invalid fields refuse TOP.

Both validated Q observations must describe the same mode; TOP also requires the same generation. This detects observed mode/scheduling invalidations, including an observed TOP→BOTH→TOP sequence. It does not freeze platform state after the observation. Generation is not a universal repair-state counter, so the terminal-repair check is independently necessary. A stale `RETRY_PENDING` may conservatively refuse an otherwise recovered TOP; refusal is retained rather than assuming safety.

Descriptor-bound identity, confirmed old exit, held successor, durable activation and eventual authenticated READY remain separate requirements. No numeric-PID fallback, automatic mode switch, panel timing change, sleep suppression or new compositor-restart rule is introduced. Nonresponsive/unknown state is not permission to replace a process or a blackout-recovery mechanism.

## Evidence and boundaries

- Before changing the gates, the new actual-producer TOP success case failed by the expected refusal for each exact1.7.1/1.7.2/1.7.3/1.7.4 source. Initial fixture compilation failure is preserved separately; it is not counted as that behavioral evidence.
- The existing actual-producer/controller HOST harness gains targeted TOP terminal/pending/ambiguous state, both mode edges, generation changes and unchanged-public-gate cases. Compiled controls target quiescence, generation, mode continuity and the public boundary, alongside the existing exit/second-Q/deadline controls.
- Real activation/service/helper HOST integration covers TOP launch ordering and no-successor-without-exit. Lower native endpoints and hardware in that tier remain inert.
- A focused HOST test executes actual `BootCoordinator`, `CpuFixController`, `BootSafety` and `DisplayActionCoordinator` with controlled OS/store/clock inputs. Stable TOP and already-confirmed CPU reach READY with zero power/process/property effects, for absent and valid APPLIED markers. A compiled caller lower-ON mutation must fail. No production wait or grace constant is changed.
- Installed generic Android/JNI adds BOTH/TOP to the four-origin native/unsupported matrix. The frozen producer, kernel process descriptor and parent-confirmed exit are real; CRTC/CPU/READY boundaries remain explicitly synthetic. Exact-head CI and its retained results determine the executed outcome; HOST/build-only does not establish Android PASS.

The requested notice is shorter, says “contact Jesty” and removes the final uninstall/data sentence from the ordinary transfer notice. Storage/identity-failure guidance is unchanged. Detail text increases12sp→13sp; no layout/activation decision is redesigned.

Independent boundary review, full candidate CI/CodeQL and the affected physical TOP-upgrade gate must be recorded separately. No production signing, release, installation on Thor or physical PASS is implied. A future release needs its own exact artifact/version and currently available upgrade-origin decision; do not re-sign the published code85 or downgrade a device to manufacture a trial.

## Same-version failed-start recovery

The same S2 increment also shares the retained-process transport with the existing
same-version failed-start recovery. Authorization stays separate: an update needs
the healthy exact published predecessor and both I/Q rounds; recovery needs the
current version, matching display intent and an unchanged explicitly replaceable
failed/stalled boot phase across two I observations. READY is not a failed-start
state. Duplicate or insufficient identity, unsupported native operations and
unconfirmed exit remain denials, with no numeric-PID fallback.

Recovery durably holds activation before its COMMIT and clears that hold only
after the confirmed exit receipt and successful transport cleanup. Interrupted or
unconfirmed work remains conservative across reopening and package identity
changes in the same kernel boot. Existing baseline/live activation record formats
and ordinary restart recovery are preserved. Unused historical numeric stop
helpers have no runtime callers and are removed; the read-only public predecessor
policy remains BOTH-only.

HOST validation exercises the actual service, app/helper controllers and durable
activation machinery: 107 activation scenarios/588 assertions, 38 recovery cases,
28 storage/boot checks and seven compiled negative controls. The four frozen
producer suites and TOP successor boot controls remain separate evidence. The
Android fixture now requests sixteen update plus four recovery cases, using the
real current producer/JNI/preferences with synthetic failed-start/readiness and
prior activation inputs. New-head Android/CodeQL execution and independent review
must be verified before integration; earlier baseline PASS does not qualify this
changed implementation. Physical Thor acceptance remains NOT RUN.
