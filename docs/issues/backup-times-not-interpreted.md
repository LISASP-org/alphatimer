# Backup times are not interpreted

ARES marks a time from the backup buttons with time info `B` (`TimeInfo.Backup`), in the results it sends "With
backup" (ARES manual §9, message types 4 and 6) and when the operator takes the backup time for a lane (§5.3.10 "Take
Backup time"). `TimeParser` drops every time with time info `B`; the touchpad times of the results with backup times
are refined like those of the ordinary results.

- **Effect:** when the operator takes the backup time for a lane whose touch was too soft, that time never reaches the
  heat; the lane keeps no finish time.
- **Open question:** a result with backup times carries the backup time in addition to the touchpad time. Refined as an
  ordinary time it would overwrite the official time, so it must be told apart: a backup time is official only when
  the operator took it for the lane. Which fields mark that case is not in the manual; it needs recorded data of a heat
  with "With backup" checked and a backup time taken.
- **Where:** `alphatimer-refinedmessages` (`TimeParser.isAcceptedTime`).
- **Idea:** once the recorded data shows how a taken backup time is marked, accept exactly that case and refine it as a
  corrected time.
