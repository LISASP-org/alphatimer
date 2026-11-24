# Legacy Heats from Refined Messages — Design

Date: 2026-11-25. Branch: `legacy-from-refined-messages`.

## Intent

The legacy heats that JAuswertung reads (`/legacy/heats.xml`, `/legacy/heats.json`) are built by `LegacyService` from
the ARES messages (`DataHandlingMessage`), below the refined layer. A second instance builds the same heats from the
refined messages and serves them at `/legacy/heats-new.xml`, beside the existing endpoints. Both outputs can then be
compared on real competitions before the refined path replaces the old one
(`docs/features/legacy-heats-from-heat-model.md` describes that later step).

## Goals

- A second legacy instance whose source is the refined messages, in the new subpackage
  `org.lisasp.alphatiming.legacy.server.refined` of `alphatimer-legacy-server`.
- It serves `GET /legacy/heats-new.xml` in the unchanged legacy format (`Heat`, `Lane`, `LaneStatus`,
  `LegacySerialization` of `alphatimer-legacy-api`).
- For Omega (ARES) input its data equals that of `/legacy/heats.xml`, apart from the accepted differences below.

## Non-goals

- Replacing or removing `LegacyService` (`docs/features/legacy-heats-from-heat-model.md`).
- A JSON variant of the new heats.
- Legacy heats for LENEX projects.
- Deciding when a backup time becomes official (`docs/issues/backup-times-not-interpreted.md`).

## Constraints

- No code of the existing legacy classes changes: `LegacyService`, `LaneTime`, `LaneTimeRepository`,
  `InMemoryLaneTimeRepository` and everything in `alphatimer-legacy-api`. The new instance uses them, it does not
  modify them.
- The one exception is `LegacyHttpServer`: it serves the new path on the same port, so it gets a second source of
  heats. `/legacy/heats.xml` and `/legacy/heats.json` stay byte-identical.
- LENEX is blocked: the new instance hears only refined messages that come from ARES messages, live and when a
  project is opened. A LENEX recording and the replay of LENEX versions do not reach it, so for a LENEX project both
  `heats.xml` and `heats-new.xml` are empty.
- The legacy REST format stays unchanged.

## Approaches considered

- **Endpoint.** (a) `LegacyHttpServer` gets a second source and serves the new path on its port — **chosen**. (b) A
  separate server on a port of its own, `LegacyHttpServer` untouched — rejected: needs a second port setting and a
  second server JAuswertung's users would have to know about.
- **Missing information in the refined messages.** (A) Accept the differences. (B) Extend the refiner so nothing is
  lost. Decided case by case against the 23 recordings in `data/` (28,619 ARES messages, 1,190 heats); see
  *Differences*. The refiner changes that were wanted are already done on this branch (*Prerequisites*).

## Prerequisites (done on this branch)

- **Deletions:** a deleted time (time type `E`) is refined as `DeletedTime` whatever its time info (Normal, `M`,
  `E`, `*`, `B`). Before, `TimeParser` dropped deletions with `M`, `E` or `*`, and `BackupTimeParser` dropped
  deleted backup times. A deletion withdraws the corresponding time; in the heat model only a time of its own origin.
- **Backup times follow normal times:** `BackupTimeMessage` and `DroppedBackupTimeMessage` are merged into
  `TimeMessage` and `DroppedTimeMessage`. The new enum `RefinedTimeOrigin` (`Primary`, `Backup`) is a required
  constructor argument of `TimeMessage`; consumers switch on it, so a backup time never counts as an official time by
  accident. A backup time carries kind of time, current lap, lap count, rank and time type like a normal time.
  Message type 7 (kind of time `Backup`) is a finish on the last lap and a split before; a time with time info `B`
  keeps its kind.

## Design

### `RefinedLegacyService` (`legacy.server.refined`)

A `RefinedMessageListener` on a `LaneTimeRepository` (`InMemoryLaneTimeRepository`), with `getHeats()` in the form
of `LegacyService.getHeats()`. It maps the refined messages the way `LegacyService` maps the ARES messages:

| Refined message | Effect |
| --- | --- |
| `TimeMessage` with origin `Backup` | ignored, as `LegacyService` ignores message type 7 |
| event ≤ 0 or heat ≤ 0 | ignored |
| `TimeType` `DeletedTime`, current lap = lap count | deletes the stored time of lane − 1 with that value |
| `TimeType` `DeletedTime`, earlier lap | ignored |
| kind of time `Finish` | stored for lane − 1 with the message's competition, event, heat, time and arrival |
| anything else (splits, starts, penalties, used lanes, official end, …) | ignored |

Lane status of a stored finish: `Live` → `RaceTimes`, `CurrentRaceResult` → `ResultsOfTheRace`,
`PreviousRaceResult` → `ResultsOfThePreviousRace`.

`getHeats()` duplicates the grouping of `LegacyService.getHeats()` (sort the lane times, one `Heat` per event and heat,
ids in order of appearance), because `LegacyService` may not change. The duplicate goes when the old instance does.

### Endpoint (`LegacyHttpServer`)

`LegacyHttpServer(int port, Supplier<Heat[]> heats, Supplier<Heat[]> refinedHeats)`; `GET /legacy/heats-new.xml`
answers `LegacySerialization.toXML(refinedHeats.get())` as `application/xml; charset=UTF-8`, with the same error
handling as `/legacy/heats.xml` (500 on a failure of the source, 405 for other methods). Other paths stay 404.

### Wiring (`alphatimer-ui-core`)

- `RecordingSession` owns one `RefinedLegacyService` beside `LegacyService`, for the lifetime of the document, and
  passes both sources to `LegacyHttpServer`; `refinedLegacyHeats()` beside `legacyHeats()`.
- Live: the refiner of an ARES recording feeds it, isolated like the other listeners (a failure never reaches the
  reader thread nor the other consumers).
- Opening: `MessageReplay` feeds it the refined messages of the stored ARES messages, each consumer isolated.
- LENEX: `LenexRecording` and `LenexRecording.replay` keep feeding the heats only.

## Differences to `/legacy/heats.xml`

Found by running every recording in `data/` through both paths; constructed variants were run through the parser
to see what each path does.

| Case | In `data/` | `heats.xml` | `heats-new.xml` | Decision |
| --- | --- | --- | --- | --- |
| Live finish with a minus sign in place of the time (`-16:23:13.50`, a time of day) | 4 messages, 1 heat: `dem2023-2023-05-06.serial`, event 11 heat 1 lane 3 | a finish of 58,993,500 ms | nothing (`docs/issues/minus-times-are-dropped.md`) | accepted |
| Results "With backup" (message types 4 and 6) | none | status `ResultsWithBackupTimes` / `BackupOfThePreviousRace` | the same time, status `ResultsOfTheRace` / `ResultsOfThePreviousRace` | accepted: only the status differs, JAuswertung does not use it |
| Finish with time info `B` | none | stored as an ordinary time | ignored like every backup time | accepted (JAuswertung would preselect a backup time as the last time of the lane) |
| Live finish with `DSQ`/`DNS`/`DNF` in place of the time | none; real penalties come as results with no kind of time, which both paths ignore | a finish of 0 ms | nothing | not ARES's format |
| Finish with rank info `D` or time info `*` | none (`D ` only on reaction times) | stored | dropped by `TimeParser` | does not occur |
| Time on lane 0 | none; lane 0 only on ReadyToStart and OfficialEnd | stored at lane index −1 | dropped | does not occur in ARES; how ARES sends lane 10 is not seen yet (recordings have up to 8 lanes) |

On all other 1,189 heats of the recordings both outputs are identical.

## Verification

- `RefinedLegacyServiceTest` (`alphatimer-legacy-server`): finishes per heat, the status per message type, deletions
  of the last lap and of earlier laps, backup times ignored, event or heat 0 ignored.
- `LegacyHttpServerTest`: `/legacy/heats-new.xml` serves the second source; the existing paths are unchanged.
- `MessageReplayTest`: a failing consumer keeps neither of the others from the message.
- `RecordingSessionTest`: after a recording and after reopening, `heats-new.xml` equals `heats.xml`; the server
  serves both.
- `LenexRecordingSessionTest`: a LENEX recording and an opened LENEX project leave the new heats empty.
- An equivalence test in `alphatimer-ui-core` replays ARES messages (live, result and previous-result finishes,
  splits, deletions of the last and an earlier lap, event 0) through both paths and compares the XML.
- Locally, not committed: replaying the recordings in `data/` through both paths gives only the accepted minus-sign
  heat as a difference. Result with `LegacyService` and `RefinedLegacyService` themselves: of 1,190 heats, only
  `dem2023-2023-05-06.serial` event 11 heat 1 differs.

### Known test failures (before this change)

On the development machine (`DISPLAY=:0`, no `xvfb-run`) three tests of `JAlphaTimingSmokeTest`
(`alphatimer-ui`) fail, on `main` as on this branch:

- `layoutAndLocking` and `theFileConnectionNeedsAReplayFileAndLocksWhileRecording`: an expectation about the
  settings fields does not hold (AssertJ "Expecting all elements of …" over the combo boxes and text fields).
- `theServerToggleSwitchesTheServerPortOnAndOff`: "Expecting value to be true but was false"
  (`JAlphaTimingSmokeTest.java:302`).

They fail the same way headless (`xvfb-run -a mvn verify`), so the display is not the cause; the cause is not known
yet. They are not caused by this change, so `mvn verify` on this branch is judged against them until they are
resolved.

In an environment whose JVM time zone is not Europe/Berlin, `Version1FixtureTest.theHeatsOfAVersion1FileAreShown`
(`alphatimer-ui-core`) fails on `main` as on this branch: the announced heat is two hours off (`2026-05-01T08:00`
instead of `10:00`). With `TZ=Europe/Berlin` it passes. Tracked in `docs/issues/version1-fixture-depends-on-time-zone.md`.

## Documentation

- `AGENTS.md`: `alphatimer-legacy-server` (the `refined` subpackage), `alphatimer-ui-core` (the second instance and
  the LENEX block), the REST format note (`/legacy/heats-new.xml`).
- `README.md`: the new endpoint next to the legacy ones.
- `docs/features/legacy-heats-from-heat-model.md`: link to this comparison step.

## Left to the builder

- Names of helpers and of the equivalence test.
- Whether `RecordingSession` exposes `refinedLegacyHeats()` only for tests or also for a later UI.
