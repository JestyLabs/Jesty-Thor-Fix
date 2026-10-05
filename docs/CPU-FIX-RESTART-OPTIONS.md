# Dashboard CPU Fix: what was tested and what remains possible

**Status (2026-10-05):** the early-write design below remains unimplemented.
The signed v1.5.20 APK was physically tested with the existing compositor
restart path; no pre-composer hook was installed or tested. Source history:
Codex thread
`01a0cbaa-8916-7ee0-8dfb-df13250c180c` (the v1.0.x-v1.2.0 CPU Fix
experiments), [LIVE-RESULTS](LIVE-RESULTS.md), current code and the
[v1.5.17 validation diary](VALIDATION-1.5.17-PENDING.md). Old message
conclusions are treated as observations, not proof of untested boot designs.

## Already demonstrated on the Thor

| Experiment | Observed result | Conclusion |
| --- | --- | --- |
| BOTH, AYN Dashboard open, fix OFF | LITTLE 2.016 GHz and BIG 2.707 GHz at their maximum in 20/20 samples; after closing the Dashboard, 0/30 maximum samples with the same display mode and fix setting | The Dashboard is the additional trigger in dual-screen use; the fix app being foreground was not the trigger. |
| Write `vendor.display.disable_system_load_check=1` with Dashboard still open, without composer restart | LITTLE/BIG stayed pinned through 15 samples; composer PID and visual state did not change | A late property write alone does not apply the fix in this firmware. |
| Write `1`, then restart the vendor composer once | Kernel boot ID remained unchanged; display/USB disappeared temporarily. After recovery and reopening Dashboard, LITTLE/BIG had 0/35 maximum samples and low CPU load | Starting a new composer with `1` applied the fix. The kernel did not reboot. |
| Write property back to `0`, then switch TOP to BOTH and reopen Dashboard, without composer restart | 0/30 maximum samples while the same composer process remained alive | A panel recreation does not make the composer reread the property. The effective state can differ from the current `getprop` value until the next composer start. |
| v1.1.0/v1.2.0 toggle OFF then ON | OFF changed the composer PID and reproduced stock behavior; ON changed it again and restored the fix. Separate observed transitions took about 28 s and 26 s. The kernel boot ID did not change. | Runtime ON and OFF both need one visual Android UI/display restart on this firmware. Duration is variable; later one transition took close to a minute. |
| v1.2.0 saved ON across a cold boot | First visual boot around 18 s; the automatic CPU Fix requested the framework/display restart around 47 s; app and daemon returned around 60 s, with preferences and clocks correct | The older two-phase boot was observed before the present v1.5.17 gate; it was not always a five-second gap. |
| Old `Thor System Load Fix` APK audit | It set the same vendor property and restarted the Qualcomm composer; no CPU governor or frequency write | It used the same mechanism, not an independent dynamic fix. |

The vendor composer init rule says `onrestart restart surfaceflinger`;
SurfaceFlinger's init rule restarts zygote. Therefore an unchanged kernel
`boot_id` does **not** mean apps and Android framework stayed running. Early
thread messages described only a quick display/USB reset; later code and
timing established the full cascade. Opening the app must not trigger
one automatically; the user-facing toggle explicitly confirms the restart.

The old binary inspection found the runtime key but no `persist.vendor...`
equivalent or exposed dynamic API. This limits the tested routes; it does not
prove that no proprietary mechanism could ever exist.

## Next investigation: reload the vendor state without a restart

The product goal is one visual Android startup on a cold boot. Before building
an early boot hook, inspect the actual consumer of the property and look for
a supported, callable way to update its effective state in a running composer.
This investigation is read-only; do not issue a display command merely because
a method name suggests it could work.

On 2026-10-05, a read-only search of likely Thor vendor display binaries found
`vendor.display.disable_system_load_check` in
`/vendor/lib64/libsdmextension.so` (local copy SHA-256
`B23AC200FAA14F81DAC69AC3C83CF922A02C237E3FC7A3202B00137622862370`).
That binary also contains the symbol `sdm::ResourceImpl::CheckSystemLoad`.
These are search leads, **not** proof of which function reads the property or
whether it can be reloaded. The composer executable copied earlier did not
contain the property string. Next, resolve the property string's code
references, trace the value into ResourceImpl and QOS state, then inspect any
public or private display-config call that reaches the same state. A host
finding must identify exact call semantics and access checks before a single
supervised runtime trial could be considered. The late `setprop` and TOP/BOTH
cycle have already failed, so repeating them alone adds no evidence.

If no safe dynamic call is found, a separate candidate may restart the composer
earlier through a CPU-only readiness gate while `BOOT HOLD` still blocks every
display and sleep action. The successor must still run the full existing
mode/CRTC stability and grace gate before reconciliation. This could move the
second visual phase earlier, but cannot remove it. Do not shorten the display
gate or infer safety from a known CPU property alone.

Only after that should a tiny Direct Boot prelude be considered. It needs
device-protected CPU intent, proof that the vendor bridge works before normal
`BOOT_COMPLETED`, and idempotence across both broadcasts. The pre-composer
property write remains a parallel research question: the observed window
between `qti_display_boot` and composer start was roughly 0.37 s, and no
automatic uninstall cleanup for a persistent `/data/adb` hook is established.
Without a demonstrably removable, recoverable mechanism, do not install one.

## If a pre-composer boot write is viable

The intended user behavior is straightforward:

- **Saved ON at cold boot:** an opt-in early mechanism writes `1` *before* the
  first composer process starts. The normal boot coordinator still holds
  display/sleep actions and verifies the mode and CRTCs. If timing and
  provenance prove the composer started after that write, it skips the second
  visual boot. A plain `getprop=1` is insufficient proof: the write could have
  happened too late.
- **ON while Android is running:** save the choice, write `1`, confirm it,
  then perform the existing single composer/framework restart with the
  explicit UI warning. Also arm the early mechanism for the next cold boot.
- **OFF while Android is running:** disarm the early mechanism first, save
  OFF, write `0`, then perform one visual restart to restore stock behavior.
  If disarming fails, reject the toggle instead of claiming OFF; otherwise a
  future boot could silently turn the fix back on.
- **No early mechanism or failed timing proof:** use the current guarded
  compositor restart at boot, with one attempt and a visible failure state.
  Do not claim `CPU FIX ACTIVE` solely from property `1`.

This preserves the immediate ON/OFF behavior the owner likes. It aims to
remove the *automatic* second visual phase on cold boot, not the restart
needed when the toggle changes during a running Android session.

## Feasibility and alternatives

The Thor's vendor `qti_display_boot` one-shot is triggered at `post-fs-data`
around 3.701 s and the composer starts around 4.074 s: only ~0.37 s separates
them in one captured boot. That vendor script sets the property for
`subtype_id=1`, while this Thor is subtype 0. An external post-fs-data hook
may run too late. ADB shell has no `su` and cannot read `/data/adb`, so the
presence and ordering of Magisk/KernelSU/APatch hooks are unconfirmed. Do
not install one based only on these timestamps.

Other routes remain limited:

- `setprop` after composer startup and TOP/BOTH cycling were physically
  tested and did not update the effective composer state.
- A persistent property name was not found in the old binary audit; the
  stock vendor script does not enable this property on subtype 0.
- Closing or replacing the AYN Dashboard avoids its observed pinning trigger
  but removes the feature the owner wants; writing CPU clocks/governors would
  mask the symptom and is outside this fix.
- An earlier `BOOT_COMPLETED` receiver or shorter gate would bring the
  existing restart forward, not eliminate it. They carry separate boot and
  broadcast risks; see the [latency investigation](BOOT-RESTART-LATENCY-INVESTIGATION.md).

## Future supervised trial: one decision, at most one candidate cold boot

1. **No write, no reboot first.** Recheck installed version, battery, BOTH
   image/CRTCs, boot ID, CPU preference/property, composer/daemon PIDs and
   known early SurfaceFlinger abort signature. Run
   `scripts/collect-thor-boot.ps1 -Serial 96f052f5 -Label cpu-early-preflight -InspectInit`
   while ADB is connected. Confirm a usable early hook and its actual order;
   package or executable absence from ADB shell is not conclusive.
2. **Go/no-go before any candidate.** A safe trial requires an opt-in,
   removable one-shot hook; a reliable timestamp and boot-ID record proving
   property write before composer start; a known state for ON/OFF; a bounded
   fallback; and uninstall cleanup. Current `D.java` skips restart whenever
   `getprop` already equals the desired value, so installing a possibly-late
   script against the current APK could falsely show success. Fix that check
   in a test candidate *before* a boot trial. If these requirements cannot be
   met, stop with no trial reboot and keep the existing restart path.
3. **If the candidate is ready:** install once in BOTH with the owner watching,
   preserve app data, verify no compositor restart on install, then use one
   supervised cold boot. Collect early logs, script write timestamp,
   `ro.boottime`/composer PID, app trace, first/second visual-finish events,
   physical CRTCs and Dashboard time-in-state after settling. Success means
   property write demonstrably precedes first composer start, no Jesty
   compositor restart, no second visual phase, correct display state and
   clocks released with the AYN Dashboard open. A second visual phase is a
   fallback result, not success for the early path.
4. **Stop immediately** on green flash, wrong panel, new SurfaceFlinger abort,
   extra kernel boot, restart loop, failed cleanup or incomplete recovery.
   Preserve logs before considering any further action. Do not run a separate
   runtime ON/OFF cycle merely to reconfirm the historical behavior; it would
   add two avoidable UI restarts. Test a newly changed toggle implementation
   only if the candidate actually changes that code.

An app uninstall cannot execute its own cleanup callback. Any persistent
boot hook needs an independently verified removal mechanism; an inert but
stranded `/data/adb` script does not satisfy that requirement. No hook, APK
change or cold boot is prepared for installation until this design is viable.
