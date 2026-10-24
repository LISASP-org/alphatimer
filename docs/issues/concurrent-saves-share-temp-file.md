# Two saves at once share one temp file

`ProjectDatabase.snapshotTo` deletes and creates `<file>.tmp` before taking the database lock.

- **Effect:** a manual Save during an auto-save of the same file deletes the other save's temp file; `Files.move` fails and the auto-save reports a spurious error. The project file itself is not corrupted.
- **Where:** `alphatimer-project-db/.../db/ProjectDatabase.java` (`snapshotTo`).
- **Idea:** do the `deleteIfExists` inside the lock.
