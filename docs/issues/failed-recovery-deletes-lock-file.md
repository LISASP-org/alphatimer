# A failed recovery deletes the project-file lock file

When a recovery that kept its project file fails, `TimingDocument.recover` releases the project-file lock with
`closeAndDelete`.

- **Effect:** if another instance takes that lock at the same moment, deleting the lock file can let a third instance
  lock a new file of the same name — two instances could then hold "the" lock. Very unlikely.
- **Where:** `alphatimer-ui-core/.../TimingDocument.recover`, `LockFile.closeAndDelete`.
- **Idea:** release without deleting, or delete only while still holding the lock.
