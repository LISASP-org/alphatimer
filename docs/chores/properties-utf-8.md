# Properties files use \uXXXX escapes

The UI texts in `alphatimer-ui-common/src/main/resources/org/lisasp/alphatiming/ui/common/` are written with `\uXXXX`
escapes for every non-ASCII character: 44 in `messages_de.properties`, 6 in `messages.properties`. German text is hard
to read and review that way, and jutils-legacy already has a properties file in plain UTF-8.

Since Java 9, `ResourceBundle.getBundle` reads properties files as UTF-8 (`PropertyResourceBundle`), and `I18n` loads
them that way. A file that is not valid UTF-8 silently falls back to ISO-8859-1, so a wrong encoding shows up as
garbled umlauts, not as an error.

Convert both files to UTF-8 without escapes. Check how `I18nTest` reads them (it compares keys and placeholders) and add
a check that a known German text with `ä`/`ß` comes back correctly. Then change *Internationalisation* in `AGENTS.md`:
properties files are UTF-8 without escapes.

## Done when

No properties file in the project contains `\uXXXX` escapes, the texts show correctly in GUI and TUI, and `AGENTS.md`
says UTF-8.
