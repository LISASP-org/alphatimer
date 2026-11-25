# The first time of a heat is lost when it opens the heat

A heat that the heat model first learns of through a time is created with every lane unused (`HeatDto.empty`).
`Lane.touch` stores the time and then calls `enforceStatus()`, which clears the times of an unused lane. The
`UsedLanesMessage` that would mark the lane as used is sent by the refiner after the `TimeMessage` of the same ARES
message, so it comes too late for that time.

It happens whenever the first message of a heat is a time: the heat number was changed after the ready-to-start (in
`dem2024-2024-04-13.serial` `30/5 ReadyToStart` is followed directly by `30/9 Finish lane 4`), or a recording starts
during the heat (the other two cases: the finish is the first message of the file). Found by comparing the results entered in JAuswertung (`data/wk`) with the
recordings; in each case the lost time is the winner's finish, the first message of the heat:

| Recording | Heat | Lane | Lost finish | Entered in JAuswertung |
| --- | --- | --- | --- | --- |
| `dem2024-2024-04-13.serial` | event 30, heat 9 | 4 | 32.14 | 32.14 |
| `dp2023-2023-11-24.serial` | event 70, heat 1 | 7 | 33.43 | 33.43 |
| `dp2024-2024-11-22.serial` | event 50, heat 1 (start and splits in `dp2024-2024-11-20.serial`) | 7 | 56.49 | 56.49 |

- **Effect:** the lane has no time and no place in the live view and in the heat table; the other lanes are placed
  without it. The legacy heats (`/legacy/heats.xml`, `/legacy/heats-new.xml`) keep the time, so JAuswertung gets it.
- **Where:** `alphatimer-heats` (`Lane.touch`, `Lane.enforceStatus`, `Heat.usedLanes`); the order in which the refiner
  (`alphatimer-refinedmessages`, `TimeParser` before `UsedLanesParser`) sends the messages of one ARES message.
- **Idea:** let the refiner send the `UsedLanesMessage` before the time of the same ARES message, or let the heat model
  keep the times of a lane whose use is not known yet.

## Agreed solution

Agreed 2026-11-25: the refiner sends the `UsedLanesMessage` of an ARES message before its other refined messages
(`UsedLanesParser` first in `DataHandlingMessageRefiner`), as `LenexRefiner` already does, so the lanes of a heat are
marked as used before its first time arrives. The heat model stays as it is. The tests of the refiner and the heat model
show that nothing depends on the old order.
