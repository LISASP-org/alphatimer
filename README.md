# AlphaTiming

Desktop application that reads the data of a Quantum/Ares timekeeping system over TCP or a serial port, stores the raw data and the
interpreted results in one project file and shows the current heat live (lanes, one time per lane, penalties, placing,
running time).

It reuses the core logic of the Spring Boot application *alphatimer* (protocol parsing, message refining, heat logic;
copied, no dependency on it), but is a plain Swing app: settings that alphatimer reads from its `application.yaml` are
edited in the UI and can be changed without restarting.

## Language

The UI is available in English and German and follows the system language. To force a language start the jar with
`java -Duser.language=de -jar …` (or `en`). Other languages show English.

## Build and run

Requires Java 25 and Maven. The Swing UI uses `jutils-swing` (`de.dennisfabri.jutils`, installed into `~/.m2` by the
workspace's `build-dependencies.sh`).

```bash
mvn clean install
java -jar alphatimer-ui/target/alphatiming.jar
```

## Workflow

1. **Settings & Recording** panel: choose the input in the *Connection type* list:
   - **TCP** (host/port of the timekeeping system),
   - **Serial port** (port from the list or typed, protocol ARES21 = 9600 baud 7E1 or Quantum = 9600 baud 8N1, as in
     AlphaServer),
   - **File replay**: a pure binary data file (e.g. a recorded `.serial` file) or another AlphaTiming project (`.timing`,
     its stored raw data is used). The data is replayed at 9600 baud (960 bytes per second) and the recording stops by
     itself at the end of the file.

   Also set the competition key (required; one per project file, a change applies to all of its data), the auto-save
   interval and the server port with its on/off toggle.
2. **Start recording.** The settings are locked while recording.
3. **Live View** panel shows the live heat on top (with its running time) and, below it, the last state of the heat that
   was active before (times and places). A lane that was used in any heat keeps its row, without a time, while it is
   unused. It keeps updating while recording continues in the background. The marker at the left end of the status bar
   switches between dark and bright green with every ping of the timekeeping system.
4. To change settings: **Stop recording**, edit, **Start recording** again.

## Text UI

`alphatiming-tui.jar` offers the same panels as the window in a terminal (Linux, macOS; on Windows Lanterna opens its own
terminal window unless it runs in WSL, Cygwin or Git Bash):

```bash
java -jar alphatimer-tui/target/alphatiming-tui.jar
```

Keys: Tab / arrow keys move between fields, Enter activates; F5 / F6 switch between *Settings & Recording* and
*Live View*, F2 saves, F10 or Ctrl+C exits (asking about unsaved changes), Ctrl+N / Ctrl+O create or open a project.
The menu bar at the top is reached with the arrow keys.

## Command line recording

`alphatiming-cli.jar` records into an existing project file without a window, e.g. on a machine that only collects the
data:

```bash
java -jar alphatimer-cli/target/alphatiming-cli.jar competition.timing
```

It uses the settings stored in the project file (create and configure the project in the UI first), prints the
received messages and the result of every finished heat, and records until a key is pressed (or Ctrl+C / SIGTERM).
Then it saves the file. A replay stops by itself at the end of the data. Auto-save runs as in the UI. Exit codes: 0 =
saved, 1 = wrong arguments, 2 = the file could not be opened, 3 = the recording could not start (e.g. invalid
settings, serial port missing), 4 = saving failed. Without a terminal (input redirected) only Ctrl+C / SIGTERM end it.

## Project file

One SQLite file (`*.timing`; files of earlier versions named `*.heat` still open; written through JPA/Hibernate) holds the settings, the raw bytes received, the interpreted
ARES messages and the heats. While a project is open, the app works on a temporary copy in `~/.alphatiming/work/`:

- **File → Save** writes a consistent snapshot of that copy (atomic replace of the `.timing` file); auto-save does the same
  periodically for projects that already have a file.
- Closing (or New/Open) stops the recording first and asks Save / Discard / Cancel when there are unsaved changes.
- After a crash the next start offers to recover the leftover work file.
- The settings of the last opened/saved project are kept in `~/.alphatiming/defaults.json` and used for new projects and
  at program start.

## Log files

Every program start writes a log file `~/.alphatiming/logs/alphatiming-<date-time>.log` (and to the console); the
text UI and the command line program write `alphatiming-tui-<date-time>.log` / `alphatiming-cli-<date-time>.log` and
nothing to the console. Attach it
when reporting a problem. Old log files are not deleted automatically.

## REST endpoints

While recording (server toggle on): `GET /legacy/heats.xml` and `GET /legacy/heats.json` — the same data as alphatimer
(finish times per heat and lane).

## Modules

See `AGENTS.md`.
