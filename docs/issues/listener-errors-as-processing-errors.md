# Errors of heat listeners are reported as processing errors

`HeatService` catches failures of its own listeners (the UIs) in the same block as store errors.

- **Effect:** a failing UI listener shows up as a generic processing error in the message log, which points at the data
  instead of the UI.
- **Where:** `alphatimer-heats/.../HeatService.accept`.
- **Idea:** isolate listener calls (as `RecordingSession.safe` does) and log them separately.
