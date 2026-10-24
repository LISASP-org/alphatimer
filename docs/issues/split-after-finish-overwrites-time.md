# A split time after the finish overwrites the final time

`Lane.touch` takes every time greater than 0 and sets `finished` from the kind of time of the message.

- **Effect:** a split time that arrives after a lane's finish (a corrected split, or results repeated with their splits)
  replaces the final time; the lane loses its place and the other lanes move up — in the live view and in the stored heat.
  Example: lane 1 finished in 25.00 (1st), lane 2 in 26.00 (2nd); a later 12.00 split of lane 1 shows lane 1 with 12.00
  and no place, lane 2 as 1st.
- **Where:** `alphatimer-heats/.../heats/Lane.touch`, `Heat.touch`; `TimeParser` accepts `SplitTime` in `OnLineTime`,
  `CurrentRaceResults` and as `CorrectedTime`. The legacy lane times are not affected (they only record `Finish`).
- **Idea:** once a lane is finished, ignore split times until the next start; a finish may still replace a finish.
  `HeatTest.repeatedResultsAndTimesAfterTheEndDoNotMoveTheStart` already sends this sequence but only checks `started` —
  add assertions on the lane time and the places. Check the orders ARES really sends with a capture.
