# TcpReader does not wait for its thread on close

`TcpReader.close()` interrupts the reader thread but does not join it, and `manageConnection` can create a socket after `close()` ran.

- **Effect:** after Stop, bytes that are still being delivered reach an already closed `RawDataRecorder` (up to 511 raw bytes lost); a quick restart can run two pipelines for a moment; the connect can take up to 60 s to give up.
- **Where:** `alphatimer-serial-core/.../serial/tcp/TcpReader.java` (`close`, `manageConnection`).
- **Idea:** join the thread with a timeout in `close()` and re-check the interrupt flag after creating the socket; test with a server that keeps sending while the session is stopped.
