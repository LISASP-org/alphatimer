# Deleted times are applied as real times

A time message of the type `DeletedTime` is applied like a normal time.

- **Effect:** a time the operator deleted at the timekeeping system overwrites the lane's time in the live view and the heat.
- **Where:** `TimeParser` (copied from alphatimer) together with `alphatimer-heats/.../heats/Lane.touch`.
- **Idea:** check the real behavior with a capture before changing it; then clear the lane time on a deleted time.
