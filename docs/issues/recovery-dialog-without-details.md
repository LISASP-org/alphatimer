# The recovery dialog does not say what it would recover

The question "The previous session ended unexpectedly and left unsaved data. Do you want to recover it?" names no file,
date or original project; "Discard" deletes the work file at once.

- **Effect:** with several leftovers the user cannot tell them apart and may delete the wrong data without a second
  question. A recovered document has no project file anymore, so it must be saved with Save As.
- **Where:** `dialog.recover.message` in `messages.properties`, `SwingSavePrompt.askRecover` (and the TUI prompt).
- **Idea:** show the work file's last-modified time (and the original project, if the work file can record it) and confirm
  before discarding.
