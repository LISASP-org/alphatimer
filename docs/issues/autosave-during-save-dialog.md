# Auto-save runs while the save dialog is open

The `AutoSaver` keeps running while the Save / Discard / Cancel dialog is shown.

- **Effect:** changes can be written to the file although the user then chooses Discard. Also `old.close()` in `AppController.replaceDocument` and `doc.close()` in `AppController.requestClose` run without the database lock and can race a running auto-save.
- **Where:** `alphatimer-ui/.../AppController.java` (`gate`, `replaceDocument`, `requestClose`).
- **Idea:** pause the auto-saver while a dialog is open and close the old document through the lock.
