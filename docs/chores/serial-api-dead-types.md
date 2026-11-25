# serial-api holds types nobody uses

The audit of the api modules (2026-11-26) found types in `alphatimer-serial-api` that no other module uses; they look
like leftovers of AlphaServer.

- **Unused anywhere:** `SerialConnectionBuilder`, `Storage` (only `StorageTest` uses it),
  `NotEnoughSerialPortsException`.
- **Used only by those:** `SerialPortWriter`, `PortAccessException` and `NoPortsFoundException` are referenced only by
  `SerialConnectionBuilder`.
- **Used and staying:** `SerialConfiguration` (with its field types `Parity`, `DataBits`, `StopBits`) and
  `SerialPortReader`.
- **Where:** `alphatimer-serial-api/src/main/java/org/lisasp/alphatimer/api/serial/` and its `exceptions` package,
  `StorageTest`.
- **Check first:** that JAuswertung, AlphaServer and the other workspace projects do not use them either (the usage check
  covered alphatimer only).

## Done when

The unused types and their tests are deleted, or each one that stays has a user.
