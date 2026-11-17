# Backup times are not interpreted

ARES sends the results with backup times when the operator checks "With backup" (ARES manual §9, message types 4 and
6, `CurrentRaceResultsWithBackupTimes`, `PreviousRaceResultsWithBackupTimes`), and marks a time from the backup buttons
with time info `B` (`TimeInfo.Backup`). The refiner handles neither: messages of types 4 and 6 are dropped completely,
and a time with time info `B` is dropped by `TimeParser`.

- **Effect:** with "With backup" checked, the result messages of a heat are lost; only the live times reach the heat.
  When the operator takes the backup time for a lane whose touch was too soft (§5.3.10 "Take Backup time"), that time
  never arrives.
- **Open question:** a result with backup times carries the backup time in addition to the touchpad time. Refined as an
  ordinary time it would overwrite the official time, so it must be told apart: a backup time is official only when
  the operator took it for the lane. Which fields mark that case is not in the manual; it needs recorded data of a heat
  with "With backup" checked and a backup time taken.
- **Where:** `alphatimer-refinedmessages` (`TimeParser`, `StartParser`, `TakeOverTimeParser`, `EnumUtils`); the tests
  for message types 4 and 6 in `TimeMessageTest`, `StartMessageTest` and `TakeOverTimeMessageTest` already expect them
  to be refined like the results without backup times.
- **Idea:** refine types 4 and 6 like 3 and 5 for the touchpad times; keep backup times out of the heat until the
  recorded data shows how a taken backup time is marked.
