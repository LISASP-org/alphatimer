# Documentation still refers to the removed Spring Boot application

The Spring Boot application *alphatimer* no longer exists. AlphaTiming lives in its repository (`LISASP-org/alphatimer`),
and the protocol and refiner modules (`alphatimer-ares-serial-api`, `alphatimer-ares-serial`,
`alphatimer-refinedmessages-api`, `alphatimer-refinedmessages`, `alphatimer-serial-api`) are AlphaTiming's own
modules, with their history in this repository since 2021. `AGENTS.md` describes them that way; other places still call
them copies of that application in `../alphatimer`, a path that is this repository itself.

- **Effect:** readers of the README and of the code take the refiner for foreign code and weigh changes to it as such.
  PIT still skips these modules, a choice made while they counted as copies.
- **Where:**
  - `README.md`: the second paragraph ("It reuses the core logic of the Spring Boot application *alphatimer* …
    copied, no dependency on it", "settings that alphatimer reads from its `application.yaml`").
  - `alphatimer-ui-core/.../recording/RecordingSession.java`: the comment on the competition constant ("The copied
    pipeline …").
  - `docs/superpowers/specs/2026-10-16-heat-timing-design.md` and `docs/superpowers/plans/2026-10-16-heat-timing.md`:
    the original design and plan describe copying the modules from `../alphatimer` and the Spring Boot BOM.
  - The PIT configuration of the root `pom.xml` (`targetClasses` `org.lisasp.alphatiming.*`).
- **Idea:** describe the modules as AlphaTiming's protocol and refiner modules in the README and the comment. Decide
  whether PIT should cover them now. Keep the package names `org.lisasp.alphatimer.*`; renaming them is a separate
  clean-up. For the dated spec and plan, decide whether to correct them or to leave them as the record of how the
  project started, with a note that the application is gone.
