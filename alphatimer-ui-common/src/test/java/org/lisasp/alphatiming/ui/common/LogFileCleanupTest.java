package org.lisasp.alphatiming.ui.common;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class LogFileCleanupTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0, 0);
    private static final DateTimeFormatter NAME_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss");

    @TempDir
    Path dir;

    private LogFileCleanup cleanup(String program, int keepNewest) {
        return new LogFileCleanup(dir, program, Duration.ofDays(30), keepNewest, () -> NOW);
    }

    private Path logFile(String program, int daysOld) throws IOException {
        LocalDateTime written = NOW.minusDays(daysOld);
        Path file = Files.writeString(dir.resolve(program + "-" + NAME_TIME.format(written) + ".log"), "log");
        Files.setLastModifiedTime(file, FileTime.from(written.toInstant(ZoneOffset.UTC)));
        return file;
    }

    @Test
    void oldLogFilesBeyondTheNewestAreDeleted() throws IOException {
        Path days40 = logFile("alphatiming", 40);
        Path days35 = logFile("alphatiming", 35);
        Path days2 = logFile("alphatiming", 2);
        Path current = logFile("alphatiming", 0);

        cleanup("alphatiming", 2).run();

        assertThat(days40).doesNotExist();
        assertThat(days35).doesNotExist();
        assertThat(days2).exists();
        assertThat(current).exists();
    }

    @Test
    void theNewestLogFilesAreKeptEvenWhenOld() throws IOException {
        Path days90 = logFile("alphatiming", 90);
        Path days60 = logFile("alphatiming", 60);
        Path days50 = logFile("alphatiming", 50);

        cleanup("alphatiming", 2).run();

        assertThat(days90).doesNotExist();
        assertThat(days60).exists();
        assertThat(days50).exists();
    }

    @Test
    void recentLogFilesAreKeptBeyondTheNewest() throws IOException {
        Path days1 = logFile("alphatiming", 1);
        Path days2 = logFile("alphatiming", 2);
        Path days29 = logFile("alphatiming", 29);

        cleanup("alphatiming", 1).run();

        assertThat(days1).exists();
        assertThat(days2).exists();
        assertThat(days29).exists();
    }

    @Test
    void filesOfOtherProgramsAndOtherFilesStay() throws IOException {
        Path tui = logFile("alphatiming-tui", 100);
        Path cli = logFile("alphatiming-cli", 100);
        Path other = Files.writeString(dir.resolve("alphatiming-notes.log"), "notes");
        Files.setLastModifiedTime(other, FileTime.from(NOW.minusDays(100).toInstant(ZoneOffset.UTC)));

        cleanup("alphatiming", 0).run();

        assertThat(tui).exists();
        assertThat(cli).exists();
        assertThat(other).exists();
    }

    @Test
    void eachProgramCleansOnlyItsOwnLogFiles() throws IOException {
        Path gui = logFile("alphatiming", 100);
        Path tui = logFile("alphatiming-tui", 100);

        cleanup("alphatiming-tui", 0).run();

        assertThat(gui).exists();
        assertThat(tui).doesNotExist();
    }

    @Test
    void aMissingLogDirectoryIsNoProblem() {
        LogFileCleanup cleanup = new LogFileCleanup(dir.resolve("missing"), "alphatiming", Duration.ofDays(30), 0, () -> NOW);

        assertThatCode(cleanup::run).doesNotThrowAnyException();
    }

    @Test
    void aLogFileThatCannotBeDeletedDoesNotStopTheOthers() throws IOException {
        Path undeletable = Files.createDirectory(dir.resolve("alphatiming-" + NAME_TIME.format(NOW.minusDays(50)) + ".log"));
        Files.writeString(undeletable.resolve("content"), "x");
        Files.setLastModifiedTime(undeletable, FileTime.from(NOW.minusDays(50).toInstant(ZoneOffset.UTC)));
        Path days40 = logFile("alphatiming", 40);

        assertThatCode(cleanup("alphatiming", 0)::run).doesNotThrowAnyException();

        assertThat(undeletable).exists();
        assertThat(days40).doesNotExist();
    }
}
