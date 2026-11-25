# Open Issues

Open findings from code work that are not fixed yet. Each issue is a self-contained file; once it is fixed and
verified, delete the file and its row here (see `AGENTS.md` → *Documentation and Issues*). Keep this README with its
table even when no issues are open.

Every row has a severity and a size; keep the table ordered by severity (High, Medium, Low), then by size (S, M, L).

- **Severity:** High = wrong results or lost/corrupted data; Medium = a feature fails or misleads, with a workaround;
  Low = no user-visible effect, or only inconvenience.
- **Size:** S = a local change of a few lines with a test; M = several classes or a migration; L = spread over many
  modules or needs a design decision.

| Issue | Severity | Size | Summary |
| --- | --- | --- | --- |
| [Backup times do not become official](backup-times-not-interpreted.md) | Medium | M | Backup times are kept beside the finish but never become official, not even when the operator takes one for a lane. |
| [LENEX rounds with the same heat number merge into one legacy heat](lenex-rounds-merge-in-legacy-heats.md) | Medium | M | A LENEX event number used in several rounds puts the times of all rounds into one legacy heat; JAuswertung may preselect a time of another round. |
| [How deleted times are handled is not settled](deleted-times-handling.md) | Medium | M | Deletions mark every entry with the same lap and value, and a re-sent time counts again; both rules are inferred, not confirmed by ARES or recordings. |
| [The version 1 fixture test depends on the time zone](version1-fixture-depends-on-time-zone.md) | Low | S | `Version1FixtureTest.theHeatsOfAVersion1FileAreShown` fails in a JVM outside Europe/Berlin: the announced heat is two hours off. |
| [Times with a minus sign are dropped](minus-times-are-dropped.md) | Low | S | Finish and split times with `TimeMarker.Minus` are dropped; seen only before the start of a heat, meaning unknown. |
| [Documentation still refers to the removed Spring Boot application](references-to-removed-spring-boot-app.md) | Low | M | `README.md`, a code comment and the first spec and plan describe the protocol and refiner modules as copies of the gone app in `../alphatimer`; PIT still skips them. |
| [The rank value "D " is read as a disqualification](rank-d-is-not-a-disqualification.md) | Low | M | The parser maps rank `D `, sent with reaction and takeover times, to `RankInfo.Disqualified`; its meaning is unknown. |
| [A disqualified relay takeover skips the lane and lap checks](disqualified-takeover-skips-checks.md) | Low | S | `TakeOverTimeParser` accepts a disqualified takeover without lane or valid lap, because of missing parentheses. |
