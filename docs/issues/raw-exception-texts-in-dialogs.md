# Error dialogs show raw exception texts

`AppController.describe(ex)` puts the exception message into the dialog text.

- **Effect:** users see technical Hibernate/SQLite messages, in English even in the German UI.
- **Where:** `alphatimer-ui-common/.../AppController.describe` and the dialogs that use it.
- **Idea:** describe known failures (file not found, no permission, disk full, corrupt file, …) with I18n texts and show
  the technical text only as detail (or in the log).
