# Target SDK migration feasibility — non-executing

Baseline: Android 13 Thor, manifest targetSdkVersion 25 and code76 application. The compile SDK/API 34 is a separate value. This analysis makes NO manifest or target change.

| Boundary | Observed source | Required next proof |
| --- | --- | --- |
| Boot service startup | Exported BootReceiver, non-exported AutoService; MainActivity starts the service via startService | Emulator background start/denial and receiver events at each candidate target; Thor cold boot and post-compositor handoff separately |
| Vendor Binder and hidden API | PServer reflects ServiceManager service named PServerBinder, transacts with IBinder; daemon uses vendor and root services | Source/reflection API audit for targets 28+ and actual vendor bridge on pinned firmware; compile alone insufficient |
| Unknown-app-sources install approval | AppUpdater deliberately skips canRequestPackageInstalls pre-check for app target below 26; uses PackageInstaller sessions, Android confirmation and API31 mutable PendingIntent | Emulator tests of target-specific permission denial, cancel, retry, stale callback, process recreation; preserve signature chain |
| Component exports | Launcher exported, BOOT_COMPLETED receiver exported, AutoService and UpdateInstallReceiver non-exported | Manifest parser test and emulator callback route; no external implicit installer trigger |
| UI and lifecycle | Landscape-only Activity, paused/resumed updater readiness, restart-tolerant daemon | Emulator UI/font/keyboard and Activity recreation; owner-approved physical handover for vendor-dependent behavior |
| Hardware coupling | ThorHardwareProfile IDs, DRM/CRTC, Hall, secure socket, native PServer | No other firmware, BOTTOM-only, dock or alternate handheld inferred as compatible |

Candidate study stages: 26–27 (unknown sources and service starts); 28–30 (hidden API restrictions); 31–32 (PendingIntent flags and background starts); 33+ (current component, notification and user-consent interactions). These are areas to research, NOT assertions that a given restriction necessarily breaks the app.

Test plan: keep current API 25 compile/build/test baseline; create isolated candidate-target emulator branch only after approval, measure actual behavior with disposable keys; never silently change package name, signing, daemon privilege, or published version. Vendor Binder, Hall and real display states remain physically unproven by emulator.

Current supported claim: tested AYN Thor Android 13 firmware family TKQ1.231222.001. Other firmware/revisions, BOTTOM-only, dock/external and positive closed-lid interception remain limited or NOT OBSERVED.

Decision: no target SDK bump in PR #67.
