# Backup times do not become official

ARES marks a time from the backup buttons with time info `B` (`TimeInfo.Backup`), in the results it sends "With
backup" (ARES manual §9, message types 4 and 6) and when the operator takes the backup time for a lane (§5.3.10 "Take
Backup time"). The refiner now keeps them: `BackupTimeParser` refines message type 7 (`MessageType.BackupTime`) and
times with time info `B` into `BackupTimeMessage` (`KindOfTime.Backup`), and the heat stores them as `TimeKind.Backup`
beside the finish of the lane (`LaneDto.backups()`), where they never count and the lane rows show them.

The recordings in `data/` contain message type 7 with kind of time `B` and time type `1`, rank 0, for single lanes on
their last lap, sent after all finish times and right before the official end of the heat. It occurs 5 times, for one to
three lanes of a heat, never for all of them:

| Recording | Heat | Lane | Touchpad finish | Type 7 time |
| --- | --- | --- | --- | --- |
| `binary-COM4.dat` | event 6, heat 2 (1 lap) | 6, 2, 4 | 13.63, 12.10, 14.27 | 13.71, 12.11, 14.33 |
| `binary-COM4.dat` | event 7, heat 1 (4 laps) | 2 | 2:31.29 | 2:31.41 |
| `dem2023-2023-05-07.serial` | event 25, heat 4 (4 laps) | 8 | 2:52.03 | 2:46.62 |

Mostly the type 7 time is a little later than the touchpad time, as a pressed button is. In event 25 heat 4 the button
was 5.41 s earlier than the touchpad, which fits a touch too soft for the pad; the touchpad time stayed the result, no
correction followed.

- **Effect:** when the operator takes the backup time for a lane whose touch was too soft, that time stays a backup
  beside the finish; the lane keeps the touchpad finish or none, and JAuswertung does not get the backup time.
- **Open question:** when does a backup time become official? It is official only when the operator took it for the
  lane. Type 7 is additional information, not such a decision. How a backup time the operator took is marked is still
  not in the recordings; it needs a heat where "Take Backup time" was used.
- **Where:** `alphatimer-heats` (`LaneDto.current()`, which ignores backup times), `alphatimer-refinedmessages`.
- **Idea:** once the recorded data shows how a taken backup time is marked, refine exactly that case as a corrected time
  that replaces the finish.
