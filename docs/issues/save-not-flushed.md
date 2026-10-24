# Save does not flush the file before replacing the project file

`ProjectDatabase.snapshotTo` writes the temp file with `VACUUM INTO`, which does not fsync its output, and then moves it
over the project file with `ATOMIC_MOVE`.

- **Effect:** after a power loss shortly after a save (a laptop at the pool), the project file can be incomplete while the
  previous good version is already gone. The work file usually allows a recovery, but a save must never leave a corrupt
  project file.
- **Where:** `alphatimer-project-db/.../project/db/ProjectDatabase.snapshotTo`.
- **Idea:** open the temp file and `FileChannel.force(true)` it before the move; ideally force the directory afterwards.
