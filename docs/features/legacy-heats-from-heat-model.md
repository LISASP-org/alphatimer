# Legacy heats from the heat model

The legacy endpoints (`/legacy/heats.xml`, `/legacy/heats.json`) are served by `LegacyService`, which reads the ARES
messages (`DataHandlingMessage`) below the refined layer. Inputs that are refined into the refined messages without ARES
messages (LENEX, later ALGE) therefore produce no legacy heats; JAuswertung gets none of their times.

A first step serves the legacy heats built from the refined messages beside the old ones (`/legacy/heats-new.xml`,
`RefinedLegacyService`; `docs/superpowers/specs/2026-11-25-legacy-heats-from-refined-messages-design.md`), so both can be
compared on real competitions.

- **Goal:** the legacy heats are built from the heat model (`HeatService`/`LiveHeats`), so every input that reaches the
  refined messages also reaches JAuswertung through the legacy endpoints; `LegacyService` and its `LaneTimeRepository`
  are removed.
- **Behavior:**
  - The legacy format stays unchanged (`Heat`, `Lane`, `LaneStatus` of `alphatimer-legacy-api`); it is JAuswertung's
    input.
  - For ARES projects the output equals the current one: a lane holds its finish time, a deleted finish removes it, the
    lane status follows the kind of the time (`RaceTimes` live, `ResultsOfTheRace`, `ResultsWithBackupTimes`,
    `ResultsOfThePreviousRace`, `BackupOfThePreviousRace`).
  - Until then LENEX projects serve no legacy heats (decided in [LENEX input](lenex-input.md)).
- **Where:** an adapter from the heat DTOs to the legacy heats in `alphatimer-legacy-server`; `RecordingSession` and
  `MessageReplay` stop feeding `LegacyService`.
- **Open questions:**
  - The heat model drops repeats of previous races (`RefinedMessageType.PreviousRaceResult`), whose times
    `LegacyService` keeps with the status `ResultsOfThePreviousRace`/`BackupOfThePreviousRace`; whether JAuswertung needs
    them, or the model has to keep the source of a time.
  - How the equality for ARES is proven: e.g. replaying the local captures (`data/*.dat`) through both paths and
    comparing the XML.
  - The lane status of times that only LENEX has (results without a live time) and whether `Disq` is set for a
    disqualified lane.
