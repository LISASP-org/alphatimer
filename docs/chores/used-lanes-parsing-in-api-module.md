# UsedLanesMessage parses the ARES flags inside an api module

`UsedLanesMessage` (`alphatimer-refinedmessages-api`) has a constructor that takes the ten used-lane flags of ARES as
text, checks them and turns them into lane numbers (`aresLanes`). An api module holds what the areas exchange and no
logic beyond that (AGENTS.md, *Api Module and Implementation Module*); reading a format of ARES belongs to the refiner.

- **Goal:** the message only carries lane numbers and a `LaneNumbering`; the flags of ARES are parsed in
  `alphatimer-refinedmessages`.
- **Where:** `UsedLanesMessage` and `InvalidUsedLanesException` (`alphatimer-refinedmessages-api`), the parsers of
  `alphatimer-refinedmessages` that build the message, `TestMessageCreator` (`alphatimer-heats`) and the tests that use
  the constructor.
