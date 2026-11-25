# ares-serial-api holds the byte events of ares-serial

The audit of the api modules (2026-11-26) found that the byte-level input events in `alphatimer-ares-serial-api` are
only used by its implementation module `alphatimer-ares-serial`, so they are its internal model, not api.

- **Only used by ares-serial:** `BytesInputEvent`, `BytesInputEventListener`, `DataInputEventListener`;
  `InputDataDroppedEvent` is used nowhere outside the api module.
- **Used outside:** `DataInputEvent` and its dropped events are read by `alphatimer-cli` (raw data analysis), so they
  stay, as do `DataHandlingMessage`, its enums, `UsedLanes`, the listeners and `DataHandlingMessageAggregator`.
- **Where:** `alphatimer-ares-serial-api` (events package), `alphatimer-ares-serial`.
- **Idea:** move the types used only by `ares-serial` there; delete those nobody uses. Check every type again with its
  tests and generic names (`Message`, `Bytes`) by hand, the first check worked on imports only.

## Done when

`alphatimer-ares-serial-api` holds only what other modules use.
