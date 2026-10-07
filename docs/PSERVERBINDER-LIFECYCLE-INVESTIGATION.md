# PServerBinder lifecycle investigation

Status: **read-only research; no runtime or v1.6.0 behavior change**

## Scope

This workstream investigates the observed case where stock `pservice` remained alive while
`PServerBinder` was not visible through ServiceManager. Restarting only `pservice` restored
the Binder while the display composer and SurfaceFlinger PIDs remained unchanged.

That recovery proves that a fresh `pservice` instance can restore the privileged bridge. It
does **not** identify why the previous instance had no visible `PServerBinder`.

Do not add aggressive retries, restart services automatically, change the CPU-fix startup path,
or weaken existing fail-safe behavior from this investigation alone.

## Existing physical observation

During the physically validated early-CPU prototype setup:

```text
pservice:       1390 -> 675
composer:       7330 -> 7330
SurfaceFlinger: 7327 -> 7327
PServerBinder:  not found -> found
```

Only `pservice` was restarted for that recovery.

Important limitation: the existing evidence does not establish whether `PServerBinder` had
previously been visible during PID 1390's lifetime. Therefore these remain distinct possibilities:

1. the first instance never completed or retained registration;
2. the service registered and was later lost;
3. ServiceManager state was replaced/reset and the old pservice did not republish;
4. SELinux/service-manager policy prevented add/find;
5. the client lookup failed even though the service remained registered.

This observation is recovery evidence, not root-cause evidence.

## Binary identity gate

The reverse-engineering notes apply to this tested stock binary:

```text
/system/bin/pservice
SHA-256:
8a0b75b44f0139843f2608f1ac7946ed1184cb126ed2777ee2bc2fb509357be4
```

The local analysis copy is expected at:

```text
C:\Temp\jesty-pservice-readonly.bin
```

Do not trust local offsets, symbol addresses or reconstructed basic blocks until that file hashes
to the exact value above.

Known static result for the tested binary:

```text
pservice main
  -> sh /data/boot_start.sh &
  -> cpu_init / device-manager setup
  -> BinderMainBlock
```

The hook invocation occurs before the main Binder block. The exact Binder object creation and
ServiceManager registration call inside `BinderMainBlock` still needs to be reconstructed from
the hash-matched local binary before making a server-side implementation claim.

The display libraries previously used for the mixed-refresh investigation are out of scope here
unless a concrete reference from pservice/Binder lifecycle analysis reaches them.

## Application-side call graph

```text
BootReceiver / normal app launch
  -> AutoService
      -> PServer.startDaemon(...)
          -> DaemonLaunchScript.command(...)
          -> PServer.send(...)
              -> reflection: android.os.ServiceManager
              -> getService("PServerBinder")
                  -> null: launch bridge unavailable
                  -> IBinder:
                      -> transact(code=0, [command, "0"])
                          -> stock pservice
                              -> root shell command
                                  -> app_process / D ...
```

Current `PServer.send()` returns `false` when `getService("PServerBinder")` returns null.
That is safe, but it does not currently classify the failure as lookup-null versus transaction
failure.

## Stock lifecycle model to reconstruct

The target reconstruction is:

```text
init starts pservice
  -> pservice process identity established
  -> Binder object created
  -> PServerBinder registration attempted
  -> registration accepted or rejected
  -> Binder serving loop/thread pool active
  -> later service visibility state
```

For every observed transition, retain:

- kernel boot ID;
- pservice PID and `/proc/<pid>/stat` start time;
- servicemanager PID and start time;
- `service check PServerBinder`;
- whether `PServerBinder` appears in `service list`;
- SELinux mode and relevant AVC/service-manager logs;
- composer and SurfaceFlinger PIDs only as control evidence.

## Hypotheses and discriminants

### H1 — initial registration did not complete or stick

Signature:

```text
pservice alive
same pservice PID/starttime
PServerBinder never observed as found
```

Needed evidence:

- hash-matched reconstruction of `BinderMainBlock`;
- registration return/status handling;
- logs around process startup;
- any add-service denial/error.

If proven, the correction belongs at the registration/lifecycle boundary. Do not compensate with
client polling loops.

### H2 — registered Binder was later lost while pservice remained alive

Strong discriminant:

```text
same boot_id
same pservice PID
same pservice starttime

t1: PServerBinder found
t2: PServerBinder not found
```

That eliminates a simple whole-process restart and strongly weakens initial-registration failure.

Needed evidence after that point:

- Binder driver state for the pservice process;
- ServiceManager logs/death notifications if exposed;
- pservice thread/task state;
- exact Binder ownership in the reconstructed stock binary.

A worker thread merely being stalled is not enough by itself to explain a ServiceManager lookup
returning no service; distinguish registry loss from an unresponsive registered Binder.

### H3 — ServiceManager registry generation changed

Strong discriminant:

```text
same boot_id
same pservice PID/starttime
new servicemanager PID/starttime
PServerBinder missing
```

Also compare ordinary framework services. If the registry was recreated and pservice has no
republish path, restarting pservice would naturally restore its registration.

### H4 — SELinux / service-manager policy

Capture both service addition and service lookup evidence.

Useful split:

```text
PServerBinder absent from service list
  -> registration/server/registry side remains possible

PServerBinder present in service list
but one caller cannot check/get it
  -> investigate caller visibility / service_manager find policy
```

Do not change SELinux policy from a single missing-service observation.

### H5 — client lookup failure

The app uses the same named ServiceManager service that can be independently checked from shell.
A future incident must compare both at the same time.

If shell sees the service and the app lookup alone fails, instrument the client path before
changing the server.

## Read-only next-episode collector

Use:

```powershell
.\scripts\collect-thor-pserverbinder-readonly.ps1
```

Default behavior is one snapshot. It performs no Binder transaction and no service restart.

For a short passive sequence when the anomaly is already present:

```powershell
.\scripts\collect-thor-pserverbinder-readonly.ps1 -Samples 3 -IntervalSeconds 5
```

The collector records:

- host/device timestamps and boot ID;
- local and device pservice hashes;
- init service state and boot-time properties;
- `service check PServerBinder` and full service list;
- pservice, servicemanager, SurfaceFlinger, composer and system_server PID/starttime;
- readable Binder debug state;
- SELinux mode and relevant AVC/audit output;
- full and filtered logcat.

It intentionally performs no property write, service control, process signal, reboot, Binder
transaction, privilege escalation, or device-side file mutation.

## External implementation survey

A read-only survey of unrelated public clients of the same vendor bridge was used only to
identify useful failure-mode questions. No external code or project-specific implementation is
copied into this workstream.

Recurring client-side patterns included:

- re-resolving a cached Binder after it is no longer alive;
- caching successful availability rather than permanently caching an early boot failure;
- serializing concurrent transactions;
- throttling repeated failed probes;
- treating a missing or unhealthy bridge as unavailable rather than forcing recovery.

Those patterns are useful for client robustness but do **not** explain or fix the observed case
where ServiceManager itself reported `PServerBinder` missing. They must not be used as a reason
to add retries or automatic pservice recovery before the server-side lifecycle is understood.

## Minimum safe correction, only after proof

No v1.6.0 change is justified yet.

If H1 is proven, the smallest correct fix is at the registration/lifecycle boundary: handle the
registration failure explicitly and recover in a bounded, observable way. Do not add an
unbounded client retry loop.

If H2 is proven, identify why the published Binder object can disappear while the host process
survives, then repair or republish at that lifecycle boundary.

If H3 is proven, investigate explicit republish/reconnect semantics after a ServiceManager
generation change.

If H4 is proven, correct the relevant policy/label issue rather than bypassing it with retries.

If H5 is proven, improve client observability first. A small future change could distinguish:

```text
LOOKUP_NULL
LOOKUP_EXCEPTION
BINDER_DEAD
TRANSACT_FALSE
TRANSACT_EXCEPTION
```

That instrumentation is not part of this read-only PR.

## Stop conditions

Stop and preserve evidence if any of these occur:

- the local pservice hash differs from the documented stock hash;
- the anomaly cannot be reproduced passively and only a forced failure would continue the work;
- analysis would require restarting pservice, servicemanager, compositor or SurfaceFlinger;
- a proposed fix relies only on retries without distinguishing the failure class;
- a proposed change modifies v1.6.0 runtime behavior before a discriminating test exists;
- the evidence points into another subsystem but the connection is not yet concrete.

## Instructions for the next Codex pass

Use this document as the handoff.

1. Treat the current repository and the physical-test record in PR #33 as the source of truth.
2. Read:
   - `docs/ROADMAP.md`;
   - `docs/ARCHITECTURE.md`;
   - `docs/PSERVICE-EARLY-CPU-RESTART.md`;
   - this document;
   - `src/com/thor/displaypowertest/PServer.java`;
   - `scripts/collect-thor-pserverbinder-readonly.ps1`.
3. On the local machine, hash `C:\Temp\jesty-pservice-readonly.bin`.
4. Continue binary analysis only if it exactly matches
   `8a0b75b44f0139843f2608f1ac7946ed1184cb126ed2777ee2bc2fb509357be4`.
5. Reconstruct `BinderMainBlock` far enough to identify:
   - Binder object creation;
   - service name construction;
   - ServiceManager acquisition;
   - registration call;
   - registration return/status handling;
   - thread-pool / join behavior;
   - any exit, retry or republish path.
6. Search the already-collected local init trees, logs and traces before asking for new device
   experiments.
7. Build a timestamped lifecycle timeline with boot ID, pservice PID/starttime, servicemanager
   PID/starttime and Binder visibility.
8. Keep H1-H5 separate. Do not collapse “restart fixed it” into a cause.
9. If a future real anomaly is observed, run the read-only collector before any recovery action.
10. Do not provoke the failure, restart stock services, change SELinux, add aggressive retries,
    or modify stable runtime behavior.
11. Only propose an implementation after one hypothesis has a discriminating test and the
    evidence selects it over the alternatives.
12. If a fix becomes justified, keep it minimal, isolated and host-tested before any supervised
    physical test.

Expected next deliverable:

- hash/identity result;
- exact reconstructed Binder registration call graph;
- evidence table for H1-H5;
- remaining gaps;
- one discriminating next test;
- smallest safe correction **only if** the cause is actually proven.
