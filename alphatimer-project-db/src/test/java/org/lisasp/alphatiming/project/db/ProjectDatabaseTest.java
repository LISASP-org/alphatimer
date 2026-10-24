package org.lisasp.alphatiming.project.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectDatabaseTest {

    @TempDir
    Path dir;

    private static long countSettings(ProjectDatabase db) {
        return db.read(em -> em.createQuery("select count(s) from SettingsEntity s", Long.class).getSingleResult());
    }

    @Test
    void writeSetsDirtyAndSnapshotRoundTrips() throws Exception {
        Path file = dir.resolve("a.heat");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            assertThat(db.isDirty()).isFalse();
            db.write(em -> {
                em.persist(new SettingsEntity("host", 1234, 60, 1999));
                return null;
            });
            assertThat(db.isDirty()).isTrue();
            db.snapshotTo(file);
            db.markClean();
            assertThat(db.isDirty()).isFalse();
            db.closeAndDelete();
        }
        try (ProjectDatabase db = ProjectDatabase.openCopy(file, dir)) {
            assertThat(countSettings(db)).isEqualTo(1);
            assertThat(db.isDirty()).isFalse();
            db.closeAndDelete();
        }
    }

    @Test
    void snapshotReplacesExistingFileAtomicallyAndLeavesNoTempFile() throws Exception {
        Path file = dir.resolve("a.heat");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            db.snapshotTo(file);
            db.write(em -> {
                em.persist(new SettingsEntity("h", 1, 60, 0));
                return null;
            });
            db.snapshotTo(file);
            db.closeAndDelete();
        }
        try (var files = Files.list(dir)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("a.heat", "a-previous.heat");
        }
    }

    private void saveWithSettings(ProjectDatabase db, Path file, int count) throws IOException {
        db.write(em -> {
            em.createQuery("delete from SettingsEntity").executeUpdate();
            for (int i = 0; i < count; i++) {
                em.persist(new SettingsEntity("h", 1, 60, 0));
            }
            return null;
        });
        db.snapshotTo(file);
    }

    private long settingsIn(Path file) throws IOException {
        try (ProjectDatabase db = ProjectDatabase.openCopy(file, dir)) {
            long count = countSettings(db);
            db.closeAndDelete();
            return count;
        }
    }

    @Test
    void savingAgainKeepsThePreviousStateNextToTheFile() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            saveWithSettings(db, file, 1);
            saveWithSettings(db, file, 2);
            db.closeAndDelete();
        }
        Path previous = projects.resolve("wk-previous.timing");
        assertThat(previous).exists();
        assertThat(settingsIn(previous)).isEqualTo(1);
        assertThat(settingsIn(file)).isEqualTo(2);
    }

    @Test
    void aFurtherSaveReplacesThePreviousFile() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            saveWithSettings(db, file, 1);
            saveWithSettings(db, file, 2);
            saveWithSettings(db, file, 3);
            db.closeAndDelete();
        }
        Path previous = projects.resolve("wk-previous.timing");
        assertThat(previous).exists();
        assertThat(settingsIn(previous)).isEqualTo(2);
        assertThat(settingsIn(file)).isEqualTo(3);
    }

    @Test
    void theFirstSaveCreatesNoPreviousFile() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            saveWithSettings(db, projects.resolve("wk.timing"), 1);
            db.closeAndDelete();
        }
        try (var files = Files.list(projects)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("wk.timing");
        }
    }

    @Test
    void openRejectsNonSqliteFileAndCreatesNoWorkFile() throws Exception {
        Path bad = dir.resolve("bad.heat");
        Files.writeString(bad, "this is not a database");
        Path work = Files.createDirectory(dir.resolve("work"));
        assertThatThrownBy(() -> ProjectDatabase.openCopy(bad, work)).isInstanceOf(InvalidProjectFileException.class);
        try (var files = Files.list(work)) {
            assertThat(files).isEmpty();
        }
    }

    @Test
    void concurrentWritersAreSerialized() throws Exception {
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            var pool = Executors.newFixedThreadPool(4);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                int n = i;
                futures.add(pool.submit(() -> db.write(em -> {
                    em.persist(new SettingsEntity("h" + n, n + 1, 60, 0));
                    return null;
                })));
            }
            for (Future<?> f : futures) {
                f.get(20, TimeUnit.SECONDS);
            }
            pool.shutdown();
            assertThat(countSettings(db)).isEqualTo(40);
            db.closeAndDelete();
        }
    }
}
