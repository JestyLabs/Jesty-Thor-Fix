# S2 — restart before the first open after an update

A user who updates and restarts the Thor before opening the app lost the fix for that whole boot and needed a second
restart. This has been true since v1.7.2. The owner's requirement (2026-11-02) is that an update never requires a
restart.

## Observed

On 2026-11-02, the third signed 1.7.7 candidate was installed over a running 1.7.7 daemon and the Thor was restarted
before the app was opened. After the restart:
- **service:** no daemon ran. The boot start logged `live handover held; safe predecessor not verified`;
- **display:** both panels were on, the panel-RAM bypass interception was absent, and there was no DSI error.

A second restart started the daemon normally.

## Cause

`RebootActivation` records its baseline (package stamp and kernel boot) the first time the app or service runs after an
install. A start is authorized only in a later kernel boot, or after a verified live transfer in the same boot.

The in-app updater does not reopen the app after installing. When the first run is the boot after the restart, the
baseline names that boot. The evidence is then `RESTART_REQUIRED`, and the service takes the live-transfer path. With
no predecessor running, the transfer is held and nothing starts.

## Change

When the evidence is `RESTART_REQUIRED`, the service first asks whether any daemon has run in this kernel boot
(`PServer.noDaemonInThisKernelBoot`). The answer is no only when both hold:
- no authenticated daemon answers on the secure socket;
- the socket inode exists and the boot ID in the instance lock file names a different, valid boot (`STALE_PREVIOUS_BOOT`).

Every daemon from 1.7.1 on takes the exclusive instance lock and then writes the current boot ID into it. So a stamp
from an earlier boot means no daemon of any installation has taken the lock in this boot. In that case:
- the service launches the daemon held, through the boot coordinator, as after a verified restart;
- once the new runtime answers STARTING or READY, it records the same durable proof that a live transfer records. Later
  opens in this boot are authorized, and the baseline verifies the next restart.

Everything else is unchanged:
- **Still held, with the restart fallback:**
  - an absent socket (clean install, or cleared app data, where a root daemon may still run);
  - an unreadable or malformed stamp;
  - a stamp from the current boot.
- **A daemon that answers** still goes through live retirement, whatever the stamp says.
- **A legacy listener** still holds the launch.
- **The instance lock** still stops a second daemon from binding while another holds it.

## Evidence

| Check | Result |
| --- | --- |
| New host scenarios with the real `AutoService`, `PServer` and `RebootActivation`, before the change | FAIL: `restart-before-first-open-starts: launches` |
| The same, after the change: start, reopen, next restart, three unproved stamps, absent socket, legacy listener, stale stamp with a live 1.7.6 predecessor | PASS (128 scenarios, 760 assertions in the suite) |
| Compiled negative controls: path removed; any unanswered socket accepted; current-boot stamp accepted; listener ignored | 4/4 rejected (18/18 in the runner) |
| Thor, `STALE_PREVIOUS_BOOT` on a normal restart | OBSERVED in every 1.7.7 boot log (`secure socket STALE_PREVIOUS_BOOT; grace_polls=0`) |

Host fixtures model the socket inode and the stamp; they are not Android or Thor proof. Physical acceptance needs:
- install the next signed candidate over a running daemon;
- do not open the app;
- restart, and observe one daemon READY without a second restart.
