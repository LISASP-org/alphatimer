# Serial reader does not reconnect

`NrSerialReader` opens the port once in `start()`. `TcpReader` reconnects after a lost connection; the serial reader does not.

- **Effect:** when a USB serial adapter is unplugged or the port is lost, the recording stays in the RUNNING state and `isConnected()` stays true while no data arrives, until the user stops and starts the recording.
- **Where:** `alphatimer-serial-core/.../serial/com/NrSerialReader.java` (`onEvent` only logs read errors).
- **Idea:** treat a read error or a vanished port as a lost connection (`isConnected()` false, status bar warning) and retry opening the port periodically, as `TcpReader` does; verify with a pty pair or real hardware.
