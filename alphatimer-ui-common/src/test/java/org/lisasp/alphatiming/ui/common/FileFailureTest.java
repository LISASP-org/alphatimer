package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lisasp.alphatiming.project.db.TimingDatabase;
import org.lisasp.alphatiming.project.db.ProjectFileIsFolderException;
import org.lisasp.alphatiming.project.db.TimingWorkFolder;
import org.sqlite.SQLiteErrorCode;
import org.sqlite.SQLiteException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.AccessDeniedException;
import java.nio.file.FileSystemException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.ReadOnlyFileSystemException;
import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchException;

class FileFailureTest {

    @TempDir
    Path dir;

    private final Path file = Path.of("events", "wk.timing");

    private FileFailure.Kind kindOf(Throwable ex) {
        return FileFailure.of(ex, file).kind();
    }

    private static RuntimeException sqlite(SQLiteErrorCode code) {
        return new IllegalStateException("could not execute statement", new SQLiteException("[" + code.name() + "] " + code.message, code));
    }

    @Test
    void aMissingFileOrFolderIsNotFound() {
        assertThat(kindOf(new IOException("Could not save project file", new NoSuchFileException("events/wk.timing.lock")))).isEqualTo(FileFailure.Kind.NOT_FOUND);
        assertThat(kindOf(new FileNotFoundException("events/wk.timing (No such file or directory)"))).isEqualTo(FileFailure.Kind.NOT_FOUND);
    }

    @Test
    void missingRightsAndReadOnlyStorageAreNoPermission() {
        assertThat(kindOf(new AccessDeniedException("events/wk.timing"))).isEqualTo(FileFailure.Kind.NO_PERMISSION);
        assertThat(kindOf(new ReadOnlyFileSystemException())).isEqualTo(FileFailure.Kind.NO_PERMISSION);
        assertThat(kindOf(new FileNotFoundException("events/wk.timing (Permission denied)"))).isEqualTo(FileFailure.Kind.NO_PERMISSION);
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_READONLY))).isEqualTo(FileFailure.Kind.NO_PERMISSION);
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_READONLY_DBMOVED))).isEqualTo(FileFailure.Kind.NO_PERMISSION);
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_CANTOPEN))).isEqualTo(FileFailure.Kind.NO_PERMISSION);
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_PERM))).isEqualTo(FileFailure.Kind.NO_PERMISSION);
    }

    @Test
    void aFullDiskIsDiskFull() {
        assertThat(kindOf(new FileSystemException("events/wk.timing.tmp", null, "No space left on device"))).isEqualTo(FileFailure.Kind.DISK_FULL);
        assertThat(kindOf(new IOException("Could not save", new IOException("No space left on device")))).isEqualTo(FileFailure.Kind.DISK_FULL);
        assertThat(kindOf(new IOException("There is not enough space on the disk"))).isEqualTo(FileFailure.Kind.DISK_FULL);
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_FULL))).isEqualTo(FileFailure.Kind.DISK_FULL);
    }

    @Test
    void readingAFolderAsAFileIsIsDirectory() throws IOException {
        Exception reading = catchException(() -> {
            try (InputStream in = Files.newInputStream(dir)) {
                in.read();
            }
        });

        assertThat(kindOf(reading)).isEqualTo(FileFailure.Kind.IS_DIRECTORY);
        assertThat(kindOf(new FileNotFoundException("events (Is a directory)"))).isEqualTo(FileFailure.Kind.IS_DIRECTORY);
    }

    @Test
    void savingOntoAFolderIsIsDirectory() {
        Path folder = dir.resolve("wk.timing");

        assertThat(kindOf(new ProjectFileIsFolderException(folder))).isEqualTo(FileFailure.Kind.IS_DIRECTORY);
    }

    @Test
    void aWorkFileThatIsNoDatabaseIsCorrupt() throws IOException {
        Path garbage = Files.writeString(dir.resolve("broken" + TimingWorkFolder.WORK_FILE_SUFFIX), "not a database, just some text that is long enough");

        Exception opening = catchException(() -> TimingDatabase.adopt(garbage).close());

        assertThat(kindOf(opening)).isEqualTo(FileFailure.Kind.CORRUPT);
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_CORRUPT))).isEqualTo(FileFailure.Kind.CORRUPT);
    }

    @Test
    void aDatabaseHeldByAnotherProgramIsLocked() {
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_BUSY))).isEqualTo(FileFailure.Kind.LOCKED);
        assertThat(kindOf(sqlite(SQLiteErrorCode.SQLITE_LOCKED))).isEqualTo(FileFailure.Kind.LOCKED);
    }

    @Test
    void anythingElseIsOtherWithTheMessageOfTheRootCauseAsDetail() {
        FileFailure failure = FileFailure.of(new IOException("Could not save", sqlite(SQLiteErrorCode.SQLITE_IOERR)), file);

        assertThat(failure.kind()).isEqualTo(FileFailure.Kind.OTHER);
        assertThat(failure.detail()).isEqualTo("[SQLITE_IOERR] Some kind of disk I/O error occurred");
        assertThat(FileFailure.of(new IllegalStateException(), file).detail()).isEqualTo("java.lang.IllegalStateException");
    }

    @Test
    void theFailureNamesTheFileTheUserActedOn() {
        assertThat(FileFailure.of(new NoSuchFileException("events/wk.timing.lock"), file).file()).isEqualTo(file);
    }

    @Test
    void everyKindHasATextNamingTheFileAndOtherAddsTheDetail() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.ENGLISH);
            assertThat(I18n.describe(new FileFailure(FileFailure.Kind.CORRUPT, file, "x"))).isEqualTo(file + " is damaged or not a database");
            assertThat(I18n.describe(new FileFailure(FileFailure.Kind.OTHER, file, "boom"))).isEqualTo("unexpected error with " + file + " (boom)");
            Locale.setDefault(Locale.GERMANY);
            assertThat(I18n.describe(new FileFailure(FileFailure.Kind.CORRUPT, file, "x"))).isEqualTo(file + " ist beschädigt oder keine Datenbank");
            assertThat(I18n.describe(new FileFailure(FileFailure.Kind.OTHER, file, "boom"))).isEqualTo("unerwarteter Fehler bei " + file + " (boom)");
            for (FileFailure.Kind kind : FileFailure.Kind.values()) {
                assertThat(I18n.describe(new FileFailure(kind, file, "boom"))).as(kind.name()).contains(file.toString()).doesNotContain("failure.");
            }
        } finally {
            Locale.setDefault(original);
        }
    }
}
