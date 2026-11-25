# Why ui-core uses legacy-api and lenex-api

`alphatimer-ui-core` is the application layer that wires the implementation modules together. It also uses types of
two api modules directly, and it is not clear that it has to:

- **legacy-api:** `RecordingSession` imports `Heat` (`legacyHeats()` hands the legacy heats on, following
  `recordedInput()`). Inside alphatimer it is the only user of `legacy-api` outside `legacy-server`.
- **lenex-api:** `LenexRecording` imports `LenexMeet` and `InvalidLenexException`: it reads a version with
  `LenexReader`, keeps the last meet and passes both to `LenexRefiner.refine`.
- **Question per case:** does `ui-core` need the type (it decides something with it), or does it only pass it from one
  implementation module to another, so the implementation module could keep it (e.g. `LegacyHttpServer` asks a supplier
  of `legacy-server`, `alphatimer-lenex` offers one call that reads and refines)?
- **Related:** [Is lenex-api an api module?](lenex-api-or-lenex.md)

## Done when

For both api modules it is decided why `ui-core` uses them, and the code follows the decision.
