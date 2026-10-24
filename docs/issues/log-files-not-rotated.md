# Old log files are never deleted

`logback.xml` writes one log file per program start (`~/.alphatiming/logs/alphatiming-<timestamp>.log`), like JAuswertung, and nothing removes old ones.

- **Effect:** the log directory grows by one file per start, and by the DEBUG output of a whole recording each time.
- **Where:** `alphatimer-ui/src/main/resources/logback.xml`.
- **Idea:** delete log files older than a number of days at startup, or switch to a `RollingFileAppender` with `maxHistory`.
