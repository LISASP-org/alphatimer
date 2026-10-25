# Read-only mode

Opening a project creates a lock file next to it, so that only one running AlphaTiming has it open. In a read-only
folder (CD, read-only share, write-protected stick) the lock file cannot be created and the open is refused. Before the
lock existed, such a project could be opened and viewed, but never saved.

- **Goal:** a project that cannot be locked can still be opened to look at it: settings, heats, the live view of the
  recorded heats, the message log, and the legacy REST endpoints.
- **Behavior:**
  - Open falls back to read-only when the lock file cannot be created (the user is told), and could also be chosen
    explicitly (e.g. "Open read-only…", useful to look at a project another instance has open).
  - The title and status bar show that the project is read-only.
  - Settings cannot be changed and no recording can be started; there is no auto-save.
  - Save is not available; Save As writes a normal, locked copy that is then the open project (from there everything
    works as usual).
- **Where:** `TimingDocument.open` and the project-file lock, `AppController` (save, auto-save, recording), `AppStatus`
  and `SettingsFormRules` (state shown, editable fields, record button), the three front ends.
- **Open questions:** whether a read-only project offers the REST endpoints; whether opening a project that another
  instance has open should offer read-only instead of the current refusal.
