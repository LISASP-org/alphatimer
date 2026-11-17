# A disqualified relay takeover skips the lane and lap checks

`TakeOverTimeParser.isValid` joins the check for a disqualified takeover (rank `D`, no time marker, no time type) with
`||` to a chain of `&&` without parentheses. `&&` binds tighter, so the disqualified case alone makes the message
valid: the checks for the current lap (`> 0` and below the lap count), the lane (`> 0`), the rank and the time info
apply only to normal takeovers.

- **Effect:** a disqualified takeover without a lane or on lap 0 is refined instead of dropped. `HeatService` ignores
  takeover messages so far, so nothing visible changes yet; it would once relay disqualifications are shown.
- **Where:** `alphatimer-refinedmessages/.../TakeOverTimeParser.isValid`.
- **Idea:** put parentheses around the two alternatives so the common checks apply to both, with a test for a
  disqualified takeover without a lane.
