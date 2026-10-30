# Associate `.timing` files in the JAuswertung installer

The JAuswertung installer bundles AlphaTiming but registers no file association, so a double-clicked project file does
not open AlphaTiming. `timing-file.ico` is already shipped in `alphatiming/icons`.

- **Goal:** the installer associates `.timing` files with the AlphaTiming launcher, so a double-click opens the project
  in AlphaTiming.
- **Where:** in JAuswertung, a `CreateFileAssociationAction` for `timing` in `jauswertung-setup/src/install4j.install4j`
  that points to the AlphaTiming launcher (id 1387) with `alphatiming/icons/timing-file.ico`; check it on Windows,
  macOS and Linux.
- **Blocked by:** [Open a project file from the command line](../features/open-project-from-command-line.md) — until the
  launchers open their argument, the association would only start AlphaTiming with a new project.
