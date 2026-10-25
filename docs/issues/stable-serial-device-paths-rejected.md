# Stable serial device paths are rejected

Port names longer than 48 bytes are rejected because nrjavaserial crashes on them. Linux's stable paths
(`/dev/serial/by-id/usb-…`) are usually longer.

- **Effect:** users cannot use the path that stays the same when the adapter is plugged into another USB port; they must
  use `/dev/ttyUSBn`, which can change.
- **Where:** `alphatimer-serial-core/.../SerialPorts` (`fitsDriver`), `NrSerialReader`, `Settings.validate`.
- **Idea:** resolve symbolic links to the real device (`/dev/ttyUSBn`) before opening and check the resolved name.
