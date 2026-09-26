# Artwork integrity

Final maintainer-supplied source assets integrated on 2026-09-26:

| Asset | SHA-256 |
| --- | --- |
| `assets/jesty_thor_background.png` | `4A35CD338657DC2A2BB862BB45D85F3724F59B905A27B02831C5322BF8922CC3` |
| `assets/jesty_thor_background_off.png` | `B769A8DFD0CD0AB3674074C3DE4F2BF1F3EAC012A92C2744E071DAF52DD59BA6` |
| `assets/branding/jesty_wordmark_header.png` | `1387F3D28679A9E2FA248656064EF42C9CF6F66E247AC659B651F40A728500A2` |
| `assets/jesty_thor_background_loop.mp4` | `AACBB625E4E91A77DF6F706896EFCCCCD0A456EA78B2FC97513CE3559D753294` |

The MP4 is an eight-second, silent, 1920x1080 H.264 loop generated from the
approved ON/OFF pair. It normally displays the ON artwork and includes three
brief lower-screen-off intervals. Confirmed hardware `power=0` does not use the
loop; the Activity pauses it and shows the OFF PNG continuously.
