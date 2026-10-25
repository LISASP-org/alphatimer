# Recording state listeners are not isolated

`RecordingSession.setState` calls its state listeners with a plain `forEach`.

- **Effect:** when the recording stops on the reader thread (lost connection, replay finished), a throwing state listener
  skips the other listeners and the exception reaches the reader thread. The other listener lists of the session are
  already isolated.
- **Where:** `alphatimer-ui-core/.../recording/RecordingSession.setState`.
- **Idea:** call each state listener in its own try/catch and log a failure, as `announce` does.
