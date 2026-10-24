# Save in the Swing UI does not add the file extension

`SwingSavePrompt.askSavePath` uses the chosen name as it is. The native file dialogs on Linux (portal, AWT `FileDialog`)
do not append `.timing`.

- **Effect:** a project saved as `WK2026` has no extension; the overwrite question checks the wrong path, and the Open
  dialog's `.timing`/`.heat` filter hides the file afterwards, so the project seems lost.
- **Where:** `alphatimer-ui/.../ui/SwingSavePrompt.askSavePath`; the TUI already uses `ProjectFiles.withDefaultExtension`.
- **Idea:** apply `ProjectFiles.withDefaultExtension` before the existence check.
