AlphaTiming ${project.version}

Requires Java 25 or newer (from JAVA_HOME, otherwise from PATH) on Linux or macOS (Intel).

  ./alphatiming.sh                       the window
  ./alphatiming-tui.sh                   the text UI in this terminal
  ./alphatiming-cli.sh <file.timing>     records into an existing project file without a window

AlphaTiming keeps its settings, work files and log files in ~/.alphatiming/ (logs in ~/.alphatiming/logs/).
If a start script cannot find a suitable Java, it says so and exits with code 127.

Documentation: https://github.com/LISASP-org/alphatimer
