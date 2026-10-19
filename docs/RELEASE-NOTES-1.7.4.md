# v1.7.4 — candidate, not released

- Prepares automatic daemon replacement from published 1.7.1, 1.7.2 and 1.7.3, preserving settings and requiring authenticated process exit before the successor starts.
- Preserves the TOP wake-cancellation correction already accepted into main.

The normal upgrade target is a healthy new daemon without restarting the device. Unsafe or unsupported state remains blocked with the existing recovery path.

Host tests pass. Native Android execution and exact-candidate Thor acceptance remain release gates; this document is not a publication record.
