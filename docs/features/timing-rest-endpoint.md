# REST endpoint for the heat model

The REST server of AlphaTiming offers only the legacy endpoints (`/legacy/heats.xml`, `/legacy/heats.json`). They
serve the lane times of `LegacyService` in the format of the former AlphaServer: finish times per lane with a status,
no split times, no backup times, no penalties, no heat state. The heat model of `alphatimer-heats` holds all of that,
but no endpoint serves it.

- **Goal:** a REST endpoint that serves the content of the heat model to JAuswertung. It replaces the legacy endpoints
  in the future; until then both are offered.
- **Behavior:**
  - `GET /timing` on the existing REST server (`LegacyHttpServer`, `HandlerPool`), in REST style; the response is
    JSON.
  - It serves what the heat model holds: the heats with their state, and per lane the times (split times, finish time,
    backup times) and the penalty flags.
  - Only `GET` is offered; other methods are answered as the legacy endpoints answer them.
- **Where:** `alphatimer-legacy-server` (`LegacyHttpServer`) or a new server module beside it; the data classes of the
  response in a module of their own that JAuswertung can depend on, like `alphatimer-legacy-api`, so the heat model can
  change without changing the contract.
- **Depends on:** the extended heat model (split and backup times, penalty flags, ReadyToStart, heats rebuilt from
  `aresmessage`), designed in `docs/superpowers/specs/`.
- **Open questions:** the exact resources below `/timing` (e.g. one heat by event and heat number); how the format is
  versioned; when the legacy endpoints are removed.
