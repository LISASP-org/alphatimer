# Text errors in English messages

Two English texts are wrong: a file that is not a project reads "{0} is not a AlphaTiming project file.", and the
Live View without a heat shows "Do data to display present".

- **Effect:** the messages shown when opening a wrong file (GUI dialog, CLI error output) and in an empty Live View (GUI,
  TUI) are ungrammatical.
- **Where:** `error.open.invalid` and `live.none` in `alphatimer-ui-common/.../ui/common/messages.properties`.
- **Idea:** "is not an AlphaTiming project file." and "No data to display yet".
