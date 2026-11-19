# Heat Model with Time History — Design

Date: 2026-11-19. Branch: `heat-time-history`.

## Intent

The heat model (`alphatimer-heats`) keeps only one time per lane, one penalty and ignores ReadyToStart and backup
times. It is to hold everything the refined messages carry that is worth serving: every split and finish time, the
backup times next to them, all penalties of a lane and the announced heat. A later REST endpoint (`GET /timing`,
`docs/features/timing-rest-endpoint.md`) serves this model to JAuswertung; it is not part of this change.

## Goals

- Keep every split and finish time of a lane, with its lap, kind, source and type, in order of arrival.
- Keep backup times as additional times of the lane; they never replace a touchpad time.
- Penalties are flags: a lane can carry several.
- ReadyToStart announces a heat.
- Heats and legacy lane times are no longer stored; they are rebuilt from `aresmessage` when a project is opened.

## Non-goals

- Take-over times stay ignored.
- The REST endpoint for the new model (separate feature).
- How JAuswertung uses the data.
- Deciding when a backup time becomes the official time (`docs/issues/backup-times-not-interpreted.md` keeps that
  open question).
- Purging the references to the removed Spring Boot application
  (`docs/issues/references-to-removed-spring-boot-app.md`). The refiner modules are AlphaTiming's own code; changing
  them needs no special justification.

## Constraints

- Project files of version 1 must still open; their heats are rebuilt from their messages.
- The legacy REST format stays unchanged.
- No noticeable cost when opening a project (measured below).

## Approaches considered

- **Persistence.** (a) Keep storing heats and extend the tables — every model change needs a data migration, and old
  files keep their old, less complete heats. (b) Store only `aresmessage` and `rawdata` and rebuild heats and lane
  times on open — **chosen**: the model can change freely, refiner fixes apply to old files, recording writes less.
  (c) Rebuild from `rawdata` — rejected: its timestamps are per chunk, not per message, so arrival times would be
  approximate.
- **Times of a lane.** (a) One current time per lane as today — loses splits and alternatives. (b) A list of every
  time received, from which the current and the finish time are derived — **chosen**.

### Rebuild performance (measured 2026-11-19)

23 recordings from `data/` (28,619 messages, 1,206 heats) were recorded into project files and opened.

- Rebuild from `aresmessage` after the program start (Hibernate already started by the empty startup project): on
  average 107 ms per file against 94 ms for loading the stored heats; the largest file (5,476 messages) 180 ms.
- In the Swing UI, opening a project file takes 205 ms on average, at most 244 ms, with the stored heats; the rebuild
  adds about 13 ms on average.
- Conclusion: the rebuild causes no performance issue.

## Design

### Data structure (`alphatimer-heats`)

```java
public record HeatDto(int event, int heat, HeatStatus status, int lapCount,
                      LocalDateTime readyToStart,       // arrival of the ReadyToStart; null when none was received
                      LocalDateTime started, LocalDateTime ended, LocalDateTime lastMessage,
                      Integer startTimeOfDayMillis, List<LaneDto> lanes) { }

public record LaneDto(int number, LaneStatus status,
                      Set<Penalty> penalties,           // empty = no penalty
                      int place,                        // 0 = no place
                      List<LaneTime> times) {           // in order of arrival
    Optional<LaneTime> finish();                        // newest finish that is not deleted
    Optional<LaneTime> current();                       // finish(), else the newest split that is not deleted
    List<LaneTime> backups();
}

public record LaneTime(int lap, TimeKind kind,          // Split, Finish, Backup
                       int timeInMillis,
                       TimeSource source,               // Live, Result
                       TimeType type,                   // Normal, Manual, Corrected
                       boolean deleted,
                       LocalDateTime arrived) { }

public enum Penalty { DidNotStart, DidNotFinish, Disqualified }
```

### Rules

- **One entry per time:** a time message adds nothing only when it equals (same time value) the newest entry of the same
  lap and kind that is not deleted — a repeat, like the result list after a live time. Otherwise it is appended, so a
  value that returns after a correction, or a deleted time that is sent again, counts again as the newest entry; the
  newest valid entry counts. Backup times follow the same rule.
- **Deleting:** a deleted time marks the entry with the same lap and time value as `deleted` (any kind but backup,
  since a deleted finish arrives as a split of the last lap). Older splits can be withdrawn too.
- **Penalties:** DNS, DNF and DSQ add their flag and clear no times. A lane gets a place only without penalty flags and
  with a finish; ties share a place.
- **ReadyToStart:** for a heat that has not started, it creates the heat as `Open` if needed and sets `lapCount` and
  `readyToStart`. For a started heat it changes nothing.
- **Estimated end:** a ReadyToStart counts as a message of its heat like any other: when it announces another heat,
  a started heat without end is ended as `FinishedEstimated`, as the first message of another heat always does; the
  official end replaces the estimate.
- **Start:** a live start resets the heat as today and clears times, penalties and places of its lanes.
- **Previous race** results stay ignored. Running clock, estimated ends and `LiveHeats` keep their behavior.

### Refiner (`alphatimer-refinedmessages`)

- New `BackupTimeMessage` (lane, lap, time, source). It is refined from message type 7 (`BackupTime`, kind of time
  `B`) and from times with time info `B` (`TimeInfo.Backup`) in the result messages, which `TimeParser` drops
  today. `KindOfTime.UnknownValueB` is renamed to `Backup` (project files store its name: the converter must still
  read the former name).

### Persistence and wiring

- The heats and the legacy lane times live as long as the document: `RecordingSession` owns one `HeatService` and
  one `LegacyService`, both in memory, and fills them from `aresmessage` (in id order, through the refiner) when it
  is created. Recordings feed the same services.
- `AppController` takes the heats from the session instead of `HeatStore`.
- The first `MigrationStep` drops `heat`, `lane` and `lanetime`; their entities and stores go, and so do the write
  kinds `HEATS` and `LANE_TIMES`. `TimingDatabase` no longer requires a `heat` table to accept a file. A version 1
  fixture file (from the test sample bytes, never from `data/`) proves that old files open with their heats.

### Readers

`LaneRows` (and through it the GUI, TUI and `FinishedHeats`) shows `current()`, the lap of the current time and the
penalty flags; with several flags, all are shown.

## Left to the builder

- Names of helper classes and the in-memory repositories.
- Test layout: `HeatServiceTest` and `LegacyServiceTest` no longer need the database and may move to their modules.
