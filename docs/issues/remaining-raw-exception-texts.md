# Two messages still show raw exception texts

`RecordingSession` builds `RecordingError.UNEXPECTED` from `ex.getMessage()` (`error.UNEXPECTED={0}`), and `AppController`
writes `log.processingError` with `ex.toString()`.

- **Effect:** an unexpected recording error and processing errors in the message log show technical English texts, also
  in the German UI.
- **Where:** `RecordingSession` (start errors), `AppController` (session error listener); `FileFailure`/`I18n.describe`
  already turn file errors into texts.
- **Idea:** describe them with a generic translated sentence plus the technical detail in parentheses, as `FileFailure`
  does for its OTHER kind.
