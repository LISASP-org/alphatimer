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
            assertThat(files.map(p -> p.getFileName().toString())).containsExactly("a.heat");
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
