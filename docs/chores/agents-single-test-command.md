# The single-test command in AGENTS.md fails for some modules

`mvn -pl <module> -am test -Dtest=…` does not put the test-jars of other modules on the classpath (e.g. `TestMessageCreator`
from `alphatimer-heats` for `alphatimer-project-db`), so it fails to compile there; with `package` it works.

- **Where:** `AGENTS.md` → *Build, Test, Run*.
- **Idea:** use `package` in the documented commands (and mention `clean` when stale `target/` folders exist).
