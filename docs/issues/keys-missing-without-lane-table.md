# Files without a lane table get no keys

The migration steps that add the unique key of `heat` and the keys of `lane` do nothing when the table is missing; Hibernate
then creates the table without them.

- **Effect:** such a file (no released version wrote one) would work without the database keys.
- **Where:** `alphatimer-project-db/.../SchemaMigrations` (steps 2 and 3), `TimingDatabase.createKeys`.
- **Idea:** create the keys after Hibernate's schema update for every file, not only for new ones.
