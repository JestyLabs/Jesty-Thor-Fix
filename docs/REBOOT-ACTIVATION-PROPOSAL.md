# Reboot activation proposal V3

This is an unpublished source proposal. After installation, open Jesty Thor Fix once to prepare activation, restart the Thor normally, then reopen the app and verify the service. The source still has the candidate identity; no stable artifact or recovery version is allocated by this proposal.

## Proof and trust boundary

The first app/service observation of an installation stores its package stamp and current kernel boot ID as a private, checksummed baseline. The stamp uses versionName, versionCode and lastUpdateTime as equality metadata, never as a wall-clock ordering proof. A matching baseline must already exist and its valid boot UUID must differ from the current UUID before startup is admitted. UUID casing differences do not qualify. BOOT_COMPLETED supplies no authorization; it is only a startup trigger.

The causal order is: installed package observed, baseline recorded, a different kernel boot observed. If the first observation occurs only after a reboot, that observation prepares the baseline and one further reboot is required. Missing, malformed, different-installation or old V2 markers cannot authorize; they prepare a new current-boot baseline when storage and identity permit. Read failures do not overwrite unknown evidence. A false/throwing commit cannot authorize the first observed boot. Once the baseline survives reboot, activation needs no new authorization write.

This assumes genuine kernel boot IDs, accurate PackageManager metadata for this installation, private app storage, and a normal single app process. The manifest excludes app backup. The checksum detects accidental corruption; it is not an authenticity signature against root, deliberate preference editing or restored privileged snapshots. Installation stamp uniqueness and storage/boot-ID behavior still need Android acceptance. No elapsedRealtime-minus-wall-clock or lastUpdateTime comparison is used to infer a reboot.

## Entry points and dashboard

AutoService checks this evidence before probing or privileged launch. Every newly launched daemon enters the existing boot hold/coordinator, including a foreground launch after a missed broadcast. No previous-version or legacy stop is requested. Existing same-version recovery remains, with its existing process identity limitations. Root/debug execution of the daemon outside these application entry points is outside this gate. The separately installed early CPU hook is unchanged and is not a daemon authorization path; its pre-existing behavior is not claimed to be gated by this proposal.

The existing Q response includes runtime_version. Activation needs verified boot evidence plus matching runtime, watcher RUNNING, boot READY and released display actions. Earlier/missing runtime identity is never new ACTIVE. Pending controls remain disabled. Missing storage/identity displays ACTIVATION BLOCKED and recovery guidance, rather than an instruction to keep rebooting. Already executing commands are not cancelled by this change.

Once the kernel reboot is verified, earlier or missing runtime identity displays ACTIVATION BLOCKED / SERVICE IDENTITY MISMATCH. The user is asked to save diagnostics and seek support, without suggesting another preparation/reboot or implying that a recovery artifact is already available. RESTART REQUIRED is reserved for a prepared baseline that still needs a different kernel boot. Starting/held current-version telemetry remains ACTIVATION PENDING.

## Lifecycle and recovery

- Clean install or update: open once, prepare baseline, normal reboot, reopen and verify.
- Never opened/stopped, or reboot before first observation: the eventual first open prepares a baseline and requests the additional reboot. It does not assume a missed broadcast was delivered.
- Force-stop: retain the baseline. A manual open after a new boot can activate without any broadcast. Force-stop alone does not count as reboot.
- Data clear: baseline is gone, so preparation and a new reboot are required. Clearing data is not a suggested recovery action.
- Storage/identity failure: remain blocked, show a bounded retry/escalation procedure and preserve data. The updater remains blocked while boot evidence is unverified; external recovery is deliberately independent of it.

See [the proposed recovery guide](ACTIVATION-RECOVERY-GUIDE.md). Recovery requires a separately approved forward-versioned, independently sourced and correctly signed package installed through Android's normal external installer. No such artifact/channel is approved or produced here. Lack of that concrete rescue route remains a release HOLD.

## Validation limits

Host tests run actual service, PServer, activation policy/helper and renderer with explicit Android/Binder/shell/widget adapters. Stopped state, force-stop, missed broadcasts, durable versus cached preferences and kernel transitions are modeled inputs. No Android receiver delivery, persistent storage across an actual reboot, APK parser/install, visible controls, root/vendor runtime or physical Thor behavior is proved. Android and Thor acceptance remain NOT RUN. The version comparator, archive verifier, release identity and predecessor allowlist remain unchanged.
