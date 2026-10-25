# Save As onto a folder renames the folder

`TimingFileWriter.write` moves an existing target aside as `<name>-previous.timing` without checking that it is a file.

- **Effect:** saving to a path that is an existing folder named `….timing` (Save As appends the extension) renames that
  folder to `…-previous.timing` and writes the project next to it. Nothing is deleted, but the folder is moved.
- **Where:** `alphatimer-project-db/.../TimingFileWriter.write`; the save path chooser `ProjectFiles.chooseSavePath`.
- **Idea:** reject a target that is a directory before saving (and in the Save As dialog), with a `FileFailure` text.
