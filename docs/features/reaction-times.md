# Reaction times

The refined messages carry no reaction times, so the heat model cannot keep them although timekeeping systems measure
them: the reaction time at the start and, in relays, the take-over (relay take-off) times of the following swimmers.
ALGE sends them on the video output of the TM-SWIM (`M` lines with lane and touch counter, added by SwimTime) and in
LENEX results (`RESULT reactiontime`, for relays that of the first swimmer).

- **Goal:** reaction times reach the heat model through the refined messages, independent of the timekeeping system.
- **Behavior:**
  - A new refined message carries the reaction time of a lane in a heat; a relay take-off time is told apart from the
    reaction time at the start.
  - The lane keeps the reaction time beside its times; it never counts for the place.
- **Where:** `alphatimer-refinedmessages-api` (the new message), `alphatimer-heats` (`Lane`, `LaneDto`), the refiners of
  the systems that send reaction times.
- **Open questions:** whether ARES sends reaction times and where (`TakeOverTimeMessage` covers relay take-overs only);
  whether and where the front ends and the REST endpoint show them.
