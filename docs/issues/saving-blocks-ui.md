# Saving and opening block the UI

Saving (`VACUUM INTO`) and the 1 s refresh of the status bar read through `ProjectDatabase`, whose single lock is also held by the recording. Opening a project copies the file and builds a Hibernate `SessionFactory` on the EDT.

- **Effect:** with hours of raw data a manual save can freeze the window for a noticeable time; every open freezes it while the session factory is built.
- **Where:** `AppController.save`, `AppController.open`, `JAlphaTiming.refresh`, `ProjectDatabase.snapshotTo`, `ProjectDatabase.openCopy`.
- **Idea:** run saves and opens in a `SwingWorker` with the window disabled (like JAuswertung's `PrintExecutor`) and read the settings for the refresh from a cache instead of the database.
