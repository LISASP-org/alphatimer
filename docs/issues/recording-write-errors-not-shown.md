# Write errors during a recording only reach the message log

Errors of `HeatService`, `RawDataRecorder` and the stores during a recording (e.g. a full disk) are reported through
`AppController.install` → `log.processingError` into the message log on the Settings panel. Only a failed auto-save sets
the warning in the status bar.

- **Effect:** while the user watches the live view, the recording can stop storing data without a visible warning.
- **Where:** `alphatimer-ui-common/.../AppController` (error listener of the session).
- **Idea:** set the same persistent warning as for a failed auto-save when the recording pipeline cannot store data.
