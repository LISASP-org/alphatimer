# Heat Model with Time History Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Heats keep every split, finish and backup time of a lane, penalties become flags, ReadyToStart announces a heat, and heats and legacy lane times are rebuilt from `aresmessage` instead of being stored.

**Architecture:** First the persistence moves (baby steps 1–5): a version 1 fixture pins today's behavior, the heat and legacy services work in memory and are rebuilt from the stored messages when a document's `RecordingSession` is created, then a migration drops the derived tables. Then the model grows (steps 6–11) inside `alphatimer-heats` and the refiner, each step behind its own tests, with `LaneRows` as the one reader the front ends share.

**Tech Stack:** Java 25, Maven multi-module, JUnit 6 + AssertJ, Hibernate 6 on SQLite (project-db only), Lombok.

**Spec:** `docs/superpowers/specs/2026-11-19-heat-time-history-design.md`

## Global Constraints

- Every build/test command is prefixed with `nice -n 19`.
- Single module test: `nice -n 19 mvn -q -pl <module> -am test -Dtest=<Class> -Dsurefire.failIfNoSpecifiedTests=false`; full: `nice -n 19 mvn -q clean verify` (a display is available, the Swing smoke test runs).
- Follow `AGENTS.md`: PTDD (state the expected failure before running a new test), records for value types, no comments that only restate code, enums stored by name through `EnumNameConverter`, timing vocabulary (`heat`, `lane`, `split`/`finish`, `penalty`).
- One commit per task, message in German, at most 3–4 bullets, ending with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. Branch `heat-time-history`; never commit `data/`.
- The legacy REST format (`/legacy/heats.xml`, `/legacy/heats.json`) must not change.
- Project files of version 1 must still open (fixture of Task 1).
- Never use the recordings in `data/` in tests (personal data).

## Review Focus

1. A version 1 project file opens with the same heats and legacy heats as before → Task 1 test, re-run in Tasks 4, 5, 7.
2. Starting a recording in a reopened project continues its heats without replaying the stored messages a second time → Task 4 test `aRecordingContinuesTheRebuiltHeats`.
3. A stored message the heat model rejects (lane 11 → `UnknownLaneException`) does not stop the rebuild of the other messages → Task 4 test `aMessageThatCannotBeAppliedDoesNotStopTheRebuild`.
4. The result list repeating a live time adds no second entry; a different result time supersedes the live one → Task 7 tests.
5. A backup time from the previous race's results changes nothing → Task 10 test `aBackupTimeOfThePreviousRaceIsIgnored`.

---

### Task 1: Version 1 fixture project file

**Files:**
- Create: `alphatimer-ui-core/src/test/resources/fixtures/version-1.timing`
- Create: `alphatimer-ui-core/src/test/java/org/lisasp/alphatiming/ui/core/project/Version1FixtureTest.java`

**Interfaces:**
- Produces: the fixture file and the test `Version1FixtureTest`, which every later task keeps green.

- [ ] **Step 1: Generate the fixture with the current code** (before any change): a throwaway program outside the repo (e.g. in the session scratchpad) that does `TimingDatabase.createNew`, wires `MessageStore`, `LegacyService(new LaneTimeStore(db))` and `HeatService(new HeatStore(db))` behind a `DataHandlingMessageRefiner` like `RecordingSession.build`, feeds these `DataHandlingMessage`s (competition `""`, arrival times from 2026-05-01T10:00:00 one second apart) and saves with `snapshotTo`:
  1. ReadyToStart, event 1, heat 1, lap count 2
  2. used lanes `+++-------` (event 1, heat 1)
  3. OnLineTime start (kind `S`, lap 0)
  4. OnLineTime split lane 1 lap 1 = 30.00 s; lane 2 lap 1 = 31.00 s
  5. OnLineTime finish lane 1 lap 2 = 61.23 s; lane 2 = 62.00 s
  6. DNS lane 3 (time marker `DNS`)
  7. CurrentRaceResults finish lane 1 = 61.23 s, rank 1
  8. OfficialEnd, event 1, heat 1
  
  Verify the saved file has `user_version` 0 and rows in `heat`, `lane`, `lanetime`, `aresmessage`.

- [ ] **Step 2: Write `Version1FixtureTest`** — copies the fixture to `@TempDir`, opens it through `TimingDocument.open` and a `RecordingSession` the way `AppController.install` does, and asserts:
  - test `theHeatsOfAVersion1FileAreShown`: heat 1/1 is `Finished`; lane 1 finish 61 230 ms, place 1; lane 2 62 000 ms, place 2; lane 3 penalty DNS, place 0.
  - test `theLegacyHeatsOfAVersion1FileAreServed`: `session.legacyHeats()` has one heat with lane index 0 times containing 61 230 and lane index 1 containing 62 000.
  Read the heats through the API the session/controller offers at this point (today: `HeatStore.findAll()`); later tasks switch the read path, not the expectations.
- [ ] **Step 3: Run** `Version1FixtureTest` → PASS (it pins current behavior).
- [ ] **Step 4: Commit** fixture and test ("Testdatei der Projektversion 1 anlegen").

### Task 2: `MessageStore.findAll()`

**Files:**
- Modify: `alphatimer-project-db/src/main/java/org/lisasp/alphatiming/project/db/MessageStore.java`
- Test: `alphatimer-project-db/src/test/java/org/lisasp/alphatiming/project/db/MessageStoreTest.java`

**Interfaces:**
- Produces: `public List<DataHandlingMessage> findAll()` — every stored message in insertion (id) order, regardless of competition.

- [ ] **Step 1: Test** `findAllReturnsEveryMessageInTheOrderItWasStored`: put three messages of two heats and two competitions, assert `findAll()` equals them in put order.
- [ ] **Step 2: Run** → fails to compile (no `findAll`).
- [ ] **Step 3: Implement** with `order by a.id`, reusing `toMessage`.
- [ ] **Step 4: Run** → PASS. **Step 5: Commit.**

### Task 3: In-memory repositories; service tests leave the database

**Files:**
- Create: `alphatimer-heats/src/main/java/org/lisasp/alphatiming/heats/InMemoryHeatRepository.java`
- Create: `alphatimer-legacy-server/src/main/java/org/lisasp/alphatiming/legacy/server/InMemoryLaneTimeRepository.java`
- Move: `alphatimer-project-db/src/test/.../HeatServiceTest.java` → `alphatimer-heats/src/test/java/org/lisasp/alphatiming/heats/HeatServiceTest.java`; `LegacyServiceTest.java` → `alphatimer-legacy-server/src/test/java/org/lisasp/alphatiming/legacy/server/LegacyServiceTest.java`

**Interfaces:**
- Produces: `public final class InMemoryHeatRepository implements HeatRepository` (no-arg constructor; `findAll()` newest change first, `findUsedLanes()` lanes with status `Used` in any heat); `public final class InMemoryLaneTimeRepository implements LaneTimeRepository` (keeps insertion order; `delete` removes every matching entry).

- [ ] **Step 1:** Move both test classes to their modules, replacing `TimingDatabase`/`HeatStore`/`LaneTimeStore` with the in-memory repositories; assertions unchanged (`store.find(...)` becomes the in-memory repository's `find`).
- [ ] **Step 2: Run** them → fail to compile (classes missing).
- [ ] **Step 3: Implement** both repositories.
- [ ] **Step 4: Run** both test classes and `HeatStore`-based tests left in project-db → PASS. **Step 5: Commit.**

### Task 4: Heats and legacy lane times rebuilt from the messages

**Files:**
- Create: `alphatimer-ui-core/src/main/java/org/lisasp/alphatiming/ui/core/recording/MessageReplay.java`
- Modify: `RecordingSession.java` (constructor, `build`, new `liveHeats()`), `WriteMonitor.java` (remove `heats`, `laneTimes`), `RecordedData.java` (remove `HEATS`, `LANE_TIMES`)
- Modify: `alphatimer-ui-common/.../AppController.java:509-511`, `messages.properties`/`messages_de.properties` (remove `data.HEATS`, `data.LANE_TIMES`)
- Test: `RecordingSessionTest.java` (drop the `heat`/`lanetime` rows of the CSV source at line 322), `Version1FixtureTest`

**Interfaces:**
- Consumes: `MessageStore.findAll()` (Task 2), the in-memory repositories (Task 3).
- Produces: `RecordingSession` owns one `HeatService` (on `InMemoryHeatRepository`) and one `LegacyService` (on `InMemoryLaneTimeRepository`) for its whole life; the constructor replays `findAll()` into them; `public LiveHeats liveHeats()`. `MessageReplay.replay(List<DataHandlingMessage> messages, DataHandlingMessageListener legacy, Consumer<RefinedMessage> heats)` feeds each message to `legacy` and through a new `DataHandlingMessageRefiner` to `heats`, in order.

- [ ] **Step 1: Tests in `RecordingSessionTest`:**
  - `aNewSessionRebuildsTheHeatsOfTheStoredMessages`: record a heat (start, finish lane 1) through a fake reader, stop, create a second `RecordingSession` on the same db → its `liveHeats().current()` equals the first session's, and `legacyHeats()` are equal.
  - `aRecordingContinuesTheRebuiltHeats`: on that second session, record a finish for lane 2 → lane 1 keeps its time, lane 2 gets its time, and the db holds each message exactly once (`MessageStore.findAll().size()`).
  - `aMessageThatCannotBeAppliedDoesNotStopTheRebuild`: store a time for lane 11 before a valid finish of lane 1 → the rebuilt heat has lane 1's time.
- [ ] **Step 2: Run** → compile failure (`liveHeats`), then the expected assertion failures.
- [ ] **Step 3: Implement.** `build()` registers the session's existing services; it no longer creates a `HeatService` or `HeatStore`. Heat listeners registered later get the current state as `HeatService.register` does today. `AppController.install` sets `lastHeats = next.liveHeats()`. `HeatStore`/`LaneTimeStore` stay unused until Task 5.
- [ ] **Step 4: Run** `RecordingSessionTest`, `Version1FixtureTest` (now reading through `session.liveHeats()`), `AppControllerTest`, `I18nTest` → PASS. **Step 5: Commit.**

### Task 5: Migration drops the derived tables

**Files:**
- Create: `alphatimer-project-db/src/main/java/org/lisasp/alphatiming/project/db/DropDerivedTables.java` (a `MigrationStep`)
- Modify: `SchemaMigrations.java` (`STEPS = List.of(new DropDerivedTables())`), `TimingDatabase.java` (`ENTITIES` without `LaneTimeEntity`, `HeatEntity`, `LaneEntity`; `createKeys` removed; `checkIsTimingDatabase` requires `settings`, `rawdata`, `aresmessage` only)
- Delete: `HeatEntity`, `LaneEntity`, `LaneTimeEntity`, `HeatStore`, `LaneTimeStore`, `LaneKeys`, `EnumNameConverters` converters only those entities used (`HeatStatusConverter`, `LaneStatusConverter`, `PenaltyConverter`, `LegacyLaneStatusConverter`); the project-db dependencies on `alphatimer-heats` and `alphatimer-legacy-server` if nothing else needs them
- Test: `SchemaMigrationsTest`, `TimingDatabaseTest` (remove the two `heat`/`lane` key tests), `Version1FixtureTest`

**Interfaces:**
- Produces: schema version 1 = no `heat`, `lane`, `lanetime` tables.

- [ ] **Step 1: Tests:** `TimingDatabaseTest.aNewProjectHasNoDerivedTables` (no table named `heat`, `lane`, `lanetime`; `user_version` 1); `Version1FixtureTest.openingAVersion1FileDropsItsDerivedTables` (after open, the work file has `user_version` 1 and none of the three tables; the heat assertions of Task 1 still hold).
- [ ] **Step 2: Run** → FAIL on the tables still present.
- [ ] **Step 3: Implement** `DropDerivedTables.apply` with `drop table if exists lane`, `heat`, `lanetime` (lane before heat).
- [ ] **Step 4: Run** `nice -n 19 mvn -q clean verify` → PASS. **Step 5: Commit.**

### Task 6: Penalties become flags

**Files:**
- Modify: `alphatimer-heats/.../Penalty.java` (remove `None`), `LaneDto.java` (`Set<Penalty> penalties` replaces `penalty`), `Lane.java`, `Heat.java`
- Modify: `alphatimer-ui-common/.../LaneRows.java`
- Test: `HeatTest`, `HeatServiceTest`, `LaneRowsTest`, `Version1FixtureTest`

**Interfaces:**
- Produces: `LaneDto(int number, int timeInMillis, int lap, LaneStatus status, Set<Penalty> penalties, int place, boolean finished)`; `penalties` is an unmodifiable set, empty for none.

- [ ] **Step 1: Tests (`HeatTest`):** `aLaneCanCarrySeveralPenalties` (DNF then DSQ → `{DidNotFinish, Disqualified}`); `aPenaltyKeepsTheTimesOfTheLane` (finish 61 230, then DNS → time still 61 230, place 0); `onlyALaneWithoutPenaltyGetsAPlace`; `aLiveStartClearsThePenalties`. `LaneRowsTest.everyPenaltyOfALaneIsShown` (DNF+DSQ → detail `"DNF, DSQ"` in English, in enum order, joined by `", "`).
- [ ] **Step 2: Run** → compile failure, then the predicted assertion failures.
- [ ] **Step 3: Implement**; `isDone` = used and (finished or penalties not empty); `ranks` = used, finished, no penalties, time > 0.
- [ ] **Step 4: Run** `alphatimer-heats`, `alphatimer-ui-common` tests and `Version1FixtureTest` → PASS. **Step 5: Commit.**

### Task 7: Every time of a lane is kept

**Files:**
- Create: `alphatimer-heats/.../LaneTime.java`, `TimeKind.java`, `TimeSource.java`, `TimeType.java`
- Modify: `LaneDto.java`, `Lane.java`, `Heat.java`, `alphatimer-ui-common/.../LaneRows.java`
- Test: `HeatTest`, `HeatServiceTest`, `LaneRowsTest`, `HeatRowsTest`, `FinishedHeatsTest`, `LiveViewPanelTest`, `TuiMainWindowTest`, `Version1FixtureTest`

**Interfaces:**
- Produces:
  - `record LaneTime(int lap, TimeKind kind, int timeInMillis, TimeSource source, TimeType type, boolean deleted, LocalDateTime arrived)`
  - `enum TimeKind { Split, Finish, Backup }`, `enum TimeSource { Live, Result }`, `enum TimeType { Normal, Manual, Corrected }`
  - `LaneDto(int number, LaneStatus status, Set<Penalty> penalties, int place, List<LaneTime> times)` with `Optional<LaneTime> finish()` (newest non-deleted `Finish`), `Optional<LaneTime> current()` (`finish()`, else the newest non-deleted `Split`), `List<LaneTime> backups()`.
  - Mapping: `RefinedMessageType.Live` → `Live`, `CurrentRaceResult` → `Result`; `RefinedTimeType.Normal/ManualTime/CorrectedTime` → `Normal/Manual/Corrected`; `DeletedTime` marks.

- [ ] **Step 1: Tests (`HeatTest`):**
  - `everySplitIsKept`: splits lap 1 and 2 → two `Split` entries in arrival order.
  - `aRepeatedTimeAddsNoEntry`: live finish 61 230 then result finish 61 230 → one `Finish` entry, source `Live`.
  - `aDifferentResultTimeSupersedesTheLiveTime`: live finish 61 230, result finish 61 250 → two entries, `finish()` = 61 250.
  - `aDeletedSplitIsMarkedNotRemoved`: split lap 1 = 30 000, split lap 2 = 45 000, deleted lap 1 = 30 000 → lap 1 entry `deleted`, `current()` = 45 000.
  - `aDeletedFinishWithdrawsThePlaceAndTheEnd` (existing behavior: deleted as split of last lap with the finish value → `finish()` empty, place 0, `ended` null while the heat runs).
  - `aSplitAfterTheFinishDoesNotReplaceIt`: finish then split → `current()` is the finish.
  - `aLiveStartClearsTheTimes`.
- [ ] **Step 2: Run** → compile failure, then the predicted assertion failures.
- [ ] **Step 3: Implement.** Places and `isDone` use `finish()`; `syncStart` and the running clock keep their behavior. `LaneRows` shows `current()` (time, lap of a split, `finished` when it is the finish). Update the other tests only where they construct `LaneDto` (add a test helper if several need the same lane).
- [ ] **Step 4: Run** `nice -n 19 mvn -q clean verify` → PASS. **Step 5: Commit.**

### Task 8: ReadyToStart announces a heat

**Files:**
- Modify: `HeatDto.java` (`LocalDateTime readyToStart` after `lapCount`), `Heat.java` (`void readyToStart(ReadyToStartMessage message)`), `HeatService.java` (dispatch), `TestMessageCreator.java` (`readyToStart(int event, int heat, int lapCount, LocalDateTime at)`)
- Test: `HeatTest`, `HeatServiceTest`

**Interfaces:**
- Produces: `HeatDto(int event, int heat, HeatStatus status, int lapCount, LocalDateTime readyToStart, LocalDateTime started, LocalDateTime ended, LocalDateTime lastMessage, Integer startTimeOfDayMillis, List<LaneDto> lanes)` — remove the shorter overloads, update callers.

- [ ] **Step 1: Tests:** `HeatServiceTest.aReadyToStartAnnouncesTheHeat` (unknown heat → `Open`, lap count 4, `readyToStart` = arrival, becomes `current()`); `HeatTest.aReadyToStartDoesNotChangeAStartedHeat` (started heat → DTO unchanged).
- [ ] **Step 2: Run** → compile failure, then FAIL.
- [ ] **Step 3: Implement.** `lastMessage` is set by the ReadyToStart of an open heat.
- [ ] **Step 4: Run** module tests and `Version1FixtureTest` (its ReadyToStart now sets `readyToStart`; add that assertion) → PASS. **Step 5: Commit.**

### Task 9: Refiner keeps backup times

**Files:**
- Modify: `alphatimer-ares-serial-api/.../enums/KindOfTime.java` (`UnknownValueB` → `Backup`), `alphatimer-project-db/.../EnumNameConverters.java` (`KindOfTimeConverter` reads `"UnknownValueB"` as `Backup`, like `MessageTypeConverter`)
- Create: `alphatimer-refinedmessages-api/.../accepted/BackupTimeMessage.java`, `.../dropped/DroppedBackupTimeMessage.java`, `alphatimer-refinedmessages/.../BackupTimeParser.java`
- Modify: `DataHandlingMessageRefiner.java` (register the parser), `TimeParser.java` (`TimeInfo.Backup` is the backup parser's, not dropped)
- Test: `alphatimer-refinedmessages/src/test/.../BackupTimeMessageTest.java`, `TimeMessageTest`, `EnumNameConvertersTest` (or the existing converter test), `CaptureAnalysis` tests if counts change

**Interfaces:**
- Produces: `@Value class BackupTimeMessage implements RefinedMessage { LocalDateTime timestamp; String competition; short event; byte heat; RefinedMessageType messageType; byte lane; byte currentLap; int timeInMillis; }` with `getDeviceTimeInMillis()` = the time. Type 7 maps to `RefinedMessageType.CurrentRaceResult`; types 3/4 → `CurrentRaceResult`, 5/6 → `PreviousRaceResult`, 2 → `Live`.

- [ ] **Step 1: Tests (`BackupTimeMessageTest`):** `messageType7IsABackupTime` (type 7, kind `B`, time type `1`, lane 6, lap 1, 13.71 s → one `BackupTimeMessage` lane 6, lap 1, 13 710 ms, `CurrentRaceResult`); `aResultTimeWithTimeInfoBackupIsABackupTime` (type 4, finish, time info `B` → `BackupTimeMessage`, no `TimeMessage`); `aBackupTimeWithoutLaneIsDropped` (lane 0 → `DroppedBackupTimeMessage`). Converter: `"UnknownValueB"` → `KindOfTime.Backup`.
- [ ] **Step 2: Run** → compile failure, then FAIL.
- [ ] **Step 3: Implement**; valid = lane > 0, current lap > 0, time > 0.
- [ ] **Step 4: Run** refiner, project-db and cli tests → PASS. **Step 5: Commit.**

### Task 10: Backup times reach the lane

**Files:**
- Modify: `Heat.java` (`void backupTime(BackupTimeMessage message)`), `Lane.java`, `HeatService.java` (dispatch; `isRepeatOfAPreviousRace` includes `BackupTimeMessage`), `TestMessageCreator.java` (`backup(int event, int heat, int lane, int lap, int millis, LocalDateTime at)`)
- Test: `HeatTest`, `HeatServiceTest`

**Interfaces:**
- Consumes: `BackupTimeMessage` (Task 9), `LaneTime`/`TimeKind.Backup` (Task 7).

- [ ] **Step 1: Tests:** `aBackupTimeIsKeptBesideTheFinish` (finish 13 630, backup 13 710 → `finish()` 13 630, `backups()` one entry 13 710, place unchanged); `aBackupTimeAloneGivesNoFinish` (`finish()` empty, place 0); `HeatServiceTest.aBackupTimeOfThePreviousRaceIsIgnored`.
- [ ] **Step 2: Run** → FAIL. **Step 3: Implement** (same one-entry-per-time rule; deletion never marks a backup).
- [ ] **Step 4: Run** module tests → PASS. **Step 5: Commit.**

### Task 11: Documentation and final verification

**Files:**
- Modify: `AGENTS.md` (module rows of `alphatimer-heats`, `alphatimer-legacy-server`, `alphatimer-project-db`, `alphatimer-ui-core`; *Persistence and Compatibility*: tables, the first migration step, the rebuild on open; the refiner additions: backup times are refined), `README.md` if it describes "one time per lane", `docs/issues/backup-times-not-interpreted.md` (backup times are kept now; what stays open: when a backup time becomes official) and its row in `docs/issues/README.md`

- [ ] **Step 1:** Update the documents to the new behavior; do not touch the Spring Boot references (separate issue).
- [ ] **Step 2: Run** `nice -n 19 mvn -q clean verify` → PASS.
- [ ] **Step 3: Commit.**
