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
| [Errors of heat listeners are reported as processing errors](listener-errors-as-processing-errors.md) | Low | S | A failing UI listener looks like a data problem |
| [Looking for crash leftovers takes the work file locks](leftover-scan-takes-locks.md) | Low | S | A document opened at that moment could fail to get its lock |
| [The status refresh reads the database on the EDT](refresh-reads-database-on-edt.md) | Low | S | The window waits briefly behind writes every second |
| [Lanes have no foreign key to their heat](missing-foreign-keys.md) | Low | M | The SQLite dialect drops foreign and multi-column keys; other entities unchecked |
