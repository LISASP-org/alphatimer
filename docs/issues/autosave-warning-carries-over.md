# The auto-save warning carries over to the next project

The warning of a failed auto-save is only cleared when the document becomes clean.

- **Effect:** after Discard and New/Open, the new project shows the old project's auto-save warning.
- **Where:** `alphatimer-ui-common/.../AppController`, `StatusWarnings`.
- **Idea:** clear the auto-save warning when the document is replaced, as the write-error warnings already are.
