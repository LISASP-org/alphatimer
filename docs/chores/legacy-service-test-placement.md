# LegacyServiceTest lives in the wrong module

`LegacyService` and its `InMemoryLaneTimeRepository` are in `alphatimer-legacy-server`, but `LegacyServiceTest` is in
`alphatimer-project-db`. It builds its messages with `DataHandlingMessageTestData` from project-db's test sources, and
project-db depends on legacy-server (in test scope), so moving the test would make the two modules depend on each other.
Since the heats and lane times are rebuilt in memory, nothing of the legacy service needs the database any more.

- **Effect:** none at runtime. The test of a module is found in another module, project-db keeps a test dependency on
  legacy-server only for it, and changes to the legacy service are tested only when project-db is built.
- **Where:** `alphatimer-project-db/src/test/.../LegacyServiceTest.java`, `.../testdata/DataHandlingMessageTestData.java`,
  the poms of `alphatimer-project-db` and `alphatimer-legacy-server`.
- **Idea:** two ways, to be decided:
  - Move `DataHandlingMessageTestData` to a module below both (`alphatimer-ares-serial-api` as a test-jar; note that
    `alphatimer-ares-serial` already has a test class of the same name), then move `LegacyServiceTest` to
    `alphatimer-legacy-server`.
  - Put the legacy code into one module of its own (`LegacyService`, `LaneTime`, its repository, `LegacyHttpServer`,
    `HandlerPool` and their tests), so everything that goes away when the legacy endpoints are replaced
    (`docs/features/timing-rest-endpoint.md`) can be removed as a whole.
