# Dashboard accessibility and discoverability — offline audit

Original scope: source inspection, host static contracts and Android build only. The historical NOT RUN statuses below describe that audit. Subsequent [native emulator validation](ENGINEERING-HARDENING-UI-EMULATOR-2026-10-13.md) covers keyboard/D-pad, accessibility nodes and 100/150/200% scaling, with a confirmed diagnostics dialog layout gap. TalkBack speech remains NOT RUN; no accessibility certification.

## Findings by control

| Entry point | Source finding | Status |
| --- | --- | --- |
| Three feature switches | The visible title and help are separate TextViews; each Switch has empty text | Added accessible control name from its own heading; Android retains checked state announcement; emulator screen reader still to verify |
| Diagnostics | Focusable clickable image with existing accessible description | Static review PASS; Android traversal NOT RUN |
| GitHub, Support, Update | TextView buttons with fixed 38 dp height | Added explicit keyboard focus and update description; 48 dp conventional target and focus ring require layout/visual assessment |
| GitHub updater preferences | Hidden under GitHub long press | Added TalkBack shortcut hint, but keyboard/D-pad long-press access remains UNVERIFIED. Recommend a separately reviewed visible Settings action |
| Decorative image/video | Background image and TextureView are not interactive | Removed from accessibility focus |
| Fonts and landscape | 56% fixed-width scroll panel, 9–10sp metrics text, small control heights | 150–200% font-scale clipping, contrast and landscape screenshots NOT RUN |

## Follow-up acceptance checklist

- [x] Static source audit and labels/focus semantics in PR #67 (no privileged or install-policy change).
- [ ] Run accessibility tree/TalkBack on Android 13 emulator: switch name plus checked state, single speech announcements, no decorative traps.
- [ ] Run Tab, Shift+Tab, D-pad, Enter and Space navigation; verify Settings access without touch long press, not assumed.
- [ ] Capture emulator screenshots at 100%, 150%, 200% text scaling. Inspect top actions, help text, CPU/status values and updater dialog for clipping/overlap.
- [ ] Measure actionable touch areas; if buttons remain under 48dp, propose layout-only enlargement with screenshot proof.
- [ ] Check contrast from rendered states, not color constants alone.
- [ ] Propose visible Update Settings affordance in separate UI-only review; never change updater permission, confirmation, signing or daemon control to solve discoverability.

Status: static labels implemented; real user interaction remains to validate. No physical Thor session yet.
