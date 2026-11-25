# Features

Planned features that are not built yet. Each feature is a self-contained file listed in the table below; once it is
implemented, delete its file and its row here (same rule as `docs/issues/`).

Every row has a priority and a size; keep the table ordered by priority (High, Medium, Low), then by size (S, M, L).

- **Priority:** High = needed for the next event; Medium = clearly useful, can wait; Low = nice to have.
- **Size:** S = a local change of a few lines with a test; M = several classes or a migration; L = spread over many
  modules or needs a design decision.

| Feature | Priority | Size | Summary |
| --- | --- | --- | --- |
| [Legacy heats from the heat model](legacy-heats-from-heat-model.md) | Medium | M | Build the legacy heats from the heat model instead of the ARES messages, so every input reaches JAuswertung; removes `LegacyService` |
| [REST endpoint for the heat model](timing-rest-endpoint.md) | Medium | M | `GET /timing` serves the heat model (split and backup times, penalty flags) to JAuswertung; replaces the legacy endpoints later |
| [Open a project file from the command line](open-project-from-command-line.md) | Medium | S | Start the GUI and the TUI with the project file given as argument, so the installer can associate `.timing` files |
| [Read-only mode](support-readonly-mode.md) | Low | M | Open a project that cannot be locked or written (read-only folder or medium) for viewing only |
| [Reaction times](reaction-times.md) | Low | M | A refined message for reaction and relay take-off times, kept per lane in the heat model |
| [ALGE serial inputs](alge-serial-inputs.md) | Low | L | Read the documented ALGE outputs (TM-SWIM Video-Out, Timy3 RS-232) and refine them into the refined messages |
