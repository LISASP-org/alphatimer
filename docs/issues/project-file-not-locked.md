# The project file is not locked

Only work files are protected by an OS lock, not the `.timing` project file.

- **Effect:** two program instances can open and save the same project file; the later save silently wins.
- **Where:** `ProjectDocument.open/saveAs`.
- **Idea:** hold a lock file next to the project file while it is open and tell the user on Open.
