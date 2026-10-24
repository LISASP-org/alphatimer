# Mutation testing with PIT

The build has no mutation testing. The old IntelliJ run configuration "Mutation Testing" called
`org.pitest:pitest-maven:mutationCoverage` without any PIT configuration in the POM; without the JUnit 5 plugin for PIT it
found no tests, so it was removed.

- **Where:** root `pom.xml`, `AGENTS.md` → *Build, Test, Run*.
- **Idea:** add a Maven profile `mutation` as in JAuswertung (`pitest-maven` with the `pitest-junit5-plugin` dependency,
  HTML and XML reports, target classes `org.lisasp.alphatiming.*`), document the call in `AGENTS.md`
  (`mvn -Pmutation -pl <module> test-compile org.pitest:pitest-maven:mutationCoverage`, report in
  `<module>/target/pit-reports/`), and add a matching IntelliJ run configuration. The Swing and TUI modules are of little
  use for mutation testing; start with `heats`, `project-db`, `ui-core` and `ui-common`.
