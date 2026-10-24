# New and Open stop a running recording without asking

`AppController.prepareToLeaveDocument` stops the recording first and only then asks about unsaved changes, if there are
any. As the spec describes, Cancel returns to the document with the recording still stopped.

- **Effect:** an accidental New or Open (e.g. Ctrl+N) during a race ends the recording, even when the user then cancels;
  data arriving until the user notices and restarts is not recorded.
- **Where:** `alphatimer-ui-common/.../AppController.prepareToLeaveDocument` (New, Open, Close);
  spec `docs/superpowers/specs/2026-10-16-heat-timing-design.md` → *Closing*.
- **Idea:** while recording, ask explicitly whether to stop the recording before leaving the document; Cancel keeps the
  recording running. Update the spec accordingly.
