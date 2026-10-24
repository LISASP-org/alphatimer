# Buffered raw data is dropped silently at stop

`RawDataRecorder` keeps bytes buffered while the store fails. When the recording stops and the store still fails,
`close()` drops the buffer; it also still accepts bytes after `close()`.

- **Effect:** raw bytes received during a disk problem are lost without a message (the write warning stays visible, but
  does not say that data was discarded).
- **Where:** `alphatimer-project-db/.../RawDataRecorder` (`accept`, `close`).
- **Idea:** on close, try a last flush and report the number of dropped bytes as a write failure; reject bytes after close.
