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
     AlphaServer). Port names longer than 48 bytes are not offered and cannot be recorded, since the serial driver
     cannot open them; use a shorter path or link. If the port is lost (e.g. the USB adapter is unplugged), the recording stops, keeps what it recorded
     and the status bar warns until the recording is started again by hand; TCP reconnects by itself instead,
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
  periodically for projects that already have a file, but never while a question about the project or the Save As
  dialog is open, so changes the user then discards do not reach the file.
- **File → Save As** adds `.timing` to a name typed without it (a name ending in `.heat` is kept) and asks before
  overwriting the file that will actually be written.
- Each save keeps the state before it as `<name>-previous.timing` next to the project file (replacing an older one). Open
  it like any project to go back one save.
- Closing (or New/Open) during a recording first asks whether to stop it; Cancel keeps the recording running and the
  project open. Then Save / Discard / Cancel is asked when there are unsaved changes (Cancel keeps the project open, the
  recording stays stopped).
- A failed auto-save or data a recording could not store (raw data, messages, heats, lane times; e.g. a full disk) shows
  a red warning in the status bar; with several, the most important one and the number of the others. Its tooltip (in the
  text UI: the message log on *Settings & Recording*) names what failed and why. An auto-save warning disappears with the
  next successful save; a recording warning once that data has been stored again and the project saved afterwards.
  A lost serial connection shows such a warning too, until the recording is started again.
- After a crash the next start offers to recover the leftover work file and names its project file and the time it was
  last changed. A recovered project keeps its project file unless that file was saved since or is open elsewhere (then
  use Save As). Discard asks a second time before it deletes the data; Back returns to the recovery question. If the
  data cannot be recovered, an error names the reason and the question comes back, so the data can be discarded or
  kept for a later start.
- A project file can be open in only one running AlphaTiming (window, text UI or command line) at a time; another one
  refuses to open it or to save over it. While it is open, a `<name>.timing.lock` file lies next to it.
- The settings of the last opened/saved project are kept in `~/.alphatiming/defaults.json` and used for new projects and
  at program start.

## Log files

Every program start writes a log file `~/.alphatiming/logs/alphatiming-<date-time>.log` (and to the console); the
text UI and the command line program write `alphatiming-tui-<date-time>.log` / `alphatiming-cli-<date-time>.log` and
nothing to the console. Attach it when reporting a problem. At its start, each program deletes its own log files that
are older than 30 days, but always keeps its 10 newest ones.

## REST endpoints

While recording (server toggle on): `GET /legacy/heats.xml` and `GET /legacy/heats.json` — the same data as alphatimer
(finish times per heat and lane). The server answers up to 4 requests at a time and keeps up to 16 more waiting.
Further connections are closed at once, as is a connection whose request and response take longer than 10 seconds.

The endpoints have no authentication on purpose: they serve read-only data to JAuswertung on the local network, as
alphatimer did. Enable the server only on a network you trust.

## Modules

See `AGENTS.md`.
