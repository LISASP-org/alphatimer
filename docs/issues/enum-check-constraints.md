# Enum columns carry CHECK constraints that are never updated

Hibernate creates the enum columns with SQLite CHECK constraints, e.g.
`status varchar(255) check (status in ('Open','Started','Finished'))` in `heat`; the same for `lane.penalty`,
`lane.status`, the legacy status and the enums of `aresmessage`. `hbm2ddl=update` never changes an existing constraint.

- **Effect:** once a value is added to one of these enums (a new `Penalty`, `HeatStatus`, a value in the copied modules),
  every insert of that value into a project file of an earlier version fails — the recording can no longer store data.
  Nothing in *Persistence and Compatibility* warns about it, because it does not look like an entity change.
- **Where:** the entities in `alphatimer-project-db` (enum attributes), visible in
  `alphatimer-ui-core/src/test/resources/compat/first-version.heat`.
- **Idea:** decide before the first release: map the enums with an `AttributeConverter` to a plain string (no CHECK is
  generated) and handle the constraints of existing files (rebuild the tables on open), extend the compatibility test.
