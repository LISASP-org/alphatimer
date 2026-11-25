# Omega result files (Quantum LSTRslt.txt)

When AlphaTiming cannot connect to the serial port of the Omega timing system (no cable, no free port, a driver
problem), the results of the competition come from the result file Quantum writes instead. The project then records
that file; JAuswertung gets the times through the legacy endpoints as usual, so the timing desk works the same way.

- **Goal:** a project can record Quantum's `LSTRslt.txt` as its only input. Every heat Quantum makes official reaches the
  heat model as refined messages and JAuswertung through the legacy endpoints, as with LENEX.
- **Non-goals:**
  - Switching a project from the serial line to the file. A project keeps exactly one input; when the line fails
    during an event, the user starts a new project on the file.
  - A live view. The file only gets a heat when it is official: no running time, no splits while the heat runs.
  - The result table of ARES 21 ("Update result table file", appended on *End of race*). Its name and format are not
    known; it may follow later as another source of the same reader if it turns out to be the same format.
  - Changes to `AresResultFile` (`alphatimer-legacy-api`). It stays as it is for JAuswertung.
- **Anti-goal:** times counted twice or a corrected time lost when a heat is written again. The file is a backup at an
  event where the line already failed; it must be at least as reliable as the line.

## The file

- **Source:** Quantum, data handling interface *DH LST Files* (Quantum Swimming user manual 3480.509.02 v1.4, 5.2.1.2;
  `docs/ares`). Quantum writes `LSTRslt.txt` into the LST working folder (the folder it reads `LSTRACE.TXT`,
  `LSTSTART.TXT`, … from) when a race is made official, if *Save results on official* is on (off by default). With a
  session mask the folder is the `SessionN` subfolder. *Export all results* writes the whole file again.
- **Format:** text, windows-1252, CRLF line endings, separator `;` with one after the last column (17 fields per
  line; `,` also accepted, as in `AresResultFile`), a header line; a file without results is the header alone. Checked
  against 12 real files of two German championships (2014, 2016; `data/ares`, ignored by Git):

  ```
  event;round;heat;lap;lane;idStatus;rank;time ;result ;mod ;btime;bresult;bmod;dtime;dresult;dmod
  1 ;0 ;0 ;0 ;0 ;0 ;0 ;57550360 ;" 15:59:10.36" ;" " ;0 ;"" ;"" ;0 ;"" ;"" ;
  1 ;0 ;0 ;50 ;7 ;0 ;1 ;33920 ;" 33.92" ;" " ;33920;" 33.92" ;" " ;0 ;"" ;"" ;
  ```

  | Column | Meaning |
  | --- | --- |
  | `event` | event number |
  | `round` | `idRound` of `LSTROUND.TXT`; `0` in every sample |
  | `heat` | heat, counted from 0 (as in `LSTSTART.TXT`) |
  | `lap` | distance in metres (50, 100, 150, 200); `0` is the start: lane `0` holds the time of day of the start, every lane has a row with time `0` |
  | `lane` | lane (1 to 8 in the samples) |
  | `idStatus` | status, an index into `LstStat.txt`; `0` = none; only `64` seen, always on the lane's `lap` 0 row, on lanes without a time (meaning unknown, e.g. DNS or DSQ) |
  | `rank` | place, on the rows with `lap` above 0 |
  | `time`, `result`, `mod` | time in ms, as text, modifier: `B` = the time is the backup time (`time` equals `btime`), `E` = edited by hand |
  | `btime`, `bresult`, `bmod` | backup time in ms (`0` = none), as text, modifier |
  | `dtime`, `dresult`, `dmod` | only in the 2016 files; on `lap` 0 about the reaction time (median 0.79 s), on other laps unknown; `dmod` `*` unknown |

- **One folder per competition:** each competition gets its own LST working folder, so the file holds no data of
  earlier competitions and a recording takes everything in it.
- **Rows of a heat:** event, heat, lane and lap are unique, but the rows of one heat need not stand together: in the 2014
  files the start row comes first and the lanes follow later, between the rows of other heats. A lane with only its
  `lap` 0 row has no time. `time` 0 with `btime` set occurs once in all samples; Quantum normally writes the backup
  time into `time` and marks it `B`.

## Stages

1. **Final times (this feature).** Event, round, heat, lane, final time and backup time.
2. **Everything the file shows (later, separate feature).** Splits from the `lap` rows, the status from `idStatus`
   (DSQ, DNS, DNF), places. Needs the meaning of `idStatus` (`LstStat.txt` of a real installation) and of the `d`
   columns.

## Design (stage 1)

Built like the LENEX input (`LenexReader`, `LenexRefiner`, `LenexRecording`, `LenexVersionStore`), chosen over
generalising the LENEX recording (would rebuild a working path and migrate `lenexversion`) and over turning rows into
ARES messages (would invent protocol messages and bypass the refined path).

- **Modules:** a new pair, `alphatimer-lstresults-api` (the snapshot: heats with event, round, heat and lanes with final
  time and backup time) and `alphatimer-lstresults` (`LstResultsReader`, `LstResultsRefiner`). Same dependency rules
  as `lenex-api`/`lenex`; the snapshot is exchanged with `ui-core` like `LenexMeet` (see the chore
  [Why ui-core uses legacy-api and lenex-api](../chores/ui-core-api-usage.md)).
- **Reading:** `LstResultsReader` reads one version of the file into the snapshot. Rows whose event, heat or lane
  cannot be read are skipped, as in `AresResultFile`; the start row (lane 0, lap 0) is skipped. Per lane the final time
  is the `time` of its row with the largest `lap`, the backup time its `btime` if not `0`. Event and heat identify a
  heat; its rows are collected from the whole file, since they need not stand together. A lane with only its `lap` 0
  row has no time. The file heat `0` is heat `1`. The modifier gives the time's kind: `B` makes the final time a backup
  time (it is the backup button's time), `E` a corrected time (`CorrectedTime`, as an edited time of the line).
- **Refining:** `LstResultsRefiner.refine(previous, next, arrival)` compares two snapshots, like `LenexRefiner`: a new
  heat gives `UsedLanesMessage` (its lanes, `OneBased`), its final times as `TimeMessage`
  (`RefinedMessageType.CurrentRaceResult`, kind `Finish`, origin `Primary`), the backup times with origin `Backup`, and
  `OfficialEndMessage`; a time that changed or vanished is sent again as deleted, then the new one. Each version is the
  complete state of the results (Quantum always holds all of them in the file), so a heat missing from the newer
  version gives `HeatRemovedMessage`, as with LENEX. The round is the `round` column as a number string (`RefinedMessage.getRound()`). Competition as in `LenexRecording` (`""`).
- **Heats without a start:** like a LENEX heat known from results alone, `HeatService` dates the start back by the
  longest finish; no running clock (`isLive` false).
- **Recording:** `LstResultsRecording` in `alphatimer-ui-core`, like `LenexRecording`: checks the file at an interval on
  its own thread; a readable file is a sign of life; a changed file (modification time or size) is read once it ends with a line break and its size and modification
  time are unchanged since the previous check (a row cut in the middle would otherwise give a wrong time that looks
  right, `33920` read as `339`; a heat therefore arrives one interval later), compared with the last recorded version and, if its content differs, stored and refined; a file that cannot be read or is being
  written stops nothing and is read again at the next check. A missing file stops nothing.
- **Storage:** every changed version of the whole file, gzip-compressed, in a new table `lstresultsversion` through an
  `LstResultsVersionStore` (`alphatimer-project-db`), like `lenexversion`: whole versions, not appended rows, because
  *Export all results* rewrites the file. Additive schema change (Hibernate `update`); older project files get the table
  when opened; no migration step. `RecordedData` gets a kind for it, so write failures are reported as for LENEX.
- **Opening a project:** the stored versions are replayed through the refiner after the ARES messages, as LENEX
  versions are.
- **Input:** a new `Input` for the result file and a new `ConnectionType` (`input()` returns it). A project holding
  result-file data refuses every other input (`RecordingError.Kind.OTHER_INPUT_RECORDED`) and vice versa.
  `recordedInput()` reads it from the database on opening (the table holds rows).
- **Backup times:** stay backups in the heat model, also for a lane without a final time (`time` 0, `btime` set).
- **Legacy:** `legacyHeats()` serves `RefinedLegacyService` for the result-file input on all legacy paths, as for LENEX.
  For a lane without a final time, `RefinedLegacyService` serves the backup time as its time; a final time arriving
  later replaces it. This fallback is switched on for the result-file input only, so the legacy output of ARES and LENEX
  projects (and `LegacyEquivalenceTest`) stays as it is. The legacy format does not change.
- **Settings:** the file path and the check interval as new settings (nullable columns of `settings`, read as empty /
  the default), with the values and default of the LENEX interval (1, 5, 15, 30, 60 s; 5 s). `Settings.validate()`
  requires a path. The form offers the result file as a third system next to Omega and LENEX (a new `Input`
  value; the form rules stay as they are), and the Omega system is renamed "Omega Serial" in the UI texts (the
  `Input` value `ARES` keeps its name); once the project holds data, only its own system is offered
  (`SettingsFormRules`).
- **UI texts:** `MessageText` gives one line per recorded version (heats changed), like LENEX; the status bar shows the
  sign of life as for LENEX (`SignOfLife`: three check intervals). Texts in English and German.
- **Docs:** `AGENTS.md` (module table, dependency rules, api pairs, persistence) and `README.md` (how to switch on
  *Save results on official* in Quantum and which file to choose).

## Left to the builder

- Names of modules, classes, the `Input`/`ConnectionType` value, settings fields and the table, following the existing
  ones.
- Whether reading the file and comparing versions share code with `LenexRecording`; no change to the LENEX path in this
  feature.
- Test data: hand-written files in the format above (as in `AresResultFileTest`), including a heat whose rows are spread
  over the file, the modifiers `B` and `E`, a lane with only its `lap` 0 row and a file being rewritten. The real files in
  `data/ares` contain personal data and are never committed; the builder may check the reader against them locally.

## Open questions

- The meaning of `idStatus` and of `dtime`/`dresult`/`dmod` (stage 2).
- A pool numbered from 0 (Quantum's *Pool Config* allows lanes 0 to 9): the samples only have lanes 1 to 8. A lane 0
  with a `lap` above 0 is read as a lane and makes the numbering `ZeroBased`.
