# The command line ends with OK although data was lost

A CLI recording with write errors (raw bytes dropped at stop, or data that was never stored again) still exits with
`ExitCode.OK` if the final save works.

- **Effect:** scripts cannot tell that the recorded project is incomplete.
- **Where:** `alphatimer-cli/.../RecordCommand`, `ExitCode`; `WriteListener.dataDropped`, `WriteFailures`.
- **Idea:** decide what counts as data loss (dropped raw bytes; unrecovered message/heat failures) and return a separate
  exit code for it; define which code wins over a lost connection.
