# AGENTS Guide

AlphaTiming is a Swing desktop application that reads the data of a Quantum/Ares timekeeping system over TCP or a
serial port at competitions, records it into one project file and shows the current heat live (lanes, one time per lane, penalties,
placing, running time). It is a Maven multi-module project (Java 25, root `pom.xml`, groupId `org.lisasp.alphatiming`).
The protocol/refiner modules are independent copies of the Spring Boot application `alphatimer` (`../alphatimer`) and
keep its packages `org.lisasp.alphatimer.*`; new code lives under `org.lisasp.alphatiming.*`, its packages following groupId and artifactId without
the `alphatimer-` prefix (`alphatimer-ui` → `org.lisasp.alphatiming.ui`, `alphatimer-ui-core` →
`org.lisasp.alphatiming.ui.core`, `alphatimer-project-db` → `org.lisasp.alphatiming.project.db`). There is no JPMS and no
Spring (the root pom manages all dependency and plugin versions itself). The workspace guide
(`../AGENTS.md`) describes how this project relates to the others. Design and plan: `docs/superpowers/`.

## Desktop, Not a Backend

AlphaTiming is a single-user desktop tool that runs next to the timekeeping system. Do not import web/backend patterns
that solve problems this application does not have:

- One project is open at a time. Its working database is a temporary SQLite file, saved as a whole to the `.timing`
  project file (Save, auto-save). There are no migrations of a shared database, no pagination, no optimistic locking.
- Projects are bounded (a few hours of raw data, hundreds of heats). Prefer clear, simple code over caching schemes.
- The real concurrency risk is threads, not users: the reader thread, the EDT, the auto-save thread and the REST threads
  all touch the same project (see *Swing Threading and Concurrency*).
- The project file is a contract with users' existing files (see *Persistence and Compatibility*).

## Modules

| Module | Purpose |
| --- | --- |
| `alphatimer-ares-serial-api`, `alphatimer-ares-serial`, `alphatimer-refinedmessages-api`, `alphatimer-refinedmessages`, `alphatimer-serial-api` | Copied from `../alphatimer` (`module-info.java` removed): protocol messages, byte→message parsing, message refining, `SerialPortReader`, `SerialConfiguration`. Independent copies — never make them depend on alphatimer. `SerialPortReader` has the additions `start()`, `isConnected()` and `addConnectionLostListener(…)`. `RefinedMessage` has the addition `getDeviceTimeInMillis()` (the time value the device sent, where the message has one; `getTimestamp()` is the local arrival time) and `TakeOverTimeMessage` keeps its time. |
| `alphatimer-serial-core` | The `SerialPortReader` implementations: `TcpReader` (`serial.tcp`, copied from `../alphatimer`; `close()` waits up to 5 s for the reader thread to end) and `NrSerialReader`, `SerialPorts`, `SerialConnectionException` (`serial.com`, on nrjavaserial as in AlphaServer; nrjavaserial silently leaves out ports it cannot open, so on Linux `SerialPorts.withoutAccess` reports the USB adapters (`/dev/ttyUSB*`, `/dev/ttyACM*`) this user may not open, with the group that may (`InaccessiblePort`), and `UserGroups` tells whether the user's account is in that group (`id -nG <user>`, not the groups of this process) and how to add it; line settings from `SerialConfiguration`; a read error or vanished port closes it and is reported to its connection-lost listeners, never reconnected). Never depends on the UI modules. |
| `alphatimer-heats` | Heat model, placing, `HeatService`, `RunningClock`, `LiveHeats`, the heat/lane DTOs and enums. Works on refined messages only; stores through the `HeatRepository` interface. No database. |
| `alphatimer-legacy-server` | alphatimer-compatible heats (`LegacyService`, `legacy.dto`) and the JDK `HttpServer` endpoints (`LegacyHttpServer`; its requests run on the bounded `HandlerPool`, which closes connections beyond its queue and those over the time limit). Stores `LaneTime`s through the `LaneTimeRepository` interface. No database. |
| `alphatimer-project-db` | The `.timing` project file: Hibernate on SQLite (`TimingDatabase`, entities), the save protocol of the project file (`TimingFileWriter`), the work folder with its work files, their locks and origin notes and the crash leftovers (`TimingWorkFolder`, `CrashLeftover`) and the stores that implement the repositories (`HeatStore`, `LaneTimeStore`) or write the recording (`MessageStore`, `RawDataRecorder`). `replay` (`FileReplayReader`: binary file or the raw data of a `.timing` file, paced like 9600 baud; reads the `rawdata` table with plain JDBC). The only module with Hibernate/SQLite. |
| `alphatimer-ui-core` | No Swing. The application layer: `settings` (`SerialMode` maps to a `SerialConfiguration`), `project` (`TimingDocument`: the open project with its database, project file and lock, unsaved state and save lock and its settings, kept in memory so the 1 s refresh reads them without the database lock; `AutoSaver`), `recording` (`RecordingSession` wires reader, parser, heats, stores and the legacy server; its `WriteListener` hears which `RecordedData` could not be stored, when it is stored again and how many bytes of it were dropped when the recording stopped; a lost serial connection stops it and is reported to its connection-lost listeners). |
| `alphatimer-ui-common` | What every front end shares, no Swing: `AppController` (document workflow), `SavePrompt` (the questions it asks, implemented per front end; `RecoveryTexts` builds the texts of the recovery questions), `ProjectFiles` (file extensions; the Save As path choice the prompts share: default extension, a folder is rejected, then the overwrite question), `I18n` with the `messages*.properties`, `FileFailure` (classifies the cause chain of a failed file action — not found, no permission, disk full, folder, corrupt, locked, other — so error texts name the reason and the file instead of the exception message), `AppStatus` (title, file, state text, warning summary and details, Start/Stop), `StatusWarnings` (the active status-bar warnings, each kind with its own clearing rule, all of them cleared when the document is replaced or closed; `WriteFailures` raises and clears the write-error warning; the lost serial connection is cleared by the next start), `SettingsFormRules` (required/editable fields, record button), `SerialPortTexts` (the hint below the port list that names the ports without access, and the info behind its icon: the group to join with its command, or that only a new login is missing), `ShownDocument` (a field being edited keeps its text on a refresh, but not when the document is replaced), `LaneRows`, `TimeFormat`, `MessageText`, `LogFileCleanup` (old log files, run by every launcher). |
| `alphatimer-ui` | Swing UI on the JUtils `JButtonBar` in the layout of the JAuswertung plugin window (`JAlphaTiming.createBody`: `SelectorScrollPane` with the bar left, 4 dlu gap, the panel right with a labeled border of its name, 5 px space around; window sized like `JPFrame`), `SwingSavePrompt`, `AlphaTimingLauncher`. Panels: "Settings & Recording" and "Live View" (lane rows built from `LaneRows`, no `JTable`). Builds `alphatiming.jar` with its dependencies in `target/lib/`. |
| `alphatimer-tui` | Text UI on Lanterna with the structure of the GUI (`TuiMainWindow`: menu bar, panel buttons left, status line; `TuiSettingsPanel`, `TuiLiveViewPanel`, `TuiSavePrompt` with text dialogs). All logic comes from `alphatimer-ui-common`; listeners go through `gui.getGUIThread().invokeLater`. Shortcuts are function keys (F2 save, F5/F6 panels, F10 exit), Ctrl+N/O, and Ctrl+C asks like Exit — never Ctrl+S/Ctrl+Q, which terminals use for flow control. Logs to the file only. Builds `alphatiming-tui.jar` with `target/lib/`. |
| `alphatimer-cli` | Command line recording into an existing project file (`RecordLauncher`, `RecordCommand`): opens it through `AppController` with the non-interactive `CliSavePrompt` (stops and saves without asking), records with its stored settings until `TerminalStopTrigger` (JLine raw mode: any key, Ctrl+C) or SIGTERM fires or the serial connection is lost, saves. Prints `MessageText` lines and finished heats (`FinishedHeats`). Exit codes in `ExitCode` (a lost connection has its own). Logs to the file only. Builds `alphatiming-cli.jar` with `target/lib/`. |

### Dependency Rules

- `alphatimer-*` copies → `alphatimer-serial-core`, `alphatimer-heats`, `alphatimer-legacy-server` → `alphatimer-project-db`
  → `alphatimer-ui-core` → `alphatimer-ui-common` → front ends (`alphatimer-ui`, `alphatimer-tui`, `alphatimer-cli`). Lower modules never depend on higher ones; `alphatimer-heats` and
  `alphatimer-legacy-server` never depend on the database (they define repository interfaces, project-db implements them).
- `alphatimer-ui-core` and `alphatimer-ui-common` must not depend on Swing. UI text lives in `alphatimer-ui-common`, dialogs
  in the front ends; core validation messages and exceptions are plain-English data.
- Logic that more than one front end needs (status texts, form rules, formatting) goes into `alphatimer-ui-common`, never
  into a front end's widgets.
- The copied modules change only where AlphaTiming needs it; keep such changes small and note them above.

## Swing Threading and Concurrency

- Swing components are only accessed on the EDT. Callbacks from the reader thread (`RecordingSession` listeners) must
  go through `SwingUtilities.invokeLater`, as `JAlphaTiming` does; `AppController` itself contains no Swing code and calls
  its listeners on whatever thread caused the change.
- All database access goes through `TimingDatabase.read/write` (one lock, one connection). Never open sessions on the
  side. Lock order: component locks (`RecordingSession`, `HeatService`, `RawDataRecorder`) first, the database lock
  second. Saves (Save, Save As, auto-save) run one after another on the save lock of `TimingDocument`, taken
  before the database lock; `TimingDatabase.snapshotTo` holds the database lock only while copying the database, so the
  recording keeps writing while `TimingFileWriter` moves the copy to the target. `TimingDocument` switches to a new file
  (Save As), records the work file's origin note and closes under the same save lock, so a running save finishes before
  the database closes. Its dirty flag is set by a write listener of `TimingDatabase`, which runs under the database lock.
- `AppController` pauses the `AutoSaver` while it asks the user about the document (stop recording, Save / Discard /
  Cancel, Save As path, recovery) and until a discarded document is closed; `pause()` waits for a running auto-save. If
  New or Open fails after Discard, it replaces the discarded document with the saved state of its file (a new project
  without a file; closes it if that fails too) before auto-save resumes.
- An exception inside the recording pipeline must never reach the reader thread (`RecordingSession.safe`); a failing
  listener must not affect the others.
- Replacing the document (New, Open, the crash recovery at program start) builds a Hibernate session factory and takes
  seconds, so the Swing UI runs these controller calls through `JAlphaTiming.runDocumentWork` on a `SwingWorker`: the
  window stays disabled with a wait cursor, is not refreshed and holds the commits of the settings form until the work
  is done (also after a failure). `SwingSavePrompt` hands each question to the EDT and waits for the answer, so the
  controller may ask from that worker. The TUI and the CLI call the controller synchronously. Saving stays on the EDT;
  it holds the database lock only for the `VACUUM INTO` of a small file. Do not add other long-running work to the EDT.
- Every thread, socket, REST port and file lock that a recording or a document opens must be released on stop/close;
  start → stop → start must be repeatable.

## Persistence and Compatibility

- A `.timing` project file (earlier versions saved `.heat`; the Open dialog still accepts it) is a SQLite file written through JPA/Hibernate (native bootstrap, no `persistence.xml`,
  `hibernate-community-dialects`). Tables: `settings`, `rawdata`, `aresmessage`, `lanetime`, `heat`, `lane`. Columns added later (e.g. the connection type in `settings`) are nullable and read as the earlier behavior (TCP), so files of earlier versions still open.
- Save (`TimingDocument`) = `TimingDatabase.snapshotTo`: under the database lock, `VACUUM INTO` a temp file next to the
  work file, flushed to disk; then, outside the database lock, `TimingFileWriter` moves that file to a temp file next to
  the target (across filesystems, where an atomic move fails, copied and flushed), renames the existing file to
  `<name>-previous.timing` (replacing an older one), moves the temp file into place and flushes the directory. The
  document is marked clean only if nothing was written since the `VACUUM INTO` (`snapshotTo` returns that write count,
  `ifUnchangedSince` checks it under the database lock).
  A failed save keeps the old file (at worst as `-previous`) and the dirty flag and removes its temp files. A target
  that is a folder is refused before anything is moved (`ProjectFileIsFolderException`).
  Open copies the file to `~/.alphatiming/work/` and rejects files that are not AlphaTiming projects.
- Work files are protected by an OS lock (`.lock` next to them); only unlocked ones are offered for crash recovery, so
  Open locks the new work file before it copies the project file into it. The
  same scan deletes the snapshot temp files of a save that crashed (`<work file>.<uuid>.tmp` whose work file is unlocked
  or gone); a snapshot of a locked work file stays, its program may be saving. The scan probes a lock by taking it for
  a moment, so a program locking its own work file retries briefly (`LockFile.acquireOwn`); project-file locks are never
  probed, so Open and Save As learn at once that another instance holds one. A
  `.project` note next to a work file names the project file it was last opened from or saved to, with that file's
  modification time (`TimingWorkFolder.Origin`). A recovered document keeps that project file only if it is unchanged since and
  its lock can be taken; otherwise Save would overwrite newer data, so it has no file.
- The project file of an open document is protected the same way (`wk.timing.lock`, `LockFile`), never by a lock on the
  file itself, which the renames of a save would break on Windows. Open and Save As refuse a file another instance holds
  (`ProjectFileInUseException`); Save As moves the lock, closing releases it. Only a held lock counts, so a lock file left
  behind by a crash blocks nothing. Whoever takes that lock (Open, a recovery that keeps its file) deletes a
  `<name>.timing.tmp` a crashed save left next to the file; without the lock it stays.
- **Users open files from earlier versions.** Version 1 is the first released version, so its files are the baseline and
  there are no migrations yet; they start with version 2.0.0. From then on every change to an entity (table or column)
  must keep older project files loadable; `hbm2ddl=update` only adds, it never migrates data. Renaming or removing a
  column or table requires an explicit migration. Prove compatibility with a test that opens a fixture file created by
  the previous version (`alphatimer-ui-core/src/test/resources`).
- Explicit migrations are `MigrationStep`s in `SchemaMigrations` (`alphatimer-project-db`; `STEPS` is still empty). The
  schema version is SQLite's `user_version`: the number of steps applied; `VACUUM INTO` keeps it. Opening (`openCopy`,
  `adopt`) runs every step above the file's version on the work copy, each in its own transaction, before Hibernate's
  `update` sees the file; a new file is created with the current version and gets no step. To add a step, append it to
  `SchemaMigrations.STEPS` (never reorder or change a released step) and add the fixture test. SQLite cannot drop a
  constraint or a column with ALTER TABLE; such a step rebuilds the table with `TableRebuild`.
- Enum attributes are stored as their name through an `EnumNameConverter` (`EnumNameConverters`), never with
  `@Enumerated`: Hibernate writes a CHECK with the enum's values for `@Enumerated`, so a value added later could not be
  stored in existing files.
- Hibernate's SQLite dialect silently skips foreign keys and unique keys over several columns, so `TimingDatabase`
  creates them itself for a new file: the unique key (event, heatNumber) of `heat` (`HeatEntity.UNIQUE_KEY`) and, by
  rebuilding `lane` (`LaneKeys.addTo`), `heat_id` not null, a foreign key to `heat` (no `ON DELETE` action: lanes are
  deleted before their heat) and the unique key (heat_id, number). The other tables have no
  keys besides their id: `settings` is read as its first row, `rawdata` and `aresmessage` record what arrived, and
  `lanetime` legitimately holds the same finish several times (one row per result message).
- Hibernate's connections enforce foreign keys (`foreign_keys`); the migration connection does not, as `TableRebuild`
  requires.
- The settings of the last opened/saved project are kept in `~/.alphatiming/defaults.json` (`DefaultSettingsStore`);
  unknown or corrupt content falls back to the built-in defaults.
- A project holds exactly one competition. Its key is a setting (`Settings.competitionKey`, the `competitionKey` column of
  `settings`), stored once per project file and required to start a recording; changing it applies to all data of the
  project, including what was recorded before. Recorded data is therefore not tagged with the key: a heat is identified
  by event and heat number only (`heat` has no competition column). The copied pipeline still tags messages with a
  competition, which `RecordingSession` fills with a constant; heats ignore it, `aresmessage.competitionKey` and
  `lanetime.competition` store it, and queries do not filter by it. Older project files keep their stored tags there;
  files written while the key was ignored have no key.
- The legacy REST format (`/legacy/heats.xml`, `/legacy/heats.json`) is consumed by JAuswertung; keep it unchanged.

## Validation

There is no server behind the UI, so the desktop application is the only line of defense. The device and the network are
untrusted input:

- Settings are validated by `Settings.validate()` (a list of `SettingsProblem`) before a recording starts; the UI surfaces the first problem at the
  fields and a failing start ends in the ERROR state, never in an exception dialog.
- A serial port that is missing or in use ends the start in the ERROR state (`SerialConnectionException`), never in a crash.
- nrjavaserial aborts the whole JVM natively on a port name of 54 bytes or more. Every name passes
  `SerialPorts.fitsDriver` (`MAX_NAME_BYTES`) before nrjavaserial sees it: `Settings.validate()`, `NrSerialReader.start()`
  and `SerialPorts.list()` reject longer ones. Never let a test open such a name inside the test JVM.
- Bytes from the device are validated by the copied parsers; unknown or garbled data is dropped and logged, never fatal.
- Files from disk are checked before use (`TimingDatabase.openCopy`, `DefaultSettingsStore.load`).
- The REST server is intentionally without authentication: it serves read-only heat data to JAuswertung on the local
  network, as alphatimer did. Do not add authentication unless JAuswertung supports it.

## Internationalisation

- The UI is available in English (base) and German. All user-visible text lives in
  `alphatimer-ui-common/src/main/resources/org/lisasp/alphatiming/ui/common/messages.properties` (English) and `messages_de.properties`
  and is looked up with `I18n.get(key, args…)` (`MessageFormat` placeholders `{0}`). The language follows the JVM's
  default locale (`-Duser.language=de` to force it); other languages fall back to English. Never hardcode UI text in Java.
- Write non-ASCII characters in the properties files as `\uXXXX` escapes. Both files must have identical keys and
  placeholders; `I18nTest` checks this, that every key used in code exists, and that no key is dead (it scans the sources
  of all front ends; add a new front end to its list). A key built
  dynamically (`"problem." + name`) needs its family enumerated in that test.
- Never put UI text into `alphatimer-ui-core`: core reports problems as data (`SettingsProblem`, `RecordingError`,
  `RecordedData`, `SerialConnectionException.Reason`, `InvalidProjectFileException`, `ProjectFileInUseException`) and
  `alphatimer-ui-common` turns them into text — with `I18n.describe(…)`, or for the project-file exceptions in
  `AppController`, which catches them by type.
- Numbers shown to the user follow the locale (`TimeFormat` uses the locale's decimal separator).

## Documentation and Issues

- `docs/superpowers/` holds the design spec and the implementation plan; `README.md` describes the usage.
- `docs/issues/` tracks open findings from code work. Each open issue is a self-contained file listed in
  `docs/issues/README.md`'s table. Once an issue is fixed and the fix is verified, delete its file and remove its row
  from the table — do not mark it "Fixed" and leave the file in place, and do not fold a summary of the fix into
  `README.md` either. The commit that fixes the issue is the record. Keep `docs/issues/README.md` even when its table is
  empty.
- When you find a problem outside the scope of the current task, add an issue there instead of fixing it in the same
  change.
- `docs/chores/` tracks planned clean-ups of code or data model that are not bugs, with the same rules: one file per
  chore, listed in `docs/chores/README.md`, deleted once done.
- `docs/features/` tracks planned features, with the same rules: one file per feature, listed in
  `docs/features/README.md`, deleted once implemented.
- Every row in the issue and chore tables has a severity (High/Medium/Low), every feature a priority (High/Medium/Low),
  and every row a size (S/M/L), as defined in the respective `README.md`; keep the rows ordered by severity or priority,
  then by size. Rate new entries when you add them.

## Build, Test, Run

- Sibling dependencies (jutils-legacy and its libraries) are installed to `~/.m2` by `../build-dependencies.sh`
  (extra args such as `-DskipTests` are passed to Maven). Rebuild `jutils-legacy` first whenever a change here relies on
  a change there; `jutils` is compiled for Java 25.
- Fast compile check: `mvn clean compile -DskipTests`
- Full build with tests: `mvn clean verify`. When finishing a branch, a call to `mvn verify` is required. Swing tests
  (`JAlphaTimingSmokeTest`) are skipped without a display: on a headless machine run `xvfb-run -a mvn clean verify`.
- Single module: `mvn -pl <module> -am test` (e.g. `mvn -pl alphatimer-ui-core -am test`)
- Single test class / method: `mvn -pl <module> -am test -Dtest=ClassName` / `-Dtest=ClassName#methodName` (add
  `-Dsurefire.failIfNoSpecifiedTests=false` when `-am` pulls in modules without that test)
- Mutation testing (PIT, profile `mutation`): `mvn -Pmutation -pl <module> -am clean test-compile
  org.pitest:pitest-maven:mutationCoverage`; the report is in `<module>/target/pit-reports/`. Only AlphaTiming's own
  classes (`org.lisasp.alphatiming.*`) are mutated, not the copied `alphatimer-*` modules. Narrow a run with
  `-DtargetClasses=<classes> -DtargetTests=<tests>`.
- Run: `java -jar alphatimer-ui/target/alphatiming.jar` (after `mvn package`); state lives in `~/.alphatiming/`
  (`defaults.json`, `work/`). Main class `org.lisasp.alphatiming.ui.AlphaTimingLauncher`.
- Text UI: `java -jar alphatimer-tui/target/alphatiming-tui.jar`; main class `org.lisasp.alphatiming.tui.TuiLauncher`.
  Tests render on Lanterna's `DefaultVirtualTerminal` (no display needed); a modal text dialog blocks a test, so tests
  give the controller a prompt that never asks.
- Command line: `java -jar alphatimer-cli/target/alphatiming-cli.jar <file.timing>`; main class
  `org.lisasp.alphatiming.cli.RecordLauncher`.
- `alphatiming.jar` references its dependencies in `lib/` through the manifest class path (no fat jar); keep `lib/`
  next to the jar when copying it.
- Captured event data (`data/*.dat`, ignored by Git) contains personal data: never commit it or paste its content.
  Use it only locally, e.g. replayed through a fake TCP server; tests use the sample bytes in
  `alphatimer-project-db/src/test/java/org/lisasp/alphatiming/project/db/testdata/`.

## Tests

- JUnit 5 (Jupiter) with AssertJ and Awaitility. Name test classes `*Test`; there are no integration-test conventions —
  tests that need a database use `TimingDatabase.createNew(@TempDir)`, tests that need a device use a local
  `ServerSocket` or a fake `SerialPortReader`. `HeatServiceTest` and `LegacyServiceTest` run against the real stores and
  therefore live in `alphatimer-project-db`.
- Shared test helpers are published as test-jars: `TestMessageCreator` from `alphatimer-heats`,
  `DataHandlingMessageTestData` from `alphatimer-project-db`.
- Prefer tests on the non-Swing modules and `AppController`; keep heat, placing and clock logic and the document workflow
  out of Swing classes so they can be tested without a GUI. Swing classes get a smoke test at most.
- Tests resolve fixtures relative to the module directory (`src/test/resources/...`); keep it that way.

## Coding Standards

- **Comments**: do not write a comment unless it provides benefit on its own — standing alone, with no other context,
  it must tell the reader something they could not already get from the code around it (names, types, structure, a
  nearby signature). A comment that restates what the code does, restates a parameter's name/type/default in prose, or
  explains something inferable in a few seconds of reading does not clear this bar. The only comments that clear it: a
  hidden constraint, a subtle invariant, or a workaround for a specific bug that isn't obvious from the code itself.
  When in doubt, don't add the comment. This is a hard rule, not a default that yields to matching a file's existing
  style — a comment already present in a file is not evidence it belongs there. Whenever you touch a file or area of
  code, also remove any existing comment in that area that fails this test.
- **PTDD (Predictive TDD)**: follow Red-Green-Refactor, but make two predictions explicit. Before writing a test, state
  precisely what the code should do and how you'll observe it (e.g. Given-When-Then). Before running a newly written or
  adjusted test, predict exactly how it will fail; if the actual failure differs, or the test passes unexpectedly, stop
  and investigate before writing any implementation — the mismatch means a wrong assumption, a broken test setup, or a
  missing dependency. Once the failure matches the prediction, implement the minimal change that turns it green, then
  tighten assertions in small increments across further red-green cycles. Source: https://ted.dev/tag/ptdd/.
- **Working approach**: keep changes localized to the requested feature or defect — no unrelated refactorings in the
  same change. Follow the dominant pattern already present in the code being touched. Preserve the project file format,
  the legacy REST format and public APIs unless the task explicitly requires changing them. When in doubt, make the
  smallest change that satisfies the requirement — but escalate to the stricter rule when the concern is data integrity
  of a running recording (no lost bytes, no corrupt project file) or correctness of times and places.
- **Language**: identifiers are English and follow the timing vocabulary already used (`heat`, `lane`, `event`,
  `competitionKey`, `place`, `penalty`, `split`/`finish`); reuse those terms instead of introducing synonyms. The
  `alphatimer-*` copies keep their own names.
- **Commit messages**: write them in German, as in JAuswertung. The body is a brief bullet list of at most 3–4 one-line
  bullets. Focus on intention — why the change was made and what behavior it affects — not mechanics visible in the
  diff. Don't mention tests that accompany a code change; do state it when a commit only adds or changes tests.
  Each affected repository gets its own commit (see the workspace guide).

## Class Design

- One primary responsibility per class; split classes when responsibilities drift (e.g. a panel that also computes
  placings or writes files). Keep domain logic out of Swing components.
- File dialogs go through `FileChooserUtils` (the native dialog of the platform, installed by
  `PlatformFileChooser.initialize()` in `AlphaTimingLauncher`, as in JAuswertung); do not create a `JFileChooser` directly.
- Swing: never set preferred, minimum or maximum sizes of components. Let layouts and natural sizes decide; steer widths
  with text field columns, `FormLayout` column specs and layout constraints instead.
- Prefer composition over large utility or god classes; keep methods short and intention-revealing.
- Use records for new immutable value and DTO types (`Settings`, `HeatDto`, `LaneDto`). Lombok is available (`@Slf4j`,
  `@Value`, `@Getter`, `@RequiredArgsConstructor`); prefer constructor injection of collaborators.
- Do not introduce new static mutable state or manual singletons (`getInstance()`, static instance holders); pass
  collaborators explicitly. A recording works on a settings snapshot (`RecordingSession.start(Settings)`), never on
  shared mutable settings.

## Exceptions

- Throw specific exceptions named after the concept and the problem rather than generic `RuntimeException`.
- Exceptions carry data only; text shown to the user is built in the dialog or panel that shows it.
- Do not swallow exceptions or end with `printStackTrace()`. Either handle the failure — and tell the user in the UI
  when an action they triggered failed — or propagate it to the code that can.
- Wrap only when the abstraction level changes (e.g. a Hibernate failure while saving becomes an `IOException` naming the
  file).

## Logging

- SLF4J with Logback, configured like JAuswertung (`alphatimer-ui/src/main/resources/logback.xml`): console plus one
  file per program start, `~/.alphatiming/logs/alphatiming-<timestamp>.log` (TUI/CLI: `alphatiming-tui-…`/`alphatiming-cli-…`,
  file only); root level DEBUG, Hibernate INFO. Each launcher runs `LogFileCleanup` at start: it deletes the program's own
  log files older than 30 days except its 10 newest, and only logs a WARN if that fails. Tests use
  `logback-test.xml` (console only) so they never write into the home directory. `AlphaTimingLauncher` logs uncaught exceptions of all
  threads. Use Lombok's `@Slf4j` in new code; do not add `System.out.println` or `printStackTrace()`.
- `DEBUG` for diagnostic detail, `INFO` sparingly for lifecycle events (recording started/stopped, file opened/saved),
  `WARN` for handled unexpected situations (dropped messages, failed auto-save), `ERROR` for real failures.
- Timing data and project files contain personal data (names, clubs, times). Do not log whole messages beyond what the
  copied pipeline already does, and never log credentials; log ids, file names and counts instead.
- Log a stack trace once, at the place that handles or reports the failure.
