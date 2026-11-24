# LENEX rounds with the same heat number merge into one legacy heat

The legacy format (`Heat` of `alphatimer-legacy-api`) identifies a heat by event and heat number; it has no round.
LENEX files may use one event number for several rounds. `RefinedLegacyService` (which serves the legacy heats of LENEX
projects at `/legacy/heats.xml`, `/legacy/heats.json` and `/legacy/heats-new.xml`) then puts the times of every round of
that number into one legacy heat: each lane holds one time per round.

In `data/lenex/swimrankings-example-results` (`.lef` and `.lxf`) 93 event/heat numbers occur in several rounds
(`PreliminaryHeat`, `Final`, `EXTRAHEATS`, `SwimOffAfterPrelim`), e.g. event 1 heat 2 as prelim and final. The other
LENEX examples number their events per round. The heat model keeps the rounds apart (`HeatDto.round`).

- **Effect:** JAuswertung offers the times of several rounds for one lane and preselects the last one, which may belong
  to another round than the heat being entered. A removed heat (`HeatRemovedMessage`) also drops the times of the other
  rounds with that number.
- **Where:** `alphatimer-legacy-server` (`refined.RefinedLegacyService`); the format of `alphatimer-legacy-api` cannot carry
  the round.
- **Idea:** decide how such heats are served: keep them merged, serve only the newest round per event and heat, or leave
  heats out whose number occurs in more than one round. Spec:
  `docs/superpowers/specs/2026-11-25-legacy-heats-from-refined-messages-design.md`.
