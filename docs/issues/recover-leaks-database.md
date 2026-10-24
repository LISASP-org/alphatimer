# A failed recovery keeps the work file locked

`ProjectDocument.recover` adopts the work file and then calls `touch()`. If `touch()` fails (e.g. a corrupt work file),
the `ProjectDatabase` with its session factory and file lock is never closed.

- **Effect:** the leftover stays "in use" for the rest of the run: it is neither offered again nor can it be discarded
  until the program is restarted.
- **Where:** `alphatimer-ui-core/.../project/ProjectDocument.recover`.
- **Idea:** close the database when `touch()` fails, as `open()` does.
