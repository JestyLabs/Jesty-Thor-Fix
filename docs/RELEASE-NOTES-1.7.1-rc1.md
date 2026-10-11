# Jesty Thor Fix v1.7.1-rc1

Small pre-release for updater cancellation and diagnostics on existing events.

- A Cancel accepted before installer commit prevents handoff. After commit,
  the app reports that Android owns the installation.
- Classify privileged bridge failures and record CPU helper handoff decisions
  on existing events, with bounded output and no added polling.
- Admit the exact v1.7.0 daemon through the existing authenticated,
  healthy-watcher, BOTH/active-CRTC update gate.

Install over the existing app to preserve settings. Version code: 72.
