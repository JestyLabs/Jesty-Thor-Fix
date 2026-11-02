# S1 — write the panel-RAM bypass only after the lower panel has finished starting

The third 1.7.7 candidate broke the lower panel in physical check C3. The interceptor applied a waiting refresh request
while the platform was still starting the lower panel at a wake in TOP. The lower DSI link stayed dead until reboot.

## Observed (owner-supervised, 2026-11-02)

**Sequence:**
- **In TOP**, a 60→120 change was intercepted and held (`BYPASS_DEFERRED reason=LOWER_OFF`).
- **Sleep by power button.** Then a wake by power button, still in TOP.
- **At the wake**, the platform started the lower panel as it always does in TOP. The daemon's wake repair would have
  turned it off again 0.7 s later.
- **About 20 ms into that start**, the watcher saw the lower CRTC active and the power state `On`. The interceptor wrote
  the attribute 60 ms into the start (`BYPASS_APPLIED value=0;source=WATCHER`).
- **SurfaceFlinger** finished its power-mode call for the lower panel only 1.4 s later.

**Kernel**, with the writing thread recorded by the long-sleep collector:
- **Display commit thread:** set the mode and reset the panel; this was the platform's start.
- **The daemon's `thor-bypass-ram` thread**, inside the `bypass_ram_store` call, 35 ms after the panel reset:
  - `DSI_PLL_0: lock failed`, then `enable failed: -110`;
  - clock-rate failures, then `failed to send DSI_CMD_SET_VID_PASS_RAM cmds, rc=-22`.
- **Display commit thread, from then on:** every command failed (`Command transfer failed`).

**Outcome:**
- The poisoned-boot detection latched and posted its notice. It kept the lower panel off, also in BOTH.
- No compositor process restarted. A restart recovered the panel.

**Why C2 passed and C3 did not:**
- In C2 (TOP, refresh change, then BOTH) the daemon's own lower ON applied the request after the call had returned,
  once the start was complete.
- In C3 the platform made the ON, and the power state already read `On` from the start of the ON.

## Change

A write now also needs the lower panel to have settled:
- it must have been seen on, in BOTH or BOTTOM ONLY, for at least `SETTLE_MS` (1000 ms; a normal platform start took
  about 200 ms);
- with the display fix on, TOP keeps the lower panel off, so an ON seen in TOP never counts as settled. A request made in
  TOP waits for BOTH;
- an ON made by the daemon itself counts as settled when its call returns, as in C2;
- while the fix is off and a request is draining, the watcher takes no display samples. The drain checks time the ON
  themselves, and an OFF restarts the time.

The watcher passes the sampled AYN mode to the interceptor; nothing else in the coordinator changes. The new deferral
reason is `LOWER_SETTLING`.

## Evidence

| Check | Result |
| --- | --- |
| Host: wake in TOP with a waiting request (`topwake`), before the change | FAIL: store during the platform start, as on the Thor |
| Host: wake in BOTH with a waiting request (`platformon`), before the change | FAIL: same |
| Host, after the change: both cases, a request made mid-start, a lower panel left on in TOP, drain with an OFF in between, reuse after a live transfer | PASS (68 checks) |
| Compiled mutants: start not awaited; first sighting counted as settled; TOP counted; own ON not trusted; drain start counted; OFF not restarting the time | 6/6 rejected (18/18 in the runner) |
| Poisoned-boot, TOP boot, back-off and containment host suites | PASS |

Host fixtures model the start window with a fixed 200 ms. They are not Thor evidence.

Physical acceptance needs C3 repeated on the next signed candidate. Expected:
- no `bypass_ram_store` during the wake in TOP;
- `BYPASS_APPLIED source=LOWER_ON` after the switch to BOTH;
- no DSI error.

C1 and C2 must also be repeated, since the settle rule touches both paths.
