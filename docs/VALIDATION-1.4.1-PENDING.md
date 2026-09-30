# v1.4.1 physical validation — testing pre-release

Completed:

- Boot/lid model tests and dashboard tests passed.
- Static smali checks passed, including boot-hold polarity, mode watcher and
  suppression while wake repair is pending.
- Clean signed APK build passed manifest, alignment and v1/v2/v3 signature
  verification with the existing Jesty Thor certificate.
- The local watcher-fix test candidate with the same logic was installed on
  the maintainer's Thor. Its installed hash matched the host artifact.
- Physical AYN-button TOP/BOTH cycling appeared correct to the maintainer;
  daemon logs showed three `WATCH ON MANUAL` and three `WATCH OFF MANUAL` actions.
- In steady TOP mode: `boot_phase=READY`, `fix=1`, `mode=1`, `top_crtc=1`,
  `bottom_crtc=0`, and `repair_result=OFF_OK`.

Still required before stable promotion:

- Five cold boots per combination: both fixes OFF, bottom-only, CPU-only,
  and both ON. Record lower-screen visuals, CRTCs and composer restart count.
- Closed-lid false wake, anti-loop and external-display tests.
- Sleep/wake and TOP/BOTH/BOTTOM mode transition matrix on the final build.
- Investigate the initial SurfaceFlinger EGLConfig abort; its observed timing
  precedes application actions, but its root cause is not established.

No boot stress testing was performed for this patch at the user's request;
those tests are reserved for a later phase.
