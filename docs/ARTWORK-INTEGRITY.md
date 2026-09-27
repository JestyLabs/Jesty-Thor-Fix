# Artwork integrity

Final maintainer-supplied source assets integrated on 2026-09-26:

| Asset | SHA-256 |
| --- | --- |
| `assets/jesty_thor_background.png` | `4A35CD338657DC2A2BB862BB45D85F3724F59B905A27B02831C5322BF8922CC3` |
| `assets/jesty_thor_background_off.png` | `B769A8DFD0CD0AB3674074C3DE4F2BF1F3EAC012A92C2744E071DAF52DD59BA6` |
| `assets/jesty_thor_background_fake_off.png` | `ACC9EB01BA5961380E6406FCE0E06D2E33ED89367B8DC3CEA81C6E880CF09A18` |
| `assets/jesty_thor_background_true_off.png` | `7A7299675351AF59BA8AB6CD9AAC9ED883826FB22BE172AB83CA51F94A5D2045` |
| `assets/branding/jesty_wordmark_header.png` | `1387F3D28679A9E2FA248656064EF42C9CF6F66E247AC659B651F40A728500A2` |
| `assets/jesty_thor_background_loop.mp4` | `AACBB625E4E91A77DF6F706896EFCCCCD0A456EA78B2FC97513CE3559D753294` |

The MP4 is an eight-second, silent, 1920x1080 H.264 loop generated from the
approved ON/OFF pair. It normally displays the ON artwork and includes three
brief lower-screen-off intervals. Confirmed hardware `power=0` does not use the
loop; the Activity pauses it and shows the true-off PNG continuously. TOP mode
with the lower CRTC still active uses the separate fake-off PNG. The two 1.2.0
variants were created with generative-AI assistance under maintainer direction
and review; see [AI_DISCLOSURE.md](../AI_DISCLOSURE.md).
