# How deleted times are handled is not settled

ARES withdraws a time by sending it again with time type `E` (`DeletedTime`), with the value it withdraws; a deleted
finish arrives as a split time of the last lap. The heat model (`alphatimer-heats`) handles this with two rules that
are inferred, not confirmed by the ARES manual or by recordings:

- A deletion marks every entry of the lane with the same lap and time value as `deleted` (any kind but backup). The
  entry stays in the history; `finish()` and `current()` skip it, so the lane falls back to its previous valid time.
- A time adds an entry unless it equals the newest valid entry of its lap and kind. So a deleted time that is sent
  again counts again, and so does a value that returns after a correction.

Related behavior elsewhere: `LegacyService` deletes every stored finish of the lane with that value, regardless of
lap; `BackupTimeParser` drops a backup line marked as deleted; deletions in the results of the previous race are
ignored with those results.

- **Effect:** if ARES means something else by these messages, a lane shows a withdrawn time or loses a valid one, and
  its place is wrong. The REST endpoint planned in `docs/features/timing-rest-endpoint.md` would serve the same.
- **Open questions:**
  - Does ARES only ever withdraw the latest time of a lane, or also an older split?
  - Is a deletion always followed by a corrected time, and does the corrected time arrive as `CorrectedTime` or as an
    ordinary time?
  - Can the same value legitimately occur twice on one lap (e.g. a repeat after a deletion), and should a deletion then
    mark only the newest matching entry instead of all of them?
  - Does a deletion in the result list (message type 3/4) differ from one in the live times (type 2)?
  - Should withdrawn entries be served to clients at all, or only kept for the history?
- **Where:** `alphatimer-heats` (`Lane.deleteTime`, `Lane.touch`), `alphatimer-legacy-server` (`LegacyService`),
  `alphatimer-refinedmessages` (`TimeParser`, `BackupTimeParser`).
- **Idea:** search the ARES manuals in `docs/ares` for the time type `E` and look for deletions in the recordings in
  `data/` (raw data analysis of `alphatimer-cli`); pin what is found with tests, then adjust the two rules.
