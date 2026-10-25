# The work copy is not locked while a project is opened

`TimingDatabase.openCopy` copies the project file into the work folder before the work file is locked (the lock is taken
in the constructor).

- **Effect:** another AlphaTiming starting at that moment sees an unlocked work file, offers it as a crash leftover, and
  "Discard" deletes it — the project that is just being opened loses its working copy.
- **Where:** `alphatimer-project-db/.../TimingDatabase.openCopy`, `TimingWorkFolder` (`lock`, `findLeftovers`).
- **Idea:** take the work-file lock before copying (lock the new work file name first, then copy into it).
