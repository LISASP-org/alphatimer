# Older files with a competition tag can show a heat twice

Heats in older project files keep their competition tag (`TestWK` in the compatibility fixture); new data is tagged with
the constant `""` (`RecordingSession.COMPETITION`), and `HeatStore.findEntity` matches on the competition.

- **Effect:** recording into such a file creates a second heat row for the same event and heat; the live view can show
  both. Only relevant if a released version wrote tags.
- **Where:** `alphatimer-ui-core/.../recording/RecordingSession`, `alphatimer-project-db/.../HeatStore.findEntity`.
- **Idea:** normalise the tags to `""` when a file is opened, or ignore the competition in `findEntity`.
