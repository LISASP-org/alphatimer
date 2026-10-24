# Open Issues

Open findings from code work that are not fixed yet. Each issue is a self-contained file; once it is fixed and
verified, delete the file and its row here (see `AGENTS.md` → *Documentation and Issues*). Keep this README with its
table even when no issues are open.

Every row has a severity and a size; keep the table ordered by severity (High, Medium, Low), then by size (S, M, L).

- **Severity:** High = wrong results or lost/corrupted data; Medium = a feature fails or misleads, with a workaround;
  Low = no user-visible effect, or only inconvenience.
- **Size:** S = a local change of a few lines with a test; M = several classes or a migration; L = spread over many
  modules or needs a design decision.

| Issue | Severity | Size | Summary |
| --- | --- | --- | --- |
| [Saving also blocks the recording](save-blocks-recording.md) | Medium | M | A slow save target blocks the reader thread for the whole save |
| [A failed recovery keeps the work file locked](recover-leaks-database.md) | Low | S | The leftover can be neither recovered nor discarded until a restart |
| [Older files with a competition tag can show a heat twice](competition-tag-duplicates-heat.md) | Low | S | Recording into an old tagged file creates a second row for the same heat |
| [Some code does not follow the comment and exception rules](coding-rules-not-followed.md) | Low | S | Comments that restate the code, a generic `NoSuchElementException` |
| [Saving and opening block the UI](saving-blocks-ui.md) | Low | M | Saving, opening and the 1 s refresh work on the EDT; `VACUUM INTO` of hours of raw data or building the session factory can freeze the window |
