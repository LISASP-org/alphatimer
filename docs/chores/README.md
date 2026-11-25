# Chores

Planned clean-ups of the code or data model that are not bugs. Each chore is a self-contained file listed in the
table below; once it is done, delete its file and its row here (same rule as `docs/issues/`).

Every row has a severity and a size; keep the table ordered by severity (High, Medium, Low), then by size (S, M, L).

- **Severity:** High = the current state loses data or user settings; Medium = it invites bugs (duplicated or
  inconsistent data); Low = only redundancy.
- **Size:** S = a local change of a few lines with a test; M = several classes or a migration; L = spread over many
  modules or needs a design decision.

| Chore | Severity | Size | Summary |
| --- | --- | --- | --- |
| [Associate `.timing` files in the JAuswertung installer](associate-timing-files.md) | Low | S | Register `.timing` files with the AlphaTiming launcher in the install4j project; blocked by opening a project from the command line |
| [serial-api holds types nobody uses](serial-api-dead-types.md) | Low | S | `SerialConnectionBuilder`, `Storage`, `NotEnoughSerialPortsException` and what only they use look like AlphaServer leftovers |
| [ares-serial-api holds the byte events of ares-serial](ares-serial-api-byte-events.md) | Low | M | The byte-level input events are only used by `alphatimer-ares-serial`; move them there |
| [Why ui-core uses legacy-api and lenex-api](ui-core-api-usage.md) | Low | M | `RecordingSession` uses `Heat`, `LenexRecording` uses `LenexMeet`; check whether it only passes them between implementation modules |
| [Is lenex-api an api module?](lenex-api-or-lenex.md) | Low | L | Only `ui-core` uses it, for `LenexMeet`; keep it or merge it into `alphatimer-lenex` |
