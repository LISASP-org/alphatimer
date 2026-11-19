# FileFailure recognises folders only by the English OS text

`FileFailure.kindOfReason` matches the lower-cased exception message against English texts such as "is a directory".
The JDK takes that message from the operating system's `strerror`, which follows the locale of the process (`LANG`,
`LC_ALL`), not `user.language`. On a machine with `LANG=de_DE.UTF-8` reading a folder as a file fails with
"Ist ein Verzeichnis", so the cause is classified as OTHER instead of `IS_DIRECTORY`. The same holds for the other
texts matched there (no space left, permission denied, read-only file system).

- **Effect:** on a non-English system a failed file action names the exception message instead of the reason; the
  test `FileFailureTest.readingAFolderAsAFileIsIsDirectory` fails on such a machine (it passes with `LC_ALL=C`).
- **Where:** `alphatimer-ui-common` (`FileFailure.kindOfReason`).
- **Idea:** decide the kind from the file system instead of the text: where the path is known, check
  `Files.isDirectory(path)`, and keep the text match only as a fallback.
