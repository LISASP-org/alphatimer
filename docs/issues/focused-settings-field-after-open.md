# A focused settings field keeps its old text after New or Open

`SettingsRecordingPanel.refresh` does not overwrite a field that has the keyboard focus.

- **Effect:** if a settings field still has the focus when a project is created or opened, it keeps showing the text of
  the previous project; when it loses the focus, that text is committed into the newly opened project.
- **Where:** `alphatimer-ui/.../ui/SettingsRecordingPanel` (`refresh`, the focus-lost commit).
- **Idea:** when the document changes, reset all fields from the new settings regardless of the focus.
