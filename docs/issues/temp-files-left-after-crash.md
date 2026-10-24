# Temp files of a save are left behind after a crash

A save writes a snapshot `*.tmp` next to the work file (and possibly a `<target>.tmp` next to the project file).

- **Effect:** a crash during a save leaves these files; nothing removes them, so they accumulate in
  `~/.alphatiming/work/` (and next to project files).
- **Where:** `alphatimer-project-db/.../TimingWorkFolder`, `TimingFileWriter`.
- **Idea:** remove unlocked snapshot temp files when the work folder is scanned for leftovers at start.
