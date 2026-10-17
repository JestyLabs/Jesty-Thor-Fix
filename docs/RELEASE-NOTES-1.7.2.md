# Jesty Thor Fix 1.7.2 — release preparation

This version is under integration and has not been published. The existing public release remains available through the README.

## Changes

- Activation requires a kernel reboot after the installed package has been observed and its baseline saved. A boot broadcast alone cannot authorize a new daemon.
- The dashboard distinguishes restart required, activation pending and activation blocked. Controls stay disabled until the expected runtime is ready.
- Startup does not request live replacement of a daemon from another version. Home/reopening the app does not reset a valid activation baseline.
- The engineering changes integrated in PR #67 add updater, diagnostics and test coverage. The updater retains its local version-code check before installation.

## Install and activate

Install the approved APK as a normal update, open once to prepare activation, restart the Thor normally and reopen to verify the service. See the [activation and recovery guide](ACTIVATION-RECOVERY-GUIDE.md). A newly installed app first opened after a reboot may need one additional reboot after preparation.

## Validation and limits

The exact signed 1.7.2/code80 candidate passed the supervised RC78 update, baseline preparation, normal reboot, service readiness, Home/reopen and BOTH→TOP→BOTH checks on Android 13 Thor hardware. The separate controlled recovery chain through code81 and code82 also passed within its tested conditions. Selected preferences and application data identity were retained. These checks do not cover every predecessor, clean installation on Thor, storage faults or recovery during an actual blackout.

Historical performance results remain scoped to their tested artifacts: the original RC78 T09 CPU failure is preserved alongside the later A1 acceptance series. No new CPU-budget or battery-runtime measurement was performed for code80/code82 in the final acceptance session.

## Known issues

Persistent black screens following TOP sleep/wake remain tracked in [issue #84](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/84); their cause and correction are not established. The associated delayed lower-screen activation observation is not a demonstrated reproduction of that blackout. The brief brightness flash has a separate tracker, [issue #65](https://github.com/JestyLabs/Jesty-Thor-Fix/issues/65). This version does not claim to fix either investigation.

External recovery depends on a usable normal installer or previously authorized, functioning transport. The controlled recovery packages are not normal updater releases and do not provide guaranteed recovery if both screen access and transport are unavailable.
