# v1.7.6

- Updating from v1.7.5 transfers the background service without a restart.
- The background service can also be transferred while the Thor is in TOP mode with the lower screen confirmed off, not only in BOTH.
- If the background service of the current version fails to start correctly, the app can replace it safely without a reboot; an unconfirmed attempt still asks for one normal restart.
- Shorter and clearer pending-update notice.
- After a display call that stalls for more than five seconds, the app keeps the lower screen OFF in BOTH until the next
  reboot or a manual fix toggle, instead of turning it back on after the next mode change or wake. This avoids re-entering
  a vendor display-reset loop that can blank both screens.

Display-recovery actions are unchanged except for the boot-scoped stall containment above; CPU Fix, Closed-Lid Wake Guard
and the documented hardware limitations are unchanged.
