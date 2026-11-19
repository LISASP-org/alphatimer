# Backup times are not interpreted

ARES marks a time from the backup buttons with time info `B` (`TimeInfo.Backup`), in the results it sends "With
backup" (ARES manual §9, message types 4 and 6) and when the operator takes the backup time for a lane (§5.3.10 "Take
Backup time"). `TimeParser` drops every time with time info `B`; the touchpad times of the results with backup times
are refined like those of the ordinary results.

The recordings in `data/` contain a third form: message type 7 (`MessageType.BackupTime`) with kind of time `B` and
time type `1`, rank 0, for single lanes on their last lap, sent after all finish times and right before the official
end of the heat. It occurs 5 times, for one to three lanes of a heat, never for all of them:

| Recording | Heat | Lane | Touchpad finish | Type 7 time |
| --- | --- | --- | --- | --- |
| `binary-COM4.dat` | event 6, heat 2 (1 lap) | 6, 2, 4 | 13.63, 12.10, 14.27 | 13.71, 12.11, 14.33 |
| `binary-COM4.dat` | event 7, heat 1 (4 laps) | 2 | 2:31.29 | 2:31.41 |
| `dem2023-2023-05-07.serial` | event 25, heat 4 (4 laps) | 8 | 2:52.03 | 2:46.62 |

Mostly the type 7 time is a little later than the touchpad time, as a pressed button is. Kind `B` matches the source
`B` "manual pushbutton backup" of the event list (§7); time type `1` probably is the number of buttons the official
backup time is taken from (§3.1.1: one, two or three buttons). In event 25 heat 4 the button was 5.41 s earlier than
the touchpad, which fits a touch too soft for the pad; the touchpad time stayed the result, no correction followed. The
refiner drops type 7 (`DroppedUnknownMessage`), which is right as long as it is only additional information.

- **Effect:** when the operator takes the backup time for a lane whose touch was too soft, that time never reaches the
  heat; the lane keeps no finish time. The backup times of type 7 are not shown, although a large difference to the
  touchpad time (as in event 25 heat 4) points to a lane whose result should be checked.
- **Open question:** a result with backup times carries the backup time in addition to the touchpad time. Refined as an
  ordinary time it would overwrite the official time, so it must be told apart: a backup time is official only when
  the operator took it for the lane. Type 7 is additional information, not such a decision. How a backup time the
  operator took is marked is still not in the recordings; it needs a heat where "Take Backup time" was used.
- **Where:** `alphatimer-refinedmessages` (`TimeParser.isAcceptedTime`, no parser for type 7).
- **Idea:** refine type 7 into its own message (lane, lap, time) and show the difference to the touchpad time, without
  changing the lane's time; once the recorded data shows how a taken backup time is marked, accept exactly that case and
  refine it as a corrected time.
