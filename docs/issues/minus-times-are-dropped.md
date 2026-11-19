# Times with a minus sign are dropped

ARES sometimes sends finish and split times (message type 2, `OnLineTime`) with a minus sign in front of the time
(`TimeMarker.Minus`). `TimeParser` drops them (`DroppedTimeMessage`), so they never reach the heat. The raw data
analysis shows them under "Well-formed, but not implemented". It is not known what ARES means by them.

The recordings in `data/` contain 8 such times, in two groups. In both groups they are the first messages of the heat,
come before its ReadyToStart and its start, have ranks 1 to 4, and are only a few seconds apart, like real touches:

| Recording | Heat | Kind of time | Lanes (lap) | Times | Next messages of the heat |
| --- | --- | --- | --- | --- | --- |
| `dem2023-2023-05-06.serial` | event 11, heat 1 (2 laps) | finish (`A`) | 3, 6, 2, 5 (lap 2) | `-16:23:13.50` … `-16:22:53.19` | ReadyToStart, start `5:31:23.65`, reaction times |
| `dp2023-2023-10-26.serial` | event 1, heat 1 (4 laps) | split (`I`) | 2, 5, 4 (lap 1), 6 (lap 2) | `-9:49:58.24` … `-9:49:56.60` | ReadyToStart, manual start (`M`) `7:47:56.91` |

The `dp2023` group is the first heat recorded in that file.

- **Assumption:** the touchpads were touched before the start of the heat (a pad test, or swimmers in the water), and
  ARES measures these touches against a start that has not happened yet, so the time comes out negative. Then dropping
  them is right.
- **Open question:** is a minus time ever sent after a valid start, e.g. as the correction or withdrawal of a time
  ARES sent before? If it is, dropping it would keep a time on the lane that ARES has taken back. The ARES manuals in
  `docs/ares` are scanned PDFs without a text layer. They still need to be searched for the time marker `-`.
- **Where:** `alphatimer-refinedmessages` (`TimeParser`), `TimeMarker.Minus` in `alphatimer-ares-serial-api`.
- **Idea:** check the manual and look for minus times after a start in future recordings. If the assumption holds, add
  a test that pins down the drop. Otherwise refine the case that appears.
