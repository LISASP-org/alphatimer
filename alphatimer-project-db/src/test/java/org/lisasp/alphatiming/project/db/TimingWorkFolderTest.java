package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TimingWorkFolderTest {

    @TempDir
    Path dir;

    private Path work() throws IOException {
        return Files.createDirectories(dir.resolve("work"));
    }

    private long workFiles() throws IOException {
        try (var files = Files.list(work())) {
            return files.count();
        }
    }

    @Test
    void workFilesOfOpenDatabasesAreNotOfferedForRecovery() throws Exception {
        try (TimingDatabase live = TimingDatabase.createNew(work())) {
            assertThat(TimingWorkFolder.findLeftovers(work())).isEmpty();
            live.close();
            assertThat(TimingWorkFolder.findLeftovers(work())).hasSize(1);
        }
    }

    @Test
    void discardRemovesWorkFileAndItsLock() throws Exception {
        TimingDatabase db = TimingDatabase.createNew(work());
        db.close();
        TimingWorkFolder.discard(TimingWorkFolder.findLeftovers(work()).get(0).workFile());
        assertThat(workFiles()).isZero();
    }

    @Test
    void discardRemovesTheNoteOfTheProjectFile() throws Exception {
        Path projectFile = Files.writeString(dir.resolve("x.timing"), "saved");
        TimingDatabase crashed = TimingDatabase.createNew(work());
        Path leftover = crashed.workFile();
        TimingWorkFolder.recordOrigin(leftover, projectFile);
        crashed.close();
        TimingWorkFolder.discard(leftover);
        assertThat(workFiles()).isZero();
    }
}
