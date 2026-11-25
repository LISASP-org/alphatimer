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
| [Associate `.timing` files in the JAuswertung installer](associate-timing-files.md) | Low | S | Register `.timing` files with the AlphaTiming launcher in the install4j project; blocked by opening a project from the command line |
| [UsedLanesMessage parses the ARES flags inside an api module](used-lanes-parsing-in-api-module.md) | Low | S | Move the parsing of the ten ARES used-lane flags from the message in `alphatimer-refinedmessages-api` to the refiner |
| [Check whether the api modules are real api modules](api-modules-audit.md) | Low | L | Find out per api module which types other areas use and which are the internal model of its implementation module |
