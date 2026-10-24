# Heat-Timing — Design

## Purpose

Heat-Timing is a Swing desktop application that reads the data of a Quantum/Ares timekeeping system over TCP, stores
the incoming raw data and the interpreted results, and shows the current heat live. It reuses the core logic of the
Spring Boot application `alphatimer` (copied, no dependency on it), but is a document-based desktop app: one project
file per competition, settings editable in the UI without a restart.

Workflow: stop recording → change settings → start recording.

## Decisions (from the discussion)

- Plain Java (21) Swing app, **no Spring / no Spring Boot**. Wiring by hand in `main`.
- Persistence: **JPA with Hibernate**. Project file = **one SQLite file** (`*.heat`) holding settings, raw data,
  interpreted messages and heats. SQLite via `hibernate-community-dialects` + `sqlite-jdbc`.
- Working database while a project is open: a temporary SQLite database. **Save** writes a consistent snapshot to the
  project file (`VACUUM INTO` a temp file next to the target, then atomic rename).
- Dirty tracking, periodic auto-save (when dirty and the project has a file; interval is a setting, default 60 s),
  explicit Save / Save As.
- Closing (window close, File → Exit, and also File → New / Open): while a recording runs, a dialog first asks whether
  to **stop the recording**; Cancel returns to the app with the recording still running. Only after the user confirmed,
  the recording stops, and then, if the document is dirty, a dialog offers Save / Discard / Cancel. Cancel there returns
  to the app (recording stays stopped). The command line stops its recording itself and is never asked.
- Default settings: the settings of the last loaded/saved project are stored in `~/.heat-timing/defaults.json` and used
  when the program starts and when a new project is created.
- Main window: JAuswertung-style left button bar with two panels, "Settings & Recording" (host, port, other
  settings, Start/Stop) and "Live View".
- Current-heat display (see below).
- The `/legacy/heats.xml` and `/legacy/heats.json` endpoints of alphatimer are kept through the JDK
  `com.sun.net.httpserver.HttpServer` (started with the recording, port is a setting), so JAuswertung can still pull
  the data. (Assumption — can be dropped.)
- Artemis/JMS configuration of alphatimer is not carried over (unused by its Java code).

## Project and modules

New repository `Heat-Timing` (Maven multi-module, group `org.lisasp.heattiming`, Java 25 (required by jutils-legacy)):

| Module | Content |
| --- | --- |
| `ares-serial-api`, `ares-serial`, `refinedmessages-api`, `refinedmessages`, `serial-api`, `serial-tcp` | Copied from alphatimer: protocol messages, input collector/converter/aggregator, message refiner, `SerialPortReader`, `TcpReader`. `ares-lst(-api)` only if required by the others. |
| `heattiming-core` | Settings, project file (JPA entities, repositories, save/open/recover), recording session, heat model, REST endpoint. No Swing. |
| `heattiming-app` | Swing UI and `main`. Depends on `jutils-legacy` (`jutils-swing` for `JButtonBar` and Swing helpers, further `jutils-*` modules as needed) and their transitive dependencies (jgoodies-forms, flatlaf, …), installed in `~/.m2` by the workspace build. |

alphatimer's `messagesstorage`, `heats` and `legacy` modules are not copied as modules; their logic moves into
`heattiming-core` on plain JPA. The legacy DTO/XML format of the endpoints is preserved.

Changes to copied code: `TcpReader` must not start its thread in the constructor (explicit `start()`), and
`close()` must be safe when never connected (currently NPE) and repeatable, so a session can be started and stopped
many times. `close()` waits (bounded) until the reader thread has ended, so the bytes it already read reach the
consumers before the session closes the raw data recorder, and a connect in progress is abandoned.

## Core components

- **`Settings`** (immutable record): TCP host, TCP port, auto-save interval, server port with an on/off switch (the competition key of alphatimer was dropped: a project is one
  competition). Validation: non-blank host, ports 1–65535. Stored in the project file; defaults in `defaults.json`.
- **`ProjectFile` / `ProjectDocument`**: owns the working database (EntityManagerFactory), the current file path
  (optional), the dirty flag and listeners. Operations: `create()`, `open(path)`, `save()`, `saveAs(path)`, `close()`.
  Any write (incoming data, settings change) sets dirty. After a crash the temp working database remains; at next start
  the app offers to recover it, naming the project file the work file was last opened from or saved to (a
  `<work file>.project` note next to it) and its last change; discarding needs a second confirmation. While a document has a project file, it holds an OS lock on `<file>.lock` next to it, so
  a second program instance refuses to open the file or to save over it.
- **`RecordingSession`**: `start()` snapshots the settings and builds a fresh pipeline (`TcpReader` → raw-data
  storage + `InputCollector` → `MessageConverter` → aggregator → message storage, refiner, heat service); `stop()`
  closes it. State: STOPPED / RUNNING / ERROR, reported to the UI via listener. Settings are read-only in the UI while
  RUNNING. `start()` failures (invalid settings, REST port in use) produce an error state with a message, not a crash.
  The TCP reader keeps reconnecting while running; the UI shows connected/disconnected.
- **Entities**: `RawDataChunk` (timestamp, bytes), `AresMessage` (as in alphatimer), `HeatEntity`, `LaneEntity`
  (adds `place`; one time field only), `SettingsEntity` (single row).
- **Heat service**: as alphatimer's `HeatService`/`Heat`/`Lane`, extended with `place` and the heat start timestamp.
  Placing is computed from the final times of the finished lanes (ties share a place) and updates whenever a lane
  finishes; the device's rank data is ignored because it is provisional while the race runs and was found to be
  inconsistent with the times in captured data. Repeated results of previous races (RefinedMessageType
  PreviousRaceResult) are ignored by the heat service so they cannot re-select or change an older heat.
  A deleted time (time type DeletedTime; the device sends a deleted final time as a split time of the last lap) clears
  the lane's time, finish and place if it is the lane's current time, and lets a stopped clock run again; it is never
  applied as a time.

## UI

Main window follows the JAuswertung layout: a **button bar on the left** (`de.df.jutils.gui.JButtonBar` from
`jutils-swing`; not the l2fprod one) and
the **content on the right**; selecting a button switches the content panel (CardLayout, driven like JAuswertung's
`PanelSelector`; the full jutils plugin framework is not used). Two panels:

1. **Settings & Recording**: connection type (a combo box: TCP, a file replay of a binary file or another `.heat` file at 9600 baud, or a serial port with protocol ARES21 7E1 / Quantum 8N1 as in AlphaServer), TCP host, TCP port, auto-save interval, server port with on/off toggle (all locked while
   recording), the **Start/Stop** button, state indicator (stopped / running / error with message) and connection
   state. Start and Stop therefore live on this panel, not in a global toolbar.
2. **Live View** (current-heat display): header with event, heat, heat status (Open/Started/Finished) and the
   **running time**; below a lane table with one row per used lane: lane number, **one time** (the latest
   intermediate time, replaced by the final time when the lane finishes; a lap indicator is shown for intermediates),
   penalty (DNS/DNF), placing. Active lanes are highlighted. Always shows the newest heat; browsing older heats is out
   of scope.

Below the content: status bar with recording state, last message time, file name and dirty marker. Menus: File (New,
Open…, Save, Save As…, Exit) and Recording (Start, Stop). Panel switching does not affect recording; the Live View
panel keeps updating while hidden.

**Running time**: alphatimer does not interpret the device's running-time messages, so the clock is computed locally
from the heat start timestamp (UI timer, ~100 ms repaint). Every live start signal restarts it (repeated results and corrected or deleted starts do not); it is frozen at the official end or as soon as every used lane has a final time or a penalty (captured data often lacks the official end). It can differ slightly from the
device clock; the final time from the device is authoritative.

All UI updates run on the EDT; pipeline callbacks marshal via `SwingUtilities.invokeLater`.

## Error handling

- Invalid/garbled device data: handled by the copied parsers (dropped messages are logged, never fatal).
- Connection loss: reader reconnects with back-off; the state shows "disconnected"; recording remains RUNNING.
- Save failures (disk full, permissions): error dialog, document stays dirty, previous file untouched (atomic rename).
- Corrupt/unknown project file on Open: error dialog, current document unchanged.
- Auto-save failure: status-bar warning, retried at the next interval.

## Testing

- Unit tests (JUnit): settings validation, heat/lane logic incl. placing and the running-time computation,
  dirty-state transitions, `defaults.json` handling.
- Integration tests: save/open round trip of a project file (all entity types), crash-recovery detection, a
  `RecordingSession` against a local fake TCP server replaying captured `.serial` data from alphatimer's test data;
  start/stop/start repeatability; REST endpoint output compared to alphatimer's legacy format.
- Swing: UI logic kept in presenter/model classes tested without a display; a short manual check list for the window.

## First implementation task (risk)

Prove that Hibernate + SQLite (`hibernate-community-dialects`) handles the entities and `VACUUM INTO` snapshotting.
If not workable, fall back to H2 (in-memory working DB, `SCRIPT`/`RUNSCRIPT` as file format) and report back before
continuing.

## Out of scope

Browsing past heats, multi-device input, serial (COM) input, authentication on the REST endpoint, an installer.

## Internationalisation (added later)

The UI is localized in English and German through resource bundles in `heattiming-app`; `heattiming-core` reports
problems as data and contains no UI text. The language follows the JVM default locale.

## Live View with two heats (added later)

The Live View shows the live heat on top and, below it, the last state of the heat that was active before it
(`LiveHeats(current, previous)`: the most recently changed heat and the one changed before it). The message log shows the
interpreted messages and not the raw lines; pings only switch a green marker (dark/bright green) at the left end of the status bar, sized to the status bar font.

## Running time sync (added later)

Every live split or finish time carries the race time of the device and, as message timestamp, its arrival on the local
clock. The start of the heat is set to `timestamp − race time` with every such message, so the running clock follows the
device clock instead of drifting from the local arrival of the start message. Repeats of results (message type
CurrentRaceResult), deleted times and times after the official end are not used for this.

### Running time model

The running time is `offset + (now − reference)`. A start signal sets the reference to the arrival time of the message
with offset 0; an intermediate or final time sets the reference to its arrival time with the device time as offset. The
heat stores this as `started = reference − offset`, which gives the same values. `RunningTimeModelTest` pins the rules.

### The clock cannot run on from a stale reference

The clock only counts up while a recording is running and while the reference is fresh. Every message applied to a heat
stores its arrival time as `lastMessage` (in the project file as well). The clock stands still at the value of the last
message (`lastMessage − started`) when nothing is being recorded (e.g. a heat restored from a file, or a replay that has
ended) and when no message of the heat arrived for 5 minutes (`RunningClock.STALE_AFTER`). It also stands still at the
end of a heat (official end, or every used lane has a final time or a penalty). Older project files without `lastMessage`
show 0 for an unfinished heat.
