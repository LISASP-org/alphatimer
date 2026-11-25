# Check whether the api modules are real api modules

The api modules (`alphatimer-ares-serial-api`, `alphatimer-refinedmessages-api`, `alphatimer-serial-api`,
`alphatimer-legacy-api`, `alphatimer-lenex-api`) are meant to hold what other areas exchange with an area: records and
value types, interfaces, enums, exceptions (AGENTS.md, *Api Module and Implementation Module*). It is not checked
whether each of them is that, or rather the internal model of its implementation module under an api name.

- **Goal:** for every api module it is known which of its types other areas really use and which only its own
  implementation module uses; types of the second kind move to the implementation module, or the module is named for
  what it is.
- **Check per module:**
  - Which types are used outside the module pair, and by whom.
  - Logic beyond value types (parsing, validation, conversion), e.g.
    [UsedLanesMessage parses the ARES flags](used-lanes-parsing-in-api-module.md) and `LegacySerialization` and
    `AresResultFile` in `alphatimer-legacy-api`.
  - Dependencies on anything but other api modules.
- **Constraint:** `alphatimer-legacy-api` is JAuswertung's input; its types and its XML stay as they are.
- **Open questions:** whether the snapshot types of `alphatimer-lenex-api` are an api at all (only `alphatimer-lenex`
  and the application layer use them); whether the protocol modules, which keep their own package names, follow the
  same rule.
