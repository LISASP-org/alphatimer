# Lanes have no foreign key to their heat

Hibernate's SQLite dialect drops foreign keys and multi-column unique keys silently; `lane.heat_id` has no foreign key.
The unique key of `heat` is created explicitly since the competition column was removed; other entities were not checked.

- **Effect:** the database does not prevent lanes without a heat or duplicate rows; a bug in the stores would go unnoticed.
- **Where:** the entities and `TimingDatabase` (`alphatimer-project-db`).
- **Idea:** check every entity for intended keys and create the missing ones explicitly (new files and a migration step).
