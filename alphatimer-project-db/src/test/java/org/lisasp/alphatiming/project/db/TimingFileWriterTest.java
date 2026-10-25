package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TimingFileWriterTest {

    @TempDir
    Path dir;

    private void save(TimingFileWriter writer, Path file, String content) throws IOException {
        Path snapshot = Files.writeString(dir.resolve("snapshot-" + UUID.randomUUID() + ".tmp"), content);
        writer.write(snapshot, file);
    }

    private static List<Path> tempFilesIn(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".tmp")).toList();
        }
    }

    @Test
    void snapshotReplacesExistingFileAtomicallyAndLeavesNoTempFile() throws Exception {
        Path file = dir.resolve("a.heat");
        TimingFileWriter writer = new TimingFileWriter();
        save(writer, file, "1");
        save(writer, file, "2");
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("a.heat", "a-previous.heat");
        }
    }

    @Test
    void savingAgainKeepsThePreviousStateNextToTheFile() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        TimingFileWriter writer = new TimingFileWriter();
        save(writer, file, "1");
        save(writer, file, "2");
        Path previous = projects.resolve("wk-previous.timing");
        assertThat(previous).exists();
        assertThat(previous).hasContent("1");
        assertThat(file).hasContent("2");
    }

    @Test
    void aFurtherSaveReplacesThePreviousFile() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        TimingFileWriter writer = new TimingFileWriter();
        save(writer, file, "1");
        save(writer, file, "2");
        save(writer, file, "3");
        Path previous = projects.resolve("wk-previous.timing");
        assertThat(previous).exists();
        assertThat(previous).hasContent("2");
        assertThat(file).hasContent("3");
    }

    @Test
    void aSaveToAnotherFileSystemCopiesTheFileAndLeavesNoTempFiles() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        TimingFileWriter writer = new TimingFileWriter((source, target) -> {
            throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "other file system");
        });
        save(writer, file, "1");
        save(writer, file, "2");
        assertThat(file).hasContent("2");
        assertThat(projects.resolve("wk-previous.timing")).hasContent("1");
        assertThat(tempFilesIn(dir)).isEmpty();
        try (var files = Files.list(projects)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("wk.timing", "wk-previous.timing");
        }
    }

    @Test
    void aSaveFailingToMoveTheFileKeepsTheOldFileAndLeavesNoTempFiles() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        save(new TimingFileWriter(), file, "1");
        TimingFileWriter failing = new TimingFileWriter((source, target) -> {
            throw new IOException("disk full");
        });
        assertThatThrownBy(() -> save(failing, file, "2")).isInstanceOf(IOException.class).hasMessage("disk full");
        assertThat(file).hasContent("1");
        assertThat(tempFilesIn(dir)).isEmpty();
        try (var files = Files.list(projects)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("wk.timing");
        }
    }

    @Test
    void theFirstSaveCreatesNoPreviousFile() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        save(new TimingFileWriter(), projects.resolve("wk.timing"), "1");
        try (var files = Files.list(projects)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("wk.timing");
        }
    }
}
