# A deleted finish stays in the legacy REST data

`LegacyService` stores every finish as a `LaneTime`. A finish deleted at the timekeeping system arrives as a deleted
split time of the last lap; the heat model withdraws it, the legacy data does not.

- **Effect:** JAuswertung keeps seeing a time the timekeeper deleted until a new finish arrives for that lane; if none
  arrives, it can take over the wrong time.
- **Where:** `alphatimer-legacy-server/.../legacy/server/LegacyService`, `LaneTimeRepository`/`LaneTimeStore`.
- **Idea:** remove (or mark) the matching `LaneTime` when a deleted time with the same lane, lap and time arrives, as
  `Heat.deleteTime` does.
