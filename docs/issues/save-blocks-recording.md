# Saving also blocks the recording

`ProjectDatabase.snapshotTo` holds the database lock for the whole `VACUUM INTO`. The reader thread needs the same lock
to store raw data, messages and heats.

- **Effect:** a save to a slow target (USB stick, network share) blocks the reader thread (the nrjavaserial event thread
  for serial input) for its whole duration; with enough data this can delay or lose input. `saving-blocks-ui.md` only
  covers the EDT.
- **Where:** `alphatimer-project-db/.../ProjectDatabase.snapshotTo`.
- **Idea:** vacuum into the local work directory under the lock, then copy to the target outside the lock.
