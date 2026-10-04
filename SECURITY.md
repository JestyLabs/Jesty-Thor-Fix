# Security policy

## Reporting a vulnerability

Please use GitHub's private vulnerability-reporting feature when it is enabled for this repository. Do not publish an exploit, signing material, device serial, or unredacted system log in a public issue.

Ordinary bug reports can use GitHub Issues after removing personal information and unrelated log data.

## Privileged behavior

Jesty Thor Fix is intentionally device-specific and privileged:

- it asks the Thor firmware's `PServerBinder` bridge to launch a root-side `app_process` daemon;
- it changes the lower physical display power mode through hidden Android APIs;
- it writes the Thor-specific `display.power.state` system property and, for the optional CPU Fix, the vendor property `vendor.display.disable_system_load_check`;
- it reads CPU frequency/load data and the DRM debugfs state;
- with Wake Guard ON, it reads the lid switch and can send `KEYCODE_SLEEP`.

The app does not contain analytics, advertising, or cloud synchronization. It does not modify CPU governors, frequencies, voltages, thermal limits, firmware or partitions.

## App-to-daemon channel

- The daemon listens on a filesystem Unix socket, `files/jesty-thor-control-v2.sock` in the app's private data directory, with the app UID as owner and mode `0600`. Before binding, it takes a private instance lock (`O_NOFOLLOW`, regular file, owner checked) and validates the socket inode afterwards.
- Both sides authenticate with kernel peer credentials (`SO_PEERCRED`): the daemon serves only the app's UID, and the app trusts a reply only from UID 0. A successful-looking reply is never treated as identity.
- The protocol is a closed set of one-byte commands (`I`, `Q`, `V`, `E`, `N`, `R`, `L`, `G`, `H`). None carries an argument, path or shell text; the daemon decides internally what each one does. Reads time out after 1.5 seconds and at most two connections are served, with four queued.
- There is no TCP listener. Loopback `127.0.0.1:3804` belonged to daemons before the Unix-socket protocol; the app only connects there read-only to identify such a legacy daemon once during migration, then stops it after checking its process identity.
- Replacing an existing root daemon requires an authenticated identity response, an allowlisted older version or a stalled same-version boot phase, and a check of the PID's root UID and exact command line before the signal.

## Root-written files

The daemon and its compositor helper write a sanitized boot trace and launch log in `/data/local/tmp`, where the read-only ADB collector can read them. Because that directory is shell-writable, the daemon opens the trace with `O_NOFOLLOW` and appends only to a regular, single-link, root-owned file; the shell side checks for a link or foreign file before each redirection and otherwise discards the output. The files contain phase names, timestamps, PIDs, display/CPU flags and the kernel boot ID, not personal data.

## In-app updates

While the dashboard is open, the app may ask `api.github.com` for this repository's releases (at most once an hour, or when the user asks) and download one release APK. It accepts only the asset named `Jesty-Thor-Fix-<version>.apk` from `github.com/JestyLabs/Jesty-Thor-Fix/releases/download/<tag>/`, verifies GitHub's published size and SHA-256 digest, the package name, version and signing certificate, and then uses Android's `PackageInstaller`, which shows its own confirmation. The updater never uses the root daemon, `su` or a shell. GitHub release access and the maintainer's signing key are therefore part of the update trust chain.

Do not install builds from unknown mirrors. A modified APK can execute commands through the device's privileged bridge.
