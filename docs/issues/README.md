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
| [Serial reader does not reconnect](serial-reader-no-reconnect.md) | Medium | M | After an unplugged USB adapter the recording stays running without data until it is stopped and started again |
| [TcpReader does not wait for its thread on close](tcp-reader-close-does-not-join.md) | Medium | S | Up to 511 raw bytes can be lost at stop; a connect can race with `close()` |
| [Two saves at once share one temp file](concurrent-saves-share-temp-file.md) | Low | S | A manual save during an auto-save can make the auto-save fail spuriously |
| [Auto-save runs while the save dialog is open](autosave-during-save-dialog.md) | Low | S | Changes can reach the file although the user then chooses Discard |
| [Deleted times are applied as real times](deleted-time-applied.md) | Low | S | `DeletedTime` messages overwrite a lane's time; inherited from alphatimer |
| [Old log files are never deleted](log-files-not-rotated.md) | Low | S | Every start adds a log file under `~/.alphatiming/logs`, none is ever removed |
| [Text errors in English messages](open-invalid-wrong-article.md) | Low | S | "not a AlphaTiming project file" and "Do data to display present" |
| [The project file is not locked](project-file-not-locked.md) | Low | M | Two program instances can overwrite the same `.heat` file |
| [Saving and opening block the UI](saving-blocks-ui.md) | Low | M | Saving, opening and the 1 s refresh work on the EDT; `VACUUM INTO` of hours of raw data or building the session factory can freeze the window |
