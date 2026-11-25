# Is lenex-api an api module?

The audit of the api modules (2026-11-26) found that only `alphatimer-ui-core` uses `alphatimer-lenex-api` outside its
pair, and only `LenexMeet` (in `LenexRecording`) and `InvalidLenexException`. `LenexEvent`, `LenexHeat`,
`LenexResult` and `LenexSplit` come along as parts of `LenexMeet`. `XmlParserUnavailableException` and
`XmlNotWritableException` are only thrown inside `alphatimer-lenex` (`LenexReader`, `LenexDocuments`).

- **Question:** keep `lenex-api` as the snapshot that `ui-core` passes from `LenexReader` to `LenexRefiner`, or merge it
  into `alphatimer-lenex` (and `ui-core` keeps the meet only as an opaque value, see
  [ui-core uses legacy-api and lenex-api](ui-core-api-usage.md)).
- **At least:** the two XML exceptions belong to `alphatimer-lenex` unless another module catches them.
- **Where:** `alphatimer-lenex-api`, `alphatimer-lenex`, `LenexRecording` (`alphatimer-ui-core`), `AGENTS.md` (module
  table, api pairs).

## Done when

It is decided whether `lenex-api` stays an api module, and the code follows the decision.
