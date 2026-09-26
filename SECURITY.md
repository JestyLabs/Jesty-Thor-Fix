# Security policy

## Reporting a vulnerability

Please use GitHub's private vulnerability-reporting feature when it is enabled for this repository. Do not publish an exploit, signing material, device serial, or unredacted system log in a public issue.

Ordinary bug reports can use GitHub Issues after removing personal information and unrelated log data.

## Privileged behavior

Jesty Thor Fix is intentionally device-specific and privileged:

- it asks the Thor firmware's `PServerBinder` bridge to launch a root-side `app_process` daemon;
- it changes the lower physical display power mode through hidden Android APIs;
- it writes the Thor-specific `display.power.state` system property;
- it reads CPU frequency/load data and performs an optional DRM debugfs read;
- its command socket is bound to loopback at `127.0.0.1:3804`.

The app does not contain analytics, advertising, cloud synchronization, or an update service. It does not modify CPU governors or frequencies.

Do not install builds from unknown mirrors. A modified APK can execute commands through the device's privileged bridge.
