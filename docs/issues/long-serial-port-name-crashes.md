# A long serial port name crashes the program

nrjavaserial 5.2.1 aborts the whole JVM ("buffer overflow detected") when it opens a port with a long path; it happened
with a `socat` pty link in a deep directory.

- **Effect:** a long port name typed into the settings (or a long device path) kills AlphaTiming without a chance to save.
- **Where:** `alphatimer-serial-core/.../serial/com/NrSerialReader`, `SerialPorts`; the port setting in `Settings.validate`.
- **Idea:** find the length limit of the native code and reject longer names as a `SettingsProblem` before nrjavaserial
  sees them.
