# The command line ends with OK after a lost serial connection

After a lost serial connection the recording stops, the CLI saves and exits with `ExitCode.OK`; its "connection lost" and
"stopping" lines can appear in either order.

- **Effect:** scripts cannot tell a complete recording from one that ended because the adapter was lost.
- **Where:** `alphatimer-cli/.../RecordCommand`, `ExitCode`.
- **Idea:** a separate exit code for an ended connection, and print the loss before the stop message.
