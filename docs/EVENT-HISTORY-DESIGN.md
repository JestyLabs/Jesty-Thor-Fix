# Local event history — draft / device validation pending

Purpose: make intermittent daemon, display and wake issues easier to describe
without adding background work, changing existing fixes, or requiring users to
collect raw logcat dumps for common support questions.

## Data source and ownership

The existing dashboard polls the authenticated private-socket `Q` endpoint
once per second **only while the activity is resumed**. The event history
observes those same already-parsed samples on the UI thread. No new command,
service, daemon observer, firmware hook, hidden API, polling loop, timer or
permission is introduced.

- `EventHistoryModel` (pure Java) converts **state changes** to typed,
  allow-listed events, deduplicates consecutive samples, and stores a
  fixed-size ring buffer of **64 entries**.
- `EventHistoryJournal` persists only changed history in the app's own
  `SharedPreferences` using asynchronous `apply()`; a steady 1 Hz telemetry
  stream performs **zero history writes**. Persistence is independent of
  updater readiness, boot holds and root-daemon decisions.
- `EventHistoryDialog` renders an EVENTS entry in the dashboard's top-right
  actions. **COPY** explicitly copies a sanitized text report to the Android
  clipboard; **CLEAR** asks for confirmation before deleting local entries.
  No report is sent to the network.
- The model never stores free-form error messages, raw daemon replies,
  file/socket paths, device identifiers, kernel boot IDs, arbitrary values,
  clock/energy samples, PIDs, shell output or user data.

## Event interpretation

| Type | Source | What is and is not proved |
| --- | --- | --- |
| DAEMON_CONNECTED / DAEMON_UNAVAILABLE | Authenticated Q reply succeeded or failed | App can/cannot obtain a current telemetry sample; not proof of service-manager registration |
| BOOT_HOLD_ENTERED / CLEARED | `display_actions_held=1/0` | Reported hold state, not physical wake-lock state |
| WATCHER_RUNNING / NOT_RUNNING | `watcher_health` | Reported watcher readiness, not a heartbeat from Android itself |
| DISPLAY_OBSERVED | `mode`, `top_crtc`, `bottom_crtc` | Exact reported values after **two consistent samples**, NOT physical pixel refresh or panel power measurement |
| WAKE_OBSERVED | Increasing `wake_id` | Increment observed since a prior connected sample; decreases on daemon restarts are ignored |
| REPAIR_STATUS | Strict allowlist of `repair_result` values | A change in reported repair stage; not an independent screen-off proof |
| CPU_PHASE | Strict allowlist of `cpu_fix_phase` values | App-observed CPU Fix state, not a CPU frequency/performance claim |

Unknown display/CRTC values never become proof of a screen being off.
When the `Q` response becomes unavailable, the model resets observation
baselines; it never fabricates events to bridge the unknown interval. Stored
history survives app process restarts but can be cleared on-device.

## Safety boundaries and test plan

1. **Host:** deterministic tests assert no events/writes for hundreds of
   identical samples; distinct mode/CRTC pairs, delayed two-sample
   confirmation, unknown states, counter rollover, disconnect/reconnect,
   allowlist rejection, serialization, corruption handling, clear and cap.
2. **CI compile:** existing Windows/Apktool/Java build verifies the new
   dashboard classes compile with the current app and its current target SDK.
3. **Emulator:** existing PR #51 smoke confirms app startup without
   `PServerBinder`; testing EVENTS UI/clipboard on emulators remains a
   distinct observation, not implied by compilation.
4. **Physical Thor:** owner-supervised UX and readability check, TOP/BOTH
   transitions, wake repair, persistence after process restart, clears and
   export; compare entries against telemetry/device trace. No vendor-process
   restarts or refresh-mode changes just to generate events.
5. **Release gate:** maintain this as a draft until Android device UX and
   privacy bounds are checked. No new release automatically.

## Explicit limitations

- Not a replacement for root `BootTrace`, privileged handoff traces or CRTC
  measurements; events only exist while the dashboard is open.
- It does not capture every wake/transition while the dashboard is closed.
- The app-side journal is not a boot-surviving daemon audit log.
- No background monitoring and no attempt to suppress/optimise the existing
  watcher (research PR #41 stays independent).
