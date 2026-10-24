# Chores

Planned clean-ups of the code or data model that are not bugs. Each chore is a self-contained file listed in the
table below; once it is done, delete its file and its row here (same rule as `docs/issues/`).

Every row has a severity and a size; keep the table ordered by severity (High, Medium, Low), then by size (S, M, L).

- **Severity:** High = the current state loses data or user settings; Medium = it invites bugs (duplicated or
  inconsistent data); Low = only redundancy.
- **Size:** S = a local change of a few lines with a test; M = several classes or a migration; L = spread over many
  modules or needs a design decision.

| Chore | Severity | Size | Summary |
| --- | --- | --- | --- |
| [The design spec still says the competition key was dropped](spec-competition-key-outdated.md) | Low | S | The key is a project setting again |
| [AGENTS.md does not list all data-only exceptions](agents-data-exceptions-list.md) | Low | S | `ProjectFileInUseException` is missing |
| [The single-test command in AGENTS.md fails for some modules](agents-single-test-command.md) | Low | S | `-am test` misses test-jars of other modules; `package` works |
| [An unused accessor in JAlphaTiming](unused-status-warning-label.md) | Low | S | `statusWarningLabel()` is not used |
| [Mutation testing with PIT](mutation-testing-pit.md) | Low | S | No PIT setup; add a `mutation` profile as in JAuswertung |
