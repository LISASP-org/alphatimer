# Looking for crash leftovers takes the work file locks

To decide whether a work file is a leftover, the scan acquires and releases its lock for a moment.

- **Effect:** a document that is created or opened at that exact moment in another instance could fail to get its lock.
- **Where:** `alphatimer-project-db/.../TimingWorkFolder` (`isInUse`, `findLeftovers`), `LockFile.isHeld`.
- **Idea:** retry acquiring a work file lock briefly before failing.
