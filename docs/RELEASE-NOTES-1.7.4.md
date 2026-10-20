# v1.7.4 — candidate, not released

- Prepares automatic daemon replacement from published 1.7.1, 1.7.2 and 1.7.3, preserving settings and requiring authenticated process exit before the successor starts.
- Preserves the TOP wake-cancellation correction already accepted into main.
- After a display-power call stalls, pauses automatic lower-screen ON retries and attempts to turn the lower screen off in BOTH to contain the failure. Normal mode changes, confirmed manual reconciliation or an observed Android sleep/wake cycle can restore normal BOTH operation.
- Adds display-transition diagnostics. This mitigates one retry sequence; the underlying panel/compositor blackout remains under investigation.

The normal upgrade target is a healthy new daemon without restarting the device. Unsafe or unsupported state remains blocked with the existing recovery path.

Host tests pass. Native Android execution and exact-candidate Thor acceptance remain release gates; this document is not a publication record.
