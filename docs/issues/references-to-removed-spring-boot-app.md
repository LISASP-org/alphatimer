# Documentation still refers to the removed Spring Boot application

The Spring Boot application *alphatimer* no longer exists. AlphaTiming lives in its repository (`LISASP-org/alphatimer`),
and the protocol and refiner modules (`alphatimer-ares-serial-api`, `alphatimer-ares-serial`,
`alphatimer-refinedmessages-api`, `alphatimer-refinedmessages`, `alphatimer-serial-api`) are AlphaTiming's own
modules, with their history in this repository since 2021. The documentation still describes them as independent
copies of that application in `../alphatimer`, a path that is this repository itself.

- **Effect:** the guide imposes a rule that has no reason any more: "The copied modules change only where AlphaTiming
  needs it; keep such changes small and note them above". Changes to the refiner are treated as exceptions and listed
  one by one in the module table, and design work weighs them as changes to foreign code. PIT also skips these modules
  as "copied".
- **Where:**
  - `AGENTS.md`: the introduction ("independent copies of the Spring Boot application `alphatimer` (`../alphatimer`)",
    "There is no JPMS and no Spring"), the module table (the row of the protocol/refiner modules: "Copied from
    `../alphatimer`", "Independent copies — never make them depend on alphatimer", and the list of additions that
    follows; `alphatimer-serial-core`: "copied from `../alphatimer`"), *Dependency Rules* ("`alphatimer-*` copies",
    "The copied modules change only where AlphaTiming needs it…"), *Validation* ("the copied parsers"), the PIT note in
    *Build, Test, Run* ("not the copied `alphatimer-*` modules"), *Coding Standards* ("The `alphatimer-*` copies keep
    their own names"), *Persistence and Compatibility* and *Logging* ("the copied pipeline").
  - `README.md`: the second paragraph ("It reuses the core logic of the Spring Boot application *alphatimer* …
    copied, no dependency on it", "settings that alphatimer reads from its `application.yaml`").
  - `alphatimer-ui-core/.../recording/RecordingSession.java`: the comment on the competition constant ("The copied
    pipeline …").
  - `docs/superpowers/specs/2026-10-16-heat-timing-design.md` and `docs/superpowers/plans/2026-10-16-heat-timing.md`:
    the original design and plan describe copying the modules from `../alphatimer` and the Spring Boot BOM.
- **Idea:** describe the modules as AlphaTiming's protocol and refiner modules; drop the rule to keep changes small and
  shorten their row in the module table to what they do (the list of additions only existed to track the difference to
  the original). Decide whether PIT should cover them now. Keep the package names `org.lisasp.alphatimer.*`; renaming
  them is a separate clean-up. For the dated spec and plan, decide whether to correct them or to leave them as the
  record of how the project started, with a note that the application is gone.
