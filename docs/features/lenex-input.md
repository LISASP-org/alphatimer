# LENEX input

AlphaTiming reads only ARES/Quantum. LENEX (`.lef`, XML; `.lxf`, the same zipped) is the open exchange format of
swimming meet software and timing software (Splash, SwimTime, others); its result files hold per heat and lane the
finish and split times and the result status. The specification is LENEX 3.0
(https://wiki.swimrankings.net/index.php/swimrankings:Lenex); the format may be used freely, the example files there may
not be redistributed.

- **Goal:** a LENEX file on the local disk is an input source of AlphaTiming like TCP, serial and file replay; whatever
  software writes it, its results reach the heat model.
- **Behavior:**
  - A watcher reads the file whenever it has changed; the versions are recorded, so the project file keeps what arrived
    and the project can be rebuilt from it.
  - Versions are recorded compressed in a table of their own (not in `rawdata`, which holds the serial byte stream),
    as XML in one line: the parsed file written again without line breaks and indentation between the elements, then
    gzip-compressed (about 200 KB per version of a large meet).
  - A version whose results differ from the last recorded one (heats, lanes, times, status) is kept for good. The newest
    version is always kept as well: a version that differs from the last recorded one only outside the results (export
    time `LENEX created` ignored) replaces the previous such version, or is added after a version with result changes.
    A version with result changes replaces a preceding version without them, since it holds everything that one held.
  - A version that is not well-formed XML (e.g. read while it was being written) is not recorded and not refined; the
    next change of the file is read again.
  - A LENEX refiner compares each version with the previous one and sends refined messages for the changes. No
    `StartMessage` is needed: a time starts an open heat (`Heat.assureStarted`).
    - `EVENT number`, `HEAT number` (through `RESULT heatid`), `RESULT lane` → event, heat, lane.
    - `SPLIT` → `TimeMessage` `SplitTime`; `swimtime` → `TimeMessage` `Finish`; lap = distance ÷ pool length, lap count
      from the event distance (relays: relay count × distance); message type `CurrentRaceResult`.
    - A time that changed or disappeared: the old value of that lap again as `DeletedTime`, then the new one.
    - `RESULT status` `DSQ`/`DNS`/`DNF` → `DisqualifiedMessage`/`DidNotStartMessage`/`DidNotFinishMessage`.
    - `HEAT status` `RUNNING` → `StartMessage`; `OFFICIAL` → `OfficialEndMessage`. `UNOFFICIAL`/`INOFFICIAL` need no
      message: once every used lane has a finish or a penalty the heat model ends the heat, so the refiner sends the used
      lanes of a version before its times.
    - `RESULT status` `SICK` and `WDR` (with lane) → a new refined message `WithdrawnMessage` (lane withdrawn, like
      `DidNotStartMessage`); the heat model gets the penalty `Withdrawn` (no place, the lane counts as done), shown as
      "WDR" like "DNF" (`LaneRows`, `lane.*` texts). `EXH` (exhibition swim) is a regular result: AlphaTiming keeps
      times, whether a swimmer is ranked is decided by the meet software. `RJC` and unknown values are skipped with a
      warning.
    - Lanes with results → `UsedLanesMessage`.
    - Places are computed by the heat model; reaction times are left out ([Reaction times](reaction-times.md)).
  - Results of athletes and relays are both read.
  - `.lef` and `.lxf` (a zip with one `.lef`) are read.
  - Every version is the complete state of the meet up to its export: a time, penalty or heat missing from it that the
    previous version had was removed. A `status` on meet, session or event ("used for incremental data files" in
    LENEX 3.0) tells the progress of the meet and is read like any other file; LENEX defines no partial file by time.
    Result files split by club are not supported.
  - Every input has a live tag: ARES over TCP or serial is live, LENEX is not, a replay file is live or not depending on
    its format. The tag belongs to the input, not to the messages; the program derives it from the selected input. An
    input that is not live shows no running clock. The LENEX refiner sends its times as `CurrentRaceResult`, never as
    `Live` (`RefinedMessageType` tells a time during the race from a result, which is a different thing).
  - The sign of life applies to every input. For LENEX, every check that finds the file present and readable is a sign
    of life, whether or not it changed or is well-formed; a new recorded version counts as a valid message. A read that
    fails for a moment (another program writing the file) therefore does not make the input silent: only 5 s without a
    successful check do, as for ARES.
  - LENEX produces no legacy heats: `LegacyService` reads ARES messages only, so a LENEX project serves empty legacy
    endpoints until [Legacy heats from the heat model](legacy-heats-from-heat-model.md) is done.
  - A project has one input: ARES (TCP, serial, replay file) or LENEX. Once a project holds recorded data, the other
    input cannot be chosen; switching between TCP, serial and replay file stays possible. Projects of earlier versions
    are ARES projects.
  - Opening a LENEX project replays its recorded versions in order through the parser and the LENEX refiner, starting
    from an empty snapshot; the refined messages of a version carry its recorded arrival time, live and on replay, and
    the next file that arrives is compared with the last recorded version.
- **Where:** a new module (e.g. `alphatimer-lenex`) with the parser (file → snapshot of heats) and the refiner (changes →
  refined messages), depending only on `alphatimer-refinedmessages-api`; the watcher as a new input next to the readers
  of `RecordingSession`; the connection type and the file path in `Settings`.
- **Seen in example files** (local only, `data/lenex/`, ignored by Git): heat status `INOFFICIAL` besides the documented
  `UNOFFICIAL`; result status `RJC` (undocumented), `WDR`, `EXH`, `SICK`; withdrawn results without `heatid`, `lane` and
  with `swimtime="00:00:00.00"`; split times every 50 m; reaction times as `+78` (1/100 s).
- **Tests:** fixtures are small hand-written LENEX files with made-up names; the example files contain personal data and
  are under copyright, they are never committed.
- **Open questions:**
  - What SwimTime and Splash actually write and when (per heat, whole meet, atomically); needs a file of a real
    installation.
  - How a removed penalty or a removed heat is refined: the refined messages can delete a time (`DeletedTime`), but
    neither withdraw a penalty nor remove a heat.
