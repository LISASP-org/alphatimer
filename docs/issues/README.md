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
| [TcpReader does not wait for its thread on close](tcp-reader-close-does-not-join.md) | Medium | S | Up to 511 raw bytes can be lost at stop; a connect can race with `close()` |
| [New and Open stop a running recording without asking](new-open-stop-recording-silently.md) | Medium | S | An accidental New/Open ends the recording, even when the user cancels |
| [Save in the Swing UI does not add the file extension](swing-save-without-extension.md) | Medium | S | Projects saved without `.timing` are hidden by the Open dialog |
| [The recovery dialog does not say what it would recover](recovery-dialog-without-details.md) | Medium | S | No file or date shown; Discard deletes the work file without a second question |
| [Write errors during a recording only reach the message log](recording-write-errors-not-shown.md) | Medium | S | A recording that cannot store data shows no warning in the status bar |
| [Serial reader does not reconnect](serial-reader-no-reconnect.md) | Medium | M | After an unplugged USB adapter the recording stays running without data until it is stopped and started again |
| [Saving also blocks the recording](save-blocks-recording.md) | Medium | M | A slow save target blocks the reader thread for the whole save |
| [Two saves at once share one temp file](concurrent-saves-share-temp-file.md) | Low | S | A manual save during an auto-save can make the auto-save fail spuriously |
| [Auto-save runs while the save dialog is open](autosave-during-save-dialog.md) | Low | S | Changes can reach the file although the user then chooses Discard |
| [Old log files are never deleted](log-files-not-rotated.md) | Low | S | Every start adds a log file under `~/.alphatiming/logs`, none is ever removed |
| [Text errors in English messages](open-invalid-wrong-article.md) | Low | S | "not a AlphaTiming project file" and "Do data to display present" |
| [The REST server uses an unbounded thread pool](rest-server-unbounded-threads.md) | Low | S | Every concurrent request gets its own thread, without a limit |
| [A failed recovery keeps the work file locked](recover-leaks-database.md) | Low | S | The leftover can be neither recovered nor discarded until a restart |
| [Older files with a competition tag can show a heat twice](competition-tag-duplicates-heat.md) | Low | S | Recording into an old tagged file creates a second row for the same heat |
| [Some code does not follow the comment and exception rules](coding-rules-not-followed.md) | Low | S | Comments that restate the code, a generic `NoSuchElementException` |
| [Saving and opening block the UI](saving-blocks-ui.md) | Low | M | Saving, opening and the 1 s refresh work on the EDT; `VACUUM INTO` of hours of raw data or building the session factory can freeze the window |
