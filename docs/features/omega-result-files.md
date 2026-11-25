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
- **Format, as far as known:** text, windows-1252, separator `;` (`,` also accepted, as in `AresResultFile`), a header
  line, one row per lane and lap. Known only from AlphaServer's `FileReader` (two sample rows) and the reader in
  `AresResultFile`:

  ```
  event;round;heat;lap;lane;idStatus;rank;time ;result ;mod ;btime;bresult;bmod;dtime;dresult;dmod
  1 ;0 ;0 ;0 ;0 ;0 ;0 ;57550360 ;" 15:59:10.36" ;" " ;0 ;"" ;"" ;0 ;"" ;"" ;
  1 ;0 ;0 ;50 ;7 ;0 ;1 ;33920 ;" 33.92" ;" " ;33920;" 33.92" ;" " ;0 ;"" ;"" ;
  ```

  | Column | Meaning (assumed where not proven) |
  | --- | --- |
  | `event` | event number |
  | `round` | `idRound` of `LSTROUND.TXT` |
  | `heat` | heat, counted from 0 (as in `LSTSTART.TXT`) |
  | `lap` | distance in metres at which the time was taken; `0` with lane `0` is the start row (time of day of the start) |
  | `lane` | lane |
  | `idStatus` | status, an index into `LstStat.txt` (e.g. DQ); `0` = none |
  | `rank` | place |
  | `time`, `result`, `mod` | time in ms, as text, modifier |
  | `btime`, `bresult`, `bmod` | backup time in ms (`0` = none), as text, modifier |
  | `dtime`, `dresult`, `dmod` | unknown |

- **One folder per competition:** each competition gets its own LST working folder, so the file holds no data of
  earlier competitions and a recording takes everything in it. Event and heat are unique in the file.
- **No sample, no specification.** Neither Swiss Timing nor any public source documents the columns, and no real
  `LSTRslt.txt` is available yet. Program and start lists of the same family are in `data/ares` (ignored by Git).

## Stages

1. **Final times (this feature).** Event, round, heat, lane, final time and backup time.
2. **Everything the file shows (later, separate feature).** Splits from the `lap` rows, the status from `idStatus`
   (DSQ, DNS, DNF), places. Starts only when a real `LSTRslt.txt` with splits, a disqualification and a heat written
   twice is available.

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
  heat. The file heat `0` is heat `1`.
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
- Test data: hand-written files in the format above (as in `AresResultFileTest`), including a heat written twice and a
  file being rewritten. Checking the reader against a real `LSTRslt.txt` before the first event.

## Open questions

- The meaning of `lap` (distance or lap number), `mod`, `dtime`/`dresult`/`dmod`; whether the start row is always lane
  0, also in a pool numbered from 0 (Quantum's *Pool Config* allows lanes 0 to 9). Until a real file shows it, a lane 0
  with a `lap` above 0 is read as a lane and makes the numbering `ZeroBased`.
