# A lost connection message always names the serial port

`RecordingSession` passes `settings.serialPort()` to the connection-lost listeners regardless of the connection type.

- **Effect:** if a TCP reader ever reports a lost connection, the message reads "serial port  was lost" with an empty
  name. Today only the serial reader reports a loss, so it is not visible yet.
- **Where:** `alphatimer-ui-core/.../recording/RecordingSession`, the `warn.connectionLost` texts.
- **Idea:** pass a description of the connection (host:port or port name) and word the text for both types.
