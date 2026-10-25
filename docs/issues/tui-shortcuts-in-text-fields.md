# TUI shortcuts do not work while a text field has the focus

`TuiMainWindow` handles Ctrl+N, Ctrl+O and Ctrl+C only for input the focused component leaves unhandled; a text box
consumes them and types the letter.

- **Effect:** in the settings form, Ctrl+N/O/C insert "n"/"o"/"c" instead of New, Open or Exit. Once the shortcuts work
  there, text typed but not yet committed would be lost on New/Open (Swing commits it first with `holdCommits`).
- **Where:** `alphatimer-tui/.../TuiMainWindow.onUnhandledInput`, `TuiSettingsPanel`.
- **Idea:** handle the shortcuts before the focused component (window input filter) and commit the form before New/Open.
