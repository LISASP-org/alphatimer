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
| [Backup times are not interpreted](backup-times-not-interpreted.md) | Medium | M | Results sent "With backup" and backup times taken by the operator are dropped by the refiner. |
| [The rank value "D " is read as a disqualification](rank-d-is-not-a-disqualification.md) | Low | M | The parser maps rank `D `, sent with reaction and takeover times, to `RankInfo.Disqualified`; its meaning is unknown. |
| [A disqualified relay takeover skips the lane and lap checks](disqualified-takeover-skips-checks.md) | Low | S | `TakeOverTimeParser` accepts a disqualified takeover without lane or valid lap, because of missing parentheses. |
