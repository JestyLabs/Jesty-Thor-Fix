# S1 — count only completed writes as panel-RAM bypass requests

Physical check C6 on the fourth 1.7.7 candidate passed. It also showed a minor defect: a request was logged after the
interception had already closed. The owner decided to fix it in 1.7.8.

## Observed (owner-supervised, 2026-11-02)

**Sequence:**
- **In TOP**, a 60→120 change was held (`BYPASS_DEFERRED reason=LOWER_OFF`).
- **The owner turned the display fix off.** The daemon powered the lower panel on (`USER_RECONCILE`). Its own lower ON
  applied the waiting request (`BYPASS_APPLIED value=0;source=LOWER_ON`).
- **The interception closed** (`BYPASS_INTERCEPT_OFF cause=FIX_OFF`).
- **1 ms later**, the daemon logged a request and deferred it, with the second sysfs mount already gone:
  `BYPASS_REQUEST value=0;real=?`, then `BYPASS_DEFERRED reason=REAL_UNREADABLE;source=REQUEST`.

**Effect:**
- There was no kernel write and no DSI error.
- The log line is noise, and a stale request stayed in memory while the interception was off.
- The next arm cleared it: the request file is rewritten with the real value.

## Cause (INFERRED; the event mask was not logged)

- **The request-file watch** forwarded every `FileObserver` event to the interceptor.
- **AOSP's `FileObserver`** passes the raw inotify mask. Removing a watch makes the kernel send `IN_IGNORED`, which still
  reaches the observer (STATIC, from AOSP; not checked in the Thor's framework).
- **The interceptor** handled a request callback in any state, including after it had closed.

## Change

- **`BypassRamRuntime.watch`:** only an event with `CLOSE_WRITE` or `MODIFY` set is forwarded as a request.
- **`BypassRamInterceptor`:** a request callback that reaches the worker while the interception is neither `ARMED` nor
  `DRAINING` is ignored.
  - Every state change runs on the worker, so the state the callback sees is the current one.
  - While draining, a new request still replaces the waiting one.

Nothing else changes: the write rules, the settle time, the mounts, the drain and the log lines are the same.

## Evidence

| Check | Result |
| --- | --- |
| Host, the real `BypassRamRuntime.watch` with a host `FileObserver`, before the change (`watchevents`) | FAIL: `IN_IGNORED` and `IN_UNMOUNT` counted as two requests |
| Host, a request callback after fix off, before the change (`afteroff`) | FAIL, with the C6 log lines: `BYPASS_REQUEST value=0;real=?`, then `BYPASS_DEFERRED reason=REAL_UNREADABLE;source=REQUEST` |
| Host, after the change: both cases, plus a request written while draining (`drainrequest`) | PASS (76 checks) |
| Compiled mutants: every event counted as a write; a callback after the close accepted; requests ignored while draining | 3/3 rejected (21/21 in the runner) |
| Poisoned-boot, TOP boot, back-off and containment host suites | PASS |
| App sources compiled against `android.jar` (API 34) with the build's stubs, as `build.ps1` does | PASS |

Host fixtures deliver the kernel events by hand. They are not Thor evidence.

**Window left unchanged:** a vendor write that lands in the request file between the watch close and the unmount at fix
off is not applied. That was already true before this change, when such a request was only deferred.

Physical acceptance needs C6 repeated on the next signed candidate. Expected: no `BYPASS_REQUEST` or `BYPASS_DEFERRED`
line after `BYPASS_INTERCEPT_OFF`, and no kernel store from the daemon after it.
