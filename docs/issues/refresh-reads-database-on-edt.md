# The status refresh reads the database on the EDT

The 1 s refresh of the settings panel reads the settings through the database lock on the EDT.

- **Effect:** the window waits behind recording writes and the short snapshot of a save; with small project files this is
  only milliseconds.
- **Where:** `alphatimer-ui/.../SettingsRecordingPanel.refresh` → `TimingDocument.settings()`.
- **Idea:** keep the current settings in the document (they change only through `updateSettings`) and read them without
  the database.
