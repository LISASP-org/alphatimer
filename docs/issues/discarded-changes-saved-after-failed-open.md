# Discarded changes can be auto-saved after a failed Open

When the user chooses Discard and the following Open fails, the old document stays the current one and the auto-saver
resumes on it.

- **Effect:** the changes the user just discarded can still be written to the project file by the next auto-save.
- **Where:** `alphatimer-ui-common/.../AppController` (open flow, auto-save pause/resume).
- **Idea:** after Discard, revert the document to its saved state (or close it and open the saved file again) before the
  new file is opened, so a failed Open leaves nothing discarded behind.
