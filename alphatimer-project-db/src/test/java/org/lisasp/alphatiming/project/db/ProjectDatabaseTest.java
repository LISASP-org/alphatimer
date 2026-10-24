package org.lisasp.alphatiming.project.db;

import org.hibernate.Session;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.sqlite.ProgressHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

class ProjectDatabaseTest {

    @TempDir
    Path dir;

    private static long countSettings(ProjectDatabase db) {
        return db.read(em -> em.createQuery("select count(s) from SettingsEntity s", Long.class).getSingleResult());
    }

    @Test
    void aNewProjectIdentifiesAHeatByEventAndHeatNumberOnly() throws Exception {
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            List<Object> heatColumns = db.read(em -> em.createNativeQuery("select name from pragma_table_info('heat')").getResultList());
            List<Object> uniqueKeys = db.read(em -> em.createNativeQuery(
                    "select group_concat(ii.name, ',') from pragma_index_list('heat') il, pragma_index_info(il.name) ii where il.\"unique\" = 1 group by il.name")
                                                 .getResultList());

            assertThat(heatColumns).doesNotContain("competition");
            assertThat(uniqueKeys).containsExactly("event,heatNumber");
            db.closeAndDelete();
        }
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
    void aSaveStartedDuringAnotherSaveOfTheSameFileWaitsForIt() throws Exception {
        Path projects = Files.createDirectory(dir.resolve("projects"));
        Path file = projects.resolve("wk.timing");
        Path previous = projects.resolve("wk-previous.timing");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            saveWithSettings(db, file, 1);
            db.write(em -> {
                em.persist(new SettingsEntity("h", 1, 60, 0));
                return null;
            });
            CountDownLatch tempFileWritten = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            pauseWhenTempFileAppears(db, projects, tempFileWritten, release);

            FutureTask<Void> first = snapshotTask(db, file);
            new Thread(first).start();
            assertThat(tempFileWritten.await(20, TimeUnit.SECONDS)).isTrue();
            FutureTask<Void> second = snapshotTask(db, file);
            Thread secondThread = new Thread(second);
            secondThread.start();
            await().atMost(20, TimeUnit.SECONDS).until(() -> secondThread.getState() == Thread.State.WAITING);
            release.countDown();

            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
            db.closeAndDelete();
        }
        try (var files = Files.list(projects)) {
            assertThat(files.map(p -> p.getFileName().toString())).containsExactlyInAnyOrder("wk.timing", "wk-previous.timing");
        }
        assertThat(settingsIn(previous)).isEqualTo(2);
        assertThat(settingsIn(file)).isEqualTo(2);
    }

    private static FutureTask<Void> snapshotTask(ProjectDatabase db, Path file) {
        return new FutureTask<>(() -> {
            db.snapshotTo(file);
            return null;
        });
    }

    private static void pauseWhenTempFileAppears(ProjectDatabase db, Path projects, CountDownLatch reached, CountDownLatch release) {
        AtomicBoolean paused = new AtomicBoolean();
        db.read(em -> {
            em.unwrap(Session.class).doWork(connection -> ProgressHandler.setHandler(connection, 1, new ProgressHandler() {
                @Override
                protected int progress() {
                    if (!paused.get() && hasTempFile(projects)) {
                        paused.set(true);
                        reached.countDown();
                        awaitUninterruptibly(release);
                    }
                    return 0;
                }
            }));
            return null;
        });
    }

    private static boolean hasTempFile(Path projects) {
        try (var files = Files.list(projects)) {
            return files.anyMatch(p -> p.getFileName().toString().endsWith(".tmp"));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void awaitUninterruptibly(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
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
    void aNewProjectHasNoCheckConstraintsAndTheCurrentSchemaVersion() throws Exception {
        try (ProjectDatabase db = ProjectDatabase.createNew(dir);
             var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + db.workFile());
             var st = connection.createStatement()) {
            List<String> tables = new ArrayList<>();
            try (var rs = st.executeQuery("select sql from sqlite_master where type = 'table' and sql like '%check%'")) {
                while (rs.next()) {
                    tables.add(rs.getString(1));
                }
            }
            assertThat(tables).isEmpty();
            try (var rs = st.executeQuery("pragma user_version")) {
                assertThat(rs.getInt(1)).isEqualTo(SchemaMigrations.CURRENT_VERSION);
            }
        }
    }

    @Test
    void aSavedProjectKeepsItsSchemaVersion() throws Exception {
        Path file = dir.resolve("a.timing");
        try (ProjectDatabase db = ProjectDatabase.createNew(dir)) {
            db.snapshotTo(file);
            db.closeAndDelete();
        }
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + file);
             var st = connection.createStatement();
             var rs = st.executeQuery("pragma user_version")) {
            assertThat(rs.getInt(1)).isEqualTo(SchemaMigrations.CURRENT_VERSION);
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
