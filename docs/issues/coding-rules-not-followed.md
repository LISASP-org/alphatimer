# Some code does not follow the comment and exception rules

- **Effect:** none for the user; the code contradicts `AGENTS.md` → *Coding Standards* and *Exceptions*.
- **Where:** Javadoc that only restates the code, e.g. `HeatRepository` ("Where HeatService keeps the heats") and
  `LaneTimeRepository`; `Heat.lane` throws a generic `NoSuchElementException`.
- **Idea:** remove the comments that fail the rule, throw a specific exception for an unknown lane.
