# A repeated or corrected start resets a finished heat

`Heat.start` always resets all lanes and restarts the clock. `HeatService` only ignores starts of type
`PreviousRaceResult`.

- **Effect:** a start of type `CurrentRaceResults`, or a corrected/manual start, after the finish sets every lane to time 0,
  place 0 and no penalty (DNS/DNF are lost), clears the end and restarts the running time. Repeated finish times may
  restore the times, but not the penalties, and the clock shows the time since the repeat.
- **Where:** `alphatimer-heats/.../heats/Heat.start`, `HeatService.isRepeatOfAPreviousRace`; `StartParser` accepts these
  start types.
- **Idea:** only a live start (re)starts a heat; ignore other starts on a heat that has already started (or only correct
  `started`). Check with a capture which start messages ARES sends after a race.
