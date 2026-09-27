# Public release checklist

## Repository and identity

- [x] Public staging tree is separate from the signing workspace.
- [x] No keystore, signing password, token, or credential file is present.
- [x] No personal Windows username, home path, email address, or device serial is present in tracked text.
- [x] Configure the repository-local Git author as `SirJesty` with a non-personal
  `users.noreply.github.com` address.
- [x] Configure `origin` for `JestyLabs/Jesty-Thor-Fix`.
- [x] Use the repository-local `SirJesty` identity with the matching GitHub
  `users.noreply.github.com` address.
- [x] Confirm GitHub email privacy for command-line commits.
- [x] Review the complete staged diff before the stable release commit.

## Build and release

- [x] Build script has no maintainer-specific path and supports unsigned reproducible builds.
- [x] Complete a clean unsigned build from the staged repository.
- [x] Confirm package/version and preserved true-off timing implementation.
- [x] Install the exact signed 1.1.0 APK in place on the physical Thor.
- [x] Test TOP native/fix, BOTH, bottom-screen check, Back/closed UI,
  sleep/wake, Dashboard CPU Fix display reset, daemon recovery, and reboot
  persistence.
- [x] Confirm the official APK certificate and SHA-256 before publishing.
- [x] Capture README screenshots from the exact signed 1.1.0 artifact and the
  directly preceding real Dashboard-open pinning reproduction.

## Artwork blocker

- [x] Jesty mascot and wordmark have separate reserved-branding terms.
- [x] AI assistance is disclosed.
- [x] The old development-room composition is absent from the staged tree.
- [x] The maintainer supplied new public-release ON/OFF backgrounds.
- [x] The new Jesty wordmark is used in the top-left application header.
- [x] Final artwork terms are recorded in `ASSETS-LICENSE.md` and AI assistance
  is disclosed.

Do not push or publish until the final staged diff and signed release APK have
completed privacy and hardware validation.
