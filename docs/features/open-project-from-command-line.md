# Open a project file from the command line

The JAuswertung installer bundles AlphaTiming but does not register `.timing` files with it: `AlphaTimingLauncher` and
`TuiLauncher` ignore their arguments, so a double-clicked project file would only start AlphaTiming with a new project.
`timing-file.ico` already exists for the association.

- **Goal:** `AlphaTiming <file.timing>` (and `alphatiming-tui <file.timing>`) start with that project open, so the
  installer can associate `.timing` files with the AlphaTiming launcher.
- **Behavior:** the file is opened through `AppController` like Open, after the crash recovery at program start; a file
  that cannot be opened (missing, locked by another instance, not a project) is reported as for Open and leaves a new
  project open.
- **Where:** `AlphaTimingLauncher`, `TuiLauncher`, `AppController`; then, in JAuswertung, a
  `CreateFileAssociationAction` for `timing` in `jauswertung-setup/src/install4j.install4j` that points to the
  AlphaTiming launcher with `alphatiming/icons/timing-file.ico`.
- **Open questions:** whether a recovered work file or the file from the command line wins when both exist.
