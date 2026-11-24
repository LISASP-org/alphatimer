# The version 1 fixture test depends on the time zone

`Version1FixtureTest.theHeatsOfAVersion1FileAreShown` (`alphatimer-ui-core`) expects the heat of
`fixtures/version-1.timing` to be announced at `2026-05-01T10:00`. In a JVM whose default time zone is not
Europe/Berlin (seen in a container where `date` reports CET but the JVM runs on UTC), it is announced at
`2026-05-01T08:00` and the test fails; with `TZ=Europe/Berlin` it passes. `main` behaves the same.

- **Effect:** `mvn verify` fails on build machines outside Europe/Berlin although nothing is broken. Whether the
  application itself shows arrival times shifted when a project is opened in another time zone is not checked.
- **Where:** the fixture and its test in `alphatimer-ui-core`; the time stamps of `aresmessage` (`alphatimer-project-db`)
  and how they are written and read back.
- **Idea:** find out in which zone the stored time stamps are read (`LocalDateTime` through Hibernate/SQLite or the
  JDBC driver), then either pin the zone in the test (surefire `user.timezone`) or store the time stamps zone-free.
