# The rank value "D " is read as a disqualification

`DataHandlingMessage1Parser` turns the rank field `D ` (bytes 17-18 of message 1) into `RankInfo.Disqualified` with
rank 0. The recordings in `data/` contain it 1,776 times, all on reaction times (kind of time `R`, time type `+`), and
never on a lane that was disqualified. A real disqualification arrives as `DSQ` in place of the time
(`TimeMarker.Disqualified`). The meaning of `D ` is not in the ARES manual; the code comment of the parser already
notes that it also comes with takeover times, where no rank is sent.

- **Effect:** none visible so far. Reaction times are not refined, `TimeParser` drops a time with `RankInfo.Disqualified`
  (none in the recordings), and `TakeOverTimeParser` accepts a takeover with it as a disqualified takeover, which
  `HeatService` ignores. The name misleads: code built on `RankInfo.Disqualified` (as in
  `disqualified-takeover-skips-checks.md`) would treat ordinary reaction and takeover times as disqualifications.
- **Where:** `alphatimer-ares-serial` (`DataHandlingMessage1Parser.getRank`/`getRankInfo`), `RankInfo`;
  `alphatimer-refinedmessages` (`TimeParser`, `TakeOverTimeParser`) and the tests that build messages with
  `RankInfo.Disqualified`.
- **Idea:** find out what `D ` stands for (a recording of a relay heat with takeover times; ask Swiss Timing for the GP
  output protocol), then rename `RankInfo.Disqualified` accordingly and decide whether `TakeOverTimeParser` should
  accept it at all. Settle `disqualified-takeover-skips-checks.md` together with this issue.
